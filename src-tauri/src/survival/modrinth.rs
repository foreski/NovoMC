//! The Modrinth API, for the survival client's mods, shaders and packs.
//!
//! Novo Client only reads from Modrinth and downloads the files its API points
//! at. Every jar or zip is checked against the SHA-512 Modrinth publishes for
//! it before it is written into an instance, so a redirect or a mirrored CDN
//! can't slip a different file in.

use std::path::{Path, PathBuf};

use serde::Deserialize;
use sha2::{Digest, Sha512};

use crate::download::http;
use crate::error::{AppError, AppResult};

const API: &str = "https://api.modrinth.com/v2";

/// A search result, trimmed to what the launcher shows.
#[derive(Debug, Clone, Deserialize)]
pub struct Hit {
    pub slug: String,
    pub title: String,
    #[serde(default)]
    description: String,
    #[serde(default)]
    icon_url: Option<String>,
    #[serde(default)]
    downloads: u64,
}

/// What the UI needs about a search result.
#[derive(Debug, Clone, serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Found {
    pub slug: String,
    pub title: String,
    pub description: String,
    pub icon_url: Option<String>,
    pub downloads: u64,
}

impl From<Hit> for Found {
    fn from(hit: Hit) -> Self {
        Self {
            slug: hit.slug,
            title: hit.title,
            description: hit.description,
            icon_url: hit.icon_url,
            downloads: hit.downloads,
        }
    }
}

#[derive(Debug, Clone, Default, Deserialize)]
pub struct Hashes {
    #[serde(default)]
    pub sha512: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct File {
    pub url: String,
    pub filename: String,
    #[serde(default)]
    pub primary: bool,
    #[serde(default)]
    pub size: u64,
    #[serde(default)]
    pub hashes: Hashes,
}

#[derive(Debug, Clone, Deserialize)]
struct Dependency {
    #[serde(default)]
    project_id: Option<String>,
    #[serde(default)]
    dependency_type: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct Version {
    pub id: String,
    pub version_number: String,
    pub date_published: String,
    #[serde(default)]
    pub files: Vec<File>,
    #[serde(default)]
    dependencies: Vec<Dependency>,
}

impl Version {
    /// The file to install: Modrinth's primary one, else the first.
    pub fn file(&self) -> Option<&File> {
        self.files.iter().find(|f| f.primary).or_else(|| self.files.first())
    }

    /// Modrinth project IDs this version can't run without.
    pub fn required(&self) -> Vec<String> {
        self.dependencies
            .iter()
            .filter(|d| d.dependency_type == "required")
            .filter_map(|d| d.project_id.clone())
            .collect()
    }
}

#[derive(Deserialize)]
struct SearchResponse {
    #[serde(default)]
    hits: Vec<Hit>,
}

/// Escapes a value for a Modrinth facet list, e.g. `1.20.1` or `fabric`.
fn quoted(value: &str) -> String {
    format!("\"{}\"", value.replace('"', ""))
}

/// Searches one project type among builds for `edition`. Fabric mods are
/// filtered to Fabric; shaders and packs aren't loader-specific.
pub async fn search(query: &str, project_type: &str, edition: &str, fabric: bool) -> AppResult<Vec<Found>> {
    let mut facets = format!("[[\"project_type:{project_type}\"],[\"versions:{edition}\"]");
    if fabric {
        facets.push_str(",[\"categories:fabric\"]");
    }
    facets.push(']');

    let mut url = format!(
        "{API}/search?query={}&limit=20&facets={}",
        encode(query),
        encode(&facets)
    );
    // An empty query browses: most downloaded first.
    if query.trim().is_empty() {
        url.push_str("&index=downloads");
    }
    let bytes = http::get_bytes(&url).await?;
    let response: SearchResponse = serde_json::from_slice(&bytes)?;
    Ok(response.hits.into_iter().map(Found::from).collect())
}

/// Every version of a project for `edition`, newest first.
pub async fn versions(slug: &str, edition: &str, loader: Option<&str>) -> AppResult<Vec<Version>> {
    let mut url = format!(
        "{API}/project/{}/version?game_versions={}",
        encode(slug),
        encode(&format!("[{}]", quoted(edition)))
    );
    if let Some(loader) = loader {
        url.push_str(&format!("&loaders={}", encode(&format!("[{}]", quoted(loader)))));
    }
    let bytes = http::get_bytes(&url).await?;
    let mut list: Vec<Version> = serde_json::from_slice(&bytes)?;
    list.sort_by(|a, b| b.date_published.cmp(&a.date_published));
    list.retain(|v| v.file().is_some());
    Ok(list)
}

/// The newest version of `slug` with a downloadable file for `edition`.
pub async fn latest(slug: &str, edition: &str, loader: Option<&str>) -> AppResult<Option<Version>> {
    Ok(versions(slug, edition, loader).await?.into_iter().next())
}

/// The slug of a project from its Modrinth ID (dependencies are linked by ID).
pub async fn slug_of(project_id: &str) -> AppResult<String> {
    #[derive(Deserialize)]
    struct Project {
        slug: String,
    }
    let bytes = http::get_bytes(&format!("{API}/project/{}", encode(project_id))).await?;
    Ok(serde_json::from_slice::<Project>(&bytes)?.slug)
}

/// Downloads `file` into `dir`, verifies Modrinth's SHA-512, and returns its path.
pub async fn download(file: &File, dir: &Path) -> AppResult<PathBuf> {
    let name = file.filename.trim();
    if name.is_empty() || name == "." || name == ".." || name.chars().any(|c| c == '/' || c == '\\') {
        return Err(AppError::Other(format!("Modrinth sent an unusable file name: {name:?}")));
    }
    let expected = file
        .hashes
        .sha512
        .as_deref()
        .ok_or_else(|| AppError::Other(format!("Modrinth published no checksum for {name}")))?;

    let bytes = http::get_bytes(&file.url).await?;
    if !sha512(&bytes).eq_ignore_ascii_case(expected) {
        return Err(AppError::Checksum { file: name.into() });
    }

    tokio::fs::create_dir_all(dir).await?;
    let dest = dir.join(name);
    let tmp = dest.with_extension("part");
    tokio::fs::write(&tmp, &bytes).await?;
    tokio::fs::rename(&tmp, &dest).await?;
    Ok(dest)
}

fn sha512(bytes: &[u8]) -> String {
    let mut hasher = Sha512::new();
    hasher.update(bytes);
    hasher.finalize().iter().map(|b| format!("{b:02x}")).collect()
}

/// Percent-encodes one path segment (slugs and IDs are ASCII, but be safe).
fn encode(value: &str) -> String {
    let mut out = String::with_capacity(value.len());
    for byte in value.bytes() {
        match byte {
            b'a'..=b'z' | b'A'..=b'Z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => out.push(byte as char),
            _ => out.push_str(&format!("%{byte:02X}")),
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn encodes_segments() {
        assert_eq!(encode("entity-model-features"), "entity-model-features");
        assert_eq!(encode("a b"), "a%20b");
        assert_eq!(encode("x/y"), "x%2Fy");
    }

    #[test]
    fn quotes_facets() {
        assert_eq!(quoted("1.20.1"), "\"1.20.1\"");
        assert_eq!(quoted("a\"b"), "\"ab\"");
    }

    #[test]
    fn prefers_the_primary_file() {
        let version: Version = serde_json::from_str(
            r#"{"id":"v","version_number":"1","date_published":"2024-01-01T00:00:00Z","files":[
                {"url":"https://x/a.jar","filename":"a.jar","primary":false},
                {"url":"https://x/b.jar","filename":"b.jar","primary":true}]}"#,
        )
        .unwrap();
        assert_eq!(version.file().unwrap().filename, "b.jar");
        assert!(version.required().is_empty());
    }

    #[test]
    fn reads_required_dependencies_only() {
        let version: Version = serde_json::from_str(
            r#"{"id":"v","version_number":"1","date_published":"2024-01-01T00:00:00Z","files":[
                {"url":"https://x/a.jar","filename":"a.jar","primary":true}],
                "dependencies":[
                  {"project_id":"P1","dependency_type":"required"},
                  {"project_id":"P2","dependency_type":"optional"},
                  {"version_id":"V3","dependency_type":"required"}]}"#,
        )
        .unwrap();
        assert_eq!(version.required(), vec!["P1"]);
    }
}
