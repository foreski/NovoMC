//! Instances / profiles.
//!
//! Each instance has its own game folder (mods, saves, resourcepacks, config)
//! and an `instance.json` with its version, loader, RAM, Java and JVM args.
//! See `store` for the layout.

pub mod client;
pub mod companions;
pub mod splash;
pub mod store;

use serde::Deserialize;

use crate::error::{AppError, AppResult};
use crate::paths;
use store::{Instance, Loader};

/// Runs blocking file work (copies can be large) off the async runtime.
async fn blocking<T: Send + 'static>(f: impl FnOnce() -> AppResult<T> + Send + 'static) -> AppResult<T> {
    tokio::task::spawn_blocking(f).await.map_err(|e| AppError::Other(e.to_string()))?
}

#[tauri::command]
pub async fn list_instances() -> AppResult<Vec<Instance>> {
    let root = paths::data_dir()?;
    blocking(move || store::list(&root)).await
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct NewInstance {
    name: String,
    version: String,
    #[serde(default)]
    loader: Loader,
    loader_version: Option<String>,
}

#[tauri::command]
pub async fn create_instance(instance: NewInstance) -> AppResult<Instance> {
    let root = paths::data_dir()?;
    blocking(move || store::create(&root, &instance.name, &instance.version, instance.loader, instance.loader_version)).await
}

/// Saves edits (name, version, memory, Java, JVM args).
#[tauri::command]
pub async fn update_instance(instance: Instance) -> AppResult<Instance> {
    let root = paths::data_dir()?;
    blocking(move || store::update(&root, instance)).await
}

#[tauri::command]
pub async fn duplicate_instance(id: String) -> AppResult<Instance> {
    let root = paths::data_dir()?;
    blocking(move || store::duplicate(&root, &id)).await
}

/// Moves the instance to the Recycle Bin.
#[tauri::command]
pub async fn delete_instance(id: String) -> AppResult<()> {
    let root = paths::data_dir()?;
    blocking(move || store::delete(&root, &id)).await
}

/// Opens the instance's game folder in Explorer / Finder / the file manager.
#[tauri::command]
pub fn open_instance_folder(id: String) -> AppResult<()> {
    let root = paths::data_dir()?;
    paths::open_folder(&store::game_dir(&root, &store::load(&root, &id)?.id))
}
