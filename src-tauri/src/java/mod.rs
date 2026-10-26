//! Java detection and runtime management.
//!
//! - `detect`: finds installed Javas and reads their major version.
//! - `runtime`: downloads Mojang's Java runtimes when nothing suitable is installed.
//!
//! Each Minecraft version declares the Java it needs (`javaVersion` in its
//! JSON: 8, 16, 17, 21, ...); versions without it need Java 8.

pub mod detect;
pub mod runtime;

use std::path::PathBuf;

use tokio_util::sync::CancellationToken;

use crate::download::engine::Progress;
use crate::download::version::VersionJson;
use crate::error::{AppError, AppResult};
use crate::paths;
use crate::settings::Settings;
use detect::JavaInstall;

/// The Java major version and Mojang runtime component a version needs.
pub fn requirement(version: &VersionJson) -> (u32, String) {
    match &version.java_version {
        Some(j) => (j.major_version, j.component.clone()),
        None => (8, "jre-legacy".into()),
    }
}

/// Picks the Java to launch `version` with: the custom path from settings,
/// else NovoMC's runtime, else an installed Java of the exact major version,
/// else downloads Mojang's runtime.
pub async fn resolve(
    version: &VersionJson,
    settings: &Settings,
    progress: &Progress,
    cancel: &CancellationToken,
) -> AppResult<PathBuf> {
    if let Some(custom) = &settings.java_path {
        let path = PathBuf::from(custom);
        return if path.is_file() {
            Ok(path)
        } else {
            Err(AppError::Other(format!("Custom Java not found: {custom}")))
        };
    }

    let root = paths::data_dir()?;
    let (major, component) = requirement(version);
    if runtime::is_installed(&root, &component) {
        return Ok(runtime::java_path(&root, &component));
    }

    let installed = tokio::task::spawn_blocking(detect::find_all)
        .await
        .map_err(|e| AppError::Other(e.to_string()))?;
    if let Some(java) = installed.into_iter().find(|j| j.major == major && j.is_64bit()) {
        return Ok(java.path);
    }

    runtime::install(&root, &component, progress, cancel).await
}

#[tauri::command]
pub async fn list_java() -> AppResult<Vec<JavaInstall>> {
    tokio::task::spawn_blocking(detect::find_all)
        .await
        .map_err(|e| AppError::Other(e.to_string()))
}
