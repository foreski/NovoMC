//! NovoMC's startup loading screen for Forge instances.
//!
//! Forge draws its own splash while mods load, before any mod (ours included)
//! runs, so it's themed from outside: `config/splash.properties` sets the
//! colors and textures, and a tiny folder resource pack (`novomc-splash/`)
//! supplies the NovoMC logo and a ring that Forge spins as a loading circle.

use std::path::Path;

use crate::error::AppResult;

const LOGO: &[u8] = include_bytes!("../../resources/splash/logo.png");
const SPINNER: &[u8] = include_bytes!("../../resources/splash/spinner.png");
const PACK: &str = "novomc-splash";

const PROPERTIES: &str = "\
# Written by the NovoMC launcher before each launch (NovoMC loading screen).
enabled=true
rotate=true
showMemory=false
logoOffset=0
background=0x0C1019
font=0xE8ECF4
barBorder=0x2A3142
bar=0x5B8CFF
barBackground=0x121722
memoryGood=0x4ADE80
memoryWarn=0xFACC15
memoryLow=0xF87171
resourcePackPath=novomc-splash
fontTexture=textures/font/ascii.png
logoTexture=novomc:textures/splash/logo.png
forgeTexture=novomc:textures/splash/spinner.png
";

/// Writes the NovoMC splash theme into an instance's game folder.
pub fn write(game_dir: &Path) -> AppResult<()> {
    let config = game_dir.join("config");
    std::fs::create_dir_all(&config)?;
    std::fs::write(config.join("splash.properties"), PROPERTIES)?;
    let textures = game_dir.join(PACK).join("assets").join("novomc").join("textures").join("splash");
    std::fs::create_dir_all(&textures)?;
    std::fs::write(
        game_dir.join(PACK).join("pack.mcmeta"),
        r#"{"pack":{"pack_format":1,"description":"NovoMC loading screen"}}"#,
    )?;
    write_if_changed(&textures.join("logo.png"), LOGO)?;
    write_if_changed(&textures.join("spinner.png"), SPINNER)?;
    Ok(())
}

fn write_if_changed(path: &Path, bytes: &[u8]) -> AppResult<()> {
    if std::fs::read(path).map(|old| old != bytes).unwrap_or(true) {
        std::fs::write(path, bytes)?;
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn writes_theme_and_textures() {
        let dir = std::env::temp_dir().join(format!("novomc-splash-test-{}", std::process::id()));
        write(&dir).unwrap();
        let props = std::fs::read_to_string(dir.join("config/splash.properties")).unwrap();
        assert!(props.contains("forgeTexture=novomc:textures/splash/spinner.png"));
        assert!(dir.join("novomc-splash/assets/novomc/textures/splash/spinner.png").is_file());
        assert!(dir.join("novomc-splash/pack.mcmeta").is_file());
        std::fs::remove_dir_all(&dir).unwrap();
    }
}
