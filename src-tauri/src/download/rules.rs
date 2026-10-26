//! Mojang's `rules` blocks, used by libraries and launch arguments to target
//! an OS, CPU architecture or launcher feature.

use std::collections::HashMap;

use regex::Regex;
use serde::Deserialize;

#[derive(Debug, Clone, Deserialize)]
pub struct Rule {
    pub action: RuleAction,
    pub os: Option<OsRule>,
    pub features: Option<HashMap<String, bool>>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum RuleAction {
    Allow,
    Disallow,
}

#[derive(Debug, Clone, Deserialize)]
pub struct OsRule {
    pub name: Option<String>,
    pub arch: Option<String>,
    /// Regex matched against the OS version (e.g. `^10\.` on Windows).
    pub version: Option<String>,
}

/// Launcher features some arguments depend on (`is_demo_user`,
/// `has_custom_resolution`, ...). Missing features count as `false`.
pub type Features = HashMap<String, bool>;

/// Mojang's name for the current OS: `windows`, `osx` or `linux`.
pub fn os_name() -> &'static str {
    if cfg!(target_os = "windows") {
        "windows"
    } else if cfg!(target_os = "macos") {
        "osx"
    } else {
        "linux"
    }
}

/// Mojang's name for the current CPU architecture.
pub fn os_arch() -> &'static str {
    match std::env::consts::ARCH {
        "x86" => "x86",
        "aarch64" => "arm64",
        other => other,
    }
}

/// `64` or `32`, substituted for `${arch}` in legacy native classifiers.
pub fn arch_bits() -> &'static str {
    if cfg!(target_pointer_width = "64") {
        "64"
    } else {
        "32"
    }
}

/// OS version string matched by `os.version` rules. Windows 10 and 11 both
/// report `10.0`; the version is only used for that check.
fn os_version() -> &'static str {
    if cfg!(target_os = "windows") {
        "10.0"
    } else {
        ""
    }
}

impl Rule {
    fn matches(&self, features: &Features) -> bool {
        if let Some(os) = &self.os {
            if os.name.as_deref().is_some_and(|n| n != os_name()) {
                return false;
            }
            if os.arch.as_deref().is_some_and(|a| a != os_arch()) {
                return false;
            }
            if let Some(pattern) = &os.version {
                let ok = Regex::new(pattern).map(|re| re.is_match(os_version())).unwrap_or(false);
                if !ok {
                    return false;
                }
            }
        }
        if let Some(wanted) = &self.features {
            for (name, value) in wanted {
                if features.get(name).copied().unwrap_or(false) != *value {
                    return false;
                }
            }
        }
        true
    }
}

/// Evaluates a rule list the way the vanilla launcher does: no rules means
/// allowed; otherwise the last matching rule decides, defaulting to disallow.
pub fn allowed(rules: Option<&[Rule]>, features: &Features) -> bool {
    let Some(rules) = rules.filter(|r| !r.is_empty()) else {
        return true;
    };
    let mut result = false;
    for rule in rules {
        if rule.matches(features) {
            result = rule.action == RuleAction::Allow;
        }
    }
    result
}

#[cfg(test)]
mod tests {
    use super::*;

    fn rules(json: &str) -> Vec<Rule> {
        serde_json::from_str(json).unwrap()
    }

    #[test]
    fn empty_rules_allow() {
        assert!(allowed(None, &Features::new()));
    }

    #[test]
    fn os_specific_rules() {
        let other_os = if os_name() == "osx" { "linux" } else { "osx" };
        let r = rules(&format!(r#"[{{"action":"allow"}},{{"action":"disallow","os":{{"name":"{other_os}"}}}}]"#));
        assert!(allowed(Some(&r), &Features::new()));
        let r = rules(&format!(r#"[{{"action":"allow","os":{{"name":"{other_os}"}}}}]"#));
        assert!(!allowed(Some(&r), &Features::new()));
    }

    #[test]
    fn feature_rules() {
        let r = rules(r#"[{"action":"allow","features":{"is_demo_user":true}}]"#);
        assert!(!allowed(Some(&r), &Features::new()));
        let f = Features::from([("is_demo_user".to_string(), true)]);
        assert!(allowed(Some(&r), &f));
    }
}
