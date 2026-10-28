//! Fabric for modern Minecraft (the Novo Client's modern versions).
//!
//! Fabric's meta server hands out a ready launcher profile: a version JSON
//! that inherits from vanilla and lists Fabric's libraries with their Maven
//! repository and SHA1. Novo Client writes that JSON and downloads the
//! libraries, nothing else.

use serde::Deserialize;
use tokio_util::sync::CancellationToken;

use super::engine;
use super::http;
use super::install::{self, Tracker};
use super::plan;
use super::version::VersionJson;
use crate::error::{AppError, AppResult};
use crate::paths;

const META: &str = "https://meta.fabricmc.net/v2";

/// The version ID Fabric installs as, e.g. `fabric-loader-0.20.1-26.3`.
pub fn version_id(minecraft: &str, loader: &str) -> String {
    format!("fabric-loader-{loader}-{minecraft}")
}

#[derive(Deserialize)]
struct LoaderEntry {
    version: String,
    stable: bool,
}

#[derive(Deserialize)]
struct GameEntry {
    version: String,
}

/// The newest stable Fabric Loader.
pub async fn latest_loader() -> AppResult<String> {
    let list: Vec<LoaderEntry> = serde_json::from_slice(&http::get_bytes(&format!("{META}/versions/loader")).await?)?;
    list.into_iter()
        .find(|l| l.stable)
        .map(|l| l.version)
        .ok_or_else(|| AppError::Other("Fabric has no stable loader listed".into()))
}

/// Whether Fabric supports this Minecraft version at all.
pub async fn supports(minecraft: &str) -> AppResult<bool> {
    let list: Vec<GameEntry> = serde_json::from_slice(&http::get_bytes(&format!("{META}/versions/game")).await?)?;
    Ok(list.iter().any(|g| g.version == minecraft))
}

/// The newest stable Fabric Loader if Fabric supports `minecraft`, else None.
#[tauri::command]
pub async fn fabric_loader(minecraft: String) -> AppResult<Option<String>> {
    if !supports(&minecraft).await? {
        return Ok(None);
    }
    Ok(Some(latest_loader().await?))
}

/// Installs Fabric Loader `loader` for `minecraft` and returns its version ID.
/// The vanilla version must already be installed.
pub async fn install(minecraft: &str, loader: &str, tracker: &Tracker, cancel: &CancellationToken) -> AppResult<String> {
    let valid = |s: &str| !s.is_empty() && s.chars().all(|c| c.is_ascii_alphanumeric() || ".-+_".contains(c));
    if !valid(minecraft) || !valid(loader) {
        return Err(AppError::Other("Bad Fabric version".into()));
    }
    let root = paths::data_dir()?;
    let id = version_id(minecraft, loader);
    tracker.set_stage(install::Stage::Metadata);

    let url = format!("{META}/versions/loader/{minecraft}/{loader}/profile/json");
    let bytes = http::get_bytes(&url).await?;
    let profile: VersionJson = serde_json::from_slice(&bytes)
        .map_err(|e| AppError::Other(format!("Fabric's profile for {minecraft} couldn't be read: {e}")))?;
    if profile.id != id {
        return Err(AppError::Other(format!("Fabric sent profile {} instead of {id}", profile.id)));
    }
    let dir = root.join("versions").join(&id);
    tokio::fs::create_dir_all(&dir).await?;
    tokio::fs::write(dir.join(format!("{id}.json")), &bytes).await?;

    tracker.set_stage(install::Stage::Files);
    engine::download_all(plan::version_files(&root, &profile), &tracker.progress, cancel).await?;
    install::mark_installed(&root, &id).await?;
    Ok(id)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn version_ids() {
        assert_eq!(version_id("26.3", "0.20.1"), "fabric-loader-0.20.1-26.3");
    }

    #[tokio::test]
    #[ignore = "talks to Fabric's servers"]
    async fn real_fabric_meta() {
        assert!(supports("26.3").await.unwrap());
        println!("latest loader {}", latest_loader().await.unwrap());
    }
}
