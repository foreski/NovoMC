//! Java runtimes from Mojang's Java runtime manifest (the same runtimes the
//! official launcher uses), installed under `runtimes/<component>/`.

use std::collections::HashMap;
use std::path::{Path, PathBuf};

use serde::Deserialize;
use tokio_util::sync::CancellationToken;

use super::detect::java_bin;
use crate::download::engine::{self, Progress};
use crate::download::http;
use crate::download::plan::FileTask;
use crate::download::version::Download;
use crate::error::{AppError, AppResult};

const RUNTIME_INDEX: &str =
    "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";

/// Written once a runtime is fully downloaded.
const MARKER: &str = ".novomc-runtime";

#[derive(Deserialize)]
struct RuntimeEntry {
    manifest: Download,
}

/// platform -> component -> entries (normally one).
type RuntimeIndex = HashMap<String, HashMap<String, Vec<RuntimeEntry>>>;

#[derive(Deserialize)]
struct FileManifest {
    files: HashMap<String, RuntimeFile>,
}

#[derive(Deserialize)]
#[serde(tag = "type", rename_all = "lowercase")]
enum RuntimeFile {
    File {
        #[serde(default)]
        executable: bool,
        downloads: RawDownloads,
    },
    Directory,
    Link {
        target: String,
    },
}

#[derive(Deserialize)]
struct RawDownloads {
    raw: Download,
}

/// Mojang's name for this OS/CPU in the runtime manifest.
fn platform() -> Option<&'static str> {
    Some(match (std::env::consts::OS, std::env::consts::ARCH) {
        ("windows", "x86_64") => "windows-x64",
        ("windows", "x86") => "windows-x86",
        ("windows", "aarch64") => "windows-arm64",
        ("macos", "aarch64") => "mac-os-arm64",
        ("macos", _) => "mac-os",
        ("linux", "x86_64") => "linux",
        ("linux", "x86") => "linux-i386",
        _ => return None,
    })
}

fn runtime_dir(root: &Path, component: &str) -> PathBuf {
    root.join("runtimes").join(component)
}

/// Java binary of an installed runtime component.
pub fn java_path(root: &Path, component: &str) -> PathBuf {
    let dir = runtime_dir(root, component);
    if cfg!(target_os = "macos") {
        java_bin(&dir.join("jre.bundle").join("Contents").join("Home"))
    } else {
        java_bin(&dir)
    }
}

pub fn is_installed(root: &Path, component: &str) -> bool {
    runtime_dir(root, component).join(MARKER).exists() && java_path(root, component).is_file()
}

/// Downloads runtime `component` (e.g. `java-runtime-delta`, `jre-legacy`)
/// and returns its Java binary.
pub async fn install(root: &Path, component: &str, progress: &Progress, cancel: &CancellationToken) -> AppResult<PathBuf> {
    let platform = platform().ok_or_else(|| AppError::Other("Mojang has no Java runtime for this platform".into()))?;
    let index: RuntimeIndex = serde_json::from_slice(&http::get_bytes(RUNTIME_INDEX).await?)?;
    let entry = index
        .get(platform)
        .and_then(|c| c.get(component))
        .and_then(|v| v.first())
        .ok_or_else(|| AppError::Other(format!("Mojang has no {component} runtime for {platform}")))?;

    let manifest_bytes = http::get_bytes(&entry.manifest.url).await?;
    if engine::sha1_hex(&manifest_bytes) != entry.manifest.sha1 {
        return Err(AppError::Checksum { file: format!("{component} manifest") });
    }
    let manifest: FileManifest = serde_json::from_slice(&manifest_bytes)?;

    let dir = runtime_dir(root, component);
    let mut tasks = Vec::new();
    let mut executables = Vec::new();
    let mut links = Vec::new();
    for (rel, file) in manifest.files {
        let path = dir.join(&rel);
        match file {
            RuntimeFile::File { executable, downloads } => {
                if executable {
                    executables.push(path.clone());
                }
                tasks.push(FileTask {
                    url: downloads.raw.url,
                    path,
                    sha1: Some(downloads.raw.sha1),
                    alt_sha1: Vec::new(),
                    size: downloads.raw.size,
                });
            }
            RuntimeFile::Directory => tokio::fs::create_dir_all(&path).await?,
            RuntimeFile::Link { target } => links.push((path, target)),
        }
    }

    engine::download_all(tasks, progress, cancel).await?;
    finish_unix(&executables, &links)?;
    tokio::fs::write(dir.join(MARKER), entry.manifest.sha1.as_bytes()).await?;
    Ok(java_path(root, component))
}

/// Sets executable bits and creates symlinks (macOS/Linux runtimes only).
#[cfg(unix)]
fn finish_unix(executables: &[PathBuf], links: &[(PathBuf, String)]) -> AppResult<()> {
    use std::os::unix::fs::PermissionsExt;
    for path in executables {
        std::fs::set_permissions(path, std::fs::Permissions::from_mode(0o755))?;
    }
    for (path, target) in links {
        let _ = std::fs::remove_file(path);
        if let Some(parent) = path.parent() {
            std::fs::create_dir_all(parent)?;
        }
        std::os::unix::fs::symlink(target, path)?;
    }
    Ok(())
}

#[cfg(not(unix))]
fn finish_unix(_executables: &[PathBuf], _links: &[(PathBuf, String)]) -> AppResult<()> {
    Ok(())
}
