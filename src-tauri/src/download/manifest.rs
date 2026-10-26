//! Mojang's version manifest (piston-meta): every release, snapshot and old
//! version, with the URL and SHA1 of each version JSON.

use std::path::{Path, PathBuf};
use std::time::Duration;

use serde::{Deserialize, Serialize};

use super::http;
use crate::error::AppResult;
use crate::paths;

pub const MANIFEST_URL: &str = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

/// A cached manifest younger than this is used without asking Mojang again.
const MAX_AGE: Duration = Duration::from_secs(10 * 60);

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct VersionManifest {
    pub latest: Latest,
    pub versions: Vec<ManifestEntry>,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct Latest {
    pub release: String,
    pub snapshot: String,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ManifestEntry {
    pub id: String,
    #[serde(rename = "type")]
    pub kind: VersionType,
    pub url: String,
    pub release_time: String,
    pub sha1: String,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum VersionType {
    Release,
    Snapshot,
    OldBeta,
    OldAlpha,
    #[serde(other)]
    Other,
}

fn cache_path() -> AppResult<PathBuf> {
    Ok(paths::data_dir()?.join("versions").join("version_manifest_v2.json"))
}

fn read_cache(path: &Path) -> AppResult<VersionManifest> {
    Ok(serde_json::from_slice(&std::fs::read(path)?)?)
}

fn is_recent(path: &Path) -> bool {
    std::fs::metadata(path)
        .and_then(|m| m.modified())
        .ok()
        .and_then(|t| t.elapsed().ok())
        .is_some_and(|age| age < MAX_AGE)
}

/// Returns the manifest, from a recent cache when possible. With `refresh`
/// it always asks Mojang. Falls back to the cache when offline.
pub async fn load(refresh: bool) -> AppResult<VersionManifest> {
    let path = cache_path()?;
    if !refresh && is_recent(&path) {
        if let Ok(manifest) = read_cache(&path) {
            return Ok(manifest);
        }
    }
    match http::get_bytes(MANIFEST_URL).await {
        Ok(bytes) => {
            let manifest = serde_json::from_slice(&bytes)?;
            let tmp = path.with_extension("json.tmp");
            tokio::fs::write(&tmp, &bytes).await?;
            tokio::fs::rename(&tmp, &path).await?;
            Ok(manifest)
        }
        Err(e) => read_cache(&path).map_err(|_| e),
    }
}
