//! `accounts.json`: the saved accounts and which one is active.
//! Contains no tokens; Microsoft refresh tokens go to the OS keyring.

use std::path::PathBuf;
use std::sync::Mutex;

use serde::{Deserialize, Serialize};

use super::account::Account;
use crate::error::AppResult;
use crate::paths;

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AccountStore {
    /// UUID of the account used to launch the game.
    pub active_uuid: Option<String>,
    pub accounts: Vec<Account>,
}

/// Serializes read-modify-write cycles so concurrent commands can't clobber the file.
static LOCK: Mutex<()> = Mutex::new(());

fn file() -> AppResult<PathBuf> {
    Ok(paths::data_dir()?.join("accounts.json"))
}

impl AccountStore {
    fn load() -> AppResult<Self> {
        match std::fs::read(file()?) {
            Ok(bytes) => Ok(serde_json::from_slice(&bytes)?),
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(Self::default()),
            Err(e) => Err(e.into()),
        }
    }

    /// Writes to a temp file first so a crash never leaves a half-written store.
    fn save(&self) -> AppResult<()> {
        let path = file()?;
        let tmp = path.with_extension("json.tmp");
        std::fs::write(&tmp, serde_json::to_vec_pretty(self)?)?;
        std::fs::rename(tmp, path)?;
        Ok(())
    }

    /// The account selected for launching, if any.
    pub fn active(&self) -> Option<&Account> {
        let uuid = self.active_uuid.as_deref()?;
        self.accounts.iter().find(|a| a.uuid == uuid)
    }
}

/// Returns the current store.
pub fn read() -> AppResult<AccountStore> {
    let _guard = LOCK.lock().unwrap_or_else(|e| e.into_inner());
    AccountStore::load()
}

/// Loads the store, applies `f`, saves it, and returns the new state.
pub fn update(f: impl FnOnce(&mut AccountStore) -> AppResult<()>) -> AppResult<AccountStore> {
    let _guard = LOCK.lock().unwrap_or_else(|e| e.into_inner());
    let mut store = AccountStore::load()?;
    f(&mut store)?;
    store.save()?;
    Ok(store)
}
