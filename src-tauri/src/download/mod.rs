//! Version manifest and game file downloads.
//!
//! - `manifest`: Mojang's piston-meta version list (cached, works offline).
//! - `version`, `rules`: version JSON types and OS/feature rules.
//! - `plan`: which files a version needs (client, libraries, natives, assets).
//! - `engine`: parallel downloads with SHA1 checks, retries and cancelling.
//! - `install`: the full install flow and its progress events.
//! - `forge`: Forge for 1.8.9 (legacy installer format).
//!
//! Game files only ever come from Mojang's servers.

pub mod engine;
pub mod forge;
pub mod http;
pub mod install;
pub mod manifest;
pub mod plan;
pub mod rules;
pub mod version;

use std::collections::HashMap;
use std::sync::{Arc, Mutex};
use std::time::Duration;

use serde::Serialize;
use tauri::{AppHandle, Emitter, State};
use tokio_util::sync::CancellationToken;

use crate::error::{AppError, AppResult};
use crate::paths;
use install::{Stage, Tracker};
use manifest::VersionType;

/// Installs in progress, by version ID, so they can be cancelled.
#[derive(Default)]
pub struct Installs {
    running: Mutex<HashMap<String, CancellationToken>>,
}

impl Installs {
    fn lock(&self) -> std::sync::MutexGuard<'_, HashMap<String, CancellationToken>> {
        self.running.lock().unwrap_or_else(|e| e.into_inner())
    }

    pub fn is_running(&self, id: &str) -> bool {
        self.lock().contains_key(id)
    }
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct VersionSummary {
    id: String,
    kind: VersionType,
    release_time: String,
    installed: bool,
}

/// All versions from the manifest, newest first, with install state.
#[tauri::command]
pub async fn list_versions(refresh: bool) -> AppResult<Vec<VersionSummary>> {
    let root = paths::data_dir()?;
    let manifest = manifest::load(refresh).await?;
    Ok(manifest
        .versions
        .into_iter()
        .map(|v| VersionSummary {
            installed: install::is_installed(&root, &v.id),
            id: v.id,
            kind: v.kind,
            release_time: v.release_time,
        })
        .collect())
}

/// Installs or repairs a version, emitting `install-progress` events about
/// every 150 ms and once more when it finishes.
#[tauri::command]
pub async fn install_version(app: AppHandle, installs: State<'_, Installs>, id: String) -> AppResult<()> {
    let cancel = {
        let mut running = installs.lock();
        if running.contains_key(&id) {
            return Err(AppError::Other(format!("{id} is already installing")));
        }
        let token = CancellationToken::new();
        running.insert(id.clone(), token.clone());
        token
    };

    let tracker = Arc::new(Tracker::default());
    let ticker = tauri::async_runtime::spawn({
        let (app, tracker, id) = (app.clone(), tracker.clone(), id.clone());
        async move {
            loop {
                let _ = app.emit(install::EVENT, tracker.event(&id, None));
                tokio::time::sleep(Duration::from_millis(150)).await;
            }
        }
    });

    let result = install::install(&id, &tracker, &cancel).await;
    ticker.abort();
    installs.lock().remove(&id);

    let (stage, error) = match &result {
        Ok(()) => (Stage::Done, None),
        Err(AppError::Cancelled) => (Stage::Cancelled, None),
        Err(e) => (Stage::Failed, Some(e.to_string())),
    };
    tracker.set_stage(stage);
    let _ = app.emit(install::EVENT, tracker.event(&id, error));
    result
}

#[tauri::command]
pub fn cancel_install(installs: State<'_, Installs>, id: String) {
    if let Some(token) = installs.lock().get(&id) {
        token.cancel();
    }
}
