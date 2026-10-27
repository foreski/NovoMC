//! Players' real skin heads for the launcher UI.
//!
//! The skin is looked up at runtime on Mojang's session server, the face and
//! hat layers are cut out and scaled to 64x64, and the result is cached in
//! `cache/skins/` for an hour. Nothing from Mojang ships with NovoMC.

use std::path::PathBuf;
use std::time::{Duration, SystemTime};

use base64::Engine;
use image::{imageops, ImageFormat, RgbaImage};
use serde_json::Value;

use crate::download::http;
use crate::error::{AppError, AppResult};
use crate::paths;

const SESSION: &str = "https://sessionserver.mojang.com/session/minecraft/profile/";
const MAX_AGE: Duration = Duration::from_secs(3600);

fn cache_file(uuid: &str) -> AppResult<PathBuf> {
    Ok(paths::data_dir()?.join("cache").join("skins").join(format!("{uuid}.png")))
}

/// The face (with hat layer) of a skin, as a 64x64 PNG.
pub fn head_png(skin: &[u8]) -> AppResult<Vec<u8>> {
    let skin = image::load_from_memory_with_format(skin, ImageFormat::Png)
        .map_err(|e| AppError::Other(format!("Bad skin image: {e}")))?
        .to_rgba8();
    if skin.width() < 64 || skin.height() < 32 {
        return Err(AppError::Other("Skin image too small".into()));
    }
    // Skins can be 64x64 or HD (128, 256...): scale coordinates to the real size.
    let unit = skin.width() / 64;
    let face = imageops::crop_imm(&skin, 8 * unit, 8 * unit, 8 * unit, 8 * unit).to_image();
    let hat = imageops::crop_imm(&skin, 40 * unit, 8 * unit, 8 * unit, 8 * unit).to_image();
    let mut head: RgbaImage = face;
    imageops::overlay(&mut head, &hat, 0, 0);
    let head = imageops::resize(&head, 64, 64, imageops::FilterType::Nearest);
    let mut out = std::io::Cursor::new(Vec::new());
    head.write_to(&mut out, ImageFormat::Png).map_err(|e| AppError::Other(e.to_string()))?;
    Ok(out.into_inner())
}

async fn skin_url(uuid: &str) -> AppResult<Option<String>> {
    let id = uuid.replace('-', "");
    let bytes = http::get_bytes(&format!("{SESSION}{id}")).await?;
    let profile: Value = serde_json::from_slice(&bytes)?;
    let Some(encoded) = profile["properties"]
        .as_array()
        .and_then(|p| p.iter().find(|x| x["name"] == "textures"))
        .and_then(|x| x["value"].as_str())
    else {
        return Ok(None);
    };
    let decoded = base64::engine::general_purpose::STANDARD
        .decode(encoded)
        .map_err(|e| AppError::Other(format!("Bad textures property: {e}")))?;
    let textures: Value = serde_json::from_slice(&decoded)?;
    Ok(textures["textures"]["SKIN"]["url"].as_str().map(|u| u.replace("http://", "https://")))
}

/// The player's skin head as a `data:image/png` URL, or null if they use a default skin.
#[tauri::command]
pub async fn get_skin_head(uuid: String) -> AppResult<Option<String>> {
    if uuid.len() < 32 || !uuid.chars().all(|c| c.is_ascii_hexdigit() || c == '-') {
        return Err(AppError::Other("Not a player UUID".into()));
    }
    let file = cache_file(&uuid)?;
    let fresh = std::fs::metadata(&file)
        .and_then(|m| m.modified())
        .map(|t| SystemTime::now().duration_since(t).unwrap_or_default() < MAX_AGE)
        .unwrap_or(false);
    let png = if fresh {
        std::fs::read(&file)?
    } else {
        let Some(url) = skin_url(&uuid).await? else {
            return Ok(None);
        };
        let png = head_png(&http::get_bytes(&url).await?)?;
        std::fs::create_dir_all(file.parent().unwrap())?;
        std::fs::write(&file, &png)?;
        png
    };
    Ok(Some(format!("data:image/png;base64,{}", base64::engine::general_purpose::STANDARD.encode(png))))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn cuts_face_and_hat() {
        // A 64x64 skin: red face, a blue hat pixel at the hat's top-left.
        let mut skin = RgbaImage::new(64, 64);
        for y in 8..16 {
            for x in 8..16 {
                skin.put_pixel(x, y, image::Rgba([255, 0, 0, 255]));
            }
        }
        skin.put_pixel(40, 8, image::Rgba([0, 0, 255, 255]));
        let mut png = std::io::Cursor::new(Vec::new());
        skin.write_to(&mut png, ImageFormat::Png).unwrap();
        let head = image::load_from_memory(&head_png(&png.into_inner()).unwrap()).unwrap().to_rgba8();
        assert_eq!(head.dimensions(), (64, 64));
        assert_eq!(head.get_pixel(0, 0).0, [0, 0, 255, 255], "hat drawn over the face");
        assert_eq!(head.get_pixel(63, 63).0, [255, 0, 0, 255]);
    }
}

#[cfg(test)]
mod network {
    /// `NOVOMC_DATA_DIR=<dir> cargo test real_skin -- --ignored`
    #[tokio::test]
    #[ignore = "asks Mojang's session server"]
    async fn real_skin() {
        let url = super::get_skin_head("069a79f4-44e9-4726-a5be-fca90e38aaf5".into()).await.unwrap().unwrap();
        assert!(url.starts_with("data:image/png;base64,") && url.len() > 200);
    }
}
