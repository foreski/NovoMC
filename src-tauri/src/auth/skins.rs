//! Players' real skins for the launcher UI, and uploading a new one.
//!
//! The skin is looked up at runtime on Mojang's session server, the face and
//! hat layers are cut out and scaled to 64x64, and the result is cached in
//! `cache/skins/` for an hour. Nothing from Mojang ships with NovoMC: default
//! skins are never bundled, only fetched for a player who wears one.
//!
//! Applying a skin uses the signed-in account's own Minecraft access token,
//! the same one a launch uses, so the launcher never asks for a second login.

use std::path::PathBuf;
use std::time::{Duration, SystemTime};

use base64::Engine;
use image::{imageops, ImageFormat, RgbaImage};
use serde::Serialize;
use serde_json::Value;

use crate::download::http;
use crate::error::{AppError, AppResult};
use crate::paths;

const SESSION: &str = "https://sessionserver.mojang.com/session/minecraft/profile/";
const USERNAME: &str = "https://api.mojang.com/users/profiles/minecraft/";
const SKIN_UPLOAD: &str = "https://api.minecraftservices.com/minecraft/profile/skins";
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

/// A player's skin, as the launcher's skin changer needs it: the whole 64x64
/// sheet, the model the skin declares, and who it belongs to.
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SkinProfile {
    pub uuid: String,
    pub name: String,
    /// `classic` or `slim`, as the skin's metadata declares it.
    pub model: String,
    /// The skin PNG as a `data:image/png;base64,` URL, when the player has one.
    pub png: Option<String>,
}

/// The textures property decoded from a session-server profile.
fn textures(profile: &Value) -> Option<Value> {
    let encoded = profile["properties"]
        .as_array()
        .and_then(|p| p.iter().find(|x| x["name"] == "textures"))
        .and_then(|x| x["value"].as_str())?;
    let decoded = base64::engine::general_purpose::STANDARD.decode(encoded).ok()?;
    serde_json::from_slice(&decoded).ok()
}

fn data_url(png: Vec<u8>) -> String {
    format!("data:image/png;base64,{}", base64::engine::general_purpose::STANDARD.encode(png))
}

/// Looks a player up by name and returns their skin, or null when no such
/// player exists. Used by the skin changer's search box.
#[tauri::command]
pub async fn lookup_skin(name: String) -> AppResult<Option<SkinProfile>> {
    let name = name.trim();
    if name.is_empty() || name.len() > 16 || !name.chars().all(|c| c.is_ascii_alphanumeric() || c == '_') {
        return Err(AppError::Other("Not a Minecraft username".into()));
    }
    let Some(bytes) = http::get_optional(&format!("{USERNAME}{name}")).await? else {
        return Ok(None);
    };
    let account: Value = serde_json::from_slice(&bytes)?;
    let Some(uuid) = account["id"].as_str() else {
        return Ok(None);
    };
    let exact = account["name"].as_str().unwrap_or(name).to_string();
    let profile: Value = serde_json::from_slice(&http::get_bytes(&format!("{SESSION}{uuid}")).await?)?;
    let textures = textures(&profile);
    let model = textures
        .as_ref()
        .and_then(|t| t["textures"]["SKIN"]["metadata"]["model"].as_str())
        .unwrap_or("classic")
        .to_string();
    let png = match textures.as_ref().and_then(|t| t["textures"]["SKIN"]["url"].as_str()) {
        Some(url) => Some(data_url(http::get_bytes(&url.replace("http://", "https://")).await?)),
        None => None,
    };
    Ok(Some(SkinProfile { uuid: uuid.to_string(), name: exact, model, png }))
}

/// The whole skin sheet for a player, for the changer's 3D preview.
#[tauri::command]
pub async fn get_skin_png(uuid: String) -> AppResult<Option<String>> {
    if uuid.len() < 32 || !uuid.chars().all(|c| c.is_ascii_hexdigit() || c == '-') {
        return Err(AppError::Other("Not a player UUID".into()));
    }
    let id = uuid.replace('-', "");
    let profile: Value = serde_json::from_slice(&http::get_bytes(&format!("{SESSION}{id}")).await?)?;
    let Some(url) = textures(&profile).and_then(|t| t["textures"]["SKIN"]["url"].as_str().map(String::from)) else {
        return Ok(None);
    };
    Ok(Some(data_url(http::get_bytes(&url.replace("http://", "https://")).await?)))
}

/// Uploads a skin to the signed-in Microsoft account.
///
/// Mojang takes the PNG and the model as a multipart form; the body is built
/// here rather than pulling reqwest's multipart feature in for one request.
#[tauri::command]
pub async fn apply_skin(app: tauri::AppHandle, png: String, model: String) -> AppResult<()> {
    let store = super::store::read()?;
    let account = store.active().ok_or_else(|| AppError::Other("Sign in with Microsoft to change your skin".into()))?;
    let base64_png = png.strip_prefix("data:image/png;base64,").unwrap_or(&png);
    let bytes = base64::engine::general_purpose::STANDARD
        .decode(base64_png)
        .map_err(|e| AppError::Other(format!("Bad skin image: {e}")))?;
    // Reject anything that is not a skin before it reaches Mojang.
    let image = image::load_from_memory_with_format(&bytes, ImageFormat::Png)
        .map_err(|_| AppError::Other("That file is not a PNG image".into()))?;
    if image.width() < 64 || image.height() < 32 {
        return Err(AppError::Other("That image is too small for a skin".into()));
    }
    let client_id = crate::config::msa_client_id(&app)?;
    let token = super::microsoft::access_token(&client_id, account).await?;
    let variant = if model == "slim" { "slim" } else { "classic" };
    let boundary = "----NovoMCSkinBoundary";
    let mut body: Vec<u8> = Vec::new();
    body.extend_from_slice(format!("--{boundary}\r\nContent-Disposition: form-data; name=\"variant\"\r\n\r\n{variant}\r\n").as_bytes());
    body.extend_from_slice(
        format!("--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\nContent-Type: image/png\r\n\r\n")
            .as_bytes(),
    );
    body.extend_from_slice(&bytes);
    body.extend_from_slice(format!("\r\n--{boundary}--\r\n").as_bytes());

    let response = reqwest::Client::new()
        .post(SKIN_UPLOAD)
        .header("Authorization", format!("Bearer {token}"))
        .header("Content-Type", format!("multipart/form-data; boundary={boundary}"))
        .body(body)
        .send()
        .await
        .map_err(|e| AppError::Other(format!("Couldn't reach Minecraft services: {e}")))?;
    if !response.status().is_success() {
        let status = response.status();
        let detail = response.text().await.unwrap_or_default();
        return Err(AppError::Other(format!("Minecraft services refused the skin ({status}): {detail}")));
    }
    // The cached face is now wrong.
    let _ = std::fs::remove_file(cache_file(&account.uuid)?);
    Ok(())
}

/// Puts the signed-in account back on the default skin: Mojang drops the
/// uploaded one, and the launcher's cached face goes with it.
#[tauri::command]
pub async fn reset_skin(app: tauri::AppHandle) -> AppResult<()> {
    let store = super::store::read()?;
    let account = store.active().ok_or_else(|| AppError::Other("Sign in with Microsoft to change your skin".into()))?;
    let client_id = crate::config::msa_client_id(&app)?;
    let token = super::microsoft::access_token(&client_id, account).await?;
    let response = reqwest::Client::new()
        .delete(SKIN_UPLOAD)
        .header("Authorization", format!("Bearer {token}"))
        .send()
        .await
        .map_err(|e| AppError::Other(format!("Couldn't reach Minecraft services: {e}")))?;
    if !response.status().is_success() {
        let status = response.status();
        return Err(AppError::Other(format!("Minecraft services refused the reset ({status})")));
    }
    let _ = std::fs::remove_file(cache_file(&account.uuid)?);
    Ok(())
}

async fn skin_url(uuid: &str) -> AppResult<Option<String>> {
    let id = uuid.replace('-', "");
    let bytes = http::get_bytes(&format!("{SESSION}{id}")).await?;
    let profile: Value = serde_json::from_slice(&bytes)?;
    Ok(textures(&profile)
        .and_then(|t| t["textures"]["SKIN"]["url"].as_str().map(|u| u.replace("http://", "https://"))))
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
