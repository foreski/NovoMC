//! Launcher self-updates (Tauri updater plugin).
//!
//! The update feed is `latest.json` on the newest GitHub release (see
//! `plugins.updater` in tauri.conf.json). Every installer is signed with
//! NovoMC's private key and checked against the public key built into the app,
//! so only builds published by NovoMC are installed.

use std::sync::Mutex;

use serde::Serialize;
use tauri::{AppHandle, Emitter, Manager};
use tauri_plugin_updater::{Update, UpdaterExt};

use crate::error::{AppError, AppResult};

/// The update found by the last check, kept for `install_update`.
#[derive(Default)]
pub struct PendingUpdate(Mutex<Option<Update>>);

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateInfo {
    pub version: String,
    pub current_version: String,
    pub notes: Option<String>,
    pub date: Option<String>,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct UpdateProgress {
    downloaded: u64,
    total: Option<u64>,
}

fn err(e: impl std::fmt::Display) -> AppError {
    AppError::Other(format!("Update check failed: {e}"))
}

/// Looks for a newer launcher. `None` means NovoMC is up to date.
#[tauri::command]
pub async fn check_update(app: AppHandle) -> AppResult<Option<UpdateInfo>> {
    let update = app.updater().map_err(err)?.check().await.map_err(err)?;
    let info = update.as_ref().map(|u| UpdateInfo {
        version: display_version(&u.version),
        current_version: display_version(&u.current_version),
        notes: u.body.clone(),
        date: u.date.map(|d| d.to_string()),
    });
    *app.state::<PendingUpdate>().0.lock().unwrap() = update;
    Ok(info)
}

fn display_version(version: &str) -> String {
    version.split('+').next().unwrap_or(version).to_owned()
}

/// Downloads and installs the update found by `check_update`, emitting
/// `update-progress`. On Windows the installer closes NovoMC and reopens it.
#[tauri::command]
pub async fn install_update(app: AppHandle) -> AppResult<()> {
    let update = app
        .state::<PendingUpdate>()
        .0
        .lock()
        .unwrap()
        .take()
        .ok_or_else(|| AppError::Other("No update to install; check again".into()))?;
    let mut downloaded = 0u64;
    let emitter = app.clone();
    update
        .download_and_install(
            move |chunk, total| {
                downloaded += chunk as u64;
                let _ = emitter.emit("update-progress", UpdateProgress { downloaded, total });
            },
            || {},
        )
        .await
        .map_err(|e| AppError::Other(format!("Update failed: {e}")))?;
    app.restart();
}
