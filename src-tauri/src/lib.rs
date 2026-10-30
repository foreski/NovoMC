//! NovoMC backend. All game logic (auth, downloads, verification, launching)
//! lives here; the React UI only calls the commands registered below and
//! listens for progress events.

pub mod auth;
pub mod config;
pub mod discord;
pub mod download;
pub mod error;
pub mod instances;
pub mod java;
pub mod launch;
pub mod local_server;
pub mod news;
pub mod novo;
pub mod paths;
pub mod report;
pub mod servers;
pub mod settings;
pub mod survival;
pub mod updater;

use serde::Serialize;
use tauri::AppHandle;

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
    /// The relay this build talks to, and the config file it came from, so a
    /// report can show which endpoint and which file chose it.
    api_base: Option<String>,
    config_file: Option<String>,
}

#[tauri::command]
fn app_info(app: AppHandle) -> AppResult<AppInfo> {
    let mut sys = sysinfo::System::new();
    sys.refresh_memory();
    Ok(AppInfo {
        name: "NovoMC",
        version: env!("CARGO_PKG_VERSION").split('+').next().unwrap_or(env!("CARGO_PKG_VERSION")),
        data_dir: paths::data_dir()?.display().to_string(),
        total_memory_mb: sys.total_memory() / (1024 * 1024),
        api_base: config::relay_base(&app),
        config_file: config::source(&app).map(|path| path.display().to_string()),
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
        .manage(local_server::Servers::default())
        .manage(updater::PendingUpdate::default())
        .plugin(
            tauri_plugin_updater::Builder::new()
                .default_version_comparator(|current, remote| {
                    let candidate = remote.version;
                    if candidate > current {
                        return true;
                    }
                    candidate.major == current.major
                        && candidate.minor == current.minor
                        && candidate.patch == current.patch
                        && candidate.pre == current.pre
                        && candidate.build.as_str().parse::<u64>().unwrap_or(0)
                            > current.build.as_str().parse::<u64>().unwrap_or(0)
                })
                .build(),
        )
        .setup(|app| {
            discord::start(app.handle());
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
            auth::remove_account,
            auth::set_active_account,
            auth::microsoft_login,
            auth::skins::get_skin_head,
            auth::skins::lookup_skin,
            auth::skins::get_skin_png,
            auth::skins::apply_skin,
            auth::skins::reset_skin,
            novo::novo_account,
            novo::novo_login,
            novo::novo_logout,
            novo::novo_set_display_name,
            novo::novo_set_cape,
            novo::novo_set_banner,
            novo::novo_set_wing,
            novo::novo_set_hat,
            novo::novo_shop,
            novo::novo_purchase_item,
            novo::novo_redeem_gift,
            novo::novo_checkout,
            novo::novo_link,
            novo::novo_unlink,
            novo::sync::novo_sync_settings,
            report::report_bug,
            servers::list_servers,
            servers::save_servers,
            servers::ping_server,
            updater::check_update,
            updater::install_update,
            download::list_versions,
            download::install_version,
            download::cancel_install,
            download::forge::list_forge_versions,
            download::fabric::fabric_loader,
            instances::list_instances,
            instances::create_instance,
            instances::update_instance,
            instances::duplicate_instance,
            instances::delete_instance,
            instances::open_instance_folder,
            instances::client::get_client_config,
            instances::client::save_client_config,
            survival::survival_editions,
            survival::survival_catalog,
            survival::survival_status,
            survival::survival_instances,
            survival::survival_create,
            survival::survival_install,
            survival::survival_remove,
            survival::survival_bundle,
            survival::survival_search,
            local_server::list_local_servers,
            local_server::create_local_server,
            local_server::delete_local_server,
            local_server::start_local_server,
            local_server::stop_local_server,
            local_server::match_server_mods,
            local_server::apply_server_mods,
            local_server::open_local_server_folder,
            local_server::read_server_properties,
            local_server::save_server_properties,
            java::list_java,
            launch::launch_game,
            launch::stop_game,
            settings::get_settings,
            settings::update_settings,
        ])
        .run(tauri::generate_context!())
        .expect("error while running NovoMC");
}
