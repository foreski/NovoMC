//! NovoMC backend. All game logic (auth, downloads, verification, launching)
//! lives here; the React UI only calls the commands registered below and
//! listens for progress events.

pub mod auth;
pub mod config;
pub mod download;
pub mod error;
pub mod instances;
pub mod java;
pub mod launch;
pub mod news;
pub mod paths;
pub mod report;
pub mod settings;
pub mod updater;

use serde::Serialize;

use crate::error::AppResult;

/// Basic info shown in the UI, also used as an IPC smoke test.
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AppInfo {
    name: &'static str,
    version: &'static str,
    data_dir: String,
    /// Physical memory, for the RAM slider.
    total_memory_mb: u64,
}

#[tauri::command]
fn app_info() -> AppResult<AppInfo> {
    let mut sys = sysinfo::System::new();
    sys.refresh_memory();
    Ok(AppInfo {
        name: "NovoMC",
        version: env!("CARGO_PKG_VERSION"),
        data_dir: paths::data_dir()?.display().to_string(),
        total_memory_mb: sys.total_memory() / (1024 * 1024),
    })
}

#[tauri::command]
fn open_data_folder() -> AppResult<()> {
    paths::open_folder(&paths::data_dir()?)
}

pub fn run() {
    tauri::Builder::default()
        .manage(download::Installs::default())
        .manage(launch::Games::default())
        .manage(updater::PendingUpdate::default())
        .plugin(tauri_plugin_updater::Builder::new().build())
        .setup(|_app| {
            paths::ensure_layout()?;
            let last_version = settings::load().ok().and_then(|s| s.last_version);
            instances::store::migrate_shared_default(&paths::data_dir()?, last_version.as_deref())?;
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            app_info,
            open_data_folder,
            news::get_news,
            news::open_url,
            auth::list_accounts,
            auth::add_offline_account,
            auth::remove_account,
            auth::set_active_account,
            auth::microsoft_login,
            report::report_bug,
            updater::check_update,
            updater::install_update,
            download::list_versions,
            download::install_version,
            download::cancel_install,
            download::forge::list_forge_versions,
            instances::list_instances,
            instances::create_instance,
            instances::update_instance,
            instances::duplicate_instance,
            instances::delete_instance,
            instances::open_instance_folder,
            instances::client::get_client_config,
            instances::client::save_client_config,
            java::list_java,
            launch::launch_game,
            launch::stop_game,
            settings::get_settings,
            settings::update_settings,
        ])
        .run(tauri::generate_context!())
        .expect("error while running NovoMC");
}
