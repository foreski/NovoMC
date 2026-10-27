//! Minecraft Forge for old versions (1.8.9) with the legacy installer format.
//!
//! The legacy installer jar holds an `install_profile.json` (Forge's version
//! JSON under `versionInfo`, inheriting from vanilla) and the Forge universal
//! jar. NovoMC installs it directly, like the old installer did:
//!
//! 1. download the installer from Forge's Maven and verify its SHA1,
//! 2. write the version JSON and unpack the universal jar into `libraries/`,
//! 3. download Forge's libraries, each verified against its repository's
//!    published `.sha1`.
//!
//! Newer installers (1.12.2+) need post-processing NovoMC doesn't support yet.

use std::io::Read;
use std::path::Path;

use futures_util::{stream, StreamExt, TryStreamExt};
use regex::Regex;
use serde::Serialize;
use serde_json::Value;
use tokio_util::sync::CancellationToken;

use super::engine;
use super::http;
use super::install::{self, Tracker};
use super::plan::FileTask;
use super::version::{maven_path, Library, VersionJson};
use crate::error::{AppError, AppResult};
use crate::paths;

pub const MAVEN: &str = "https://maven.minecraftforge.net/";
const METADATA: &str = "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml";
const PROMOTIONS: &str = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";

/// Repositories tried, in order, for libraries a profile doesn't pin.
const MIRRORS: &[&str] = &["https://libraries.minecraft.net/", MAVEN, "https://repo1.maven.org/maven2/"];

/// Minecraft versions whose Forge uses the legacy installer NovoMC supports.
pub const SUPPORTED: &[&str] = &["1.8.9"];

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ForgeVersions {
    /// Full Maven versions (`1.8.9-11.15.1.2318-1.8.9`), newest first.
    versions: Vec<String>,
    recommended: Option<String>,
}

/// The version ID Forge installs as, e.g. `1.8.9-forge1.8.9-11.15.1.2318-1.8.9`.
pub fn version_id(minecraft: &str, forge: &str) -> String {
    format!("{minecraft}-forge{forge}")
}

/// Forge builds for a Minecraft version, plus the recommended one.
pub async fn list_versions(minecraft: &str) -> AppResult<ForgeVersions> {
    if !SUPPORTED.contains(&minecraft) {
        return Ok(ForgeVersions { versions: Vec::new(), recommended: None });
    }
    let xml = String::from_utf8_lossy(&http::get_bytes(METADATA).await?).into_owned();
    let prefix = format!("{minecraft}-");
    let re = Regex::new(r"<version>([^<]+)</version>").unwrap();
    let mut versions: Vec<String> =
        re.captures_iter(&xml).map(|c| c[1].to_string()).filter(|v| v.starts_with(&prefix)).collect();
    versions.sort_by_key(|v| std::cmp::Reverse(build_key(v)));

    // Promotions name the build only (`11.15.1.2318`); map it to the full version.
    let promos: Value = serde_json::from_slice(&http::get_bytes(PROMOTIONS).await?)?;
    let recommended = ["recommended", "latest"]
        .iter()
        .find_map(|kind| promos["promos"][format!("{minecraft}-{kind}")].as_str())
        .and_then(|build| versions.iter().find(|v| v.starts_with(&format!("{minecraft}-{build}"))).cloned());
    Ok(ForgeVersions { versions, recommended })
}

/// `https://` and the current Maven host for URLs in old profiles.
fn normalize_repo(url: &str) -> String {
    let mut url = url.replacen("http://", "https://", 1).replace("files.minecraftforge.net/maven", "maven.minecraftforge.net");
    if !url.ends_with('/') {
        url.push('/');
    }
    url
}

/// The published SHA1 of `url`, or `None` if the repository doesn't have it.
async fn published_sha1(url: &str) -> AppResult<Option<String>> {
    let Some(body) = http::get_optional(&format!("{url}.sha1")).await? else {
        return Ok(None);
    };
    let text = String::from_utf8_lossy(&body);
    let sha1 = text.split_whitespace().next().unwrap_or_default().to_lowercase();
    Ok((sha1.len() == 40 && sha1.chars().all(|c| c.is_ascii_hexdigit())).then_some(sha1))
}

/// Finds which repository serves a library, and the checksum to verify it by:
/// the repository's published `.sha1`, else the profile's `checksums`. A few
/// old libraries have neither; those are only checked for size.
async fn resolve_library(root: &Path, lib: &Library) -> AppResult<FileTask> {
    let rel = maven_path(&lib.name).ok_or_else(|| AppError::Other(format!("Bad library name {}", lib.name)))?;
    let path = root.join("libraries").join(&rel);
    let mut repos: Vec<String> = lib.url.iter().map(|u| normalize_repo(u)).collect();
    repos.extend(MIRRORS.iter().map(|m| m.to_string()));

    for repo in &repos {
        let url = format!("{repo}{rel}");
        if let Some(sha1) = published_sha1(&url).await? {
            return Ok(FileTask { url, path, sha1: Some(sha1), ..Default::default() });
        }
    }
    for repo in &repos {
        let url = format!("{repo}{rel}");
        if http::exists(&url).await? {
            let mut sums = lib.checksums.iter().map(|s| s.to_lowercase());
            return Ok(FileTask { url, path, sha1: sums.next(), alt_sha1: sums.collect(), ..Default::default() });
        }
    }
    Err(AppError::Other(format!("Couldn't find Forge library {}", lib.name)))
}

/// Sort key for a full Forge version: the build numbers (`11.15.1.2318`).
fn build_key(full: &str) -> Vec<u32> {
    full.split('-').nth(1).unwrap_or_default().split('.').filter_map(|p| p.parse().ok()).collect()
}

/// Reads the installer: returns the profile's `versionInfo` and writes the
/// universal jar into `libraries/`. Blocking.
fn unpack_installer(root: &Path, installer: &Path) -> AppResult<Value> {
    let bad = |e: zip::result::ZipError| AppError::Other(format!("Can't read the Forge installer: {e}"));
    let mut zip = zip::ZipArchive::new(std::fs::File::open(installer)?).map_err(bad)?;
    let mut profile = String::new();
    zip.by_name("install_profile.json").map_err(bad)?.read_to_string(&mut profile)?;
    let profile: Value = serde_json::from_str(&profile)?;

    let info = profile.get("versionInfo").cloned().ok_or_else(|| {
        AppError::Other("This Forge version uses the newer installer format, which Novo Client doesn't support yet".into())
    })?;
    let file = profile["install"]["filePath"].as_str().unwrap_or_default();
    let coord = profile["install"]["path"].as_str().unwrap_or_default();
    let rel = maven_path(coord).ok_or_else(|| AppError::Other("Forge installer has no library path".into()))?;
    let dest = root.join("libraries").join(rel);
    std::fs::create_dir_all(dest.parent().unwrap_or(root))?;
    std::io::copy(&mut zip.by_name(file).map_err(bad)?, &mut std::fs::File::create(dest)?)?;
    Ok(info)
}

/// Installs Forge `forge` (full Maven version) for `minecraft` and returns its
/// version ID. The vanilla version must already be installed.
pub async fn install(minecraft: &str, forge: &str, tracker: &Tracker, cancel: &CancellationToken) -> AppResult<String> {
    if !SUPPORTED.contains(&minecraft) {
        return Err(AppError::Other(format!("Forge for {minecraft} isn't supported yet")));
    }
    if !forge.starts_with(&format!("{minecraft}-")) {
        return Err(AppError::Other(format!("Forge {forge} isn't for Minecraft {minecraft}")));
    }
    let root = paths::data_dir()?;
    let id = version_id(minecraft, forge);
    tracker.set_stage(install::Stage::Metadata);

    let rel = format!("net/minecraftforge/forge/{forge}/forge-{forge}-installer.jar");
    let url = format!("{MAVEN}{rel}");
    let installer = FileTask {
        sha1: published_sha1(&url).await?,
        url,
        path: root.join("libraries").join(&rel),
        alt_sha1: Vec::new(),
        size: 0,
    };
    engine::ensure_file(&installer, &tracker.progress, cancel).await?;

    let mut info = tokio::task::spawn_blocking({
        let (root, path) = (root.clone(), installer.path.clone());
        move || unpack_installer(&root, &path)
    })
    .await
    .map_err(|e| AppError::Other(e.to_string()))??;
    info["id"] = Value::String(id.clone());
    let dir = root.join("versions").join(&id);
    tokio::fs::create_dir_all(&dir).await?;
    tokio::fs::write(dir.join(format!("{id}.json")), serde_json::to_vec_pretty(&info)?).await?;

    // Every library except Forge itself, which came out of the installer.
    let profile: VersionJson = serde_json::from_value(info)?;
    let forge_coord = format!("net.minecraftforge:forge:{forge}");
    let libs: Vec<Library> = profile.libraries.into_iter().filter(|l| l.applies() && l.name != forge_coord).collect();
    let tasks: Vec<FileTask> = stream::iter(libs)
        .map(|lib| {
            let root = root.clone();
            async move { resolve_library(&root, &lib).await }
        })
        .buffer_unordered(8)
        .try_collect()
        .await?;

    tracker.set_stage(install::Stage::Files);
    engine::download_all(tasks, &tracker.progress, cancel).await?;
    install::mark_installed(&root, &id).await?;
    Ok(id)
}

#[tauri::command]
pub async fn list_forge_versions(minecraft: String) -> AppResult<ForgeVersions> {
    list_versions(&minecraft).await
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn version_ids_and_repos() {
        assert_eq!(version_id("1.8.9", "1.8.9-11.15.1.2318-1.8.9"), "1.8.9-forge1.8.9-11.15.1.2318-1.8.9");
        assert_eq!(normalize_repo("http://files.minecraftforge.net/maven/"), MAVEN);
        assert_eq!(normalize_repo("https://repo.example.com"), "https://repo.example.com/");
        assert!(build_key("1.8.9-11.15.1.2318-1.8.9") > build_key("1.8.9-11.15.0.1656"));
    }

    #[tokio::test]
    #[ignore = "talks to Forge's servers"]
    async fn lists_real_forge_versions() {
        let list = list_versions("1.8.9").await.unwrap();
        println!("recommended {:?}, {} builds, newest {:?}", list.recommended, list.versions.len(), list.versions.first());
        assert!(list.versions.iter().all(|v| v.starts_with("1.8.9-")));
        assert!(list.recommended.is_some());
    }
}
