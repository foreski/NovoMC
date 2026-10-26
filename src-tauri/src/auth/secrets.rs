//! Microsoft refresh tokens in the OS credential store (Windows Credential
//! Manager, macOS Keychain, Secret Service on Linux). Never written to disk
//! by NovoMC itself.

use keyring::Entry;

use crate::error::{AppError, AppResult};

const SERVICE: &str = "com.novomc.app";

fn entry(uuid: &str) -> AppResult<Entry> {
    Entry::new(SERVICE, &format!("microsoft:{uuid}")).map_err(|e| AppError::Other(format!("Credential store unavailable: {e}")))
}

pub fn save_refresh_token(uuid: &str, token: &str) -> AppResult<()> {
    entry(uuid)?
        .set_password(token)
        .map_err(|e| AppError::Other(format!("Couldn't save the sign-in to the credential store: {e}")))
}

/// The saved refresh token, or None if this account was never signed in here.
pub fn refresh_token(uuid: &str) -> AppResult<Option<String>> {
    match entry(uuid)?.get_password() {
        Ok(token) => Ok(Some(token)),
        Err(keyring::Error::NoEntry) => Ok(None),
        Err(e) => Err(AppError::Other(format!("Couldn't read the saved sign-in: {e}"))),
    }
}

pub fn delete(uuid: &str) {
    if let Ok(e) = entry(uuid) {
        let _ = e.delete_credential();
    }
}
