//! Turns the game's output into log lines for the UI.
//!
//! With Mojang's log config, the game prints log4j XML events to stdout:
//! `<log4j:Event logger=".." timestamp=".." level="INFO" thread="..">` with the
//! message in a CDATA block. Anything else (stderr, old versions) is plain text.

use std::sync::OnceLock;

use regex::Regex;
use serde::Serialize;

#[derive(Debug, Clone, Serialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct LogLine {
    /// Unix time in ms, when known.
    pub time: Option<u64>,
    /// `INFO`, `WARN`, `ERROR`, `DEBUG`, `FATAL`, or empty when unknown.
    pub level: String,
    pub thread: Option<String>,
    pub message: String,
}

/// Collects multi-line log4j events from stdout.
#[derive(Default)]
pub struct Log4jParser {
    buf: Option<String>,
}

impl Log4jParser {
    /// Feeds one stdout line; returns a log line once one is complete.
    pub fn push(&mut self, line: &str) -> Option<LogLine> {
        if let Some(buf) = &mut self.buf {
            buf.push_str(line);
            buf.push('\n');
            if line.contains("</log4j:Event>") {
                let event = self.buf.take()?;
                return Some(parse_event(&event));
            }
            return None;
        }
        if line.trim_start().starts_with("<log4j:Event") {
            if line.contains("</log4j:Event>") {
                return Some(parse_event(line));
            }
            self.buf = Some(format!("{line}\n"));
            return None;
        }
        Some(plain(line))
    }
}

fn attr(s: &str, name: &str) -> Option<String> {
    let start = s.find(&format!(" {name}=\""))? + name.len() + 3;
    let len = s[start..].find('"')?;
    Some(unescape(&s[start..start + len]))
}

fn cdata(s: &str, tag: &str) -> Option<String> {
    let open = format!("<log4j:{tag}><![CDATA[");
    let start = s.find(&open)? + open.len();
    let len = s[start..].find("]]></log4j:")?;
    Some(s[start..start + len].trim_end().to_string())
}

fn unescape(s: &str) -> String {
    s.replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
}

fn parse_event(s: &str) -> LogLine {
    let mut message = cdata(s, "Message").unwrap_or_default();
    if let Some(trace) = cdata(s, "Throwable") {
        message.push('\n');
        message.push_str(&trace);
    }
    LogLine {
        time: attr(s, "timestamp").and_then(|t| t.parse().ok()),
        level: attr(s, "level").unwrap_or_default(),
        thread: attr(s, "thread"),
        message,
    }
}

/// A plain text line, e.g. `[12:00:00] [Render thread/INFO]: Hello`.
pub fn plain(line: &str) -> LogLine {
    static RE: OnceLock<Regex> = OnceLock::new();
    let re = RE.get_or_init(|| Regex::new(r"^\[[^\]]*\] \[([^\]/]*)/(INFO|WARN|ERROR|DEBUG|FATAL|TRACE)\]:? ?(.*)$").unwrap());
    match re.captures(line) {
        Some(c) => LogLine { time: None, level: c[2].to_string(), thread: Some(c[1].to_string()), message: c[3].to_string() },
        None => LogLine { time: None, level: String::new(), thread: None, message: line.to_string() },
    }
}

/// The crash report path from vanilla's
/// `#@!@# Game crashed! Crash report saved to: #@!@# <path>` line.
pub fn crash_report_path(message: &str) -> Option<String> {
    let rest = message.split("Crash report saved to:").nth(1)?;
    let path = rest.trim().trim_start_matches("#@!@#").trim();
    (!path.is_empty()).then(|| path.to_string())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_multiline_log4j_events() {
        let mut p = Log4jParser::default();
        assert_eq!(
            p.push(r#"<log4j:Event logger="ewm" timestamp="1700000000000" level="WARN" thread="Render thread">"#),
            None
        );
        assert_eq!(p.push("  <log4j:Message><![CDATA[Hello & <world>]]></log4j:Message>"), None);
        let line = p.push("</log4j:Event>").unwrap();
        assert_eq!(line.level, "WARN");
        assert_eq!(line.thread.as_deref(), Some("Render thread"));
        assert_eq!(line.time, Some(1_700_000_000_000));
        assert_eq!(line.message, "Hello & <world>");
    }

    #[test]
    fn parses_plain_lines() {
        let line = plain("[12:00:01] [Render thread/INFO]: Setting user: Steve");
        assert_eq!(line.level, "INFO");
        assert_eq!(line.message, "Setting user: Steve");
        assert_eq!(plain("random text").level, "");
    }

    #[test]
    fn finds_crash_report() {
        assert_eq!(
            crash_report_path(r"#@!@# Game crashed! Crash report saved to: #@!@# C:\mc\crash-reports\crash.txt").as_deref(),
            Some(r"C:\mc\crash-reports\crash.txt")
        );
        assert_eq!(crash_report_path("all good"), None);
    }
}
