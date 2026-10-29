//! In-game cosmetics: the 1.8.9 client's title screen shows a paper doll
//! wearing the cape, wings and hat from the Novo account, and lets the player
//! swap them there.
//!
//! The game has no account token, so the launcher is the only side that may
//! write: before a launch it puts the equipped ids, the owned cosmetics and
//! their names in `config/novomc/cosmetics.json`. The mod edits the three worn
//! ids in that file (see `gui/NovoDoll.java`), and after the game exits the
//! launcher PATCHes whatever changed to `/account/me` and updates
//! `cosmetics-synced.json`, the copy it last wrote.

use std::path::PathBuf;
use std::time::Duration;

use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use tauri::{AppHandle, Emitter};

use super::call;
use crate::error::AppResult;
use crate::instances::{client, store};
use crate::paths;

const FILE: &str = "cosmetics.json";
const RECORD: &str = "cosmetics-synced.json";
/// Long enough for a healthy relay, short enough never to hold a launch up.
const PREPARE_TIMEOUT: Duration = Duration::from_secs(3);

/// The three cosmetic slots the doll edits.
#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
struct Worn {
    #[serde(default)]
    cape: Option<String>,
    #[serde(default)]
    wing: Option<String>,
    #[serde(default)]
    hat: Option<String>,
}

impl Worn {
    fn of(data: &Value) -> Self {
        let id = |key: &str| data[key].as_str().map(String::from);
        Self { cape: id("cape"), wing: id("wing"), hat: id("hat") }
    }
}

/// `(cosmetics.json, cosmetics-synced.json)` for an instance.
fn files(instance_id: &str) -> AppResult<(PathBuf, PathBuf)> {
    let root = paths::data_dir()?;
    let config = store::game_dir(&root, instance_id).join("config").join("novomc");
    Ok((config.join(FILE), config.join(RECORD)))
}

fn write(path: &PathBuf, data: &Value) -> AppResult<()> {
    if let Some(dir) = path.parent() {
        std::fs::create_dir_all(dir)?;
    }
    let tmp = path.with_extension("json.tmp");
    std::fs::write(&tmp, serde_json::to_vec_pretty(data)?)?;
    std::fs::rename(tmp, path)?;
    Ok(())
}

/// Writes the doll's cosmetics and owned ids for the next launch. Never fails
/// a launch: offline, signed out or a slow relay all just leave the last copy.
pub async fn prepare(app: &AppHandle, instance_id: &str) {
    match tokio::time::timeout(PREPARE_TIMEOUT, write_for_launch(app, instance_id)).await {
        Ok(Ok(())) => {}
        Ok(Err(e)) => eprintln!("[NovoMC] Couldn't prepare the cosmetics doll: {e}"),
        Err(_) => eprintln!("[NovoMC] Couldn't prepare the cosmetics doll: the relay timed out"),
    }
}

async fn write_for_launch(app: &AppHandle, instance_id: &str) -> AppResult<()> {
    // A pick made in game that never reached the account (offline exit) is the newest
    // change, so save it before the account copy overwrites the file.
    let _ = save(app, instance_id).await;
    let root = paths::data_dir()?;
    let inst = store::load(&root, instance_id)?;
    if !(client::supported(&inst) && inst.client_mod) {
        return Ok(());
    }
    let Some(account) = call(app, reqwest::Method::GET, "/account/me", None).await? else {
        return Ok(());
    };
    let worn = Worn::of(&account);
    let items = |key: &str| -> Vec<Value> {
        account[key]
            .as_array()
            .map(|list| list.iter().filter(|item| item["owned"].as_bool() == Some(true)).cloned().collect())
            .unwrap_or_default()
    };
    let data = json!({
        "version": 1,
        "cape": worn.cape.clone(),
        "wing": worn.wing.clone(),
        "hat": worn.hat.clone(),
        "items": { "capes": items("capes"), "wings": items("wings"), "hats": items("hats") },
    });
    let (path, record) = files(instance_id)?;
    write(&path, &data)?;
    write(&record, &serde_json::to_value(&worn)?)?;
    Ok(())
}

fn read_record(instance_id: &str) -> Worn {
    files(instance_id)
        .ok()
        .and_then(|(_, record)| std::fs::read(record).ok())
        .and_then(|bytes| serde_json::from_slice(&bytes).ok())
        .unwrap_or_default()
}

/// Saves cosmetics the doll changed to the account. Runs after the game exits.
pub fn after_game(app: AppHandle, instance_id: String) {
    tauri::async_runtime::spawn(async move {
        if let Err(e) = save(&app, &instance_id).await {
            eprintln!("[NovoMC] Couldn't save the cosmetics the doll changed: {e}");
        }
    });
}

async fn save(app: &AppHandle, instance_id: &str) -> AppResult<()> {
    let (path, record) = files(instance_id)?;
    let Ok(bytes) = std::fs::read(&path) else {
        return Ok(());
    };
    let Ok(data) = serde_json::from_slice::<Value>(&bytes) else {
        return Ok(());
    };
    let now = Worn::of(&data);
    let before = read_record(instance_id);
    if now == before {
        return Ok(());
    }
    let mut patch = serde_json::Map::new();
    if now.cape != before.cape {
        patch.insert("cape".into(), json!(now.cape));
    }
    if now.wing != before.wing {
        patch.insert("wing".into(), json!(now.wing));
    }
    if now.hat != before.hat {
        patch.insert("hat".into(), json!(now.hat));
    }
    call(app, reqwest::Method::PATCH, "/account/me", Some(Value::Object(patch))).await?;
    write(&record, &serde_json::to_value(&now)?)?;
    println!("[NovoMC] Saved the cosmetics picked in game for {instance_id}");
    // The launcher's Marketplace and account card show the old cosmetics until they ask again.
    let _ = app.emit("novo-account", ());
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reads_the_three_slots_and_ignores_null() {
        let worn = Worn::of(&json!({ "cape": "tidal", "wing": null, "hat": "wizard_hat" }));
        assert_eq!(worn, Worn { cape: Some("tidal".into()), wing: None, hat: Some("wizard_hat".into()) });
    }

    #[test]
    fn a_missing_field_is_none() {
        assert_eq!(Worn::of(&json!({})), Worn::default());
    }
}
