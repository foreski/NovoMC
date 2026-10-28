//! Settings sync: an instance's `config/novomc/modules.json` (mods, HUD
//! layout, keybinds) saved to the Novo account, one copy per Minecraft
//! version ("1.8.9", "26.3"), so every PC gets the same setup.
//!
//! Each instance remembers what it last synced in `cloud-sync.json` next to
//! modules.json (the file's hash and the cloud copy's time), which tells us
//! which side changed. Before a launch the UI calls [`novo_sync_settings`];
//! if both sides changed it asks the player which to keep. After the game
//! exits, local changes are uploaded unless the cloud changed too.

use std::path::{Path, PathBuf};
use std::time::UNIX_EPOCH;

use serde::{Deserialize, Serialize};
use serde_json::json;
use tauri::AppHandle;

use crate::auth::secrets;
use crate::download::engine::sha1_hex;
use crate::error::{AppError, AppResult};
use crate::instances::{client, store};
use crate::{paths, settings};

const RECORD: &str = "cloud-sync.json";

/// What the last sync of an instance saw.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Record {
    /// SHA1 of modules.json right after the sync.
    pub hash: String,
    /// The cloud copy's `updatedAt` (ms) at that point.
    pub cloud_at: i64,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Plan {
    Nothing,
    Upload,
    Download,
    /// Same content on both sides; only the record needs writing.
    Adopt,
    Conflict,
}

/// Decides from the local file's hash, the cloud copy's (time, hash) and the record.
pub fn plan(local: Option<&str>, cloud: Option<(i64, &str)>, record: Option<&Record>) -> Plan {
    match (local, cloud) {
        (None, None) => Plan::Nothing,
        (Some(_), None) => Plan::Upload,
        (None, Some(_)) => Plan::Download,
        (Some(l), Some((_, c))) if l == c => Plan::Adopt,
        (Some(l), Some((at, _))) => match record {
            // First sync on this PC with different settings on each side.
            None => Plan::Conflict,
            Some(r) => match (l != r.hash, at != r.cloud_at) {
                (false, false) => Plan::Nothing,
                (true, false) => Plan::Upload,
                (false, true) => Plan::Download,
                (true, true) => Plan::Conflict,
            },
        },
    }
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SyncResult {
    /// "off" | "none" | "uploaded" | "downloaded" | "conflict"
    pub action: &'static str,
    /// Last change of each side (ms since 1970), for the conflict prompt.
    pub local_modified: Option<i64>,
    pub cloud_modified: Option<i64>,
}

impl SyncResult {
    fn of(action: &'static str) -> Self {
        Self { action, local_modified: None, cloud_modified: None }
    }
}

/// The cloud slot of an instance, or None when it doesn't run Novo Client.
fn slot(instance_id: &str) -> AppResult<Option<(String, PathBuf)>> {
    let root = paths::data_dir()?;
    let inst = store::load(&root, instance_id)?;
    if !(client::supported(&inst) && inst.client_mod) {
        return Ok(None);
    }
    Ok(Some((inst.version.clone(), store::game_dir(&root, instance_id).join("config").join("novomc").join("modules.json"))))
}

fn read_record(config: &Path) -> Option<Record> {
    serde_json::from_slice(&std::fs::read(config.with_file_name(RECORD)).ok()?).ok()
}

fn write_record(config: &Path, record: &Record) -> AppResult<()> {
    std::fs::write(config.with_file_name(RECORD), serde_json::to_vec_pretty(record)?)?;
    Ok(())
}

fn modified_ms(path: &Path) -> Option<i64> {
    let t = std::fs::metadata(path).ok()?.modified().ok()?;
    Some(t.duration_since(UNIX_EPOCH).ok()?.as_millis() as i64)
}

async fn upload(app: &AppHandle, slot: &str, data: &str) -> AppResult<i64> {
    let res = super::call(app, reqwest::Method::PUT, &format!("/account/settings/{slot}"), Some(json!({ "data": data })))
        .await?
        .ok_or_else(|| AppError::Other("Couldn't save settings to your Novo account".into()))?;
    res["updatedAt"].as_i64().ok_or_else(|| AppError::Other("Bad answer from the relay".into()))
}

/// Syncs an instance's settings. `resolve` ("local" or "cloud") makes that
/// side win: the answer to a conflict prompt, or the Upload/Download buttons.
pub async fn run(app: &AppHandle, instance_id: &str, resolve: Option<&str>) -> AppResult<SyncResult> {
    if !settings::load()?.cloud_sync || secrets::novo_token()?.is_none() {
        return Ok(SyncResult::of("off"));
    }
    let Some((slot, config)) = slot(instance_id)? else {
        return Ok(SyncResult::of("off"));
    };
    let local = std::fs::read_to_string(&config).ok();
    let cloud = super::call(app, reqwest::Method::GET, &format!("/account/settings/{slot}"), None).await?;
    let cloud = cloud.and_then(|v| Some((v["updatedAt"].as_i64()?, v["data"].as_str()?.to_string())));

    let local_hash = local.as_deref().map(|s| sha1_hex(s.as_bytes()));
    let cloud_hash = cloud.as_ref().map(|(at, data)| (*at, sha1_hex(data.as_bytes())));
    let decided = match (resolve, plan(local_hash.as_deref(), cloud_hash.as_ref().map(|(at, h)| (*at, h.as_str())), read_record(&config).as_ref())) {
        (_, Plan::Adopt) => Plan::Adopt,
        (Some("local"), _) if local.is_some() => Plan::Upload,
        (Some("cloud"), _) if cloud.is_some() => Plan::Download,
        (_, Plan::Conflict) => {
            return Ok(SyncResult {
                action: "conflict",
                local_modified: modified_ms(&config),
                cloud_modified: cloud.as_ref().map(|(at, _)| *at),
            })
        }
        (_, p) => p,
    };

    match decided {
        Plan::Nothing => Ok(SyncResult::of("none")),
        Plan::Adopt => {
            let (at, hash) = cloud_hash.expect("adopt needs a cloud copy");
            write_record(&config, &Record { hash, cloud_at: at })?;
            Ok(SyncResult::of("none"))
        }
        Plan::Upload => {
            let data = local.expect("upload needs a local file");
            let at = upload(app, &slot, &data).await?;
            write_record(&config, &Record { hash: local_hash.unwrap_or_default(), cloud_at: at })?;
            Ok(SyncResult::of("uploaded"))
        }
        Plan::Download => {
            let (at, data) = cloud.expect("download needs a cloud copy");
            if let Some(dir) = config.parent() {
                std::fs::create_dir_all(dir)?;
            }
            if config.exists() {
                std::fs::copy(&config, config.with_extension("json.bak"))?;
            }
            std::fs::write(&config, &data)?;
            write_record(&config, &Record { hash: sha1_hex(data.as_bytes()), cloud_at: at })?;
            Ok(SyncResult::of("downloaded"))
        }
        Plan::Conflict => unreachable!(),
    }
}

#[tauri::command]
pub async fn novo_sync_settings(app: AppHandle, instance_id: String, resolve: Option<String>) -> AppResult<SyncResult> {
    run(&app, &instance_id, resolve.as_deref()).await
}

/// After the game exits: upload what changed while playing (a conflict waits
/// for the next launch, where the player is asked).
pub fn after_game(app: &AppHandle, instance_id: String) {
    let app = app.clone();
    tauri::async_runtime::spawn(async move {
        match run(&app, &instance_id, None).await {
            Ok(r) if r.action == "uploaded" => println!("[NovoMC] Saved {instance_id}'s settings to the Novo account"),
            Ok(_) => {}
            Err(e) => eprintln!("[NovoMC] Settings sync after the game failed: {e}"),
        }
    });
}

#[cfg(test)]
mod tests {
    use super::*;

    fn rec(hash: &str, at: i64) -> Record {
        Record { hash: hash.into(), cloud_at: at }
    }

    #[test]
    fn one_side_only() {
        assert_eq!(plan(None, None, None), Plan::Nothing);
        assert_eq!(plan(Some("a"), None, None), Plan::Upload);
        assert_eq!(plan(None, Some((5, "a")), None), Plan::Download);
        // The cloud copy was deleted? Upload again.
        assert_eq!(plan(Some("a"), None, Some(&rec("a", 5))), Plan::Upload);
    }

    #[test]
    fn same_content_needs_no_transfer() {
        assert_eq!(plan(Some("a"), Some((5, "a")), None), Plan::Adopt);
        assert_eq!(plan(Some("a"), Some((9, "a")), Some(&rec("b", 5))), Plan::Adopt);
    }

    #[test]
    fn the_side_that_changed_wins() {
        let r = rec("a", 5);
        assert_eq!(plan(Some("b"), Some((5, "a")), Some(&r)), Plan::Upload);
        assert_eq!(plan(Some("a"), Some((9, "c")), Some(&r)), Plan::Download);
        assert_eq!(plan(Some("b"), Some((9, "c")), Some(&r)), Plan::Conflict);
    }

    #[test]
    fn first_sync_with_different_settings_asks() {
        assert_eq!(plan(Some("a"), Some((5, "b")), None), Plan::Conflict);
    }
}
