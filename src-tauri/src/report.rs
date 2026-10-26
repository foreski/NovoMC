//! Bug reports: sent to NovoMC's relay (`bugReportUrl` in novomc.config.json),
//! which files them as GitHub issues. Crashes are reported automatically
//! (Settings → "Send crash reports"); players can also report bugs by hand.
//!
//! Everything is redacted before it leaves the PC: player names, the Windows
//! user folder, access tokens, emails and IP addresses.

use std::path::Path;

use regex::Regex;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use sha1::{Digest, Sha1};
use tauri::{AppHandle, Emitter};

use crate::download::http;
use crate::error::{AppError, AppResult};
use crate::{auth, config, paths};

/// What the player (or a crash) is reporting.
#[derive(Debug, Clone, Default, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BugReport {
    pub title: String,
    pub description: String,
    /// Instance the bug happened in; its latest log is attached.
    pub instance_id: Option<String>,
    #[serde(default)]
    pub include_logs: bool,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ReportResult {
    pub issue: Option<String>,
    pub duplicate: bool,
}

/// Replaces anything personal with placeholders.
pub fn redact(text: &str, names: &[String]) -> String {
    let mut out = text.to_string();
    if let Some(home) = dirs::home_dir() {
        let home = home.display().to_string();
        for form in [home.clone(), home.replace('\\', "/"), home.replace('\\', "\\\\")] {
            out = out.replace(&form, "<home>");
        }
    }
    let rules: [(&str, &str); 6] = [
        (r"(?i)(--accessToken\s+)\S+", "${1}<redacted>"),
        (r"token:[^:\s]+:", "token:<redacted>:"),
        (r"(?i)bearer\s+[\w\-.]+", "Bearer <redacted>"),
        (r"eyJ[\w-]{8,}\.[\w-]{8,}\.[\w-]{8,}", "<token>"),
        (r"[\w.+-]+@[\w-]+\.[\w.-]+", "<email>"),
        (r"\b(?:\d{1,3}\.){3}\d{1,3}\b", "<ip>"),
    ];
    for (pattern, with) in rules {
        out = Regex::new(pattern).unwrap().replace_all(&out, with).into_owned();
    }
    for name in names.iter().filter(|n| n.len() >= 3) {
        let re = Regex::new(&format!(r"(?i)\b{}\b", regex::escape(name))).unwrap();
        out = re.replace_all(&out, "<player>").into_owned();
    }
    out
}

/// A short id for "the same crash": the crash description, exception and top frames.
pub fn signature(crash: &str) -> String {
    let key: Vec<&str> = crash
        .lines()
        .map(str::trim)
        .filter(|l| l.starts_with("Description:") || l.contains("Exception") || l.contains("Error") || l.starts_with("at "))
        .take(5)
        .collect();
    let digest = Sha1::digest(key.join("\n").as_bytes());
    digest.iter().take(8).map(|b| format!("{b:02x}")).collect()
}

fn player_names() -> Vec<String> {
    auth::store::read().map(|s| s.accounts.into_iter().map(|a| a.username).collect()).unwrap_or_default()
}

fn os() -> String {
    format!("{} {}", sysinfo::System::name().unwrap_or_default(), sysinfo::System::os_version().unwrap_or_default())
}

/// The last `lines` lines of a file.
fn tail(path: &Path, lines: usize) -> String {
    let text = std::fs::read_to_string(path).unwrap_or_default();
    let all: Vec<&str> = text.lines().collect();
    all[all.len().saturating_sub(lines)..].join("\n")
}

async fn send(app: &AppHandle, payload: Value) -> AppResult<ReportResult> {
    let url = config::get(app)
        .bug_report_url
        .clone()
        .filter(|u| !u.trim().is_empty())
        .ok_or_else(|| AppError::Other("Bug reporting isn't set up in this build".into()))?;
    let res = http::client().post(&url).json(&payload).send().await?;
    let status = res.status();
    let v: Value = res.json().await.unwrap_or(Value::Null);
    if !status.is_success() {
        let msg = v["error"].as_str().unwrap_or("the report server refused it");
        return Err(AppError::Other(format!("Couldn't send the report: {msg}")));
    }
    Ok(ReportResult { issue: v["issue"].as_str().map(String::from), duplicate: v["duplicate"].as_bool().unwrap_or(false) })
}

fn instance_info(instance_id: Option<&str>) -> (String, String, Option<std::path::PathBuf>) {
    let root = match paths::data_dir() {
        Ok(r) => r,
        Err(_) => return (String::new(), String::new(), None),
    };
    match instance_id.and_then(|id| crate::instances::store::load(&root, id).ok()) {
        Some(inst) => {
            let loader = inst.loader_version.clone().map(|v| format!("Forge {v}")).unwrap_or_else(|| "Vanilla".into());
            let dir = crate::instances::store::game_dir(&root, &inst.id);
            (inst.version, loader, Some(dir))
        }
        None => (String::new(), String::new(), None),
    }
}

/// Sends a report written by the player.
#[tauri::command]
pub async fn report_bug(app: AppHandle, report: BugReport) -> AppResult<ReportResult> {
    let names = player_names();
    let (minecraft, loader, dir) = instance_info(report.instance_id.as_deref());
    let log = match (&dir, report.include_logs) {
        (Some(d), true) => redact(&tail(&d.join("logs").join("latest.log"), 300), &names),
        _ => String::new(),
    };
    send(
        &app,
        json!({
            "kind": "manual",
            "title": redact(report.title.trim(), &names),
            "description": redact(report.description.trim(), &names),
            "version": env!("CARGO_PKG_VERSION"),
            "os": os(),
            "minecraft": minecraft,
            "loader": loader,
            "log": log,
        }),
    )
    .await
}

/// Reports a game crash in the background; emits `bug-reported` with the result.
pub fn report_crash(app: AppHandle, instance_id: String, crash: String, log_lines: Vec<String>) {
    tauri::async_runtime::spawn(async move {
        let names = player_names();
        let (minecraft, loader, _) = instance_info(Some(&instance_id));
        let crash = redact(&crash, &names);
        let headline = crash
            .lines()
            .map(str::trim)
            .find(|l| l.starts_with("Description:"))
            .map(|l| l.trim_start_matches("Description:").trim().to_string())
            .filter(|d| !d.is_empty())
            .unwrap_or_else(|| "Game crashed".into());
        let exception = crash.lines().find(|l| l.contains("Exception") || l.contains("Error")).unwrap_or("").trim();
        let title = if exception.is_empty() { headline } else { format!("{headline}: {exception}") };
        let payload = json!({
            "kind": "crash",
            "title": title,
            "version": env!("CARGO_PKG_VERSION"),
            "os": os(),
            "minecraft": minecraft,
            "loader": loader,
            "crash": crash,
            "log": redact(&log_lines.join("\n"), &names),
            "signature": signature(&crash),
        });
        let result = send(&app, payload).await;
        let _ = app.emit("bug-reported", result.map_err(|e| e.to_string()));
    });
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn redacts_personal_info() {
        let names = vec!["NovoPlayer".to_string()];
        let text = "Setting user: NovoPlayer\n--accessToken abc.def --version 1.8.9\nmail me@site.com from 192.168.1.20\ntoken:secret123:uuid";
        let out = redact(text, &names);
        assert!(!out.contains("NovoPlayer") && out.contains("<player>"));
        assert!(!out.contains("abc.def") && out.contains("--accessToken <redacted>"));
        assert!(!out.contains("me@site.com") && !out.contains("192.168.1.20") && !out.contains("secret123"));
        assert!(out.contains("--version 1.8.9"), "version numbers aren't IPs");
    }

    #[test]
    fn same_crash_same_signature() {
        let a = "Description: Ticking entity\njava.lang.NullPointerException\n\tat a.b(C.java:1)\n\tat d.e(F.java:2)\nTime: 10:00";
        let b = "Description: Ticking entity\njava.lang.NullPointerException\n\tat a.b(C.java:1)\n\tat d.e(F.java:2)\nTime: 11:30";
        assert_eq!(signature(a), signature(b));
        assert_eq!(signature(a).len(), 16);
    }
}
