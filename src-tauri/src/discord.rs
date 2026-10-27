//! Discord Rich Presence: "In the launcher" or "Playing Minecraft 1.8.9 ·
//! Hypixel" with an elapsed timer. Talks to the local Discord app over IPC on
//! a background thread; reconnects when Discord starts later. Needs
//! `discordAppId` in novomc.config.json (a Discord application with a
//! "novomc" art asset) and the Settings toggle on.

use std::sync::mpsc::{channel, RecvTimeoutError, Sender};
use std::sync::Mutex;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use discord_rich_presence::{activity, DiscordIpc, DiscordIpcClient};
use tauri::AppHandle;

use crate::{config, settings};

#[derive(Debug, Clone, PartialEq)]
pub enum Presence {
    Launcher,
    Playing { version: String, server: Option<String>, since: i64 },
    Off,
}

static SENDER: Mutex<Option<Sender<Presence>>> = Mutex::new(None);

pub fn now() -> i64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_secs() as i64).unwrap_or(0)
}

/// Starts the presence thread (no-op without an app id).
pub fn start(app: &AppHandle) {
    let Some(id) = config::get(app).discord_app_id.clone().filter(|i| !i.trim().is_empty()) else {
        return;
    };
    let (tx, rx) = channel::<Presence>();
    *SENDER.lock().unwrap() = Some(tx);
    std::thread::spawn(move || {
        let mut client: Option<DiscordIpcClient> = None;
        let mut current = Presence::Launcher;
        let mut shown: Option<Presence> = None;
        loop {
            match rx.recv_timeout(Duration::from_secs(20)) {
                Ok(p) => current = p,
                Err(RecvTimeoutError::Timeout) => {}
                Err(RecvTimeoutError::Disconnected) => return,
            }
            let enabled = settings::load().map(|s| s.discord_presence).unwrap_or(true);
            let wanted = if enabled { current.clone() } else { Presence::Off };
            if shown.as_ref() == Some(&wanted) && client.is_some() {
                continue;
            }
            if wanted == Presence::Off {
                if let Some(mut c) = client.take() {
                    let _ = c.clear_activity();
                    let _ = c.close();
                }
                shown = Some(Presence::Off);
                continue;
            }
            if client.is_none() {
                // Discord not running yet: try again on the next tick.
                client = DiscordIpcClient::new(&id).ok().and_then(|mut c| c.connect().ok().map(|_| c));
            }
            if let Some(c) = client.as_mut() {
                if c.set_activity(build(&wanted)).is_ok() {
                    shown = Some(wanted);
                } else {
                    client = None;
                    shown = None;
                }
            }
        }
    });
    set(Presence::Launcher);
}

fn build(p: &Presence) -> activity::Activity<'_> {
    let assets = activity::Assets::new().large_image("novomc").large_text("NovoMC");
    match p {
        Presence::Playing { version, server, since } => {
            let a = activity::Activity::new()
                .details(version)
                .timestamps(activity::Timestamps::new().start(*since))
                .assets(assets);
            match server {
                Some(s) => a.state(s),
                None => a.state("Singleplayer"),
            }
        }
        _ => activity::Activity::new().details("In the launcher").assets(assets),
    }
}

/// Updates the presence (ignored when Rich Presence isn't set up).
pub fn set(p: Presence) {
    if let Some(tx) = SENDER.lock().unwrap().as_ref() {
        let _ = tx.send(p);
    }
}

/// A friendly name for a server: its favorites name, else guessed from the address.
pub fn server_name(address: &str) -> String {
    let wanted = address.split(':').next().unwrap_or(address).to_lowercase();
    if let Ok(saved) = crate::servers::list_servers() {
        if let Some(s) = saved.iter().find(|s| s.address.split(':').next().unwrap_or("").eq_ignore_ascii_case(&wanted)) {
            return s.name.clone();
        }
    }
    guess_name(address)
}

/// "mc.hypixel.net" -> "Hypixel", for servers that aren't in the favorites.
fn guess_name(address: &str) -> String {
    let host = address.split(':').next().unwrap_or(address).trim_end_matches('.').to_lowercase();
    let parts: Vec<&str> = host.split('.').filter(|p| !p.is_empty()).collect();
    let base = match parts.len() {
        0 => return address.to_string(),
        1 => parts[0],
        n => parts[n - 2],
    };
    let mut c = base.chars();
    match c.next() {
        Some(f) => f.to_uppercase().collect::<String>() + c.as_str(),
        None => address.to_string(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn names_servers() {
        assert_eq!(guess_name("mc.hypixel.net"), "Hypixel");
        assert_eq!(guess_name("play.example.org:25565"), "Example");
        assert_eq!(guess_name("localhost"), "Localhost");
    }
}
