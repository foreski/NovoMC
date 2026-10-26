//! The NovoMC Client mod in Forge 1.8.9 instances.
//!
//! - The mod jar ships inside the launcher (`resources/novomc-client.jar`) and
//!   is copied into the instance's `mods/` before each launch.
//! - Its settings live in `config/novomc/modules.json`, which the Mod Library
//!   edits and the mod reloads while the game runs.

use std::path::{Path, PathBuf};

use serde_json::Value;
use tauri::path::BaseDirectory;
use tauri::{AppHandle, Manager};

use super::store::{self, Instance, Loader};
use crate::download::engine::hash_file;
use crate::error::{AppError, AppResult};
use crate::paths;

pub const JAR_NAME: &str = "novomc-client.jar";
const BUNDLED: &str = "resources/novomc-client.jar";

/// The mod targets Minecraft 1.8.9 on Forge.
pub fn supported(inst: &Instance) -> bool {
    inst.loader == Loader::Forge && inst.version == "1.8.9"
}

fn config_path(root: &Path, id: &str) -> PathBuf {
    store::game_dir(root, id).join("config").join("novomc").join("modules.json")
}

/// Puts the bundled jar into `mods/` (or removes it when the instance has the
/// client turned off or can't run it).
pub fn sync(app: &AppHandle, root: &Path, inst: &Instance) -> AppResult<()> {
    let dest = store::game_dir(root, &inst.id).join("mods").join(JAR_NAME);
    if !(supported(inst) && inst.client_mod) {
        if dest.exists() {
            std::fs::remove_file(&dest)?;
        }
        return Ok(());
    }
    let src = app
        .path()
        .resolve(BUNDLED, BaseDirectory::Resource)
        .map_err(|e| AppError::Other(e.to_string()))?;
    if !src.is_file() {
        return Err(AppError::Other("This NovoMC build is missing the NovoMC Client mod (run `npm run mod`)".into()));
    }
    if dest.is_file() && hash_file(&src)? == hash_file(&dest)? {
        return Ok(());
    }
    std::fs::create_dir_all(dest.parent().unwrap_or(root))?;
    std::fs::copy(&src, &dest)?;
    Ok(())
}

/// The instance's module settings, or `{}` if it has none yet.
#[tauri::command]
pub fn get_client_config(id: String) -> AppResult<Value> {
    let root = paths::data_dir()?;
    store::load(&root, &id)?;
    match std::fs::read(config_path(&root, &id)) {
        Ok(bytes) => Ok(serde_json::from_slice(&bytes).unwrap_or_else(|_| Value::Object(Default::default()))),
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(Value::Object(Default::default())),
        Err(e) => Err(e.into()),
    }
}

/// Saves module settings; a running game picks them up within a second.
#[tauri::command]
pub fn save_client_config(id: String, config: Value) -> AppResult<()> {
    if !config.is_object() {
        return Err(AppError::Other("Module config must be a JSON object".into()));
    }
    let root = paths::data_dir()?;
    store::load(&root, &id)?;
    let path = config_path(&root, &id);
    std::fs::create_dir_all(path.parent().unwrap_or(&root))?;
    let tmp = path.with_extension("json.tmp");
    std::fs::write(&tmp, serde_json::to_vec_pretty(&config)?)?;
    std::fs::rename(tmp, path)?;
    Ok(())
}
