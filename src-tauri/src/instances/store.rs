//! Instances on disk:
//!
//! ```text
//! instances/<id>/
//!   instance.json   name, version, loader, RAM, Java, JVM args
//!   minecraft/      the game folder: mods, saves, resourcepacks, config, ...
//! ```
//!
//! The folder name (`id`) is fixed at creation; renaming only changes `name`.

use std::fs;
use std::path::{Path, PathBuf};
use std::sync::Mutex;
use std::time::{SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Serialize};

use crate::download::{fabric, forge};
use crate::error::{AppError, AppResult};

const FILE: &str = "instance.json";
const GAME_DIR: &str = "minecraft";
const SUBDIRS: &[&str] = &["mods", "saves", "resourcepacks", "config"];

/// Serializes ID allocation so two creates can't pick the same folder.
static LOCK: Mutex<()> = Mutex::new(());

#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "kebab-case")]
pub enum Loader {
    #[default]
    Vanilla,
    /// Minecraft Forge; the Novo Client mod's loader on 1.8.9.
    Forge,
    /// Fabric; the Novo Client mod's loader on modern versions.
    Fabric,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Instance {
    /// Folder name under `instances/`.
    pub id: String,
    pub name: String,
    /// Minecraft version ID, e.g. `1.8.9`.
    pub version: String,
    #[serde(default)]
    pub loader: Loader,
    /// Full loader version, e.g. Forge `1.8.9-11.15.1.2318-1.8.9`.
    #[serde(default)]
    pub loader_version: Option<String>,
    /// Overrides the launcher's memory setting.
    #[serde(default)]
    pub memory_mb: Option<u32>,
    /// Overrides the launcher's Java setting.
    #[serde(default)]
    pub java_path: Option<String>,
    /// Extra JVM arguments, space-separated.
    #[serde(default)]
    pub jvm_args: String,
    /// Install the NovoMC Client mod (Forge 1.8.9 instances only).
    #[serde(default = "default_true")]
    pub client_mod: bool,
    /// Unix time in ms.
    pub created_at: u64,
    #[serde(default)]
    pub last_played: Option<u64>,
}

impl Instance {
    /// The version ID to launch: vanilla's, or the loader profile's.
    pub fn launch_version(&self) -> String {
        match (self.loader, &self.loader_version) {
            (Loader::Forge, Some(forge)) => forge::version_id(&self.version, forge),
            (Loader::Fabric, Some(loader)) => fabric::version_id(&self.version, loader),
            _ => self.version.clone(),
        }
    }
}

/// A Forge instance needs a supported Minecraft version and a matching build;
/// a Fabric instance needs a loader version.
fn check_loader(version: &str, loader: Loader, loader_version: Option<&str>) -> AppResult<()> {
    if loader == Loader::Vanilla {
        return Ok(());
    }
    if loader == Loader::Fabric {
        // Whether Fabric supports the version is checked when it installs.
        return match loader_version {
            Some(v) if !v.is_empty() => Ok(()),
            _ => Err(AppError::Other("Pick a Fabric Loader version".into())),
        };
    }
    if !forge::SUPPORTED.contains(&version) {
        return Err(AppError::Other(format!("Forge isn't available for Minecraft {version} in Novo Client yet")));
    }
    match loader_version {
        Some(v) if v.starts_with(&format!("{version}-")) => Ok(()),
        _ => Err(AppError::Other("Pick a Forge version".into())),
    }
}

fn default_true() -> bool {
    true
}

pub fn now_ms() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_millis() as u64).unwrap_or(0)
}

pub fn instance_dir(root: &Path, id: &str) -> PathBuf {
    root.join("instances").join(id)
}

/// The game folder passed to Minecraft as `--gameDir`.
pub fn game_dir(root: &Path, id: &str) -> PathBuf {
    instance_dir(root, id).join(GAME_DIR)
}

/// Rejects IDs that could escape the instances folder.
fn check_id(id: &str) -> AppResult<()> {
    let ok = !id.is_empty() && id.chars().all(|c| c.is_ascii_alphanumeric() || c == '-' || c == '_');
    if ok {
        Ok(())
    } else {
        Err(AppError::Other(format!("Invalid instance ID: {id}")))
    }
}

fn clean_name(name: &str) -> AppResult<String> {
    let name = name.trim();
    if name.is_empty() || name.chars().count() > 40 {
        return Err(AppError::Other("Instance names must be 1-40 characters".into()));
    }
    Ok(name.to_string())
}

/// Folder-safe slug of `name` that isn't taken yet: `my-pvp`, `my-pvp-2`, ...
fn free_id(root: &Path, name: &str) -> String {
    let mut slug: String = name
        .to_lowercase()
        .chars()
        .map(|c| if c.is_ascii_alphanumeric() { c } else { '-' })
        .collect::<String>()
        .split('-')
        .filter(|s| !s.is_empty())
        .collect::<Vec<_>>()
        .join("-");
    slug.truncate(32);
    if slug.is_empty() {
        slug = "instance".into();
    }
    let mut id = slug.clone();
    let mut n = 2;
    while instance_dir(root, &id).exists() {
        id = format!("{slug}-{n}");
        n += 1;
    }
    id
}

pub fn load(root: &Path, id: &str) -> AppResult<Instance> {
    check_id(id)?;
    let path = instance_dir(root, id).join(FILE);
    let bytes = fs::read(&path).map_err(|e| match e.kind() {
        std::io::ErrorKind::NotFound => AppError::Other(format!("Instance {id} not found")),
        _ => e.into(),
    })?;
    let mut inst: Instance = serde_json::from_slice(&bytes)?;
    inst.id = id.to_string(); // the folder is the source of truth
    Ok(inst)
}

pub fn save(root: &Path, inst: &Instance) -> AppResult<()> {
    check_id(&inst.id)?;
    let path = instance_dir(root, &inst.id).join(FILE);
    let tmp = path.with_extension("json.tmp");
    fs::write(&tmp, serde_json::to_vec_pretty(inst)?)?;
    fs::rename(tmp, path)?;
    Ok(())
}

/// Every instance, most recently played (or created) first. Folders without
/// a readable `instance.json` are skipped.
pub fn list(root: &Path) -> AppResult<Vec<Instance>> {
    let mut out: Vec<Instance> = fs::read_dir(root.join("instances"))?
        .flatten()
        .filter(|e| e.path().join(FILE).is_file())
        .filter_map(|e| load(root, &e.file_name().to_string_lossy()).ok())
        .collect();
    out.sort_by_key(|i| std::cmp::Reverse(i.last_played.unwrap_or(i.created_at)));
    Ok(out)
}

fn create_layout(root: &Path, id: &str) -> AppResult<()> {
    let game = game_dir(root, id);
    for sub in SUBDIRS {
        fs::create_dir_all(game.join(sub))?;
    }
    Ok(())
}

pub fn create(root: &Path, name: &str, version: &str, loader: Loader, loader_version: Option<String>) -> AppResult<Instance> {
    let name = clean_name(name)?;
    let version = version.trim();
    if version.is_empty() {
        return Err(AppError::Other("Pick a Minecraft version".into()));
    }
    check_loader(version, loader, loader_version.as_deref())?;
    let _guard = LOCK.lock().unwrap_or_else(|e| e.into_inner());
    let inst = Instance {
        id: free_id(root, &name),
        name,
        version: version.to_string(),
        loader,
        loader_version: loader_version.filter(|_| loader != Loader::Vanilla),
        memory_mb: None,
        java_path: None,
        jvm_args: String::new(),
        client_mod: true,
        created_at: now_ms(),
        last_played: None,
    };
    create_layout(root, &inst.id)?;
    save(root, &inst)?;
    Ok(inst)
}

/// Saves edited settings. The ID and creation time can't change.
pub fn update(root: &Path, mut edited: Instance) -> AppResult<Instance> {
    let current = load(root, &edited.id)?;
    edited.name = clean_name(&edited.name)?;
    check_loader(&edited.version, edited.loader, edited.loader_version.as_deref())?;
    if edited.loader == Loader::Vanilla {
        edited.loader_version = None;
    }
    if let Some(mb) = edited.memory_mb {
        if !(512..=65536).contains(&mb) {
            return Err(AppError::Other("Memory must be between 512 MB and 64 GB".into()));
        }
    }
    edited.java_path = edited.java_path.map(|p| p.trim().to_string()).filter(|p| !p.is_empty());
    edited.jvm_args = edited.jvm_args.trim().to_string();
    edited.created_at = current.created_at;
    save(root, &edited)?;
    Ok(edited)
}

fn copy_dir(src: &Path, dst: &Path) -> std::io::Result<()> {
    fs::create_dir_all(dst)?;
    for entry in fs::read_dir(src)? {
        let entry = entry?;
        let to = dst.join(entry.file_name());
        if entry.file_type()?.is_dir() {
            copy_dir(&entry.path(), &to)?;
        } else {
            fs::copy(entry.path(), to)?;
        }
    }
    Ok(())
}

/// Copies an instance, including its worlds and mods (but not its logs).
pub fn duplicate(root: &Path, id: &str) -> AppResult<Instance> {
    let source = load(root, id)?;
    let _guard = LOCK.lock().unwrap_or_else(|e| e.into_inner());
    let name: String = format!("{} (copy)", source.name).chars().take(40).collect();
    let copy = Instance { id: free_id(root, &name), name, created_at: now_ms(), last_played: None, ..source };

    let (from, to) = (game_dir(root, id), game_dir(root, &copy.id));
    fs::create_dir_all(&to)?;
    for entry in fs::read_dir(&from)? {
        let entry = entry?;
        let name = entry.file_name();
        if name == "logs" || name == "crash-reports" {
            continue;
        }
        if entry.file_type()?.is_dir() {
            copy_dir(&entry.path(), &to.join(&name))?;
        } else {
            fs::copy(entry.path(), to.join(&name))?;
        }
    }
    create_layout(root, &copy.id)?;
    save(root, &copy)?;
    Ok(copy)
}

/// Moves an instance to the Recycle Bin / Trash so it can be restored.
pub fn delete(root: &Path, id: &str) -> AppResult<()> {
    check_id(id)?;
    let dir = instance_dir(root, id);
    if !dir.join(FILE).is_file() {
        return Err(AppError::Other(format!("Instance {id} not found")));
    }
    trash::delete(&dir).map_err(|e| AppError::Other(format!("Couldn't move {id} to the Recycle Bin: {e}")))
}

pub fn mark_played(root: &Path, id: &str) -> AppResult<()> {
    let mut inst = load(root, id)?;
    inst.last_played = Some(now_ms());
    save(root, &inst)
}

/// Before instances existed, every version shared `instances/default/` as its
/// game folder. Turns that folder into a real "Default" instance so its
/// worlds and settings aren't lost.
pub fn migrate_shared_default(root: &Path, version: Option<&str>) -> AppResult<()> {
    let dir = instance_dir(root, "default");
    if !dir.is_dir() || dir.join(FILE).exists() {
        return Ok(());
    }
    let entries: Vec<_> = fs::read_dir(&dir)?.flatten().collect();
    let Some(version) = version.filter(|_| !entries.is_empty()) else {
        return Ok(());
    };
    let game = dir.join(GAME_DIR);
    fs::create_dir_all(&game)?;
    for entry in entries {
        if entry.file_name() != GAME_DIR {
            fs::rename(entry.path(), game.join(entry.file_name()))?;
        }
    }
    let inst = Instance {
        id: "default".into(),
        name: "Default".into(),
        version: version.to_string(),
        loader: Loader::Vanilla,
        loader_version: None,
        memory_mb: None,
        java_path: None,
        jvm_args: String::new(),
        client_mod: true,
        created_at: now_ms(),
        last_played: None,
    };
    create_layout(root, "default")?;
    save(root, &inst)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn temp_root(name: &str) -> PathBuf {
        let dir = std::env::temp_dir().join(format!("novomc-test-{name}-{}", now_ms()));
        fs::create_dir_all(dir.join("instances")).unwrap();
        dir
    }

    #[test]
    fn create_rename_duplicate() {
        let root = temp_root("crud");
        let a = create(&root, "My PvP!", "1.8.9", Loader::Vanilla, None).unwrap();
        assert_eq!(a.id, "my-pvp");
        assert!(game_dir(&root, &a.id).join("mods").is_dir());
        assert_eq!(create(&root, "my pvp", "1.8.9", Loader::Vanilla, None).unwrap().id, "my-pvp-2");

        fs::write(game_dir(&root, &a.id).join("saves").join("world.dat"), b"x").unwrap();
        fs::create_dir_all(game_dir(&root, &a.id).join("logs")).unwrap();
        let copy = duplicate(&root, &a.id).unwrap();
        assert_eq!(copy.name, "My PvP! (copy)");
        assert!(game_dir(&root, &copy.id).join("saves").join("world.dat").is_file());
        assert!(!game_dir(&root, &copy.id).join("logs").exists());

        let renamed = update(&root, Instance { name: "Ranked".into(), ..a.clone() }).unwrap();
        assert_eq!((renamed.id.as_str(), renamed.name.as_str()), ("my-pvp", "Ranked"));
        assert_eq!(list(&root).unwrap().len(), 3);
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn rejects_bad_input() {
        let root = temp_root("bad");
        assert!(create(&root, "   ", "1.8.9", Loader::Vanilla, None).is_err());
        assert!(load(&root, "../escape").is_err());
        // Forge needs a supported version and a matching build.
        assert!(create(&root, "a", "1.21.4", Loader::Forge, Some("1.21.4-54.0.0".into())).is_err());
        assert!(create(&root, "b", "1.8.9", Loader::Forge, None).is_err());
        assert!(create(&root, "c", "1.8.9", Loader::Forge, Some("1.12.2-14.23.5.2859".into())).is_err());
        let forge = create(&root, "d", "1.8.9", Loader::Forge, Some("1.8.9-11.15.1.2318-1.8.9".into())).unwrap();
        assert_eq!(forge.launch_version(), "1.8.9-forge1.8.9-11.15.1.2318-1.8.9");
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn migrates_shared_default_folder() {
        let root = temp_root("migrate");
        let old = instance_dir(&root, "default");
        fs::create_dir_all(old.join("saves").join("World")).unwrap();
        fs::write(old.join("options.txt"), b"fov:0").unwrap();
        migrate_shared_default(&root, Some("1.21.4")).unwrap();
        let inst = load(&root, "default").unwrap();
        assert_eq!(inst.version, "1.21.4");
        assert!(game_dir(&root, "default").join("saves").join("World").is_dir());
        assert!(game_dir(&root, "default").join("options.txt").is_file());
        fs::remove_dir_all(root).unwrap();
    }
}
