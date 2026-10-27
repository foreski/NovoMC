//! Launcher settings, stored in `settings.json` in the data folder.

use std::path::PathBuf;

use serde::{Deserialize, Serialize};

use crate::error::{AppError, AppResult};
use crate::paths;

/// What NovoMC does with its window once the game has started.
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum AfterLaunch {
    #[default]
    Keep,
    Minimize,
    Close,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct Resolution {
    pub width: u32,
    pub height: u32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default, rename_all = "camelCase")]
pub struct Settings {
    /// Maximum Java heap (`-Xmx`) in MB.
    pub memory_mb: u32,
    /// Launch with NovoMC's tuned GC flags (see `launch::args::PERFORMANCE_FLAGS`).
    pub optimized_flags: bool,
    /// Custom Java binary; `None` picks or downloads the right Java per version.
    pub java_path: Option<String>,
    pub after_launch: AfterLaunch,
    /// Game window size; `None` leaves it to Minecraft (854×480).
    pub resolution: Option<Resolution>,
    pub fullscreen: bool,
    /// Launcher accent color `#RRGGBB`; `None` is the Novo blue.
    pub accent: Option<String>,
    /// Send crash reports to NovoMC automatically (redacted; see `report`).
    pub auto_report_crashes: bool,
    /// Show what you're playing in Discord.
    pub discord_presence: bool,
    /// Launcher UI language; `None` follows the system language.
    pub ui_language: Option<String>,
    /// Minecraft's language (`de_DE`, ...) written to each instance's
    /// options.txt at launch; `None` leaves the game's own choice.
    pub game_language: Option<String>,
    /// Version launched last (before instances existed). Only used to migrate
    /// the old shared game folder into a "Default" instance.
    pub last_version: Option<String>,
    /// Instance launched last, preselected on Home.
    pub last_instance: Option<String>,
}

impl Default for Settings {
    fn default() -> Self {
        Self {
            memory_mb: 4096,
            optimized_flags: true,
            java_path: None,
            after_launch: AfterLaunch::Keep,
            resolution: None,
            fullscreen: false,
            accent: None,
            auto_report_crashes: true,
            discord_presence: true,
            ui_language: None,
            game_language: None,
            last_version: None,
            last_instance: None,
        }
    }
}

fn file() -> AppResult<PathBuf> {
    Ok(paths::data_dir()?.join("settings.json"))
}

pub fn load() -> AppResult<Settings> {
    match std::fs::read(file()?) {
        Ok(bytes) => Ok(serde_json::from_slice(&bytes)?),
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(Settings::default()),
        Err(e) => Err(e.into()),
    }
}

pub fn save(settings: &Settings) -> AppResult<()> {
    let path = file()?;
    let tmp = path.with_extension("json.tmp");
    std::fs::write(&tmp, serde_json::to_vec_pretty(settings)?)?;
    std::fs::rename(tmp, path)?;
    Ok(())
}

#[tauri::command]
pub fn get_settings() -> AppResult<Settings> {
    load()
}

#[tauri::command]
pub fn update_settings(mut settings: Settings) -> AppResult<Settings> {
    if !(512..=65536).contains(&settings.memory_mb) {
        return Err(AppError::Other("Memory must be between 512 MB and 64 GB".into()));
    }
    if let Some(r) = settings.resolution {
        if !(320..=7680).contains(&r.width) || !(240..=4320).contains(&r.height) {
            return Err(AppError::Other("Resolution must be between 320×240 and 7680×4320".into()));
        }
    }
    if let Some(a) = &settings.accent {
        let hex = a.strip_prefix('#').unwrap_or_default();
        if hex.len() != 6 || !hex.chars().all(|c| c.is_ascii_hexdigit()) {
            return Err(AppError::Other("Accent must be a #RRGGBB color".into()));
        }
    }
    if let Some(l) = &settings.ui_language {
        if !["en", "de", "es", "fr", "pt-BR"].contains(&l.as_str()) {
            return Err(AppError::Other(format!("NovoMC isn't translated to {l} yet")));
        }
    }
    if let Some(l) = &settings.game_language {
        let ok = l.len() <= 8 && l.contains('_') && l.chars().all(|c| c.is_ascii_alphanumeric() || c == '_');
        if !ok {
            return Err(AppError::Other(format!("Unknown language code {l}")));
        }
    }
    settings.java_path = settings.java_path.map(|p| p.trim().to_string()).filter(|p| !p.is_empty());
    save(&settings)?;
    Ok(settings)
}
