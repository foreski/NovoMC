//! The server-list ping: what the multiplayer screen shows before you join.

use std::io::{self, Cursor};
use std::net::{TcpStream, ToSocketAddrs};
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

use novo_core::{read_i64, read_string, write_i64, write_string, write_u16, write_varint};
use serde_json::Value;

use crate::framing::{read_packet, write_packet};

/// The status handler does not validate this, so an unknown version is the same
/// trick every ping tool uses: ask, do not pretend to be a specific release.
pub const PROTOCOL_AUTO: i32 = -1;

#[derive(Debug, Clone, Default)]
pub struct Status {
    pub version_name: Option<String>,
    pub protocol: Option<i32>,
    pub players_online: Option<u32>,
    pub players_max: Option<u32>,
    pub sample: Vec<String>,
    /// MOTD with the chat components flattened to plain text.
    pub motd: String,
    /// The favicon is a base64 PNG data URL, often 10 KiB; its size is enough to
    /// report, and keeping it out of `Status` avoids copying it around.
    pub favicon_bytes: Option<usize>,
    pub latency: Option<Duration>,
    pub raw: String,
}

impl Status {
    pub fn parse_json(text: &str) -> io::Result<Status> {
        let value: Value = serde_json::from_str(text).map_err(|err| {
            io::Error::new(
                io::ErrorKind::InvalidData,
                format!("status is not JSON: {err}"),
            )
        })?;

        let version = value.get("version");
        let players = value.get("players");
        let mut sample = Vec::new();
        if let Some(entries) = players
            .and_then(|p| p.get("sample"))
            .and_then(Value::as_array)
        {
            for entry in entries {
                if let Some(name) = entry.get("name").and_then(Value::as_str) {
                    sample.push(name.to_string());
                }
            }
        }

        let mut motd = String::new();
        if let Some(description) = value.get("description") {
            flatten_chat(description, &mut motd);
        }

        Ok(Status {
            version_name: version
                .and_then(|v| v.get("name"))
                .and_then(Value::as_str)
                .map(str::to_owned),
            protocol: version
                .and_then(|v| v.get("protocol"))
                .and_then(Value::as_i64)
                .map(|v| v as i32),
            players_online: players
                .and_then(|p| p.get("online"))
                .and_then(Value::as_u64)
                .map(|v| v as u32),
            players_max: players
                .and_then(|p| p.get("max"))
                .and_then(Value::as_u64)
                .map(|v| v as u32),
            sample,
            motd,
            favicon_bytes: value.get("favicon").and_then(Value::as_str).map(str::len),
            latency: None,
            raw: text.to_string(),
        })
    }
}

/// Flatten a chat component: `text` then `extra`, with `translate` as a
/// fallback label. Formatting is discarded, because the terminal is not a
/// chat window.
fn flatten_chat(value: &Value, out: &mut String) {
    match value {
        Value::String(text) => out.push_str(text),
        Value::Array(items) => {
            for item in items {
                flatten_chat(item, out);
            }
        }
        Value::Object(map) => {
            match map.get("text") {
                Some(Value::String(text)) => out.push_str(text),
                Some(other) => flatten_chat(other, out),
                None => {
                    if let Some(Value::String(key)) = map.get("translate") {
                        out.push_str(key);
                    }
                }
            }
            if let Some(extra) = map.get("extra") {
                flatten_chat(extra, out);
            }
        }
        Value::Number(number) => out.push_str(&number.to_string()),
        _ => {}
    }
}

/// Split `host`, `host:port` or `[v6]:port` into an address and port. A bare
/// host gets 25565, the default Java port.
pub fn split_address(input: &str) -> (String, u16) {
    let input = input.trim();
    if let Some(rest) = input.strip_prefix('[')
        && let Some((host, tail)) = rest.split_once(']')
    {
        let port = tail
            .strip_prefix(':')
            .and_then(|port| port.parse().ok())
            .unwrap_or(25565);
        return (host.to_string(), port);
    }
    match input.rsplit_once(':') {
        Some((host, port)) if port.chars().all(|c| c.is_ascii_digit()) && !host.is_empty() => {
            (host.to_string(), port.parse().unwrap_or(25565))
        }
        _ => (input.to_string(), 25565),
    }
}

/// Ping a server and return what the multiplayer list would show.
pub fn ping(host: &str, port: u16, timeout: Duration) -> io::Result<Status> {
    let address = (host, port).to_socket_addrs()?.next().ok_or_else(|| {
        io::Error::new(
            io::ErrorKind::InvalidInput,
            format!("{host}:{port} did not resolve"),
        )
    })?;
    ping_address(address, host, port, timeout)
}

fn ping_address(
    address: std::net::SocketAddr,
    handshake_host: &str,
    port: u16,
    timeout: Duration,
) -> io::Result<Status> {
    let mut stream = TcpStream::connect_timeout(&address, timeout)?;
    stream.set_read_timeout(Some(timeout))?;
    stream.set_write_timeout(Some(timeout))?;
    stream.set_nodelay(true)?;

    // Handshake, next state 1 (status).
    let mut handshake = Vec::new();
    write_varint(&mut handshake, PROTOCOL_AUTO)?;
    write_string(&mut handshake, handshake_host)?;
    write_u16(&mut handshake, port)?;
    write_varint(&mut handshake, 1)?;
    write_packet(&mut stream, 0x00, &handshake)?;

    // Status request: id 0, empty body.
    write_packet(&mut stream, 0x00, &[])?;

    let (id, body) = read_packet(&mut stream)?;
    if id != 0x00 {
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            format!("expected a status response (0), got packet {id}"),
        ));
    }
    let json = read_string(&mut Cursor::new(body))?;
    let mut status = Status::parse_json(&json)?;

    // Ping/pong gives a latency figure and proves the socket is still alive.
    let nonce = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|since| since.as_nanos() as i64)
        .unwrap_or(0);
    let started = Instant::now();
    let mut payload = Vec::new();
    write_i64(&mut payload, nonce)?;
    write_packet(&mut stream, 0x01, &payload)?;
    let (pong_id, pong_body) = read_packet(&mut stream)?;
    if pong_id == 0x01 && pong_body.len() >= 8 && read_i64(&mut Cursor::new(pong_body))? == nonce {
        status.latency = Some(started.elapsed());
    }

    Ok(status)
}

/// Read a big-endian u16 and a VarInt from a socket, exposed for the tests that
/// check the handshake we actually send.
#[cfg(test)]
fn read_handshake(socket: &mut TcpStream) -> io::Result<(i32, String, u16, i32)> {
    let (_id, body) = read_packet(socket)?;
    let mut cursor = Cursor::new(body);
    let protocol = novo_core::read_varint(&mut cursor)?;
    let host = read_string(&mut cursor)?;
    let port = novo_core::read_u16(&mut cursor)?;
    let next_state = novo_core::read_varint(&mut cursor)?;
    Ok((protocol, host, port, next_state))
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::net::TcpListener;

    const RESPONSE: &str = r#"{
        "version": {"name": "26.3", "protocol": 4440},
        "players": {
            "max": 100,
            "online": 7,
            "sample": [{"name": "Buffy", "id": "069a79f4-44e9-4726-a5be-fca90e38aaf5"}]
        },
        "description": {"text": "A ", "extra": [{"text": "Novo", "color": "gold"}, {"text": " server"}]},
        "favicon": "data:image/png;base64,iVBORw0KGgo="
    }"#;

    #[test]
    fn parses_a_realistic_response() {
        let status = Status::parse_json(RESPONSE).unwrap();
        assert_eq!(status.version_name.as_deref(), Some("26.3"));
        assert_eq!(status.protocol, Some(4440));
        assert_eq!(status.players_online, Some(7));
        assert_eq!(status.players_max, Some(100));
        assert_eq!(status.sample, vec!["Buffy"]);
        assert_eq!(status.motd, "A Novo server");
        assert_eq!(
            status.favicon_bytes,
            Some("data:image/png;base64,iVBORw0KGgo=".len())
        );
    }

    #[test]
    fn parses_a_plain_string_description() {
        let status = Status::parse_json(r#"{"description":"§aHello §rworld"}"#).unwrap();
        assert_eq!(status.motd, "§aHello §rworld");
        assert_eq!(status.version_name, None);
        assert_eq!(status.players_online, None);
    }

    #[test]
    fn a_non_json_response_is_an_error() {
        assert!(Status::parse_json("<html>nope</html>").is_err());
    }

    #[test]
    fn splits_addresses() {
        assert_eq!(
            split_address("mc.hypixel.net"),
            ("mc.hypixel.net".into(), 25565)
        );
        assert_eq!(
            split_address("mc.hypixel.net:25566"),
            ("mc.hypixel.net".into(), 25566)
        );
        assert_eq!(
            split_address("  localhost:25570 "),
            ("localhost".into(), 25570)
        );
        assert_eq!(split_address("[::1]:25565"), ("::1".into(), 25565));
        assert_eq!(split_address("[::1]"), ("::1".into(), 25565));
        assert_eq!(
            split_address("play.example.com"),
            ("play.example.com".into(), 25565)
        );
    }

    #[test]
    fn pings_a_local_server() {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let port = listener.local_addr().unwrap().port();

        let server = std::thread::spawn(move || {
            let (mut socket, _) = listener.accept().unwrap();
            let (protocol, host, handshake_port, next_state) = read_handshake(&mut socket).unwrap();
            assert_eq!(protocol, PROTOCOL_AUTO);
            assert_eq!(host, "127.0.0.1");
            assert_eq!(handshake_port, port);
            assert_eq!(next_state, 1, "status state");

            let (id, body) = read_packet(&mut socket).unwrap();
            assert_eq!((id, body.len()), (0, 0));

            let mut payload = Vec::new();
            write_string(&mut payload, RESPONSE).unwrap();
            write_packet(&mut socket, 0, &payload).unwrap();

            // Echo the ping nonce back as a pong.
            let (id, body) = read_packet(&mut socket).unwrap();
            assert_eq!(id, 1);
            write_packet(&mut socket, 1, &body).unwrap();
        });

        let status = ping("127.0.0.1", port, Duration::from_secs(5)).unwrap();
        assert_eq!(status.version_name.as_deref(), Some("26.3"));
        assert_eq!(status.players_online, Some(7));
        assert_eq!(status.motd, "A Novo server");
        assert!(status.latency.is_some(), "pong should be timed");
        server.join().unwrap();
    }

    #[test]
    fn a_server_that_hangs_up_is_an_error_not_a_hang() {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let port = listener.local_addr().unwrap().port();
        let server = std::thread::spawn(move || {
            let (socket, _) = listener.accept().unwrap();
            drop(socket);
        });
        let err = ping("127.0.0.1", port, Duration::from_secs(2)).unwrap_err();
        assert!(
            matches!(
                err.kind(),
                io::ErrorKind::UnexpectedEof
                    | io::ErrorKind::ConnectionReset
                    | io::ErrorKind::BrokenPipe
            ),
            "unexpected error {err:?}"
        );
        server.join().unwrap();
    }
}
