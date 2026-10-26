//! Home page news: NovoMC's own announcements (bundled `news.json`) plus the
//! latest Minecraft release and snapshot from the version manifest. Text only;
//! the UI draws its own artwork (no Mojang assets).

use serde::{Deserialize, Serialize};

use crate::download::manifest::{self, VersionType};
use crate::error::{AppError, AppResult};

const BUILTIN: &str = include_str!("news.json");

/// Cards shown on Home.
const MAX_ITEMS: usize = 6;

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct NewsItem {
    pub id: String,
    pub title: String,
    pub body: String,
    /// ISO date (`2026-10-26`) or date-time.
    pub date: String,
    /// `novomc`, `release` or `snapshot`.
    pub tag: String,
    #[serde(default)]
    pub url: Option<String>,
}

fn builtin() -> Vec<NewsItem> {
    serde_json::from_str(BUILTIN).expect("bundled news.json is valid")
}

/// A card for a Minecraft version, linking to its Minecraft Wiki page.
fn version_item(id: &str, release_time: &str, snapshot: bool) -> NewsItem {
    NewsItem {
        id: format!("mc-{id}"),
        title: if snapshot { format!("Snapshot {id}") } else { format!("Minecraft {id} is out") },
        body: if snapshot {
            "A new test build of the next update. Try it in an instance with snapshots turned on in Versions.".into()
        } else {
            "The latest Java Edition release. Download it in Versions or create an instance with it.".into()
        },
        date: release_time.into(),
        tag: if snapshot { "snapshot" } else { "release" }.into(),
        url: Some(format!("https://minecraft.wiki/w/Java_Edition_{id}")),
    }
}

/// Newest first, capped at [`MAX_ITEMS`].
fn sorted(mut items: Vec<NewsItem>) -> Vec<NewsItem> {
    // ISO dates sort correctly as text.
    items.sort_by(|a, b| b.date.cmp(&a.date));
    items.truncate(MAX_ITEMS);
    items
}

#[tauri::command]
pub async fn get_news() -> AppResult<Vec<NewsItem>> {
    let mut items = builtin();
    // Offline is fine: the version cards just don't appear.
    if let Ok(m) = manifest::load(false).await {
        for (latest, snapshot) in [(&m.latest.release, false), (&m.latest.snapshot, true)] {
            if snapshot && latest == &m.latest.release {
                continue;
            }
            if let Some(v) = m.versions.iter().find(|v| &v.id == latest) {
                let is_snapshot = v.kind == VersionType::Snapshot;
                items.push(version_item(&v.id, &v.release_time, is_snapshot));
            }
        }
    }
    Ok(sorted(items))
}

/// Opens an https link in the default browser.
#[tauri::command]
pub fn open_url(url: String) -> AppResult<()> {
    if !url.starts_with("https://") || url.chars().any(|c| c.is_whitespace() || c == '"') {
        return Err(AppError::Other("Only https links can be opened".into()));
    }
    let program = if cfg!(windows) {
        "explorer"
    } else if cfg!(target_os = "macos") {
        "open"
    } else {
        "xdg-open"
    };
    std::process::Command::new(program).arg(&url).spawn()?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn builtin_news_parses_and_sorts() {
        let mut items = builtin();
        assert!(!items.is_empty());
        items.push(version_item("1.21.10", "2099-01-01T00:00:00+00:00", false));
        let s = sorted(items);
        assert_eq!(s[0].id, "mc-1.21.10");
        assert!(s.len() <= MAX_ITEMS);
        assert_eq!(s[0].url.as_deref(), Some("https://minecraft.wiki/w/Java_Edition_1.21.10"));
    }

    #[test]
    fn open_url_rejects_non_https() {
        assert!(open_url("file:///C:/Windows".into()).is_err());
        assert!(open_url("https://example.com\" & calc".into()).is_err());
    }
}
