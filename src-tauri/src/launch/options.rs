//! Edits to the game's own `options.txt` before launch.

use std::path::Path;

use crate::error::AppResult;

/// Minecraft 1.11+ expects lowercase codes (`de_de`); older versions `de_DE`.
fn language_code(code: &str, version: &str) -> String {
    let old = match version.strip_prefix("1.") {
        Some(rest) => rest.split('.').next().and_then(|m| m.parse::<u32>().ok()).is_some_and(|minor| minor < 11),
        // Alpha, beta, classic, infdev and rd builds are old; snapshots like 25w41a are new.
        None => version.starts_with(['a', 'b', 'c', 'i', 'r']),
    };
    if !old {
        code.to_lowercase()
    } else {
        match code.split_once('_') {
            Some((lang, region)) => format!("{}_{}", lang.to_lowercase(), region.to_uppercase()),
            None => code.to_string(),
        }
    }
}

/// Sets `lang:` in `<game_dir>/options.txt`, creating the file if needed.
pub fn set_language(game_dir: &Path, code: &str, version: &str) -> AppResult<()> {
    let path = game_dir.join("options.txt");
    let line = format!("lang:{}", language_code(code, version));
    let current = std::fs::read_to_string(&path).unwrap_or_default();
    let mut found = false;
    let mut lines: Vec<String> = current
        .lines()
        .map(|l| {
            if l.starts_with("lang:") {
                found = true;
                line.clone()
            } else {
                l.to_string()
            }
        })
        .collect();
    if !found {
        lines.push(line);
    }
    std::fs::create_dir_all(game_dir)?;
    std::fs::write(path, lines.join("\n") + "\n")?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn codes_follow_the_version() {
        assert_eq!(language_code("de_de", "1.8.9"), "de_DE");
        assert_eq!(language_code("de_DE", "1.12.2"), "de_de");
        assert_eq!(language_code("de_DE", "1.21.4"), "de_de");
        assert_eq!(language_code("de_DE", "25w41a"), "de_de");
    }

    #[test]
    fn replaces_or_adds_the_lang_line() {
        let dir = std::env::temp_dir().join(format!("novomc-options-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        std::fs::write(dir.join("options.txt"), "fov:0.0\nlang:en_US\nrenderDistance:8\n").unwrap();
        set_language(&dir, "fr_FR", "1.8.9").unwrap();
        assert_eq!(std::fs::read_to_string(dir.join("options.txt")).unwrap(), "fov:0.0\nlang:fr_FR\nrenderDistance:8\n");
        std::fs::remove_file(dir.join("options.txt")).unwrap();
        set_language(&dir, "ja_JP", "1.21.4").unwrap();
        assert_eq!(std::fs::read_to_string(dir.join("options.txt")).unwrap(), "lang:ja_jp\n");
        std::fs::remove_dir_all(dir).unwrap();
    }
}
