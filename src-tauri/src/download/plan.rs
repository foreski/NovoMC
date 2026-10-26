//! Turns a version JSON and asset index into the list of files to download.

use std::collections::HashSet;
use std::path::{Path, PathBuf};

use super::version::{maven_path, Artifact, AssetIndex, VersionJson};

pub const ASSET_BASE_URL: &str = "https://resources.download.minecraft.net";

/// One file to fetch and verify.
#[derive(Debug, Clone, Default)]
pub struct FileTask {
    pub url: String,
    pub path: PathBuf,
    /// Expected SHA1; files without one are only checked for existence and size.
    pub sha1: Option<String>,
    /// Other SHA1s that are also accepted (old Forge profiles list several).
    pub alt_sha1: Vec<String>,
    /// Expected size in bytes, or 0 when unknown.
    pub size: u64,
}

impl FileTask {
    /// Whether `digest` is an accepted SHA1. Always true without a checksum.
    pub fn accepts(&self, digest: &str) -> bool {
        match &self.sha1 {
            None => true,
            Some(expected) => {
                expected.eq_ignore_ascii_case(digest) || self.alt_sha1.iter().any(|s| s.eq_ignore_ascii_case(digest))
            }
        }
    }
}

fn artifact_task(libs: &Path, artifact: &Artifact, coord: &str) -> Option<FileTask> {
    if artifact.url.is_empty() {
        return None;
    }
    let rel = artifact.path.clone().or_else(|| maven_path(coord))?;
    Some(FileTask {
        url: artifact.url.clone(),
        path: libs.join(rel),
        sha1: artifact.sha1.clone(),
        alt_sha1: Vec::new(),
        size: artifact.size.unwrap_or(0),
    })
}

/// Client jar, libraries (artifacts and natives for this OS) and log config.
pub fn version_files(root: &Path, v: &VersionJson) -> Vec<FileTask> {
    let mut out = Vec::new();

    if let Some(d) = &v.downloads {
        out.push(FileTask {
            url: d.client.url.clone(),
            path: root.join("versions").join(&v.id).join(format!("{}.jar", v.id)),
            sha1: Some(d.client.sha1.clone()),
            alt_sha1: Vec::new(),
            size: d.client.size,
        });
    }

    let libs = root.join("libraries");
    for lib in v.libraries.iter().filter(|l| l.applies()) {
        if let Some(dl) = &lib.downloads {
            if let Some(a) = &dl.artifact {
                out.extend(artifact_task(&libs, a, &lib.name));
            }
            let native = lib.native_classifier().and_then(|c| {
                let a = dl.classifiers.as_ref()?.get(&c)?;
                artifact_task(&libs, a, &format!("{}:{c}", lib.name))
            });
            out.extend(native);
        } else if let (Some(base), Some(rel)) = (&lib.url, maven_path(&lib.name)) {
            out.push(FileTask {
                url: format!("{}/{rel}", base.trim_end_matches('/')),
                path: libs.join(rel),
                sha1: lib.sha1.clone(),
                alt_sha1: Vec::new(),
                size: lib.size.unwrap_or(0),
            });
        }
    }

    if let Some(client) = v.logging.as_ref().and_then(|l| l.client.as_ref()) {
        out.push(FileTask {
            url: client.file.url.clone(),
            path: root.join("assets").join("log_configs").join(&client.file.id),
            sha1: Some(client.file.sha1.clone()),
            alt_sha1: Vec::new(),
            size: client.file.size,
        });
    }

    out
}

/// Every asset object, stored by hash under `assets/objects/<xx>/<hash>`.
pub fn asset_files(root: &Path, index: &AssetIndex) -> Vec<FileTask> {
    let objects = root.join("assets").join("objects");
    let mut seen = HashSet::new();
    index
        .objects
        .values()
        .filter(|o| seen.insert(o.hash.as_str()))
        .map(|o| {
            let prefix = &o.hash[..2];
            FileTask {
                url: format!("{ASSET_BASE_URL}/{prefix}/{}", o.hash),
                path: objects.join(prefix).join(&o.hash),
                sha1: Some(o.hash.clone()),
                alt_sha1: Vec::new(),
                size: o.size,
            }
        })
        .collect()
}
