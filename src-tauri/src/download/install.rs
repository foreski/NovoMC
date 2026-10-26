//! Installing a vanilla version: its JSON, client jar, libraries, natives,
//! log config, asset index and assets.

use std::path::{Path, PathBuf};
use std::sync::atomic::Ordering::Relaxed;
use std::sync::Mutex;

use serde::Serialize;
use tokio_util::sync::CancellationToken;

use super::engine::{self, Progress};
use super::manifest;
use super::plan::{self, FileTask};
use super::version::{AssetIndex, VersionJson};
use crate::error::{AppError, AppResult};
use crate::paths;

/// Tauri event carrying [`InstallEvent`]s.
pub const EVENT: &str = "install-progress";

#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, Serialize)]
#[serde(rename_all = "lowercase")]
pub enum Stage {
    #[default]
    Metadata,
    Files,
    Done,
    Failed,
    Cancelled,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct InstallEvent {
    pub version_id: String,
    pub stage: Stage,
    pub files_done: u64,
    pub files_total: u64,
    pub bytes_done: u64,
    pub bytes_total: u64,
    pub error: Option<String>,
}

/// Progress counters plus the current stage of one install.
#[derive(Debug, Default)]
pub struct Tracker {
    pub progress: Progress,
    stage: Mutex<Stage>,
}

impl Tracker {
    pub fn set_stage(&self, stage: Stage) {
        *self.stage.lock().unwrap_or_else(|e| e.into_inner()) = stage;
    }

    pub fn event(&self, version_id: &str, error: Option<String>) -> InstallEvent {
        let p = &self.progress;
        InstallEvent {
            version_id: version_id.to_string(),
            stage: *self.stage.lock().unwrap_or_else(|e| e.into_inner()),
            files_done: p.files_done.load(Relaxed),
            files_total: p.files_total.load(Relaxed),
            bytes_done: p.bytes_done.load(Relaxed),
            bytes_total: p.bytes_total.load(Relaxed),
            error,
        }
    }
}

/// Written after every file of a version has been verified.
fn installed_marker(root: &Path, id: &str) -> PathBuf {
    root.join("versions").join(id).join(".novomc-installed")
}

pub fn is_installed(root: &Path, id: &str) -> bool {
    installed_marker(root, id).exists()
}

/// Records that every file of version `id` has been verified.
pub async fn mark_installed(root: &Path, id: &str) -> AppResult<()> {
    Ok(tokio::fs::write(installed_marker(root, id), b"").await?)
}

/// Downloads (or repairs) everything version `id` needs to launch.
pub async fn install(id: &str, tracker: &Tracker, cancel: &CancellationToken) -> AppResult<()> {
    let root = paths::data_dir()?;
    let progress = &tracker.progress;
    tracker.set_stage(Stage::Metadata);

    let manifest = manifest::load(false).await?;
    let entry = manifest
        .versions
        .iter()
        .find(|v| v.id == id)
        .ok_or_else(|| AppError::Other(format!("Unknown version {id}")))?;

    let json = FileTask {
        url: entry.url.clone(),
        path: root.join("versions").join(id).join(format!("{id}.json")),
        sha1: Some(entry.sha1.clone()),
        alt_sha1: Vec::new(),
        size: 0,
    };
    engine::ensure_file(&json, progress, cancel).await?;
    let version: VersionJson = serde_json::from_slice(&tokio::fs::read(&json.path).await?)?;

    let mut tasks = plan::version_files(&root, &version);
    if let Some(ai) = &version.asset_index {
        let index = FileTask {
            url: ai.url.clone(),
            path: root.join("assets").join("indexes").join(format!("{}.json", ai.id)),
            sha1: Some(ai.sha1.clone()),
            alt_sha1: Vec::new(),
            size: ai.size,
        };
        engine::ensure_file(&index, progress, cancel).await?;
        let index: AssetIndex = serde_json::from_slice(&tokio::fs::read(&index.path).await?)?;
        tasks.extend(plan::asset_files(&root, &index));
    }

    tracker.set_stage(Stage::Files);
    engine::download_all(tasks, progress, cancel).await?;
    mark_installed(&root, id).await
}

#[cfg(test)]
mod tests {
    use std::time::Instant;

    use super::*;

    /// Real download from Mojang into a scratch folder. Run with:
    /// `NOVOMC_DATA_DIR=<dir> NOVOMC_TEST_VERSION=1.21.4 cargo test install_real -- --ignored --nocapture`
    #[tokio::test(flavor = "multi_thread")]
    #[ignore = "downloads from Mojang"]
    async fn install_real_version() {
        assert!(std::env::var_os("NOVOMC_DATA_DIR").is_some(), "set NOVOMC_DATA_DIR to a scratch folder");
        let id = std::env::var("NOVOMC_TEST_VERSION").unwrap_or_else(|_| "b1.7.3".into());
        paths::ensure_layout().unwrap();

        for pass in ["download", "verify"] {
            let tracker = Tracker::default();
            let start = Instant::now();
            install(&id, &tracker, &CancellationToken::new()).await.unwrap();
            let e = tracker.event(&id, None);
            println!(
                "{id} {pass}: {}/{} files, {:.1} MB in {:.1?}",
                e.files_done,
                e.files_total,
                e.bytes_total as f64 / 1e6,
                start.elapsed()
            );
            assert_eq!(e.files_done, e.files_total);
        }
        assert!(is_installed(&paths::data_dir().unwrap(), &id));
    }
}
