//! Launching the game and watching it.
//!
//! - `args`: the Java command line (modern and legacy formats).
//! - `natives`: legacy native extraction and pre-1.7 asset layouts.
//! - `logs`: parses game output (log4j XML or plain text) for the log viewer.
//!
//! Flow: check account and files -> pick/download Java -> prepare natives and
//! assets -> start Java -> stream logs -> report exit or crash.

pub mod args;
pub mod logs;
pub mod natives;
pub mod options;

use std::path::{Path, PathBuf};
use std::process::Stdio;
use std::sync::atomic::Ordering::Relaxed;
use std::sync::{Arc, Mutex, MutexGuard};
use std::time::{Duration, SystemTime};

use serde::Serialize;
use tauri::{AppHandle, Emitter, Manager, State};
use tokio::io::{AsyncBufReadExt, AsyncRead, BufReader};
use tokio::process::Child;
use tokio::sync::mpsc;
use tokio_util::sync::CancellationToken;

use crate::auth;
use crate::download::engine::Progress;
use crate::download::forge;
use crate::download::install::{self, Tracker};
use crate::download::version::{load_resolved, AssetIndex, VersionJson};
use crate::download::Installs;
use crate::error::{AppError, AppResult};
use crate::instances;
use crate::java;
use crate::paths;
use crate::settings::{self, AfterLaunch, Settings};
use logs::{LogLine, Log4jParser};

/// Tauri event carrying [`LaunchState`].
pub const STATE_EVENT: &str = "launch-state";
/// Tauri event carrying batches of [`LogLine`]s.
pub const LOG_EVENT: &str = "game-log";

/// Crash reports larger than this are cut off in the UI.
const MAX_REPORT_BYTES: usize = 256 * 1024;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "lowercase")]
pub enum LaunchStage {
    Preparing,
    /// Downloading the instance's Minecraft version.
    Installing,
    Java,
    Starting,
    Running,
    Exited,
    Crashed,
    Failed,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LaunchState {
    instance_id: String,
    /// Empty until the instance has been read.
    version_id: String,
    stage: LaunchStage,
    /// Download progress (0-1) during the `installing` and `java` stages.
    progress: Option<f64>,
    message: Option<String>,
    exit_code: Option<i32>,
    crash_report: Option<CrashReport>,
}

impl LaunchState {
    fn new(instance_id: &str, version_id: &str, stage: LaunchStage) -> Self {
        Self {
            instance_id: instance_id.into(),
            version_id: version_id.into(),
            stage,
            progress: None,
            message: None,
            exit_code: None,
            crash_report: None,
        }
    }
}

#[derive(Debug, Clone, Serialize)]
pub struct CrashReport {
    path: String,
    content: String,
}

/// The running (or starting) game, if any. One at a time.
#[derive(Default)]
pub struct Games {
    current: Mutex<Option<(String, CancellationToken)>>,
}

impl Games {
    fn lock(&self) -> MutexGuard<'_, Option<(String, CancellationToken)>> {
        self.current.lock().unwrap_or_else(|e| e.into_inner())
    }
}

fn emit(app: &AppHandle, state: LaunchState) {
    let _ = app.emit(STATE_EVENT, state);
}

/// Re-emits `base` with `fraction()` as its progress every 150 ms until aborted.
fn progress_ticker(
    app: &AppHandle,
    base: LaunchState,
    fraction: impl Fn() -> Option<f64> + Send + 'static,
) -> tauri::async_runtime::JoinHandle<()> {
    let app = app.clone();
    tauri::async_runtime::spawn(async move {
        loop {
            emit(&app, LaunchState { progress: fraction(), ..base.clone() });
            tokio::time::sleep(Duration::from_millis(150)).await;
        }
    })
}

fn fraction(p: &Progress) -> Option<f64> {
    let total = p.bytes_total.load(Relaxed);
    (total > 0).then(|| p.bytes_done.load(Relaxed) as f64 / total as f64)
}

/// Starts an instance with the active account. Returns once the game process
/// is running; its logs and exit arrive as events.
#[tauri::command]
pub async fn launch_game(app: AppHandle, games: State<'_, Games>, instance_id: String) -> AppResult<()> {
    let stop = {
        let mut current = games.lock();
        if current.is_some() {
            return Err(AppError::Other("Minecraft is already running".into()));
        }
        let token = CancellationToken::new();
        *current = Some((instance_id.clone(), token.clone()));
        token
    };

    let result = start(&app, &instance_id, stop).await;
    if let Err(e) = &result {
        *games.lock() = None;
        let stage = if matches!(e, AppError::Cancelled) { LaunchStage::Exited } else { LaunchStage::Failed };
        let mut state = LaunchState::new(&instance_id, "", stage);
        state.message = Some(e.to_string());
        emit(&app, state);
    }
    result
}

/// Stops the running game (or cancels a launch that is still preparing).
#[tauri::command]
pub fn stop_game(games: State<'_, Games>) {
    if let Some((_, token)) = games.lock().as_ref() {
        token.cancel();
    }
}

async fn start(app: &AppHandle, instance_id: &str, stop: CancellationToken) -> AppResult<()> {
    emit(app, LaunchState::new(instance_id, "", LaunchStage::Preparing));
    let root = paths::data_dir()?;
    let mut settings = settings::load()?;
    let instance = instances::store::load(&root, instance_id)?;
    let id = instance.version.as_str();
    let state = |stage| LaunchState::new(instance_id, id, stage);
    let account = auth::store::read()?
        .active()
        .cloned()
        .ok_or_else(|| AppError::Other("Add a player in Accounts first".into()))?;
    // Microsoft accounts need a fresh Minecraft token (refreshed from the saved sign-in).
    let access_token = match account.kind {
        auth::account::AccountKind::Microsoft => {
            Some(auth::microsoft::access_token(&crate::config::msa_client_id(app)?, &account).await?)
        }
        auth::account::AccountKind::Offline => None,
    };

    // Download the version on first play.
    if !install::is_installed(&root, id) {
        if app.state::<Installs>().is_running(id) {
            return Err(AppError::Other(format!("Minecraft {id} is still downloading")));
        }
        let tracker = Arc::new(Tracker::default());
        let ticker = progress_ticker(app, state(LaunchStage::Installing), {
            let tracker = tracker.clone();
            move || fraction(&tracker.progress)
        });
        let installed = install::install(id, &tracker, &stop).await;
        ticker.abort();
        installed?;
    }
    // Then the loader, if any.
    let launch_id = instance.launch_version();
    if launch_id != id && !install::is_installed(&root, &launch_id) {
        let forge = instance.loader_version.as_deref().unwrap_or_default();
        let tracker = Arc::new(Tracker::default());
        let ticker = progress_ticker(app, state(LaunchStage::Installing), {
            let tracker = tracker.clone();
            move || fraction(&tracker.progress)
        });
        let installed = forge::install(id, forge, &tracker, &stop).await;
        ticker.abort();
        installed?;
    }
    let version = tokio::task::spawn_blocking({
        let (root, launch_id) = (root.clone(), launch_id.clone());
        move || load_resolved(&root, &launch_id)
    })
    .await
    .map_err(|e| AppError::Other(e.to_string()))??;

    // Java, possibly downloading a runtime. The instance's Java wins over the launcher's.
    let java_settings = Settings { java_path: instance.java_path.clone().or(settings.java_path.clone()), ..settings.clone() };
    let progress = Arc::new(Progress::default());
    let ticker = progress_ticker(app, state(LaunchStage::Java), {
        let progress = progress.clone();
        move || fraction(&progress)
    });
    let java = java::resolve(&version, &java_settings, &progress, &stop).await;
    ticker.abort();
    let java = java?;
    if stop.is_cancelled() {
        return Err(AppError::Cancelled);
    }

    emit(app, state(LaunchStage::Starting));
    instances::client::sync(app, &root, &instance)?;
    instances::companions::sync(&root, &instance).await?;
    let game_dir = instances::store::game_dir(&root, instance_id);
    if let Some(lang) = &settings.game_language {
        options::set_language(&game_dir, lang, id)?;
    }
    let natives_dir = root.join("versions").join(&launch_id).join("natives");
    let game_assets = tokio::task::spawn_blocking({
        let (root, version, game_dir, natives_dir) = (root.clone(), version.clone(), game_dir.clone(), natives_dir.clone());
        move || prepare_files(&root, &version, &game_dir, &natives_dir)
    })
    .await
    .map_err(|e| AppError::Other(e.to_string()))??;

    let args = args::build(&args::LaunchContext {
        version: &version,
        account: &account,
        access_token: access_token.as_deref(),
        root: &root,
        game_dir: &game_dir,
        natives_dir: &natives_dir,
        game_assets: &game_assets,
        memory_mb: instance.memory_mb.unwrap_or(settings.memory_mb),
        optimized_flags: settings.optimized_flags,
        extra_jvm_args: &instance.jvm_args.split_whitespace().map(String::from).collect::<Vec<_>>(),
        resolution: settings.resolution,
        fullscreen: settings.fullscreen,
    });

    // With "close", nobody reads the output, so don't pipe it.
    let close = settings.after_launch == AfterLaunch::Close;
    let output = || if close { Stdio::null() } else { Stdio::piped() };
    let mut cmd = tokio::process::Command::new(&java);
    cmd.args(&args).current_dir(&game_dir).stdin(Stdio::null()).stdout(output()).stderr(output());
    #[cfg(windows)]
    cmd.creation_flags(0x0800_0000); // CREATE_NO_WINDOW
    let child = cmd
        .spawn()
        .map_err(|e| AppError::Other(format!("Couldn't start Java ({}): {e}", java.display())))?;

    settings.last_instance = Some(instance_id.to_string());
    let _ = settings::save(&settings);
    let _ = instances::store::mark_played(&root, instance_id);
    let mut running = state(LaunchStage::Running);
    running.message = Some(format!("Java: {}", java.display()));
    emit(app, running);

    match settings.after_launch {
        AfterLaunch::Keep => {}
        AfterLaunch::Minimize => {
            if let Some(w) = app.get_webview_window("main") {
                let _ = w.minimize();
            }
        }
        AfterLaunch::Close => {
            app.exit(0);
            return Ok(());
        }
    }

    tauri::async_runtime::spawn(monitor(app.clone(), child, state(LaunchStage::Exited), game_dir, SystemTime::now(), stop));
    Ok(())
}

/// Extracts natives and lays out legacy assets. Returns the `game_assets` folder.
fn prepare_files(root: &Path, version: &VersionJson, game_dir: &Path, natives_dir: &Path) -> AppResult<PathBuf> {
    std::fs::create_dir_all(game_dir)?;
    natives::extract_natives(root, version, natives_dir)?;
    let assets = root.join("assets");
    let Some(ai) = &version.asset_index else { return Ok(assets) };
    let index: AssetIndex = serde_json::from_slice(&std::fs::read(assets.join("indexes").join(format!("{}.json", ai.id)))?)?;
    Ok(natives::prepare_legacy_assets(root, &ai.id, &index, game_dir)?.unwrap_or(assets))
}

async fn read_lines(reader: impl AsyncRead + Unpin, is_err: bool, tx: mpsc::UnboundedSender<(String, bool)>) {
    let mut reader = BufReader::new(reader);
    let mut buf = Vec::new();
    loop {
        buf.clear();
        match reader.read_until(b'\n', &mut buf).await {
            Ok(0) | Err(_) => break,
            Ok(_) => {
                // Lossy: Windows consoles may emit non-UTF-8 text.
                let line = String::from_utf8_lossy(&buf).trim_end_matches(['\r', '\n']).to_string();
                if tx.send((line, is_err)).is_err() {
                    break;
                }
            }
        }
    }
}

/// Newest crash report or JVM error log written since `since`.
fn find_crash_report(logged_path: Option<String>, game_dir: &Path, since: SystemTime) -> Option<CrashReport> {
    let newer = |p: &Path| std::fs::metadata(p).and_then(|m| m.modified()).is_ok_and(|t| t >= since);
    let path = logged_path.map(PathBuf::from).filter(|p| p.is_file()).or_else(|| {
        let mut candidates: Vec<PathBuf> = std::fs::read_dir(game_dir.join("crash-reports"))
            .into_iter()
            .chain(std::fs::read_dir(game_dir))
            .flatten()
            .flatten()
            .map(|e| e.path())
            .filter(|p| {
                let name = p.file_name().unwrap_or_default().to_string_lossy();
                (name.starts_with("crash-") || name.starts_with("hs_err_pid")) && newer(p)
            })
            .collect();
        candidates.sort_by_key(|p| std::fs::metadata(p).and_then(|m| m.modified()).ok());
        candidates.pop()
    })?;
    let bytes = std::fs::read(&path).ok()?;
    let content = String::from_utf8_lossy(&bytes[..bytes.len().min(MAX_REPORT_BYTES)]).into_owned();
    Some(CrashReport { path: path.display().to_string(), content })
}

/// Streams the game's output to the UI, then reports how it ended as `exited`.
async fn monitor(app: AppHandle, mut child: Child, exited: LaunchState, game_dir: PathBuf, started: SystemTime, stop: CancellationToken) {
    let (tx, mut rx) = mpsc::unbounded_channel();
    if let Some(out) = child.stdout.take() {
        tauri::async_runtime::spawn(read_lines(out, false, tx.clone()));
    }
    if let Some(err) = child.stderr.take() {
        tauri::async_runtime::spawn(read_lines(err, true, tx.clone()));
    }
    drop(tx);

    let mut parser = Log4jParser::default();
    let mut batch: Vec<LogLine> = Vec::new();
    let mut crash_path: Option<String> = None;
    // The last lines of output, attached to crash reports.
    let mut recent: std::collections::VecDeque<String> = std::collections::VecDeque::new();
    let mut handle = |line: String, is_err: bool, batch: &mut Vec<LogLine>| {
        let parsed = if is_err { Some(logs::plain(&line)) } else { parser.push(&line) };
        if let Some(l) = parsed {
            if crash_path.is_none() {
                crash_path = logs::crash_report_path(&l.message);
            }
            if recent.len() == 200 {
                recent.pop_front();
            }
            recent.push_back(format!("[{}] {}", l.level, l.message));
            batch.push(l);
        }
    };
    let flush = |batch: &mut Vec<LogLine>| {
        if !batch.is_empty() {
            let _ = app.emit(LOG_EVENT, std::mem::take(batch));
        }
    };

    let mut killed = false;
    let mut tick = tokio::time::interval(Duration::from_millis(100));
    let status = loop {
        tokio::select! {
            Some((line, is_err)) = rx.recv() => handle(line, is_err, &mut batch),
            _ = tick.tick() => flush(&mut batch),
            _ = stop.cancelled(), if !killed => {
                killed = true;
                let _ = child.start_kill();
            }
            status = child.wait() => break status,
        }
    };
    // Output still buffered in the pipes after exit.
    while let Ok(Some((line, is_err))) = tokio::time::timeout(Duration::from_millis(500), rx.recv()).await {
        handle(line, is_err, &mut batch);
    }
    flush(&mut batch);

    let exit_code = status.ok().and_then(|s| s.code());
    let crash_report = if killed { None } else { find_crash_report(crash_path, &game_dir, started) };
    let crashed = !killed && (crash_report.is_some() || exit_code.is_some_and(|c| c != 0));

    *app.state::<Games>().lock() = None;
    if crashed && settings::load().map(|s| s.auto_report_crashes).unwrap_or(false) {
        let content = crash_report.as_ref().map(|c| c.content.clone()).unwrap_or_else(|| format!("Exit code {}", exit_code.unwrap_or(-1)));
        crate::report::report_crash(app.clone(), exited.instance_id.clone(), content, recent.iter().cloned().collect());
    }
    let stage = if crashed { LaunchStage::Crashed } else { LaunchStage::Exited };
    emit(&app, LaunchState { stage, exit_code, crash_report, ..exited });

    if let Some(w) = app.get_webview_window("main") {
        if w.is_minimized().unwrap_or(false) {
            let _ = w.unminimize();
        }
        if crashed {
            let _ = w.set_focus();
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::auth::offline;

    /// Every Mojang Java runtime must accept the performance flags, or the
    /// game won't start. Downloads Java 8, 17 and 21. Run with:
    /// `NOVOMC_DATA_DIR=<dir> cargo test flags_accepted -- --ignored --nocapture`
    #[tokio::test(flavor = "multi_thread")]
    #[ignore = "downloads Java runtimes from Mojang"]
    async fn performance_flags_accepted_by_every_java() {
        assert!(std::env::var_os("NOVOMC_DATA_DIR").is_some(), "set NOVOMC_DATA_DIR to a scratch folder");
        paths::ensure_layout().unwrap();
        let root = paths::data_dir().unwrap();
        for component in ["jre-legacy", "java-runtime-gamma", "java-runtime-delta"] {
            let javaw = java::runtime::install(&root, component, &Progress::default(), &CancellationToken::new())
                .await
                .unwrap();
            // java.exe prints to the console; javaw.exe sits next to it.
            let java = javaw.with_file_name(if cfg!(windows) { "java.exe" } else { "java" });
            let out = std::process::Command::new(&java)
                .args(["-Xmx1024M", "-Xms1024M"])
                .args(args::PERFORMANCE_FLAGS)
                .arg("-version")
                .output()
                .unwrap();
            let text = String::from_utf8_lossy(&out.stderr);
            println!("{component}: {}", text.lines().next().unwrap_or_default());
            assert!(out.status.success(), "{component} rejected the flags:\n{text}");
        }
    }

    /// Real launch: installs the version, resolves Java, starts the game and
    /// waits until it has loaded, then kills it. Opens a game window. Run with:
    /// `NOVOMC_DATA_DIR=<dir> NOVOMC_TEST_VERSION=1.21.4 cargo test launch_real -- --ignored --nocapture`
    #[tokio::test(flavor = "multi_thread")]
    #[ignore = "downloads from Mojang and opens a game window"]
    async fn launch_real_version() {
        assert!(std::env::var_os("NOVOMC_DATA_DIR").is_some(), "set NOVOMC_DATA_DIR to a scratch folder");
        let id = std::env::var("NOVOMC_TEST_VERSION").unwrap_or_else(|_| "1.21.4".into());
        paths::ensure_layout().unwrap();
        let root = paths::data_dir().unwrap();
        let none = CancellationToken::new();

        install::install(&id, &Tracker::default(), &none).await.unwrap();
        // NOVOMC_TEST_FORGE=1.8.9-11.15.1.2318-1.8.9 launches through Forge instead.
        let forge = std::env::var("NOVOMC_TEST_FORGE").ok();
        let launch_id = match &forge {
            Some(f) => forge::install(&id, f, &Tracker::default(), &none).await.unwrap(),
            None => id.clone(),
        };
        let version = load_resolved(&root, &launch_id).unwrap();
        let java = java::resolve(&version, &Settings::default(), &Progress::default(), &none).await.unwrap();
        println!("{launch_id}: needs {:?}, using {}", java::requirement(&version), java.display());

        let game_dir = root.join("instances").join("test");
        let natives_dir = root.join("versions").join(&launch_id).join("natives");
        let game_assets = prepare_files(&root, &version, &game_dir, &natives_dir).unwrap();
        // NOVOMC_TEST_MOD=<jar> installs the NovoMC mod and runs its in-game autotest.
        let test_mod = std::env::var_os("NOVOMC_TEST_MOD").map(std::path::PathBuf::from);
        let mut extra = Vec::new();
        if let Some(jar) = &test_mod {
            std::fs::create_dir_all(game_dir.join("mods")).unwrap();
            std::fs::copy(jar, game_dir.join("mods").join("novomc-client.jar")).unwrap();
            crate::instances::companions::install(&root, &game_dir.join("mods"), true).await.unwrap();
            // NOVOMC_TEST_MODE=benchmark (FPS) or modcheck (mod behavior); default is the screenshot autotest.
            let mode = std::env::var("NOVOMC_TEST_MODE").unwrap_or_else(|_| "autotest".into());
            extra.push(format!("-Dnovomc.{mode}=true"));
        }
        let account = offline::new_account("NovoTester").unwrap();
        let args = args::build(&args::LaunchContext {
            version: &version,
            account: &account,
            access_token: None,
            root: &root,
            game_dir: &game_dir,
            natives_dir: &natives_dir,
            game_assets: &game_assets,
            memory_mb: 2048,
            optimized_flags: true,
            extra_jvm_args: &extra,
            resolution: None,
            fullscreen: false,
        });

        let mut child = tokio::process::Command::new(&java)
            .args(&args)
            .current_dir(&game_dir)
            .stdout(Stdio::piped())
            .stderr(Stdio::piped())
            .kill_on_drop(true)
            .spawn()
            .unwrap();
        let (tx, mut rx) = mpsc::unbounded_channel();
        tokio::spawn(read_lines(child.stdout.take().unwrap(), false, tx.clone()));
        tokio::spawn(read_lines(child.stderr.take().unwrap(), true, tx));

        if test_mod.is_some() {
            // The autotest quits the game itself; print NovoMC's lines and any errors.
            let mut parser = Log4jParser::default();
            let mut finished = false;
            let deadline = tokio::time::Instant::now() + Duration::from_secs(600);
            loop {
                tokio::select! {
                    Some((line, is_err)) = rx.recv() => {
                        let parsed = if is_err { Some(logs::plain(&line)) } else { parser.push(&line) };
                        if let Some(l) = parsed {
                            let m = &l.message;
                            if m.contains("NovoMC") || m.contains("Exception") || l.level == "ERROR" || l.level == "FATAL" {
                                println!("  [{}] {}", l.level, m.lines().take(3).collect::<Vec<_>>().join(" / "));
                            }
                            finished |= m.contains("Autotest finished") || m.contains("Benchmark result");
                        }
                    }
                    status = child.wait() => {
                        println!("game exited: {status:?}");
                        break;
                    }
                    _ = tokio::time::sleep_until(deadline) => panic!("autotest timed out"),
                }
            }
            println!("screenshots in {}", game_dir.join("screenshots").display());
            assert!(finished, "autotest did not finish");
            return;
        }

        // Modern versions log when loading is done; old ones barely log, so
        // for them surviving 20 seconds counts as started.
        let modern = version.logging.is_some();
        let deadline = tokio::time::Instant::now() + Duration::from_secs(if modern { 120 } else { 20 });
        let mut parser = Log4jParser::default();
        let mut forge_loaded = false;
        let started = loop {
            tokio::select! {
                Some((line, is_err)) = rx.recv() => {
                    let parsed = if is_err { Some(logs::plain(&line)) } else { parser.push(&line) };
                    if let Some(l) = parsed {
                        println!("  [{}] {}", if l.level.is_empty() { "-" } else { &l.level }, l.message.lines().next().unwrap_or(""));
                        forge_loaded |= l.message.contains("Forge Mod Loader version");
                        if l.message.contains("Sound engine started") || l.message.contains("Created: 1024x") {
                            break true;
                        }
                    }
                }
                status = child.wait() => panic!("game exited early: {status:?}"),
                _ = tokio::time::sleep_until(deadline) => break !modern,
            }
        };
        let _ = child.kill().await;
        assert!(started, "game did not finish starting");
        assert!(forge.is_none() || forge_loaded, "Forge did not load");
        println!("{launch_id}: started OK");
    }
}
