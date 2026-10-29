//! The Vanilla+ survival client: a Fabric profile for Minecraft 1.20.1 or
//! 1.16.5 with a curated set of mods, shader packs and resource packs.
//!
//! The game itself is installed by the normal version + Fabric flow. This
//! module adds the content: `catalog.json` lists what the client offers, and
//! every item is installed from Modrinth, verified against its SHA-512 and
//! recorded in the instance's `survival.json`. Mods the catalogue item needs
//! are resolved from Modrinth's dependency list too, so a mod never lands
//! without its libraries.
//!
//! A survival instance runs no Novo Client mod, so `instances::mods::enforce`
//! leaves its `mods/` folder alone: this is a normal modded profile, not a
//! locked-down PvP one.
//!
//! Installing a shader makes it the active one (`optionsshaders.txt`) and
//! installing a resource pack switches it on (`options.txt`), because a pack
//! that is merely dropped in a folder changes nothing in game.

pub mod modrinth;

use std::collections::HashSet;
use std::io::Read;
use std::path::{Path, PathBuf};
use std::sync::{Arc, OnceLock};
use std::time::Duration;

use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Emitter};
use tokio_util::sync::CancellationToken;

use crate::download::fabric;
use crate::download::install::{self, Stage, Tracker, EVENT};
use crate::error::{AppError, AppResult};
use crate::instances::store::{self, Instance, Loader};
use crate::paths;

/// Tauri event carrying [`ProgressEvent`]s for bundle/individual installs.
pub const PROGRESS_EVENT: &str = "survival-progress";
/// Where an instance remembers what this module installed.
const MARKER: &str = "survival.json";

/// Mod ids every instance always has, so they never need a download.
const BUILTIN_MODS: &[&str] = &[
    "minecraft",
    "java",
    "fabricloader",
    "fabric",
    "fabric-api",
    "quilt_loader",
    "quilted_fabric_api",
];

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Edition {
    pub id: String,
    pub label: String,
    pub blurb: String,
    /// The Java major version the game needs.
    pub java: u32,
    #[serde(default)]
    pub recommended: bool,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Kind {
    /// A Fabric mod, installed into `mods/`.
    Mod,
    /// A resource pack, installed into `resourcepacks/` and switched on.
    Pack,
    /// A shader pack, installed into `shaderpacks/` and made active.
    Shader,
}

impl Kind {
    /// The Fabric loader to filter versions by, if this kind needs one.
    fn loader(self) -> Option<&'static str> {
        (self == Self::Mod).then_some("fabric")
    }

    /// The Modrinth project type this kind maps to.
    fn project_type(self) -> &'static str {
        match self {
            Self::Mod => "mod",
            Self::Pack => "resourcepack",
            Self::Shader => "shader",
        }
    }

    /// The instance folder this kind is installed into.
    fn dir(self) -> &'static str {
        match self {
            Self::Mod => "mods",
            Self::Pack => "resourcepacks",
            Self::Shader => "shaderpacks",
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Item {
    pub slug: String,
    pub kind: Kind,
    pub title: String,
    pub blurb: String,
    /// `core`, `animation`, `world`, `qol` or `shader`.
    pub category: String,
    /// The editions this item has a build for.
    pub editions: Vec<String>,
    /// Part of the bundle a new survival install sets up.
    #[serde(default)]
    pub default: bool,
    #[serde(default)]
    pub note: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
struct Catalog {
    editions: Vec<Edition>,
    items: Vec<Item>,
}

static CATALOG: OnceLock<Catalog> = OnceLock::new();

fn catalog() -> &'static Catalog {
    CATALOG.get_or_init(|| {
        serde_json::from_str(include_str!("catalog.json")).expect("survival/catalog.json must be valid")
    })
}

/// One installed file, as recorded in the instance's `survival.json`.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Installed {
    pub slug: String,
    pub kind: Kind,
    pub title: String,
    pub file: String,
    pub version: String,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct Marker {
    edition: String,
    items: Vec<Installed>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Status {
    pub instance_id: String,
    pub edition: String,
    pub installed: Vec<Installed>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct BundleResult {
    pub status: Status,
    /// Items that failed, with the reason, so the UI can name them.
    pub failed: Vec<String>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ProgressEvent {
    pub instance_id: String,
    /// The item being installed.
    pub slug: Option<String>,
    pub done: usize,
    pub total: usize,
    /// Set on failure.
    pub message: Option<String>,
}

impl ProgressEvent {
    fn new(instance_id: &str, slug: Option<String>, done: usize, total: usize) -> Self {
        Self { instance_id: instance_id.to_string(), slug, done, total, message: None }
    }

    fn failed(instance_id: &str, slug: String, message: String) -> Self {
        Self { instance_id: instance_id.to_string(), slug: Some(slug), done: 0, total: 0, message: Some(message) }
    }
}

/// The editions the survival client is built for.
#[tauri::command]
pub fn survival_editions() -> Vec<Edition> {
    catalog().editions.clone()
}

/// Everything the survival client can install.
#[tauri::command]
pub fn survival_catalog() -> Vec<Item> {
    catalog().items.clone()
}

#[tauri::command]
pub fn survival_status(instance_id: String) -> AppResult<Status> {
    let root = paths::data_dir()?;
    let marker = load_marker(&root, &instance_id)?;
    Ok(status(&instance_id, &marker))
}

/// Every instance that is a survival install (it has a marker file).
#[tauri::command]
pub fn survival_instances() -> AppResult<Vec<Status>> {
    let root = paths::data_dir()?;
    let mut out = Vec::new();
    for instance in store::list(&root)? {
        if let Ok(marker) = load_marker(&root, &instance.id) {
            out.push(status(&instance.id, &marker));
        }
    }
    Ok(out)
}

/// Installs the game and Fabric for `edition`, creates the instance, and
/// records it as a survival install. The bundle itself is a separate call so
/// the UI can show progress for it.
#[tauri::command]
pub async fn survival_create(app: AppHandle, edition: String) -> AppResult<Instance> {
    let root = paths::data_dir()?;
    let edition = catalog()
        .editions
        .iter()
        .find(|e| e.id == edition)
        .cloned()
        .ok_or_else(|| AppError::Other(format!("Minecraft {edition} isn't a survival edition")))?;
    let loader = fabric::latest_loader().await?;
    let version_id = fabric::version_id(&edition.id, &loader);

    let tracker = Arc::new(Tracker::default());
    let ticker = spawn_ticker(&app, tracker.clone(), version_id.clone());
    let cancel = CancellationToken::new();
    let result = async {
        if !install::is_installed(&root, &edition.id) {
            install::install(&edition.id, &tracker, &cancel).await?;
        }
        if !install::is_installed(&root, &version_id) {
            fabric::install(&edition.id, &loader, &tracker, &cancel).await?;
        }
        Ok::<(), AppError>(())
    }
    .await;
    ticker.abort();

    let (stage, message) = match &result {
        Ok(()) => (Stage::Done, None),
        Err(e) => (Stage::Failed, Some(e.to_string())),
    };
    tracker.set_stage(stage);
    let _ = app.emit(EVENT, tracker.event(&version_id, message));
    result?;

    let mut instance = store::create(&root, &format!("Survival {}", edition.id), &edition.id, Loader::Fabric, Some(loader))?;
    instance.client_mod = false;
    instance.memory_mb = Some(if edition.id == "1.20.1" { 4096 } else { 3072 });
    store::save(&root, &instance)?;
    save_marker(&root, &instance.id, &Marker { edition: edition.id.clone(), items: Vec::new() })?;
    Ok(instance)
}

/// Installs one catalogue item, or any Modrinth project when `kind` is given
/// (results from the shader and pack search aren't in the catalogue).
#[tauri::command]
pub async fn survival_install(
    app: AppHandle,
    instance_id: String,
    slug: String,
    kind: Option<Kind>,
) -> AppResult<Status> {
    let root = paths::data_dir()?;
    let mut marker = load_marker(&root, &instance_id)?;
    let (slug, kind, title) = match catalog().items.iter().find(|i| i.slug == slug).cloned() {
        Some(item) => {
            if !item.editions.iter().any(|e| e == &marker.edition) {
                return Err(AppError::Other(format!("{} has no build for Minecraft {}", item.title, marker.edition)));
            }
            (item.slug, item.kind, item.title)
        }
        None => {
            let kind = kind.ok_or_else(|| AppError::Other(format!("{slug} isn't in the survival catalogue")))?;
            (slug.clone(), kind, slug)
        }
    };
    let _ = app.emit(PROGRESS_EVENT, ProgressEvent::new(&instance_id, Some(slug.clone()), 0, 1));
    let edition = marker.edition.clone();
    match install_project(&root, &instance_id, &edition, &slug, kind, &title, &mut marker, 0).await {
        Ok(_) => {
            // A mod can need a library its Modrinth entry never lists.
            for id in complete_mods(&root, &instance_id, &mut marker).await {
                eprintln!("survival: {id} is required by an installed mod but was not found on Modrinth");
            }
            let _ = app.emit(PROGRESS_EVENT, ProgressEvent::new(&instance_id, None, 1, 1));
            Ok(status(&instance_id, &marker))
        }
        Err(e) => {
            let _ = app.emit(PROGRESS_EVENT, ProgressEvent::failed(&instance_id, slug.clone(), e.to_string()));
            Err(e)
        }
    }
}

/// Removes an installed item, its file and its options entry.
#[tauri::command]
pub fn survival_remove(instance_id: String, slug: String) -> AppResult<Status> {
    let root = paths::data_dir()?;
    let mut marker = load_marker(&root, &instance_id)?;
    if let Some(item) = marker.items.iter().find(|i| i.slug == slug).cloned() {
        let path = pack_dir(&root, &instance_id, item.kind)?.join(&item.file);
        if path.is_file() {
            std::fs::remove_file(&path)?;
        }
        let game = store::game_dir(&root, &instance_id);
        match item.kind {
            Kind::Pack => deactivate_pack(&game, &item.file)?,
            Kind::Shader => deactivate_shader(&game, &item.file)?,
            Kind::Mod => {}
        }
        marker.items.retain(|i| i.slug != slug);
        save_marker(&root, &instance_id, &marker)?;
    }
    Ok(status(&instance_id, &marker))
}

/// Sets up the whole bundle: every default item for the instance's edition.
#[tauri::command]
pub async fn survival_bundle(app: AppHandle, instance_id: String) -> AppResult<BundleResult> {
    let root = paths::data_dir()?;
    let mut marker = load_marker(&root, &instance_id)?;
    let edition = marker.edition.clone();
    let wanted: Vec<Item> = catalog()
        .items
        .iter()
        .filter(|i| i.default && i.editions.iter().any(|e| e == &edition))
        .cloned()
        .collect();
    let total = wanted.len();
    let mut done = 0;
    let mut failed = Vec::new();
    for item in &wanted {
        let _ = app.emit(PROGRESS_EVENT, ProgressEvent::new(&instance_id, Some(item.slug.clone()), done, total));
        if marker.items.iter().any(|i| i.slug == item.slug) {
            done += 1;
            continue;
        }
        match install_project(&root, &instance_id, &edition, &item.slug, item.kind, &item.title, &mut marker, 0).await {
            Ok(_) => done += 1,
            Err(e) => failed.push(format!("{}: {e}", item.title)),
        }
        let _ = app.emit(PROGRESS_EVENT, ProgressEvent::new(&instance_id, None, done, total));
    }
    // Finally, close the gap between Modrinth's metadata and the jars themselves.
    for id in complete_mods(&root, &instance_id, &mut marker).await {
        failed.push(format!("{id}: required by an installed mod, but no Fabric build was found"));
    }
    Ok(BundleResult { status: status(&instance_id, &marker), failed })
}

/// Searches Modrinth for more of a kind, limited to the instance's edition.
#[tauri::command]
pub async fn survival_search(instance_id: String, kind: Kind, query: String) -> AppResult<Vec<modrinth::Found>> {
    let root = paths::data_dir()?;
    let marker = load_marker(&root, &instance_id)?;
    modrinth::search(&query, kind.project_type(), &marker.edition, kind == Kind::Mod).await
}

/// Downloads one project into the instance, resolving required dependencies first.
#[allow(clippy::too_many_arguments)]
async fn install_project(
    root: &Path,
    instance_id: &str,
    edition: &str,
    slug: &str,
    kind: Kind,
    title: &str,
    marker: &mut Marker,
    depth: u8,
) -> AppResult<Installed> {
    if depth > 4 {
        return Err(AppError::Other("That mod's dependency chain is too deep".into()));
    }
    let dir = pack_dir(root, instance_id, kind)?;

    // Already installed and still on disk: nothing to download.
    if let Some(done) = marker.items.iter().find(|i| i.slug == slug) {
        if dir.join(&done.file).is_file() {
            return Ok(done.clone());
        }
    }

    let version = modrinth::latest(slug, edition, kind.loader())
        .await?
        .ok_or_else(|| AppError::Other(format!("{title} has no build for Minecraft {edition}")))?;
    let file = version
        .file()
        .cloned()
        .ok_or_else(|| AppError::Other(format!("{title} has no downloadable file for {edition}")))?;
    modrinth::download(&file, &dir).await?;

    // Libraries the mod can't start without. A missing one shouldn't lose the
    // mod the player asked for, so those failures are not fatal.
    if kind == Kind::Mod {
        for project_id in version.required() {
            let Ok(dependency) = modrinth::slug_of(&project_id).await else { continue };
            if dependency == slug {
                continue;
            }
            let _ = Box::pin(install_project(root, instance_id, edition, &dependency, Kind::Mod, &dependency, marker, depth + 1)).await;
        }
    }

    // Builds rename their file; drop the old one so the mods folder has no duplicates.
    if let Some(old) = marker.items.iter().find(|i| i.slug == slug).cloned() {
        if old.file != file.filename {
            let stale = dir.join(&old.file);
            if stale.is_file() {
                let _ = std::fs::remove_file(stale);
            }
        }
    }

    let installed = Installed {
        slug: slug.to_string(),
        kind,
        title: title.to_string(),
        file: file.filename.clone(),
        version: version.version_number.clone(),
    };
    marker.items.retain(|i| i.slug != slug);
    marker.items.push(installed.clone());
    save_marker(root, instance_id, marker)?;

    match kind {
        Kind::Pack => activate_pack(&store::game_dir(root, instance_id), &file.filename)?,
        Kind::Shader => activate_shader(&store::game_dir(root, instance_id), &file.filename)?,
        Kind::Mod => {}
    }
    Ok(installed)
}

fn status(instance_id: &str, marker: &Marker) -> Status {
    Status { instance_id: instance_id.to_string(), edition: marker.edition.clone(), installed: marker.items.clone() }
}

fn marker_path(root: &Path, instance_id: &str) -> PathBuf {
    store::instance_dir(root, instance_id).join(MARKER)
}

fn load_marker(root: &Path, instance_id: &str) -> AppResult<Marker> {
    store::load(root, instance_id)?;
    match std::fs::read(marker_path(root, instance_id)) {
        Ok(bytes) => Ok(serde_json::from_slice(&bytes)?),
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => {
            Err(AppError::Other(format!("{instance_id} isn't a survival install")))
        }
        Err(e) => Err(e.into()),
    }
}

fn save_marker(root: &Path, instance_id: &str, marker: &Marker) -> AppResult<()> {
    let path = marker_path(root, instance_id);
    let tmp = path.with_extension("json.tmp");
    std::fs::write(&tmp, serde_json::to_vec_pretty(marker)?)?;
    std::fs::rename(tmp, path)?;
    Ok(())
}

/// The folder a kind installs into, created if needed.
fn pack_dir(root: &Path, instance_id: &str, kind: Kind) -> AppResult<PathBuf> {
    let dir = store::game_dir(root, instance_id).join(kind.dir());
    std::fs::create_dir_all(&dir)?;
    Ok(dir)
}

/// A mod's id and hard dependencies, read from its own `fabric.mod.json`.
fn mod_metadata(path: &Path) -> Option<(String, Vec<String>)> {
    let file = std::fs::File::open(path).ok()?;
    let mut archive = zip::ZipArchive::new(file).ok()?;
    let mut entry = archive.by_name("fabric.mod.json").ok()?;
    let mut text = String::new();
    entry.read_to_string(&mut text).ok()?;
    let value: serde_json::Value = serde_json::from_str(&text).ok()?;
    let id = value.get("id")?.as_str()?.to_string();
    let depends: Vec<String> = value
        .get("depends")
        .and_then(|d| d.as_object())
        .map(|map| map.keys().cloned().collect())
        .unwrap_or_default();
    Some((id, depends))
}

/// Installs libraries that the installed mods require in their own metadata but
/// Modrinth does not declare as dependencies. Repurposed Structures is the
/// known case: its 1.20.1 build needs MidnightLib, which its Modrinth entry
/// never lists. Returns the ids that could not be resolved.
async fn complete_mods(root: &Path, instance_id: &str, marker: &mut Marker) -> Vec<String> {
    let Ok(mods) = pack_dir(root, instance_id, Kind::Mod) else { return Vec::new() };
    let edition = marker.edition.clone();
    let mut provided: HashSet<String> = BUILTIN_MODS.iter().map(|id| id.to_string()).collect();
    let mut required: HashSet<String> = HashSet::new();
    let jars: Vec<PathBuf> = std::fs::read_dir(&mods)
        .into_iter()
        .flatten()
        .flatten()
        .map(|entry| entry.path())
        .filter(|path| path.is_file() && path.extension().is_some_and(|e| e.eq_ignore_ascii_case("jar")))
        .collect();
    for path in jars {
        if let Some((id, depends)) = mod_metadata(&path) {
            provided.insert(id);
            required.extend(depends);
        }
    }

    // Fabric API registers a family of `fabric-*` modules from one jar.
    if provided.contains("fabric-api") || provided.contains("fabric") {
        required.retain(|id| !id.starts_with("fabric-"));
    }

    let mut missing: Vec<String> = required.difference(&provided).cloned().collect();
    missing.sort();
    let mut unresolved = Vec::new();
    for id in missing {
        // A Modrinth slug normally equals the mod id, which is how a library
        // like MidnightLib is found without a project mapping.
        match install_project(root, instance_id, &edition, &id, Kind::Mod, &id, marker, 0).await {
            Ok(_) => {}
            Err(_) => unresolved.push(id),
        }
    }
    unresolved
}

/// Makes a survival instance's mods complete, for callers outside this module
/// (a local server is created from one, so both sides need the same libraries).
pub async fn ensure_instance(root: &Path, instance_id: &str) -> AppResult<Vec<String>> {
    let mut marker = load_marker(root, instance_id)?;
    Ok(complete_mods(root, instance_id, &mut marker).await)
}

fn spawn_ticker(app: &AppHandle, tracker: Arc<Tracker>, version_id: String) -> tauri::async_runtime::JoinHandle<()> {
    let app = app.clone();
    tauri::async_runtime::spawn(async move {
        loop {
            let _ = app.emit(EVENT, tracker.event(&version_id, None));
            tokio::time::sleep(Duration::from_millis(150)).await;
        }
    })
}

/// Reads an options file into lines, or nothing when the game never wrote it.
fn read_lines(path: &Path) -> Vec<String> {
    std::fs::read_to_string(path)
        .map(|text| text.lines().map(str::to_string).collect())
        .unwrap_or_default()
}

fn write_lines(path: &Path, lines: &[String]) -> AppResult<()> {
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent)?;
    }
    std::fs::write(path, format!("{}\n", lines.join("\n")))?;
    Ok(())
}

/// Switches a resource pack on in `options.txt`, where the game keeps the list.
fn activate_pack(game: &Path, filename: &str) -> AppResult<()> {
    let entry = format!("file/{filename}");
    let path = game.join("options.txt");
    let mut lines = read_lines(&path);
    let mut found = false;
    for line in lines.iter_mut() {
        if let Some(rest) = line.strip_prefix("resourcePacks:") {
            found = true;
            let mut list: Vec<String> = serde_json::from_str(rest).unwrap_or_default();
            if !list.contains(&entry) {
                list.push(entry.clone());
            }
            *line = format!("resourcePacks:{}", serde_json::to_string(&list)?);
        } else if let Some(rest) = line.strip_prefix("incompatibleResourcePacks:") {
            let mut list: Vec<String> = serde_json::from_str(rest).unwrap_or_default();
            list.retain(|pack| pack != &entry);
            *line = format!("incompatibleResourcePacks:{}", serde_json::to_string(&list)?);
        }
    }
    if !found {
        let list = vec!["vanilla".to_string(), entry];
        lines.push(format!("resourcePacks:{}", serde_json::to_string(&list)?));
    }
    write_lines(&path, &lines)
}

/// Switches a resource pack back off.
fn deactivate_pack(game: &Path, filename: &str) -> AppResult<()> {
    let entry = format!("file/{filename}");
    let path = game.join("options.txt");
    if !path.is_file() {
        return Ok(());
    }
    let mut lines = read_lines(&path);
    for line in lines.iter_mut() {
        if let Some(rest) = line.strip_prefix("resourcePacks:") {
            let mut list: Vec<String> = serde_json::from_str(rest).unwrap_or_default();
            list.retain(|pack| pack != &entry);
            *line = format!("resourcePacks:{}", serde_json::to_string(&list)?);
        } else if let Some(rest) = line.strip_prefix("incompatibleResourcePacks:") {
            let mut list: Vec<String> = serde_json::from_str(rest).unwrap_or_default();
            list.retain(|pack| pack != &entry);
            *line = format!("incompatibleResourcePacks:{}", serde_json::to_string(&list)?);
        }
    }
    write_lines(&path, &lines)
}

/// Makes a shader pack the one Iris loads (`optionsshaders.txt`).
fn activate_shader(game: &Path, filename: &str) -> AppResult<()> {
    let path = game.join("optionsshaders.txt");
    let mut lines = read_lines(&path);
    let mut found = false;
    for line in lines.iter_mut() {
        if line.starts_with("shaderPack=") {
            *line = format!("shaderPack={filename}");
            found = true;
        }
    }
    if !found {
        lines.insert(0, "#Written by Novo Client".to_string());
        lines.insert(1, format!("shaderPack={filename}"));
    }
    write_lines(&path, &lines)
}

/// Clears the active shader if this pack was the one loaded.
fn deactivate_shader(game: &Path, filename: &str) -> AppResult<()> {
    let path = game.join("optionsshaders.txt");
    if !path.is_file() {
        return Ok(());
    }
    let mut lines = read_lines(&path);
    for line in lines.iter_mut() {
        if *line == format!("shaderPack={filename}") {
            *line = "shaderPack=".to_string();
        }
    }
    write_lines(&path, &lines)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn temp_game(tag: &str) -> PathBuf {
        let dir = std::env::temp_dir().join(format!("novo-survival-{tag}-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();
        dir
    }

    #[test]
    fn catalogue_is_consistent() {
        let catalog = catalog();
        assert_eq!(catalog.editions.len(), 2);
        let editions: Vec<&str> = catalog.editions.iter().map(|e| e.id.as_str()).collect();
        assert_eq!(editions, vec!["1.20.1", "1.16.5"]);

        let mut seen = std::collections::HashSet::new();
        for item in &catalog.items {
            assert!(seen.insert(item.slug.as_str()), "{} is listed twice", item.slug);
            assert!(!item.editions.is_empty(), "{} claims no editions", item.slug);
            for edition in &item.editions {
                assert!(editions.contains(&edition.as_str()), "{} claims unknown edition {edition}", item.slug);
            }
            assert!(!item.blurb.trim().is_empty(), "{} has no blurb", item.slug);
        }
        // A bundle with nothing in it would leave a survival install empty.
        assert!(catalog.items.iter().filter(|i| i.default).count() >= 10);
        assert!(catalog.items.iter().any(|i| i.kind == Kind::Shader));
    }

    /// The reported crash: Repurposed Structures' 1.20.1 jar needs MidnightLib,
    /// which its Modrinth entry never declares. Installing the mod must still
    /// pull the library in.
    #[tokio::test]
    #[ignore = "talks to Modrinth"]
    async fn installs_libraries_the_metadata_forgets() {
        let root = temp_game("deps");
        let instance = store::create(&root, "Deps", "1.20.1", Loader::Fabric, Some("0.20.1".into())).unwrap();
        save_marker(&root, &instance.id, &Marker { edition: "1.20.1".into(), items: Vec::new() }).unwrap();
        let mods = pack_dir(&root, &instance.id, Kind::Mod).unwrap();

        let version = modrinth::latest("repurposed-structures-fabric", "1.20.1", Some("fabric"))
            .await
            .unwrap()
            .expect("Repurposed Structures has a 1.20.1 build");
        modrinth::download(version.file().unwrap(), &mods).await.unwrap();

        let unresolved = ensure_instance(&root, &instance.id).await.unwrap();
        assert!(unresolved.is_empty(), "unresolved: {unresolved:?}");

        let jars: Vec<String> = std::fs::read_dir(&mods)
            .unwrap()
            .flatten()
            .map(|entry| entry.file_name().to_string_lossy().into_owned())
            .collect();
        assert!(
            jars.iter().any(|name| name.starts_with("midnightlib-")),
            "midnightlib should be installed: {jars:?}"
        );

        let marker = load_marker(&root, &instance.id).unwrap();
        assert!(marker.items.iter().any(|item| item.slug == "midnightlib"));
        std::fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn packs_are_switched_on_in_options() {
        let game = temp_game("pack");
        activate_pack(&game, "FreshAnimations_v1.11.1_1.20+.zip").unwrap();
        activate_pack(&game, "Other.zip").unwrap();
        activate_pack(&game, "FreshAnimations_v1.11.1_1.20+.zip").unwrap();

        let text = std::fs::read_to_string(game.join("options.txt")).unwrap();
        assert_eq!(
            text.trim(),
            "resourcePacks:[\"vanilla\",\"file/FreshAnimations_v1.11.1_1.20+.zip\",\"file/Other.zip\"]"
        );

        deactivate_pack(&game, "Other.zip").unwrap();
        let text = std::fs::read_to_string(game.join("options.txt")).unwrap();
        assert!(!text.contains("Other.zip"));
        assert!(text.contains("FreshAnimations_v1.11.1_1.20+.zip"));
        std::fs::remove_dir_all(game).unwrap();
    }

    #[test]
    fn pack_activation_keeps_the_rest_of_options() {
        let game = temp_game("options");
        std::fs::write(game.join("options.txt"), "fov:0.5\nresourcePacks:[\"vanilla\"]\nincompatibleResourcePacks:[\"file/X.zip\"]\n").unwrap();
        activate_pack(&game, "X.zip").unwrap();
        let text = std::fs::read_to_string(game.join("options.txt")).unwrap();
        assert!(text.starts_with("fov:0.5\n"));
        assert!(text.contains("resourcePacks:[\"vanilla\",\"file/X.zip\"]"));
        assert!(text.contains("incompatibleResourcePacks:[]"));
        std::fs::remove_dir_all(game).unwrap();
    }

    #[test]
    fn shaders_are_made_active() {
        let game = temp_game("shader");
        activate_shader(&game, "BSL_v10.1.8.zip").unwrap();
        activate_shader(&game, "photon_v1.4.zip").unwrap();
        let text = std::fs::read_to_string(game.join("optionsshaders.txt")).unwrap();
        assert!(text.contains("shaderPack=photon_v1.4.zip"));
        assert!(!text.contains("BSL_v10.1.8.zip"));

        deactivate_shader(&game, "photon_v1.4.zip").unwrap();
        let text = std::fs::read_to_string(game.join("optionsshaders.txt")).unwrap();
        assert!(text.contains("shaderPack=\n"));
        std::fs::remove_dir_all(game).unwrap();
    }
}
