//! Local servers the player runs from the launcher.
//!
//! A server is created from an instance, so its version, loader and mods match
//! what the client runs: the vanilla server jar comes from Mojang, Fabric adds
//! its loader launcher on top, and the instance's mods are copied in. The
//! launcher writes `eula.txt`, `server.properties` and start scripts, then
//! starts and stops it with the console's `stop` command.
//!
//! This is the local-only step: no hosting, no remote machines.

use std::collections::HashMap;
use std::io::{BufRead, BufReader, Write};
use std::path::{Path, PathBuf};
use std::process::{Child, ChildStdin, Command, Stdio};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex, MutexGuard};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Emitter, Manager, State};
use tokio_util::sync::CancellationToken;

use crate::download::engine::{hash_file, Progress};
use crate::download::fabric;
use crate::download::http;
use crate::download::manifest;
use crate::error::{AppError, AppResult};
use crate::instances::store::{self, Loader};
use crate::java::{self, runtime};
use crate::paths;
use crate::settings;

/// Tauri event carrying [`ServerState`].
pub const STATE_EVENT: &str = "server-state";
/// Tauri event carrying batches of console lines.
pub const LOG_EVENT: &str = "server-log";
/// The record every server folder has.
const FILE: &str = "server.json";
const DIRS: &[&str] = &["mods", "config", "logs"];

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalServer {
    pub id: String,
    pub name: String,
    pub version: String,
    pub loader: Loader,
    pub loader_version: Option<String>,
    pub port: u16,
    pub memory_mb: u32,
    /// The instance whose mods this server mirrors.
    pub instance_id: Option<String>,
    pub created_at: u64,
    /// Filled in when listing; a server is running while its process lives.
    #[serde(default)]
    pub running: bool,
    /// The Java the server needs, resolved when it was created.
    #[serde(default = "default_java_major")]
    java_major: u32,
    #[serde(default = "default_java_component")]
    java_component: String,
}

fn default_java_major() -> u32 {
    17
}

fn default_java_component() -> String {
    "java-runtime-gamma".into()
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "lowercase")]
pub enum Stage {
    Starting,
    Running,
    Stopping,
    Stopped,
    Failed,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerState {
    pub id: String,
    pub stage: Stage,
    pub message: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerLog {
    pub id: String,
    pub lines: Vec<String>,
}

/// One `key=value` line of a server's `server.properties`.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Property {
    pub key: String,
    pub value: String,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ModMatch {
    pub missing: Vec<String>,
    pub extra: Vec<String>,
    pub differing: Vec<String>,
    pub instance_mods: usize,
    pub server_mods: usize,
}

/// A spawned server process and the handles needed to talk to it.
struct Running {
    child: Mutex<Child>,
    stdin: Mutex<Option<ChildStdin>>,
    stopped: AtomicBool,
}

/// Servers running right now, by server ID.
#[derive(Default)]
pub struct Servers {
    running: Mutex<HashMap<String, Arc<Running>>>,
}

impl Servers {
    fn lock(&self) -> MutexGuard<'_, HashMap<String, Arc<Running>>> {
        self.running.lock().unwrap_or_else(|e| e.into_inner())
    }

    fn get(&self, id: &str) -> Option<Arc<Running>> {
        self.lock().get(id).cloned()
    }

    /// Whether the process is still alive; a dead entry is dropped.
    fn alive(&self, id: &str) -> bool {
        let Some(running) = self.get(id) else { return false };
        let mut child = running.child.lock().unwrap_or_else(|e| e.into_inner());
        match child.try_wait() {
            Ok(None) => true,
            _ => {
                drop(child);
                self.lock().remove(id);
                false
            }
        }
    }
}

fn now_ms() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_millis() as u64).unwrap_or(0)
}

fn servers_root(root: &Path) -> PathBuf {
    root.join("servers")
}

pub fn server_dir(root: &Path, id: &str) -> PathBuf {
    servers_root(root).join(id)
}

fn check_id(id: &str) -> AppResult<()> {
    let ok = !id.is_empty() && id.chars().all(|c| c.is_ascii_alphanumeric() || c == '-' || c == '_');
    if ok {
        Ok(())
    } else {
        Err(AppError::Other(format!("Invalid server ID: {id}")))
    }
}

fn slug(name: &str) -> String {
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
        slug = "server".into();
    }
    slug
}

fn free_id(root: &Path, name: &str) -> String {
    let base = slug(name);
    let mut id = base.clone();
    let mut n = 2;
    while server_dir(root, &id).exists() {
        id = format!("{base}-{n}");
        n += 1;
    }
    id
}

fn save(root: &Path, server: &LocalServer) -> AppResult<()> {
    check_id(&server.id)?;
    let dir = server_dir(root, &server.id);
    std::fs::create_dir_all(&dir)?;
    let path = dir.join(FILE);
    let tmp = path.with_extension("json.tmp");
    std::fs::write(&tmp, serde_json::to_vec_pretty(server)?)?;
    std::fs::rename(tmp, path)?;
    Ok(())
}

fn load(root: &Path, id: &str) -> AppResult<LocalServer> {
    check_id(id)?;
    let path = server_dir(root, id).join(FILE);
    let bytes = std::fs::read(&path).map_err(|e| match e.kind() {
        std::io::ErrorKind::NotFound => AppError::Other(format!("Server {id} not found")),
        _ => AppError::from(e),
    })?;
    let mut server: LocalServer = serde_json::from_slice(&bytes)?;
    server.id = id.to_string();
    Ok(server)
}

fn list(root: &Path) -> AppResult<Vec<LocalServer>> {
    let dir = servers_root(root);
    if !dir.is_dir() {
        return Ok(Vec::new());
    }
    let mut out: Vec<LocalServer> = std::fs::read_dir(dir)?
        .flatten()
        .filter(|e| e.path().join(FILE).is_file())
        .filter_map(|e| load(root, &e.file_name().to_string_lossy()).ok())
        .collect();
    out.sort_by_key(|s| std::cmp::Reverse(s.created_at));
    Ok(out)
}

fn emit(app: &AppHandle, id: &str, stage: Stage, message: Option<String>) {
    let _ = app.emit(STATE_EVENT, ServerState { id: id.to_string(), stage, message });
}

#[tauri::command]
pub fn list_local_servers(servers: State<'_, Servers>) -> AppResult<Vec<LocalServer>> {
    let root = paths::data_dir()?;
    let mut all = list(&root)?;
    for server in &mut all {
        server.running = servers.alive(&server.id);
    }
    Ok(all)
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct NewServer {
    name: Option<String>,
    instance_id: String,
    port: Option<u16>,
    memory_mb: Option<u32>,
}

/// Creates a server that mirrors an instance: same version, same loader, same mods.
#[tauri::command]
pub async fn create_local_server(app: AppHandle, server: NewServer) -> AppResult<LocalServer> {
    let root = paths::data_dir()?;
    let instance = store::load(&root, &server.instance_id)?;
    if instance.loader == Loader::Forge {
        return Err(AppError::Other("Forge servers aren't supported yet; Fabric and vanilla are".into()));
    }
    let name = server
        .name
        .map(|n| n.trim().to_string())
        .filter(|n| !n.is_empty())
        .unwrap_or_else(|| format!("{} Server", instance.name));
    if name.chars().count() > 40 {
        return Err(AppError::Other("Server names must be 40 characters or fewer".into()));
    }
    let memory_mb = server.memory_mb.unwrap_or(2048).clamp(512, 32768);
    let port = match server.port {
        Some(port) => port,
        None => free_port()?,
    };

    let loader_version = if instance.loader == Loader::Fabric {
        Some(match instance.loader_version.clone() {
            Some(v) => v,
            None => fabric::latest_loader().await?,
        })
    } else {
        None
    };

    let id = free_id(&root, &name);
    let dir = server_dir(&root, &id);
    std::fs::create_dir_all(&dir)?;
    emit(&app, &id, Stage::Starting, Some("Downloading the server".into()));

    let version_json = fetch_version_json(&instance.version).await?;
    let (java_major, java_component) = java_of(&version_json);
    let installed = install_server(&dir, &instance.version, &version_json, loader_version.as_deref()).await?;

    write_eula(&dir)?;
    write_properties(&dir, &name, port, &instance.version)?;
    write_scripts(&dir, installed.launcher.as_deref(), memory_mb)?;
    for sub in DIRS {
        std::fs::create_dir_all(dir.join(sub))?;
    }

    // The server copies the instance's mods, so their libraries must all be
    // present first. A survival instance can heal itself from Modrinth here.
    if let Ok(unresolved) = crate::survival::ensure_instance(&root, &instance.id).await {
        if !unresolved.is_empty() {
            emit(
                &app,
                &id,
                Stage::Starting,
                Some(format!("Missing libraries: {}", unresolved.join(", "))),
            );
        }
    }
    copy_instance_mods(&root, &instance, &dir)?;

    let server = LocalServer {
        id: id.clone(),
        name,
        version: instance.version.clone(),
        loader: instance.loader,
        loader_version,
        port,
        memory_mb,
        instance_id: Some(instance.id.clone()),
        created_at: now_ms(),
        running: false,
        java_major,
        java_component,
    };
    save(&root, &server)?;
    emit(&app, &id, Stage::Stopped, None);
    Ok(server)
}

#[tauri::command]
pub fn delete_local_server(servers: State<'_, Servers>, id: String) -> AppResult<()> {
    if servers.alive(&id) {
        return Err(AppError::Other("Stop the server before deleting it".into()));
    }
    let root = paths::data_dir()?;
    check_id(&id)?;
    let dir = server_dir(&root, &id);
    if !dir.join(FILE).is_file() {
        return Err(AppError::Other(format!("Server {id} not found")));
    }
    trash::delete(&dir).map_err(|e| AppError::Other(format!("Couldn't move {id} to the Recycle Bin: {e}")))
}

/// The editable settings of a server, in file order.
#[tauri::command]
pub fn read_server_properties(id: String) -> AppResult<Vec<Property>> {
    let root = paths::data_dir()?;
    let server = load(&root, &id)?;
    let path = server_dir(&root, &server.id).join("server.properties");
    if path.is_file() {
        return Ok(parse_properties(&std::fs::read_to_string(&path)?));
    }
    // A server whose file was removed still needs something to edit.
    let defaults = default_properties(&server);
    write_properties_file(&path, "", &defaults)?;
    Ok(defaults)
}

/// Writes the settings back to `server.properties`, returning the server so a
/// changed port shows up in the list.
#[tauri::command]
pub fn save_server_properties(id: String, values: Vec<Property>) -> AppResult<LocalServer> {
    let root = paths::data_dir()?;
    let mut server = load(&root, &id)?;
    let path = server_dir(&root, &server.id).join("server.properties");

    let mut updates: Vec<Property> = Vec::new();
    for property in values {
        let key = property_key(&property.key)?;
        if property.value.contains('\n') || property.value.contains('\r') {
            return Err(AppError::Other(format!("{key} can't contain a line break")));
        }
        let port = server.port;
        if key == "server-port" {
            if let Ok(parsed) = property.value.trim().parse::<u16>() {
                if parsed != port {
                    server.port = parsed;
                }
            }
        }
        updates.retain(|p| p.key != key);
        updates.push(Property { key, value: property.value });
    }

    let existing = std::fs::read_to_string(&path).unwrap_or_default();
    write_properties_file(&path, &existing, &updates)?;
    save(&root, &server)?;
    Ok(server)
}

/// A property name as it may appear in the file: no spaces or separators.
fn property_key(raw: &str) -> AppResult<String> {
    let key = raw.trim();
    if key.is_empty() || key.contains(|c: char| c.is_whitespace() || c == '=' || c == '#' || c == '!') {
        return Err(AppError::Other(format!("\"{raw}\" isn't a valid property name")));
    }
    Ok(key.to_string())
}

/// Keeps comments and ordering, replaces changed values, and appends new keys.
fn write_properties_file(path: &Path, existing: &str, updates: &[Property]) -> AppResult<()> {
    let mut pending: Vec<Property> = updates.to_vec();
    let mut lines: Vec<String> = Vec::new();
    for line in existing.lines() {
        match split_property(line) {
            Some((key, _)) if pending.iter().any(|p| p.key == key) => {
                let index = pending.iter().position(|p| p.key == key).unwrap_or(0);
                let value = pending.remove(index).value;
                lines.push(format!("{key}={value}"));
            }
            _ => lines.push(line.to_string()),
        }
    }
    for property in pending {
        lines.push(format!("{}={}", property.key, property.value));
    }
    std::fs::write(path, format!("{}\n", lines.join("\n")))?;
    Ok(())
}

/// Reads `key=value` lines, skipping comments and blanks.
fn parse_properties(text: &str) -> Vec<Property> {
    text.lines()
        .filter_map(|line| {
            let (key, value) = split_property(line)?;
            Some(Property { key, value })
        })
        .collect()
}

fn split_property(line: &str) -> Option<(String, String)> {
    let trimmed = line.trim_start();
    if trimmed.is_empty() || trimmed.starts_with('#') || trimmed.starts_with('!') {
        return None;
    }
    let (key, value) = line.split_once('=')?;
    let key = key.trim();
    if key.is_empty() {
        return None;
    }
    Some((key.to_string(), value.to_string()))
}

/// The settings a server starts with, for a file that went missing.
fn default_properties(server: &LocalServer) -> Vec<Property> {
    let pairs = [
        ("motd", format!("Novo Client | {} | {}", server.name, server.version)),
        ("server-port", server.port.to_string()),
        ("server-ip", String::new()),
        ("online-mode", "false".into()),
        ("max-players", "10".into()),
        ("view-distance", "10".into()),
        ("simulation-distance", "8".into()),
        ("enable-command-block", "true".into()),
        ("level-name", "world".into()),
        ("gamemode", "survival".into()),
        ("difficulty", "normal".into()),
        ("spawn-protection", "0".into()),
    ];
    pairs.into_iter().map(|(key, value)| Property { key: key.into(), value }).collect()
}

#[tauri::command]
pub fn open_local_server_folder(id: String) -> AppResult<()> {
    let root = paths::data_dir()?;
    check_id(&id)?;
    paths::open_folder(&server_dir(&root, &id))
}

/// Starts the server and streams its console to the UI.
#[tauri::command]
pub async fn start_local_server(app: AppHandle, servers: State<'_, Servers>, id: String) -> AppResult<()> {
    let root = paths::data_dir()?;
    let server = load(&root, &id)?;
    if servers.alive(&id) {
        return Err(AppError::Other("That server is already running".into()));
    }
    let dir = server_dir(&root, &id);

    emit(&app, &id, Stage::Starting, Some("Preparing Java".into()));
    let java = server_java(&root, &server).await?;

    let jar = dir
        .join("fabric-server-launch.jar")
        .exists()
        .then(|| "fabric-server-launch.jar".to_string())
        .unwrap_or_else(|| "server.jar".to_string());
    if !dir.join(&jar).is_file() {
        return Err(AppError::Other(format!("{jar} is missing; create the server again")));
    }

    let mut command = Command::new(&java);
    command
        .current_dir(&dir)
        .arg(format!("-Xms{}M", server.memory_mb))
        .arg(format!("-Xmx{}M", server.memory_mb))
        .arg("-jar")
        .arg(&jar)
        .arg("nogui")
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    // No stray console window on Windows.
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        command.creation_flags(0x0800_0000);
    }

    let mut child = command.spawn().map_err(|e| AppError::Other(format!("Couldn't start Java: {e}")))?;
    let stdin = child.stdin.take();
    let stdout = child.stdout.take();
    let stderr = child.stderr.take();
    let running = Arc::new(Running { child: Mutex::new(child), stdin: Mutex::new(stdin), stopped: AtomicBool::new(false) });
    servers.lock().insert(id.clone(), running.clone());

    if let Some(out) = stdout {
        stream(&app, &id, out);
    }
    if let Some(err) = stderr {
        stream(&app, &id, err);
    }
    watch(&app, &id, running);
    Ok(())
}

/// Asks the server to shut down, killing it if it doesn't within 20 seconds.
#[tauri::command]
pub fn stop_local_server(app: AppHandle, servers: State<'_, Servers>, id: String) -> AppResult<()> {
    let Some(running) = servers.get(&id) else {
        return Err(AppError::Other("That server isn't running".into()));
    };
    running.stopped.store(true, Ordering::Relaxed);
    emit(&app, &id, Stage::Stopping, None);
    let asked = {
        let mut stdin = running.stdin.lock().unwrap_or_else(|e| e.into_inner());
        match stdin.as_mut() {
            Some(pipe) => pipe.write_all(b"stop\n").and_then(|_| pipe.flush()).is_ok(),
            None => false,
        }
    };
    if !asked {
        let _ = running.child.lock().unwrap_or_else(|e| e.into_inner()).kill();
    }
    std::thread::spawn(move || {
        std::thread::sleep(Duration::from_secs(20));
        let mut child = running.child.lock().unwrap_or_else(|e| e.into_inner());
        if matches!(child.try_wait(), Ok(None)) {
            let _ = child.kill();
        }
    });
    Ok(())
}

/// Reads a pipe line by line, forwarding batches to the UI.
fn stream<R: std::io::Read + Send + 'static>(app: &AppHandle, id: &str, pipe: R) {
    let app = app.clone();
    let id = id.to_string();
    std::thread::spawn(move || {
        let reader = BufReader::new(pipe);
        let mut batch = Vec::new();
        for line in reader.lines().map_while(Result::ok) {
            if line.contains("Done (") {
                emit(&app, &id, Stage::Running, None);
            }
            batch.push(line);
            if batch.len() >= 40 {
                let _ = app.emit(LOG_EVENT, ServerLog { id: id.clone(), lines: std::mem::take(&mut batch) });
            }
        }
        if !batch.is_empty() {
            let _ = app.emit(LOG_EVENT, ServerLog { id: id.clone(), lines: batch });
        }
    });
}

/// Waits for the process to end and reports how it ended.
fn watch(app: &AppHandle, id: &str, running: Arc<Running>) {
    let app = app.clone();
    let id = id.to_string();
    std::thread::spawn(move || loop {
        std::thread::sleep(Duration::from_millis(500));
        let status = {
            let mut child = running.child.lock().unwrap_or_else(|e| e.into_inner());
            child.try_wait().ok().flatten()
        };
        if let Some(status) = status {
            let stage = if running.stopped.load(Ordering::Relaxed) || status.success() { Stage::Stopped } else { Stage::Failed };
            let message = status.code().map(|code| format!("The server exited with code {code}"));
            emit(&app, &id, stage, if stage == Stage::Failed { message } else { None });
            app.state::<Servers>().lock().remove(&id);
            break;
        }
    });
}

/// How a server's mods compare to the instance it mirrors.
#[tauri::command]
pub fn match_server_mods(id: String) -> AppResult<ModMatch> {
    let root = paths::data_dir()?;
    let server = load(&root, &id)?;
    compare(&root, &server)
}

/// Copies the instance's mods onto the server, so both sides run the same set.
#[tauri::command]
pub fn apply_server_mods(id: String) -> AppResult<ModMatch> {
    let root = paths::data_dir()?;
    let server = load(&root, &id)?;
    let instance_id = server
        .instance_id
        .as_deref()
        .ok_or_else(|| AppError::Other("This server isn't linked to an instance".into()))?;
    let source = store::game_dir(&root, instance_id).join("mods");
    let target = server_dir(&root, &id).join("mods");
    std::fs::create_dir_all(&target)?;
    let stash = server_dir(&root, &id).join("mods-unmatched");
    std::fs::create_dir_all(&stash)?;

    let wanted = jars(&source)?;
    let current = jars(&target)?;
    for (file, hash) in &wanted {
        if current.get(file) == Some(hash) {
            continue;
        }
        std::fs::copy(source.join(file), target.join(file))?;
    }
    for file in current.keys() {
        if !wanted.contains_key(file) {
            // Keep the file, but out of the mods folder.
            std::fs::rename(target.join(file), unique(stash.clone(), file))?;
        }
    }
    compare(&root, &server)
}

fn compare(root: &Path, server: &LocalServer) -> AppResult<ModMatch> {
    let instance_id = server
        .instance_id
        .as_deref()
        .ok_or_else(|| AppError::Other("This server isn't linked to an instance".into()))?;
    let source = jars(&store::game_dir(root, instance_id).join("mods"))?;
    let target = jars(&server_dir(root, &server.id).join("mods"))?;

    let mut missing = Vec::new();
    let mut differing = Vec::new();
    for (file, hash) in &source {
        match target.get(file) {
            None => missing.push(file.clone()),
            Some(have) if have != hash => differing.push(file.clone()),
            Some(_) => {}
        }
    }
    let mut extra: Vec<String> = target.keys().filter(|file| !source.contains_key(*file)).cloned().collect();
    missing.sort();
    extra.sort();
    differing.sort();
    Ok(ModMatch {
        instance_mods: source.len(),
        server_mods: target.len(),
        missing,
        extra,
        differing,
    })
}

/// The jar files in a folder, by name and SHA-1.
fn jars(dir: &Path) -> AppResult<HashMap<String, String>> {
    let mut out = HashMap::new();
    if !dir.is_dir() {
        return Ok(out);
    }
    for entry in std::fs::read_dir(dir)?.flatten() {
        let path = entry.path();
        let name = entry.file_name().to_string_lossy().into_owned();
        if path.is_file() && name.to_ascii_lowercase().ends_with(".jar") {
            out.insert(name, hash_file(&path).unwrap_or_default());
        }
    }
    Ok(out)
}

fn unique(dir: PathBuf, file: &str) -> PathBuf {
    let mut candidate = dir.join(file);
    if !candidate.exists() {
        return candidate;
    }
    let stem = Path::new(file).file_stem().map(|s| s.to_string_lossy().into_owned()).unwrap_or_default();
    for n in 2..1000 {
        candidate = dir.join(format!("{stem} ({n}).jar"));
        if !candidate.exists() {
            return candidate;
        }
    }
    candidate
}

/// Copies the instance's mods and configs into a new server folder.
fn copy_instance_mods(root: &Path, instance: &store::Instance, dir: &Path) -> AppResult<()> {
    let game = store::game_dir(root, &instance.id);
    let mods = game.join("mods");
    if mods.is_dir() {
        let target = dir.join("mods");
        std::fs::create_dir_all(&target)?;
        for entry in std::fs::read_dir(&mods)?.flatten() {
            let path = entry.path();
            if path.is_file() && path.extension().is_some_and(|e| e.eq_ignore_ascii_case("jar")) {
                std::fs::copy(&path, target.join(entry.file_name()))?;
            }
        }
    }
    Ok(())
}

/// The server jar, the Fabric launcher on top, and the loader properties.
struct ServerFiles {
    /// The jar to run for Fabric, when Fabric is used.
    launcher: Option<String>,
}

async fn install_server(
    dir: &Path,
    version: &str,
    version_json: &serde_json::Value,
    fabric_loader: Option<&str>,
) -> AppResult<ServerFiles> {
    let download = version_json
        .get("downloads")
        .and_then(|d| d.get("server"))
        .ok_or_else(|| AppError::Other(format!("Minecraft {version} has no server download")))?;
    let url = download
        .get("url")
        .and_then(|u| u.as_str())
        .ok_or_else(|| AppError::Other(format!("Minecraft {version} has no server download")))?;
    let sha1 = download.get("sha1").and_then(|s| s.as_str()).unwrap_or_default();

    let bytes = http::get_bytes(url).await?;
    if !sha1.is_empty() && sha1_of(&bytes) != sha1 {
        return Err(AppError::Checksum { file: "server.jar".into() });
    }
    std::fs::write(dir.join("server.jar"), &bytes)?;

    let Some(loader) = fabric_loader else {
        return Ok(ServerFiles { launcher: None });
    };

    // Fabric's launcher is built per loader and installer version.
    let installer = latest_installer().await?;
    let launcher_url = format!(
        "https://meta.fabricmc.net/v2/versions/loader/{version}/{loader}/{installer}/server/jar"
    );
    let launcher = http::get_bytes(&launcher_url).await?;
    std::fs::write(dir.join("fabric-server-launch.jar"), &launcher)?;
    std::fs::write(dir.join("fabric-server-launcher.properties"), "serverJar=server.jar\n")?;
    Ok(ServerFiles { launcher: Some("fabric-server-launch.jar".into()) })
}

/// Newest Fabric installer version, which the server launcher is built from.
async fn latest_installer() -> AppResult<String> {
    #[derive(Deserialize, Clone)]
    struct Entry {
        version: String,
        #[serde(default)]
        stable: bool,
    }
    let bytes = http::get_bytes("https://meta.fabricmc.net/v2/versions/installer").await?;
    let list: Vec<Entry> = serde_json::from_slice(&bytes)?;
    list.iter()
        .find(|e| e.stable)
        .or_else(|| list.first())
        .map(|e| e.version.clone())
        .ok_or_else(|| AppError::Other("Fabric listed no installer version".into()))
}

/// A version's JSON from Mojang, checked against the manifest's SHA-1.
async fn fetch_version_json(version: &str) -> AppResult<serde_json::Value> {
    let manifest = manifest::load(false).await?;
    let entry = manifest
        .versions
        .iter()
        .find(|v| v.id == version)
        .ok_or_else(|| AppError::Other(format!("Minecraft {version} isn't in Mojang's version list")))?;
    let bytes = http::get_bytes(&entry.url).await?;
    if sha1_of(&bytes) != entry.sha1 {
        return Err(AppError::Checksum { file: format!("{version}.json") });
    }
    Ok(serde_json::from_slice(&bytes)?)
}

/// The Java major version and Mojang runtime a version asks for.
fn java_of(json: &serde_json::Value) -> (u32, String) {
    let java = json.get("javaVersion");
    let major = java
        .and_then(|j| j.get("majorVersion"))
        .and_then(|m| m.as_u64())
        .map(|m| m as u32)
        .unwrap_or(8);
    let component = java
        .and_then(|j| j.get("component"))
        .and_then(|c| c.as_str())
        .unwrap_or("jre-legacy")
        .to_string();
    (major, component)
}

async fn server_java(root: &Path, server: &LocalServer) -> AppResult<PathBuf> {
    if let Some(custom) = settings::load()?.java_path {
        let path = PathBuf::from(custom);
        if path.is_file() {
            return Ok(path);
        }
    }
    if runtime::is_installed(root, &server.java_component) {
        return Ok(runtime::java_path(root, &server.java_component));
    }
    let installed = tokio::task::spawn_blocking(java::detect::find_all)
        .await
        .map_err(|e| AppError::Other(e.to_string()))?;
    if let Some(java) = installed.into_iter().find(|j| j.major == server.java_major && j.is_64bit()) {
        return Ok(java.path);
    }
    let progress = Progress::default();
    runtime::install(root, &server.java_component, &progress, &CancellationToken::new()).await
}

fn sha1_of(bytes: &[u8]) -> String {
    use sha1::{Digest, Sha1};
    let mut hasher = Sha1::new();
    hasher.update(bytes);
    hasher.finalize().iter().map(|b| format!("{b:02x}")).collect()
}

/// A port nothing is listening on, from Minecraft's default upwards.
fn free_port() -> AppResult<u16> {
    for port in 25565..=25595u16 {
        if std::net::TcpListener::bind(("0.0.0.0", port)).is_ok() {
            return Ok(port);
        }
    }
    Err(AppError::Other("No free port between 25565 and 25595".into()))
}

fn write_eula(dir: &Path) -> AppResult<()> {
    std::fs::write(dir.join("eula.txt"), "#Accepted when this server was created in Novo Client\neula=true\n")?;
    Ok(())
}

fn write_properties(dir: &Path, name: &str, port: u16, version: &str) -> AppResult<()> {
    let path = dir.join("server.properties");
    if path.is_file() {
        return Ok(());
    }
    let motd = format!("Novo Client | {name} | {version}");
    let text = format!(
        "motd={motd}\nserver-port={port}\nserver-ip=\nonline-mode=false\nmax-players=10\nview-distance=10\nsimulation-distance=8\nenable-command-block=true\nlevel-name=world\ngamemode=survival\ndifficulty=normal\nspawn-protection=0\n"
    );
    std::fs::write(path, text)?;
    Ok(())
}

fn write_scripts(dir: &Path, launcher: Option<&str>, memory_mb: u32) -> AppResult<()> {
    let jar = launcher.unwrap_or("server.jar");
    std::fs::write(
        dir.join("start.bat"),
        format!("@echo off\r\ncd /d \"%~dp0\"\r\njava -Xms{memory_mb}M -Xmx{memory_mb}M -jar {jar} nogui\r\npause\r\n"),
    )?;
    let script = dir.join("start.sh");
    std::fs::write(&script, format!("#!/bin/sh\ncd \"$(dirname \"$0\")\"\nexec java -Xms{memory_mb}M -Xmx{memory_mb}M -jar {jar} nogui\n"))?;
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        let _ = std::fs::set_permissions(&script, std::fs::Permissions::from_mode(0o755));
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn root(tag: &str) -> PathBuf {
        let dir = std::env::temp_dir().join(format!("novo-server-{tag}-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(dir.join("servers")).unwrap();
        dir
    }

    fn server(id: &str) -> LocalServer {
        LocalServer {
            id: id.into(),
            name: "Test".into(),
            version: "1.20.1".into(),
            loader: Loader::Fabric,
            loader_version: Some("0.16.9".into()),
            port: 25565,
            memory_mb: 2048,
            instance_id: None,
            created_at: 1,
            running: false,
            java_major: 17,
            java_component: "java-runtime-gamma".into(),
        }
    }

    #[test]
    fn ids_are_folder_safe() {
        assert_eq!(slug("My Server!"), "my-server");
        assert_eq!(slug("   "), "server");
        assert!(check_id("../escape").is_err());
    }

    #[test]
    fn compares_and_applies_instance_mods() {
        let root = root("match");
        let source = root.join("instances").join("one").join("minecraft").join("mods");
        let target = server_dir(&root, "srv").join("mods");
        std::fs::create_dir_all(&source).unwrap();
        std::fs::create_dir_all(&target).unwrap();
        std::fs::write(source.join("same.jar"), b"same").unwrap();
        std::fs::write(source.join("new.jar"), b"new").unwrap();
        std::fs::write(source.join("changed.jar"), b"newer").unwrap();
        std::fs::write(target.join("same.jar"), b"same").unwrap();
        std::fs::write(target.join("changed.jar"), b"older").unwrap();
        std::fs::write(target.join("stale.jar"), b"stale").unwrap();

        let mut record = server("srv");
        record.instance_id = Some("one".into());
        save(&root, &record).unwrap();

        let before = compare(&root, &record).unwrap();
        assert_eq!(before.missing, vec!["new.jar"]);
        assert_eq!(before.differing, vec!["changed.jar"]);
        assert_eq!(before.extra, vec!["stale.jar"]);
        assert_eq!((before.instance_mods, before.server_mods), (3, 3));
        std::fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn edits_properties_without_losing_comments() {
        let text = "#Minecraft server properties\nmotd=Old\npvp=true\n\ncustom-crate=on\n";
        let updates = vec![
            Property { key: "motd".into(), value: "New | motd".into() },
            Property { key: "pvp".into(), value: "false".into() },
            Property { key: "hardcore".into(), value: "true".into() },
        ];
        let dir = root("props");
        let path = dir.join("server.properties");
        std::fs::write(&path, text).unwrap();
        write_properties_file(&path, text, &updates).unwrap();

        let written = std::fs::read_to_string(&path).unwrap();
        assert!(written.contains("#Minecraft server properties"));
        assert!(written.contains("motd=New | motd"));
        assert!(written.contains("pvp=false"));
        assert!(written.contains("custom-crate=on"));
        assert!(written.contains("hardcore=true"));

        let parsed = parse_properties(&written);
        assert_eq!(parsed.iter().filter(|p| p.key == "motd").count(), 1);
        assert_eq!(parsed.iter().find(|p| p.key == "custom-crate").unwrap().value, "on");
        std::fs::remove_dir_all(dir).unwrap();
    }

    #[test]
    fn rejects_illegal_property_names() {
        assert_eq!(property_key(" pvp ").unwrap(), "pvp");
        for bad in ["", "   ", "bad key", "x=y", "#comment", "rcon!password"] {
            assert!(property_key(bad).is_err(), "{bad:?} should be rejected");
        }
    }

    #[test]
    fn versions_map_to_java() {
        let json: serde_json::Value = serde_json::from_str(r#"{"javaVersion":{"majorVersion":17,"component":"java-runtime-gamma"}}"#).unwrap();
        assert_eq!(java_of(&json), (17, "java-runtime-gamma".into()));
        let legacy: serde_json::Value = serde_json::from_str("{}").unwrap();
        assert_eq!(java_of(&legacy), (8, "jre-legacy".into()));
    }
}
