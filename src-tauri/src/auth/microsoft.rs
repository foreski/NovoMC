//! Microsoft account sign-in, the same chain the official launcher uses:
//!
//! 1. Microsoft OAuth (login.live.com, authorization code in a sign-in window)
//! 2. Xbox Live user token
//! 3. XSTS token for Minecraft services
//! 4. Minecraft access token (`login_with_xbox`)
//! 5. Minecraft profile (name + UUID; fails if the account doesn't own the game)
//!
//! The Microsoft refresh token goes to the OS credential store ([`super::secrets`]);
//! Minecraft access tokens (valid ~24 h) are only kept in memory.

use std::collections::HashMap;
use std::sync::Mutex;
use std::time::{Duration, Instant};

use serde::Deserialize;
use serde_json::{json, Value};

use super::account::{Account, AccountKind};
use super::secrets;
use crate::download::http;
use crate::error::{AppError, AppResult};

pub const REDIRECT_URI: &str = "https://login.live.com/oauth20_desktop.srf";
const SCOPE: &str = "service::user.auth.xboxlive.com::MBI_SSL";
const AUTHORIZE_URL: &str = "https://login.live.com/oauth20_authorize.srf";
const TOKEN_URL: &str = "https://login.live.com/oauth20_token.srf";
const XBL_URL: &str = "https://user.auth.xboxlive.com/user/authenticate";
const XSTS_URL: &str = "https://xsts.auth.xboxlive.com/xsts/authorize";
const MC_LOGIN_URL: &str = "https://api.minecraftservices.com/authentication/login_with_xbox";
const PROFILE_URL: &str = "https://api.minecraftservices.com/minecraft/profile";

/// Minecraft access tokens by account UUID, with their expiry.
static TOKENS: Mutex<Option<HashMap<String, (String, Instant)>>> = Mutex::new(None);

#[derive(Deserialize)]
struct MsTokens {
    access_token: String,
    refresh_token: String,
}

/// The page the sign-in window opens.
pub fn authorize_url(client_id: &str) -> String {
    format!(
        "{AUTHORIZE_URL}?client_id={client_id}&response_type=code&scope={}&redirect_uri={}&prompt=select_account",
        enc(SCOPE),
        enc(REDIRECT_URI)
    )
}

/// Finishes sign-in with the code from the redirect: returns the account and
/// saves its refresh token.
pub async fn sign_in_with_code(client_id: &str, code: &str) -> AppResult<Account> {
    let ms = ms_token(client_id, &[("grant_type", "authorization_code"), ("code", code), ("redirect_uri", REDIRECT_URI)]).await?;
    let account = finish(&ms).await?;
    Ok(account)
}

/// A valid Minecraft access token for a Microsoft account, refreshing it
/// (and the saved refresh token) when the cached one is missing or old.
pub async fn access_token(client_id: &str, account: &Account) -> AppResult<String> {
    if let Some((token, expires)) = TOKENS.lock().unwrap().as_ref().and_then(|m| m.get(&account.uuid)).cloned() {
        if expires > Instant::now() + Duration::from_secs(300) {
            return Ok(token);
        }
    }
    let refresh = secrets::refresh_token(&account.uuid)?
        .ok_or_else(|| AppError::Other(format!("Sign in to {} again in Accounts", account.username)))?;
    let ms = ms_token(client_id, &[("grant_type", "refresh_token"), ("refresh_token", &refresh)])
        .await
        .map_err(|e| AppError::Other(format!("Your Microsoft sign-in for {} expired; sign in again in Accounts ({e})", account.username)))?;
    let refreshed = finish(&ms).await?;
    if refreshed.uuid != account.uuid {
        return Err(AppError::Other("That Microsoft account now has a different Minecraft profile; sign in again".into()));
    }
    cached(&account.uuid).ok_or_else(|| AppError::Other("Sign-in failed".into()))
}

pub fn forget(uuid: &str) {
    secrets::delete(uuid);
    if let Some(m) = TOKENS.lock().unwrap().as_mut() {
        m.remove(uuid);
    }
}

fn cached(uuid: &str) -> Option<String> {
    TOKENS.lock().unwrap().as_ref().and_then(|m| m.get(uuid)).map(|(t, _)| t.clone())
}

/// Steps 2-5 after a Microsoft token: caches the Minecraft token and saves the refresh token.
async fn finish(ms: &MsTokens) -> AppResult<Account> {
    let (xbl, _) = xbox_live(&ms.access_token).await?;
    let (xsts, uhs) = xsts(&xbl).await?;
    let (mc_token, expires_in) = minecraft_login(&uhs, &xsts).await?;
    let (id, name) = profile(&mc_token).await?;
    let uuid = hyphenate(&id);
    secrets::save_refresh_token(&uuid, &ms.refresh_token)?;
    TOKENS
        .lock()
        .unwrap()
        .get_or_insert_with(HashMap::new)
        .insert(uuid.clone(), (mc_token, Instant::now() + Duration::from_secs(expires_in)));
    Ok(Account { uuid, username: name, kind: AccountKind::Microsoft })
}

async fn ms_token(client_id: &str, params: &[(&str, &str)]) -> AppResult<MsTokens> {
    let mut form: Vec<(&str, &str)> = vec![("client_id", client_id), ("scope", SCOPE)];
    form.extend_from_slice(params);
    let res = http::client().post(TOKEN_URL).form(&form).send().await?;
    if !res.status().is_success() {
        let body: Value = res.json().await.unwrap_or(Value::Null);
        let reason = body["error_description"].as_str().or(body["error"].as_str()).unwrap_or("unknown error");
        return Err(AppError::Other(format!("Microsoft sign-in failed: {reason}")));
    }
    Ok(res.json().await?)
}

/// Returns (token, user hash).
async fn xbox_live(ms_access_token: &str) -> AppResult<(String, String)> {
    let body = json!({
        "Properties": { "AuthMethod": "RPS", "SiteName": "user.auth.xboxlive.com", "RpsTicket": ms_access_token },
        "RelyingParty": "http://auth.xboxlive.com",
        "TokenType": "JWT"
    });
    let res = http::client().post(XBL_URL).header("Accept", "application/json").json(&body).send().await?;
    if !res.status().is_success() {
        return Err(AppError::Other(format!("Xbox Live sign-in failed (HTTP {})", res.status().as_u16())));
    }
    xbox_token(res.json().await?)
}

/// Returns (token, user hash). Explains the common account problems.
async fn xsts(xbl_token: &str) -> AppResult<(String, String)> {
    let body = json!({
        "Properties": { "SandboxId": "RETAIL", "UserTokens": [xbl_token] },
        "RelyingParty": "rp://api.minecraftservices.com/",
        "TokenType": "JWT"
    });
    let res = http::client().post(XSTS_URL).header("Accept", "application/json").json(&body).send().await?;
    if res.status().as_u16() == 401 {
        let v: Value = res.json().await.unwrap_or(Value::Null);
        let msg = match v["XErr"].as_u64() {
            Some(2148916233) => "This Microsoft account has no Xbox profile. Sign in once at xbox.com to create one, then try again.",
            Some(2148916235) => "Xbox Live isn't available in this account's country.",
            Some(2148916236) | Some(2148916237) => "This account needs adult verification on xbox.com first.",
            Some(2148916238) => "This is a child account; an adult must add it to a Microsoft family first.",
            _ => "Xbox Live refused the sign-in.",
        };
        return Err(AppError::Other(msg.into()));
    }
    if !res.status().is_success() {
        return Err(AppError::Other(format!("Xbox sign-in failed (HTTP {})", res.status().as_u16())));
    }
    xbox_token(res.json().await?)
}

fn xbox_token(v: Value) -> AppResult<(String, String)> {
    let token = v["Token"].as_str().ok_or_else(|| AppError::Other("Xbox Live sent no token".into()))?;
    let uhs = v["DisplayClaims"]["xui"][0]["uhs"].as_str().ok_or_else(|| AppError::Other("Xbox Live sent no user hash".into()))?;
    Ok((token.to_string(), uhs.to_string()))
}

/// Returns (Minecraft access token, seconds until it expires).
async fn minecraft_login(uhs: &str, xsts: &str) -> AppResult<(String, u64)> {
    let body = json!({ "identityToken": format!("XBL3.0 x={uhs};{xsts}") });
    let res = http::client().post(MC_LOGIN_URL).json(&body).send().await?;
    if !res.status().is_success() {
        return Err(AppError::Other(format!("Minecraft services refused the sign-in (HTTP {})", res.status().as_u16())));
    }
    let v: Value = res.json().await?;
    let token = v["access_token"].as_str().ok_or_else(|| AppError::Other("Minecraft services sent no token".into()))?;
    Ok((token.to_string(), v["expires_in"].as_u64().unwrap_or(86400)))
}

/// Returns (UUID without hyphens, name).
async fn profile(mc_token: &str) -> AppResult<(String, String)> {
    let res = http::client().get(PROFILE_URL).bearer_auth(mc_token).send().await?;
    if res.status().as_u16() == 404 {
        return Err(AppError::Other(
            "This Microsoft account doesn't own Minecraft: Java Edition (or hasn't set a username yet at minecraft.net).".into(),
        ));
    }
    if !res.status().is_success() {
        return Err(AppError::Other(format!("Couldn't load the Minecraft profile (HTTP {})", res.status().as_u16())));
    }
    let v: Value = res.json().await?;
    match (v["id"].as_str(), v["name"].as_str()) {
        (Some(id), Some(name)) => Ok((id.to_string(), name.to_string())),
        _ => Err(AppError::Other("The Minecraft profile was incomplete".into())),
    }
}

fn hyphenate(id: &str) -> String {
    if id.len() != 32 {
        return id.to_string();
    }
    format!("{}-{}-{}-{}-{}", &id[0..8], &id[8..12], &id[12..16], &id[16..20], &id[20..])
}

fn enc(s: &str) -> String {
    s.bytes()
        .map(|b| match b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => (b as char).to_string(),
            _ => format!("%{b:02X}"),
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn hyphenates_uuids() {
        assert_eq!(hyphenate("069a79f444e94726a5befca90e38aaf5"), "069a79f4-44e9-4726-a5be-fca90e38aaf5");
    }

    #[test]
    fn builds_authorize_url() {
        let url = authorize_url("abc");
        assert!(url.starts_with("https://login.live.com/oauth20_authorize.srf?client_id=abc&response_type=code"));
        assert!(url.contains("scope=service%3A%3Auser.auth.xboxlive.com%3A%3AMBI_SSL"));
        assert!(url.contains("redirect_uri=https%3A%2F%2Flogin.live.com%2Foauth20_desktop.srf"));
    }
}
