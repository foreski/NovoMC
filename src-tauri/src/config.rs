//! `novomc.config.json`: build-time identity settings that are kept out of
//! the source, like the Microsoft sign-in client ID.
//!
//! Looked up in order: the data folder (`%APPDATA%\NovoMC`, for overrides),
//! the copy bundled with the app, and (debug builds) the repository root.

use std::path::PathBuf;
use std::sync::OnceLock;

use serde::Deserialize;
use tauri::{AppHandle, Manager};

use crate::error::{AppError, AppResult};
use crate::paths;

const FILE: &str = "novomc.config.json";

#[derive(Debug, Clone, Default, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AppConfig {
    /// OAuth client ID for Microsoft sign-in (login.live.com flow).
    pub msa_client_id: Option<String>,
    /// Your own Azure app's client ID (Microsoft identity platform flow).
    pub azure_client_id: Option<String>,
    /// The bug-report relay (relay/ in the repo), e.g. https://novomc-bug-relay.example.workers.dev/report.
    pub bug_report_url: Option<String>,
    /// Discord application ID for Rich Presence (an app with a "novomc" art asset).
    pub discord_app_id: Option<String>,
}

static CONFIG: OnceLock<AppConfig> = OnceLock::new();

/// Reads the config once; later calls return the same values.
pub fn get(app: &AppHandle) -> &'static AppConfig {
    CONFIG.get_or_init(|| {
        for path in candidates(app) {
            if let Ok(bytes) = std::fs::read(&path) {
                match serde_json::from_slice(&bytes) {
                    Ok(config) => return config,
                    Err(e) => eprintln!("[NovoMC] Ignoring {}: {e}", path.display()),
                }
            }
        }
        AppConfig::default()
    })
}

fn candidates(app: &AppHandle) -> Vec<PathBuf> {
    let mut out = Vec::new();
    if let Ok(dir) = paths::data_dir() {
        out.push(dir.join(FILE));
    }
    if let Ok(dir) = app.path().resource_dir() {
        out.push(dir.join(FILE));
    }
    if cfg!(debug_assertions) {
        out.push(PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("..").join(FILE));
    }
    out
}

/// The relay's base URL (the bug-report URL without /report), if set up.
pub fn relay_base(app: &AppHandle) -> Option<String> {
    let url = get(app).bug_report_url.as_deref()?.trim();
    let base = url.strip_suffix("/report").unwrap_or(url).trim_end_matches('/');
    (!base.is_empty()).then(|| base.to_string())
}

/// The Microsoft sign-in client ID, or an error explaining where to set it.
pub fn msa_client_id(app: &AppHandle) -> AppResult<String> {
    get(app)
        .msa_client_id
        .clone()
        .filter(|id| !id.trim().is_empty())
        .ok_or_else(|| AppError::Other(format!("Microsoft sign-in isn't configured: set msaClientId in {FILE}")))
}
