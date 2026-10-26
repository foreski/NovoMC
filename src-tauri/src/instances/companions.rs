//! Third-party mods NovoMC installs next to the NovoMC Client in Forge 1.8.9
//! instances. They are not shipped with NovoMC: each is downloaded once from
//! its author's official release, checked against a pinned SHA-1, cached in
//! `cache/mods/`, then copied into the instance's `mods/` before launch.

use std::path::Path;

use super::client::supported;
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

/// Installs (or removes, when the client is off) the companion mods for an instance.
pub async fn sync(root: &Path, inst: &Instance) -> AppResult<()> {
    let mods = store::game_dir(root, &inst.id).join("mods");
    install(root, &mods, supported(inst) && inst.client_mod).await
}

/// Puts the companion mods into (or, if not `wanted`, removes them from) a `mods/` folder.
pub async fn install(root: &Path, mods: &Path, wanted: bool) -> AppResult<()> {
    for c in COMPANIONS {
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
