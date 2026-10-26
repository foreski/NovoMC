//! On-disk layout of the NovoMC data folder.
//!
//! Windows: `%APPDATA%\NovoMC`, macOS: `~/Library/Application Support/NovoMC`,
//! Linux: `~/.local/share/NovoMC`.
//!
//! ```text
//! NovoMC/
//!   versions/   version JSONs and client jars, shared by all instances
//!   libraries/  Maven-style library jars
//!   assets/     indexes/ + objects/ (game assets from Mojang)
//!   runtimes/   Java runtimes downloaded by NovoMC
//!   instances/  one folder per instance (mods, saves, config, ...)
//!   logs/       launcher logs
//! ```

use std::path::PathBuf;

use crate::error::{AppError, AppResult};

const SUBDIRS: &[&str] = &["versions", "libraries", "assets", "runtimes", "instances", "logs"];

/// Root data folder (`%APPDATA%\NovoMC` on Windows). The `NOVOMC_DATA_DIR`
/// environment variable overrides it (for tests and development).
pub fn data_dir() -> AppResult<PathBuf> {
    if let Some(dir) = std::env::var_os("NOVOMC_DATA_DIR") {
        return Ok(PathBuf::from(dir));
    }
    dirs::data_dir()
        .map(|d| d.join("NovoMC"))
        .ok_or_else(|| AppError::Other("could not locate the user data folder".into()))
}

/// Opens a folder in Explorer / Finder / the file manager, creating it if needed.
pub fn open_folder(dir: &std::path::Path) -> AppResult<()> {
    std::fs::create_dir_all(dir)?;
    let program = if cfg!(windows) {
        "explorer"
    } else if cfg!(target_os = "macos") {
        "open"
    } else {
        "xdg-open"
    };
    std::process::Command::new(program).arg(dir).spawn()?;
    Ok(())
}

/// Creates the data folder and its standard subfolders if they are missing.
pub fn ensure_layout() -> AppResult<()> {
    let root = data_dir()?;
    for sub in SUBDIRS {
        std::fs::create_dir_all(root.join(sub))?;
    }
    Ok(())
}
