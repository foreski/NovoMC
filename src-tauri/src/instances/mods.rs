//! Mods folder enforcement: only the launcher's own mods run with Novo Client.
//!
//! `client::sync` and `companions::sync` put `novomc-client.jar` and a couple of
//! pinned companion mods into the instance's `mods/` folder. Anything else a
//! player drops there is moved to `mods-blocked/` before the game starts, so no
//! external mod runs next to Novo Client. Instances that don't run the client
//! (or can't) are left alone: their mods are the player's business.

use std::path::{Path, PathBuf};

use super::client::{self, JAR_NAME};
use super::companions;
use super::store::{self, Instance};
use crate::error::AppResult;

/// A file that was moved out of `mods/`.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Blocked {
    /// Where it was under `mods/`, e.g. `SomeMod.jar` or `1.8.9/SomeMod.jar`.
    pub from: String,
    /// Where it went, relative to the instance's game folder.
    pub to: String,
}

/// Moves unauthorized mods out of the instance's `mods/` folder.
pub fn enforce(root: &Path, inst: &Instance) -> AppResult<Vec<Blocked>> {
    if !(client::supported(inst) && inst.client_mod) {
        return Ok(Vec::new());
    }
    let game = store::game_dir(root, &inst.id);
    let mods = game.join("mods");
    if !mods.is_dir() {
        return Ok(Vec::new());
    }
    let allowed = authorized(inst);
    let mut blocked = Vec::new();
    sweep(&mods, &mods, &game, &allowed, &mut blocked)?;
    Ok(blocked)
}

/// The jar file names that may stay in `mods/`: the client and its companions.
pub fn authorized(inst: &Instance) -> Vec<&'static str> {
    let mut files = vec![JAR_NAME];
    files.extend(companions::files(inst));
    files
}

fn sweep(dir: &Path, mods: &Path, game: &Path, allowed: &[&str], out: &mut Vec<Blocked>) -> AppResult<()> {
    for entry in std::fs::read_dir(dir)? {
        let entry = entry?;
        let path = entry.path();
        // symlink_metadata: don't follow links into folders we shouldn't walk.
        let kind = std::fs::symlink_metadata(&path)?.file_type();
        if kind.is_dir() {
            sweep(&path, mods, game, allowed, out)?;
            continue;
        }
        let name = entry.file_name().to_string_lossy().into_owned();
        let lower = name.to_ascii_lowercase();
        let mod_file = lower.ends_with(".jar") || lower.ends_with(".zip");
        if !(kind.is_file() || kind.is_symlink()) || !mod_file {
            continue;
        }
        if allowed.iter().any(|candidate| candidate.eq_ignore_ascii_case(&name)) {
            continue;
        }
        let relative = path.strip_prefix(mods).unwrap_or(Path::new(&name)).to_path_buf();
        let dest = unique(game.join("mods-blocked").join(&relative));
        if let Some(parent) = dest.parent() {
            std::fs::create_dir_all(parent)?;
        }
        std::fs::rename(&path, &dest)?;
        out.push(Blocked {
            from: slashed(&relative),
            to: slashed(dest.strip_prefix(game).unwrap_or(&dest)),
        });
    }
    Ok(())
}

/// A free path next to `path`, so two mods with the same name don't overwrite each other.
fn unique(path: PathBuf) -> PathBuf {
    if !path.exists() {
        return path;
    }
    let stem = path.file_stem().map(|s| s.to_string_lossy().into_owned()).unwrap_or_default();
    let ext = path.extension().map(|e| format!(".{}", e.to_string_lossy())).unwrap_or_default();
    let parent = path.parent().map(Path::to_path_buf).unwrap_or_default();
    for n in 2..1000 {
        let candidate = parent.join(format!("{stem} ({n}){ext}"));
        if !candidate.exists() {
            return candidate;
        }
    }
    path
}

fn slashed(path: &Path) -> String {
    path.to_string_lossy().replace('\\', "/")
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::instances::store::Loader;

    fn instance(id: &str, loader: Loader, version: &str, client_mod: bool) -> Instance {
        Instance {
            id: id.into(),
            name: "Test".into(),
            version: version.into(),
            loader,
            loader_version: None,
            memory_mb: None,
            java_path: None,
            jvm_args: String::new(),
            client_mod,
            created_at: 0,
            last_played: None,
        }
    }

    fn root(tag: &str) -> PathBuf {
        let root = std::env::temp_dir().join(format!("novo-mods-{tag}-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&root);
        std::fs::create_dir_all(&root).unwrap();
        root
    }

    fn touch(dir: &Path, name: &str) {
        std::fs::create_dir_all(dir).unwrap();
        std::fs::write(dir.join(name), b"jar").unwrap();
    }

    #[test]
    fn moves_external_mods_out_of_the_way() {
        let root = root("legacy");
        let inst = instance("one", Loader::Forge, "1.8.9", true);
        let game = store::game_dir(&root, "one");
        let mods = game.join("mods");
        touch(&mods, "novomc-client.jar");
        touch(&mods, "MemoryFix-0.3.jar");
        touch(&mods, "Sodium.jar");
        touch(&mods, "notes.txt");
        touch(&mods.join("1.8.9"), "OldMod.jar");

        let mut moved: Vec<String> = enforce(&root, &inst).unwrap().into_iter().map(|b| b.from).collect();
        moved.sort();
        assert_eq!(moved, vec!["1.8.9/OldMod.jar", "Sodium.jar"]);

        assert!(mods.join("novomc-client.jar").is_file(), "the client stays");
        assert!(mods.join("MemoryFix-0.3.jar").is_file(), "its companion stays");
        assert!(mods.join("notes.txt").is_file(), "non-mod files are left alone");
        assert!(!mods.join("Sodium.jar").exists());
        assert!(game.join("mods-blocked").join("Sodium.jar").is_file());
        assert!(game.join("mods-blocked").join("1.8.9").join("OldMod.jar").is_file());

        // A second external mod with the same name doesn't overwrite the first.
        touch(&mods, "Sodium.jar");
        let again = enforce(&root, &inst).unwrap();
        assert_eq!(again[0].to, "mods-blocked/Sodium (2).jar");
        assert!(game.join("mods-blocked").join("Sodium (2).jar").is_file());

        std::fs::remove_dir_all(&root).unwrap();
    }

    #[test]
    fn leaves_instances_without_the_client_alone() {
        let root = root("off");
        let inst = instance("one", Loader::Forge, "1.8.9", false);
        let mods = store::game_dir(&root, "one").join("mods");
        touch(&mods, "SomeMod.jar");

        assert!(enforce(&root, &inst).unwrap().is_empty());
        assert!(mods.join("SomeMod.jar").is_file());
        std::fs::remove_dir_all(&root).unwrap();
    }

    #[test]
    fn allows_only_the_matching_companion_set() {
        let root = root("modern");
        let inst = instance("one", Loader::Fabric, "26.3", true);
        let mods = store::game_dir(&root, "one").join("mods");
        touch(&mods, "novomc-client.jar");
        touch(&mods, "fabric-api-0.165.0+26.3.jar");
        touch(&mods, "MemoryFix-0.3.jar");

        let moved: Vec<String> = enforce(&root, &inst).unwrap().into_iter().map(|b| b.from).collect();
        assert_eq!(moved, vec!["MemoryFix-0.3.jar"]);
        assert!(mods.join("fabric-api-0.165.0+26.3.jar").is_file());
        std::fs::remove_dir_all(&root).unwrap();
    }
}
