//! Third-party mods Novo Client installs next to its own mod: MemoryFix in
//! Forge 1.8.9 instances, Fabric API (which the modern client needs) in Fabric
//! instances. They are not shipped with Novo Client: each is downloaded once from
//! its author's official release, checked against a pinned SHA-1, cached in
//! `cache/mods/`, then copied into the instance's `mods/` before launch.

use std::path::Path;

use super::client::{legacy, modern};
use super::store::{self, Instance};
use crate::download::{engine::hash_file, http};
use crate::error::{AppError, AppResult};

struct Companion {
    file: &'static str,
    url: &'static str,
    sha1: &'static str,
}

const COMPANIONS: &[Companion] = &[
    // prplz's MemoryFix: fixes the oversized resource pack icon and OptiFine cape memory leaks.
    // https://github.com/prplz/MemoryFix
    Companion {
        file: "MemoryFix-0.3.jar",
        url: "https://github.com/prplz/MemoryFix/releases/download/0.3/MemoryFix-0.3.jar",
        sha1: "8f363b35fd4df1389f3cd9408c5be2b23b61e9a0",
    },
];

const MODERN_COMPANIONS: &[Companion] = &[
    // Fabric API (Apache 2.0), required by the modern Novo Client, from its official Modrinth release.
    // https://modrinth.com/mod/fabric-api
    Companion {
        file: "fabric-api-0.165.0+26.3.jar",
        url: "https://cdn.modrinth.com/data/P7dR8mSH/versions/EQKgpeKu/fabric-api-0.165.0%2B26.3.jar",
        sha1: "8f8a23d6c9fbf7b075b31b4fb0a1007b0488d1d2",
    },
];

/// The companion jar names the launcher installs for this instance, which
/// `mods::enforce` lets stay in its `mods/` folder.
pub fn files(inst: &Instance) -> Vec<&'static str> {
    let set = if modern(inst) { MODERN_COMPANIONS } else { COMPANIONS };
    set.iter().map(|c| c.file).collect()
}

/// Installs (or removes, when the client is off) the companion mods for an instance.
pub async fn sync(root: &Path, inst: &Instance) -> AppResult<()> {
    let mods = store::game_dir(root, &inst.id).join("mods");
    install(root, &mods, COMPANIONS, legacy(inst) && inst.client_mod).await?;
    install(root, &mods, MODERN_COMPANIONS, modern(inst) && inst.client_mod).await
}

/// Puts a client's companion mods into a `mods/` folder: modern (Fabric) or 1.8.9.
pub async fn install_for(root: &Path, mods: &Path, modern: bool) -> AppResult<()> {
    install(root, mods, COMPANIONS, !modern).await?;
    install(root, mods, MODERN_COMPANIONS, modern).await
}

/// Puts the companion mods into (or, if not `wanted`, removes them from) a `mods/` folder.
async fn install(root: &Path, mods: &Path, companions: &[Companion], wanted: bool) -> AppResult<()> {
    for c in companions {
        let dest = mods.join(c.file);
        if !wanted {
            if dest.exists() {
                std::fs::remove_file(&dest)?;
            }
            continue;
        }
        if dest.is_file() && hash_file(&dest)? == c.sha1 {
            continue;
        }
        let cached = root.join("cache").join("mods").join(c.file);
        if !(cached.is_file() && hash_file(&cached)? == c.sha1) {
            let bytes = http::get_bytes(c.url).await?;
            std::fs::create_dir_all(cached.parent().unwrap_or(root))?;
            let tmp = cached.with_extension("part");
            std::fs::write(&tmp, &bytes)?;
            if hash_file(&tmp)? != c.sha1 {
                let _ = std::fs::remove_file(&tmp);
                return Err(AppError::Checksum { file: c.file.into() });
            }
            std::fs::rename(&tmp, &cached)?;
        }
        std::fs::create_dir_all(mods)?;
        std::fs::copy(&cached, &dest)?;
    }
    Ok(())
}
