//! Novo Client accounts: a Novo profile (signed in with Discord through the
//! relay, see `relay/src/account.js`) that links the player's Minecraft
//! accounts, picks a cape and keeps the client's settings in the cloud.
//!
//! - Sign-in: a window opens Discord's authorize page; Discord redirects to
//!   `<relay>/account/discord/callback`, where we catch the code (like the
//!   Microsoft sign-in) and the relay swaps it for a Novo session token. The
//!   token lives in the OS credential store.
//! - Linking: we prove each Microsoft account through Mojang's session server
//!   (`join` here, `hasJoined` on the relay), so the Minecraft token never
//!   leaves for the relay.
//! - `sync`: `config/novomc/modules.json` per Minecraft version.

pub mod sync;

use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use tauri::AppHandle;

use crate::auth::{self, account::AccountKind, secrets};
use crate::config;
use crate::download::http;
use crate::error::{AppError, AppResult};

const LOGIN_WINDOW: &str = "novo-login";
const DISCORD_AUTHORIZE: &str = "https://discord.com/oauth2/authorize";
const MOJANG_JOIN: &str = "https://sessionserver.mojang.com/session/minecraft/join";

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Cape {
    pub id: String,
    pub name: String,
    pub owned: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LinkedPlayer {
    pub uuid: String,
    pub name: String,
}

/// The signed-in Novo account, as the relay describes it.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct NovoAccount {
    pub id: String,
    pub display_name: String,
    pub discord_name: Option<String>,
    pub avatar_url: Option<String>,
    pub cape: Option<String>,
    pub capes: Vec<Cape>,
    pub minecraft: Vec<LinkedPlayer>,
}

fn relay(app: &AppHandle) -> AppResult<String> {
    config::relay_base(app).ok_or_else(|| AppError::Other("Novo accounts need the relay (bugReportUrl in novomc.config.json)".into()))
}

pub(crate) fn random_hex(bytes: usize) -> String {
    let mut buf = vec![0u8; bytes];
    getrandom::getrandom(&mut buf).expect("no system randomness");
    buf.iter().map(|b| format!("{b:02x}")).collect()
}

/// Calls the relay as the signed-in account. Signs out locally if the relay
/// says the session is gone.
pub(crate) async fn call(app: &AppHandle, method: reqwest::Method, path: &str, body: Option<Value>) -> AppResult<Option<Value>> {
    let token = secrets::novo_token()?.ok_or_else(|| AppError::Other("Sign in to your Novo account first".into()))?;
    let mut req = http::client().request(method, format!("{}{path}", relay(app)?)).bearer_auth(token);
    if let Some(body) = body {
        req = req.json(&body);
    }
    let res = req.send().await?;
    match res.status().as_u16() {
        200 => Ok(Some(res.json().await?)),
        404 => Ok(None),
        401 => {
            secrets::delete_novo_token();
            Err(AppError::Other("You were signed out of Novo; sign in again".into()))
        }
        status => Err(relay_error(status, res.json().await.unwrap_or(Value::Null))),
    }
}

fn relay_error(status: u16, body: Value) -> AppError {
    AppError::Other(body["error"].as_str().map(String::from).unwrap_or_else(|| format!("Novo account error (HTTP {status})")))
}

fn account_from(value: Option<Value>) -> AppResult<NovoAccount> {
    Ok(serde_json::from_value(value.ok_or_else(|| AppError::Other("Novo account not found".into()))?)?)
}

/// The signed-in Novo account, or None.
#[tauri::command]
pub async fn novo_account(app: AppHandle) -> AppResult<Option<NovoAccount>> {
    if secrets::novo_token()?.is_none() {
        return Ok(None);
    }
    match call(&app, reqwest::Method::GET, "/account/me", None).await {
        Ok(v) => account_from(v).map(Some),
        Err(_) if secrets::novo_token()?.is_none() => Ok(None),
        Err(e) => Err(e),
    }
}

/// Discord sign-in in a window; then links every Microsoft account on this PC.
#[tauri::command]
pub async fn novo_login(app: AppHandle) -> AppResult<NovoAccount> {
    use tauri::{Manager, WebviewUrl, WebviewWindowBuilder};
    use tokio::sync::oneshot;

    let base = relay(&app)?;
    let client_id = config::get(&app)
        .discord_app_id
        .clone()
        .filter(|id| !id.trim().is_empty())
        .ok_or_else(|| AppError::Other("Discord sign-in isn't configured: set discordAppId in novomc.config.json".into()))?;
    if let Some(old) = app.get_webview_window(LOGIN_WINDOW) {
        let _ = old.close();
    }
    let redirect = format!("{base}/account/discord/callback");
    let state = random_hex(16);
    let url = authorize_url(&client_id, &redirect, &state).parse().map_err(|e| AppError::Other(format!("{e}")))?;

    let (tx, rx) = oneshot::channel::<Result<String, String>>();
    let tx = std::sync::Arc::new(std::sync::Mutex::new(Some(tx)));
    let nav_tx = tx.clone();
    let nav_app = app.clone();
    let window = WebviewWindowBuilder::new(&app, LOGIN_WINDOW, WebviewUrl::External(url))
        .title("Sign in to Novo with Discord")
        .inner_size(520.0, 760.0)
        .center()
        .on_navigation(move |url| {
            if !url.as_str().starts_with(&redirect) {
                return true;
            }
            let result = callback_code(url, &state);
            if let Some(tx) = nav_tx.lock().unwrap().take() {
                let _ = tx.send(result);
            }
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
    let res = http::client().post(format!("{base}/account/login")).json(&json!({ "code": code })).send().await?;
    let status = res.status().as_u16();
    let body: Value = res.json().await.unwrap_or(Value::Null);
    if status != 200 {
        return Err(relay_error(status, body));
    }
    let token = body["token"].as_str().ok_or_else(|| AppError::Other("The relay sent no session".into()))?;
    secrets::save_novo_token(token)?;
    let mut account = account_from(Some(body["account"].clone()))?;
    for player in auth::store::read()?.accounts.iter().filter(|a| a.kind == AccountKind::Microsoft) {
        match link(&app, &player.uuid).await {
            Ok(a) => account = a,
            Err(e) => eprintln!("[NovoMC] Couldn't link {} to Novo: {e}", player.username),
        }
    }
    Ok(account)
}

pub fn authorize_url(client_id: &str, redirect: &str, state: &str) -> String {
    let q = |s: &str| -> String { url_escape(s) };
    format!(
        "{DISCORD_AUTHORIZE}?client_id={}&response_type=code&scope=identify&prompt=none&redirect_uri={}&state={}",
        q(client_id),
        q(redirect),
        q(state)
    )
}

fn url_escape(s: &str) -> String {
    s.bytes()
        .map(|b| match b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => (b as char).to_string(),
            _ => format!("%{b:02X}"),
        })
        .collect()
}

/// The code from Discord's redirect, checking it answers our own request.
fn callback_code(url: &tauri::Url, state: &str) -> Result<String, String> {
    let param = |k: &str| url.query_pairs().find(|(name, _)| name == k).map(|(_, v)| v.into_owned());
    if let Some(err) = param("error") {
        return Err(if err == "access_denied" { "Sign-in was cancelled".into() } else { format!("Discord sign-in failed: {err}") });
    }
    if param("state").as_deref() != Some(state) {
        return Err("Discord sign-in failed: unexpected answer; try again".into());
    }
    param("code").ok_or_else(|| "Discord sign-in failed: no code".into())
}

#[tauri::command]
pub async fn novo_logout(app: AppHandle) -> AppResult<()> {
    let _ = call(&app, reqwest::Method::POST, "/account/logout", Some(json!({}))).await;
    secrets::delete_novo_token();
    Ok(())
}

#[tauri::command]
pub async fn novo_set_display_name(app: AppHandle, name: String) -> AppResult<NovoAccount> {
    account_from(call(&app, reqwest::Method::PATCH, "/account/me", Some(json!({ "displayName": name }))).await?)
}

#[tauri::command]
pub async fn novo_set_cape(app: AppHandle, cape: Option<String>) -> AppResult<NovoAccount> {
    account_from(call(&app, reqwest::Method::PATCH, "/account/me", Some(json!({ "cape": cape }))).await?)
}

#[tauri::command]
pub async fn novo_link(app: AppHandle, uuid: String) -> AppResult<NovoAccount> {
    link(&app, &uuid).await
}

#[tauri::command]
pub async fn novo_unlink(app: AppHandle, uuid: String) -> AppResult<NovoAccount> {
    account_from(call(&app, reqwest::Method::DELETE, &format!("/account/link/{uuid}"), None).await?)
}

/// Links a Microsoft account on this PC to the Novo account.
pub async fn link(app: &AppHandle, uuid: &str) -> AppResult<NovoAccount> {
    let player = auth::store::read()?
        .accounts
        .into_iter()
        .find(|a| a.uuid == uuid && a.kind == AccountKind::Microsoft)
        .ok_or_else(|| AppError::Other("Only Microsoft accounts on this PC can be linked".into()))?;
    let access_token = auth::microsoft::access_token(&config::msa_client_id(app)?, &player).await?;
    let server_id = random_hex(10);
    let res = http::client()
        .post(MOJANG_JOIN)
        .json(&json!({ "accessToken": access_token, "selectedProfile": player.uuid.replace('-', ""), "serverId": server_id }))
        .send()
        .await?;
    if !res.status().is_success() {
        return Err(AppError::Other(format!("Mojang refused to confirm {} (HTTP {})", player.username, res.status().as_u16())));
    }
    account_from(call(app, reqwest::Method::POST, "/account/link", Some(json!({ "name": player.username, "serverId": server_id }))).await?)
}

/// After a Microsoft sign-in: link it too, if a Novo account is signed in.
pub fn link_in_background(app: &AppHandle, uuid: String) {
    if !matches!(secrets::novo_token(), Ok(Some(_))) {
        return;
    }
    let app = app.clone();
    tauri::async_runtime::spawn(async move {
        if let Err(e) = link(&app, &uuid).await {
            eprintln!("[NovoMC] Couldn't link {uuid} to Novo: {e}");
        }
    });
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn authorize_url_escapes_the_redirect() {
        let url = authorize_url("123", "https://relay.example/account/discord/callback", "ab12");
        assert!(url.starts_with("https://discord.com/oauth2/authorize?client_id=123&response_type=code&scope=identify"));
        assert!(url.contains("redirect_uri=https%3A%2F%2Frelay.example%2Faccount%2Fdiscord%2Fcallback"));
        assert!(url.ends_with("&state=ab12"));
    }

    #[test]
    fn callback_needs_our_state() {
        let ok: tauri::Url = "https://r.example/account/discord/callback?code=abc&state=s1".parse().unwrap();
        assert_eq!(callback_code(&ok, "s1"), Ok("abc".into()));
        assert!(callback_code(&ok, "other").is_err());
        let denied: tauri::Url = "https://r.example/account/discord/callback?error=access_denied&state=s1".parse().unwrap();
        assert_eq!(callback_code(&denied, "s1"), Err("Sign-in was cancelled".into()));
    }

    #[test]
    fn random_hex_is_random() {
        let (a, b) = (random_hex(16), random_hex(16));
        assert_eq!(a.len(), 32);
        assert_ne!(a, b);
    }

    #[test]
    fn account_parses_from_relay_json() {
        let v = json!({
            "id": "u1", "displayName": "Steve", "discordName": "steve", "avatarUrl": null, "cape": "ember",
            "capes": [{ "id": "ember", "name": "Ember", "owned": true }],
            "minecraft": [{ "uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5", "name": "Steve" }]
        });
        let a = account_from(Some(v)).unwrap();
        assert_eq!(a.cape.as_deref(), Some("ember"));
        assert_eq!(a.minecraft[0].name, "Steve");
    }
}
