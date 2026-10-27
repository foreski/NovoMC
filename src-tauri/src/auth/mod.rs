//! Accounts and login.
//!
//! - `offline`: username-only accounts, used only by the developer test launches
//!   (players sign in with Microsoft; saved offline accounts are dropped on load).
//! - `microsoft`: Microsoft sign-in (sign-in window -> Xbox Live -> XSTS ->
//!   Minecraft token -> profile). The client ID comes from `novomc.config.json`;
//!   refresh tokens go to the OS credential store (`secrets`, via `keyring`).
//! - `store`: the saved accounts (no secrets) and which one is active.

pub mod account;
pub mod microsoft;
#[cfg(test)]
pub mod offline;
pub mod secrets;
pub mod skins;
pub mod store;

use crate::error::{AppError, AppResult};
use store::AccountStore;

#[tauri::command]
pub fn list_accounts() -> AppResult<AccountStore> {
    store::read()
}

/// Removes an account. If it was active, the first remaining one becomes active.
#[tauri::command]
pub fn remove_account(uuid: String) -> AppResult<AccountStore> {
    store::update(|s| {
        if s.accounts.iter().any(|a| a.uuid == uuid && a.kind == account::AccountKind::Microsoft) {
            microsoft::forget(&uuid);
        }
        s.accounts.retain(|a| a.uuid != uuid);
        if s.active_uuid.as_deref() == Some(uuid.as_str()) {
            s.active_uuid = s.accounts.first().map(|a| a.uuid.clone());
        }
        Ok(())
    })
}

#[tauri::command]
pub fn set_active_account(uuid: String) -> AppResult<AccountStore> {
    store::update(|s| {
        if !s.accounts.iter().any(|a| a.uuid == uuid) {
            return Err(AppError::Other("account not found".into()));
        }
        s.active_uuid = Some(uuid);
        Ok(())
    })
}

const LOGIN_WINDOW: &str = "microsoft-login";

/// Opens a Microsoft sign-in window, waits for the user to finish, then runs
/// the Xbox/Minecraft chain. The account is added (or updated) and made active.
#[tauri::command]
pub async fn microsoft_login(app: tauri::AppHandle) -> AppResult<AccountStore> {
    use tauri::{Manager, WebviewUrl, WebviewWindowBuilder};
    use tokio::sync::oneshot;

    let client_id = crate::config::msa_client_id(&app)?;
    if let Some(old) = app.get_webview_window(LOGIN_WINDOW) {
        let _ = old.close();
    }

    // Resolved once: Ok(code) from the redirect, Err(message) if the window closes or Microsoft reports an error.
    let (tx, rx) = oneshot::channel::<Result<String, String>>();
    let tx = std::sync::Arc::new(std::sync::Mutex::new(Some(tx)));
    let url = microsoft::authorize_url(&client_id).parse().map_err(|e| AppError::Other(format!("{e}")))?;

    let nav_tx = tx.clone();
    let nav_app = app.clone();
    let window = WebviewWindowBuilder::new(&app, LOGIN_WINDOW, WebviewUrl::External(url))
        .title("Sign in with Microsoft")
        .inner_size(480.0, 640.0)
        .center()
        .on_navigation(move |url| {
            if !url.as_str().starts_with(microsoft::REDIRECT_URI) {
                return true;
            }
            let param = |k: &str| url.query_pairs().find(|(name, _)| name == k).map(|(_, v)| v.into_owned());
            let result = match (param("code"), param("error")) {
                (Some(code), _) => Ok(code),
                (_, Some(err)) if err == "access_denied" => Err("Sign-in was cancelled".to_string()),
                (_, err) => Err(format!("Microsoft sign-in failed: {}", param("error_description").or(err).unwrap_or_default())),
            };
            if let Some(tx) = nav_tx.lock().unwrap().take() {
                let _ = tx.send(result);
            }
            // Close from outside the navigation callback.
            let app = nav_app.clone();
            tauri::async_runtime::spawn(async move {
                if let Some(w) = app.get_webview_window(LOGIN_WINDOW) {
                    let _ = w.close();
                }
            });
            false
        })
        .build()
        .map_err(|e| AppError::Other(format!("Couldn't open the sign-in window: {e}")))?;

    let close_tx = tx.clone();
    window.on_window_event(move |event| {
        if let tauri::WindowEvent::Destroyed = event {
            if let Some(tx) = close_tx.lock().unwrap().take() {
                let _ = tx.send(Err("Sign-in was cancelled".to_string()));
            }
        }
    });

    let code = rx.await.map_err(|_| AppError::Other("Sign-in was cancelled".into()))?.map_err(AppError::Other)?;
    let account = microsoft::sign_in_with_code(&client_id, &code).await?;
    store::update(|s| {
        s.accounts.retain(|a| a.uuid != account.uuid);
        s.active_uuid = Some(account.uuid.clone());
        s.accounts.push(account);
        Ok(())
    })
}
