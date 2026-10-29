//! Novo Client accounts: a Novo profile (signed in with Discord through the
//! relay, see `relay/src/account.js`) that links the player's Minecraft
//! accounts, picks a cape and keeps the client's settings in the cloud.
//!
//! - Sign-in: a window opens Discord's authorize page; Discord redirects to
//!   `<relay>/account/discord/callback`, where we catch the code (like the
//!   Microsoft sign-in) and the relay swaps it for a Novo session token. The
//!   token lives in the OS credential store.
//! - Linking: each Microsoft account on this PC is sent as name + UUID. The
//!   relay can't check it with Mojang (Mojang blocks Cloudflare Workers), so
//!   it trusts the launcher; no Minecraft token is involved.
//! - `sync`: `config/novomc/modules.json` per Minecraft version.

pub mod cosmetics;
pub mod sync;

use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use tauri::AppHandle;

use crate::auth::{self, account::AccountKind, secrets};
use crate::config;
use crate::download::http;
use crate::error::{AppError, AppResult};

const LOGIN_WINDOW: &str = "novo-login";
const CHECKOUT_WINDOW: &str = "novo-checkout";
const DISCORD_AUTHORIZE: &str = "https://discord.com/oauth2/authorize";

/// Frames an animated cosmetic plays (see `scripts/make-capes.mjs`).
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CapeAnimation {
    pub frames: u32,
    pub fps: u32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Cape {
    pub id: String,
    pub name: String,
    pub owned: bool,
    /// Present only for animated capes; the launcher animates the preview.
    #[serde(default)]
    pub animated: Option<CapeAnimation>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Banner {
    pub id: String,
    pub name: String,
    pub owned: bool,
}

/// A wing or hat the relay lists for the account.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Cosmetic {
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
    #[serde(default)]
    pub coins: u64,
    pub display_name: String,
    pub discord_name: Option<String>,
    pub avatar_url: Option<String>,
    pub cape: Option<String>,
    pub capes: Vec<Cape>,
    pub banner: Option<String>,
    pub banners: Vec<Banner>,
    #[serde(default)]
    pub wing: Option<String>,
    #[serde(default)]
    pub wings: Vec<Cosmetic>,
    #[serde(default)]
    pub hat: Option<String>,
    #[serde(default)]
    pub hats: Vec<Cosmetic>,
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
pub async fn novo_set_banner(app: AppHandle, banner: Option<String>) -> AppResult<NovoAccount> {
    account_from(call(&app, reqwest::Method::PATCH, "/account/me", Some(json!({ "banner": banner }))).await?)
}

#[tauri::command]
pub async fn novo_set_wing(app: AppHandle, wing: Option<String>) -> AppResult<NovoAccount> {
    account_from(call(&app, reqwest::Method::PATCH, "/account/me", Some(json!({ "wing": wing }))).await?)
}

#[tauri::command]
pub async fn novo_set_hat(app: AppHandle, hat: Option<String>) -> AppResult<NovoAccount> {
    account_from(call(&app, reqwest::Method::PATCH, "/account/me", Some(json!({ "hat": hat }))).await?)
}

#[tauri::command]
pub async fn novo_shop(app: AppHandle) -> AppResult<Value> {
    call(&app, reqwest::Method::GET, "/account/shop", None).await?
        .ok_or_else(|| AppError::Other("Marketplace is unavailable".into()))
}

#[tauri::command]
pub async fn novo_purchase_item(app: AppHandle, id: String) -> AppResult<NovoAccount> {
    account_from(call(&app, reqwest::Method::POST, "/account/shop/purchase", Some(json!({ "id": id }))).await?)
}

#[tauri::command]
pub async fn novo_redeem_gift(app: AppHandle, code: String) -> AppResult<NovoAccount> {
    account_from(call(&app, reqwest::Method::POST, "/account/shop/redeem", Some(json!({ "code": code }))).await?)
}

/// Starts a Stripe Checkout session and shows it in a small window of its own.
///
/// The session link must be used exactly as Stripe sends it: it carries an
/// encrypted fragment after the session id, and handing it to a browser (or
/// through Explorer) drops that fragment, which makes Stripe answer "This link
/// is incomplete". The window is closed once Stripe redirects to the relay's
/// return page; the main window refreshes the wallet when it gets focus back.
#[tauri::command]
pub async fn novo_checkout(app: AppHandle, bundle_id: String) -> AppResult<()> {
    use tauri::{Manager, WebviewUrl, WebviewWindowBuilder};

    let value = call(&app, reqwest::Method::POST, "/account/checkout", Some(json!({ "bundleId": bundle_id }))).await?;
    let link = value
        .and_then(|value| value["url"].as_str().map(String::from))
        .ok_or_else(|| AppError::Other("Checkout did not return a secure payment link".into()))?;
    let url = link.parse().map_err(|e| AppError::Other(format!("{e}")))?;
    if let Some(old) = app.get_webview_window(CHECKOUT_WINDOW) {
        let _ = old.close();
    }

    let done_app = app.clone();
    WebviewWindowBuilder::new(&app, CHECKOUT_WINDOW, WebviewUrl::External(url))
        .title("Novo Coins checkout")
        .inner_size(480.0, 720.0)
        .center()
        .on_navigation(move |url| {
            // Stripe sends the player here once the payment succeeds or is cancelled.
            if url.path().starts_with("/account/checkout/return") {
                let app = done_app.clone();
                tauri::async_runtime::spawn(async move {
                    // Let the return page render before the window disappears.
                    tokio::time::sleep(std::time::Duration::from_millis(1500)).await;
                    if let Some(w) = app.get_webview_window(CHECKOUT_WINDOW) {
                        let _ = w.close();
                    }
                });
            }
            true
        })
        .build()
        .map_err(|e| AppError::Other(format!("Couldn't open the checkout window: {e}")))?;
    Ok(())
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
    account_from(call(app, reqwest::Method::POST, "/account/link", Some(json!({ "name": player.username, "uuid": player.uuid }))).await?)
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
            "capes": [{ "id": "ember", "name": "Ember", "owned": true }], "banner": "aurora",
            "banners": [{ "id": "aurora", "name": "Aurora", "owned": true }],
            "minecraft": [{ "uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5", "name": "Steve" }]
        });
        let a = account_from(Some(v)).unwrap();
        assert_eq!(a.cape.as_deref(), Some("ember"));
        assert_eq!(a.banner.as_deref(), Some("aurora"));
        assert_eq!(a.minecraft[0].name, "Steve");
    }
}
