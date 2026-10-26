//! Per-launch file preparation: native libraries and legacy asset layouts.

use std::fs;
use std::path::{Path, PathBuf};

use crate::download::version::{maven_path, AssetIndex, VersionJson};
use crate::error::{AppError, AppResult};

/// Extracts legacy native jars (libraries with a `natives` map) into `dir`.
/// Modern versions (1.19+) ship natives as classpath jars that LWJGL unpacks itself.
pub fn extract_natives(root: &Path, v: &VersionJson, dir: &Path) -> AppResult<()> {
    if dir.exists() {
        fs::remove_dir_all(dir)?;
    }
    fs::create_dir_all(dir)?;

    for lib in v.libraries.iter().filter(|l| l.applies()) {
        let Some(classifier) = lib.native_classifier() else { continue };
        let artifact = lib.downloads.as_ref().and_then(|d| d.classifiers.as_ref()?.get(&classifier));
        let Some(rel) = artifact.and_then(|a| a.path.clone()).or_else(|| maven_path(&format!("{}:{classifier}", lib.name)))
        else {
            continue;
        };
        let exclude = lib.extract.as_ref().map(|e| e.exclude.as_slice()).unwrap_or_default();

        let jar = root.join("libraries").join(rel);
        let mut archive = zip::ZipArchive::new(fs::File::open(&jar)?)
            .map_err(|e| AppError::Other(format!("Can't read {}: {e}", jar.display())))?;
        for i in 0..archive.len() {
            let mut entry = archive.by_index(i).map_err(|e| AppError::Other(e.to_string()))?;
            let Some(name) = entry.enclosed_name() else { continue };
            let name_str = name.to_string_lossy().replace('\\', "/");
            if entry.is_dir() || exclude.iter().any(|e| name_str.starts_with(e.as_str())) {
                continue;
            }
            let out = dir.join(&name);
            if let Some(parent) = out.parent() {
                fs::create_dir_all(parent)?;
            }
            std::io::copy(&mut entry, &mut fs::File::create(out)?)?;
        }
    }
    Ok(())
}

/// Copies assets by name for versions that can't read the hashed object store:
/// `resources/` in the game folder (`map_to_resources`, very old) or
/// `assets/virtual/<index>/` (`virtual`, pre-1.7). Returns that folder.
pub fn prepare_legacy_assets(root: &Path, index_id: &str, index: &AssetIndex, game_dir: &Path) -> AppResult<Option<PathBuf>> {
    let target = if index.map_to_resources {
        game_dir.join("resources")
    } else if index.is_virtual {
        root.join("assets").join("virtual").join(index_id)
    } else {
        return Ok(None);
    };
    let objects = root.join("assets").join("objects");
    for (name, obj) in &index.objects {
        let dest = target.join(name);
        if fs::metadata(&dest).is_ok_and(|m| m.len() == obj.size) {
            continue;
        }
        if let Some(parent) = dest.parent() {
            fs::create_dir_all(parent)?;
        }
        fs::copy(objects.join(&obj.hash[..2]).join(&obj.hash), dest)?;
    }
    Ok(Some(target))
}
