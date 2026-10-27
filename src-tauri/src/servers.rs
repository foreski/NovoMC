//! Favorite servers for Home's quick-join cards: stored in `servers.json`,
//! pinged with Minecraft's Server List Ping protocol for player counts and
//! latency, and joined directly with `--server`/`--port` at launch.

use std::time::{Duration, Instant};

use serde::{Deserialize, Serialize};
use serde_json::Value;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::TcpStream;

use crate::error::{AppError, AppResult};
use crate::paths;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct Server {
    pub name: String,
    /// `host` or `host:port`.
    pub address: String,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerStatus {
    pub online: u32,
    pub max: u32,
    pub latency_ms: u32,
    /// The server's description with § codes removed.
    pub motd: String,
}

fn defaults() -> Vec<Server> {
    [("Hypixel", "mc.hypixel.net"), ("PvP Land", "pvp.land"), ("Minemen Club", "minemen.club")]
        .into_iter()
        .map(|(name, address)| Server { name: name.into(), address: address.into() })
        .collect()
}

fn file() -> AppResult<std::path::PathBuf> {
    Ok(paths::data_dir()?.join("servers.json"))
}

#[tauri::command]
pub fn list_servers() -> AppResult<Vec<Server>> {
    match std::fs::read(file()?) {
        Ok(bytes) => Ok(serde_json::from_slice(&bytes)?),
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(defaults()),
        Err(e) => Err(e.into()),
    }
}

#[tauri::command]
pub fn save_servers(servers: Vec<Server>) -> AppResult<Vec<Server>> {
    for s in &servers {
        if s.name.trim().is_empty() || split(&s.address).is_none() {
            return Err(AppError::Other(format!("\"{}\" isn't a valid server address", s.address)));
        }
    }
    std::fs::write(file()?, serde_json::to_vec_pretty(&servers)?)?;
    Ok(servers)
}

/// `host[:port]` -> (host, port), port defaulting to 25565.
pub fn split(address: &str) -> Option<(String, u16)> {
    let a = address.trim();
    if a.is_empty() || a.contains(char::is_whitespace) || a.contains('/') {
        return None;
    }
    match a.rsplit_once(':') {
        Some((host, port)) if !host.is_empty() => Some((host.to_string(), port.parse().ok()?)),
        Some(_) => None,
        None => Some((a.to_string(), 25565)),
    }
}

fn varint(mut v: i32, out: &mut Vec<u8>) {
    loop {
        let mut b = (v & 0x7F) as u8;
        v = ((v as u32) >> 7) as i32;
        if v != 0 {
            b |= 0x80;
        }
        out.push(b);
        if v == 0 {
            break;
        }
    }
}

async fn read_varint(s: &mut TcpStream) -> AppResult<i32> {
    let mut value = 0i32;
    for i in 0..5 {
        let b = s.read_u8().await?;
        value |= ((b & 0x7F) as i32) << (7 * i);
        if b & 0x80 == 0 {
            return Ok(value);
        }
    }
    Err(AppError::Other("Bad response from server".into()))
}

fn packet(id: i32, body: &[u8]) -> Vec<u8> {
    let mut inner = Vec::new();
    varint(id, &mut inner);
    inner.extend_from_slice(body);
    let mut out = Vec::new();
    varint(inner.len() as i32, &mut out);
    out.extend(inner);
    out
}

fn plain_text(v: &Value) -> String {
    let mut out = String::new();
    match v {
        Value::String(s) => out.push_str(s),
        Value::Object(o) => {
            if let Some(t) = o.get("text").and_then(Value::as_str) {
                out.push_str(t);
            }
            if let Some(Value::Array(extra)) = o.get("extra") {
                for e in extra {
                    out.push_str(&plain_text(e));
                }
            }
        }
        Value::Array(a) => a.iter().for_each(|e| out.push_str(&plain_text(e))),
        _ => {}
    }
    let mut clean = String::new();
    let mut chars = out.chars();
    while let Some(c) = chars.next() {
        if c == '§' {
            chars.next();
        } else {
            clean.push(c);
        }
    }
    clean.split_whitespace().collect::<Vec<_>>().join(" ")
}

async fn ping_inner(address: &str) -> AppResult<ServerStatus> {
    let (host, port) = split(address).ok_or_else(|| AppError::Other("Invalid address".into()))?;
    let start = Instant::now();
    let mut s = TcpStream::connect((host.as_str(), port)).await?;
    let latency_ms = start.elapsed().as_millis() as u32;
    // Handshake (protocol 47 = 1.8), next state 1 (status), then a status request.
    let mut hs = Vec::new();
    varint(47, &mut hs);
    varint(host.len() as i32, &mut hs);
    hs.extend_from_slice(host.as_bytes());
    hs.extend_from_slice(&port.to_be_bytes());
    varint(1, &mut hs);
    s.write_all(&packet(0, &hs)).await?;
    s.write_all(&packet(0, &[])).await?;
    let _len = read_varint(&mut s).await?;
    let _id = read_varint(&mut s).await?;
    let n = read_varint(&mut s).await?;
    if !(0..=1 << 20).contains(&n) {
        return Err(AppError::Other("Bad response from server".into()));
    }
    let mut json = vec![0u8; n as usize];
    s.read_exact(&mut json).await?;
    let v: Value = serde_json::from_slice(&json)?;
    Ok(ServerStatus {
        online: v["players"]["online"].as_u64().unwrap_or(0) as u32,
        max: v["players"]["max"].as_u64().unwrap_or(0) as u32,
        latency_ms,
        motd: plain_text(&v["description"]),
    })
}

/// Player count, latency and description, or an error if the server is down.
#[tauri::command]
pub async fn ping_server(address: String) -> AppResult<ServerStatus> {
    tokio::time::timeout(Duration::from_secs(5), ping_inner(&address))
        .await
        .map_err(|_| AppError::Other("Server didn't answer".into()))?
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn splits_addresses() {
        assert_eq!(split("mc.hypixel.net"), Some(("mc.hypixel.net".into(), 25565)));
        assert_eq!(split("play.example.org:25566"), Some(("play.example.org".into(), 25566)));
        assert_eq!(split("bad host"), None);
        assert_eq!(split(":25565"), None);
        assert_eq!(split("host:notaport"), None);
    }

    #[test]
    fn strips_motd_formatting() {
        let v: Value = serde_json::from_str(r#"{"text":"§aHypixel ","extra":[{"text":"§c[1.8-1.21]"}]}"#).unwrap();
        assert_eq!(plain_text(&v), "Hypixel [1.8-1.21]");
    }

    #[test]
    fn encodes_varints() {
        let mut out = Vec::new();
        varint(300, &mut out);
        assert_eq!(out, vec![0xAC, 0x02]);
    }

    #[tokio::test]
    #[ignore = "pings Hypixel"]
    async fn pings_a_real_server() {
        let s = ping_server("mc.hypixel.net".into()).await.unwrap();
        assert!(s.max > 0);
    }
}
