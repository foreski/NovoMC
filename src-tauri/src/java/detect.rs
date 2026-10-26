//! Finds Java installations: JAVA_HOME, PATH, the usual install folders,
//! the official Minecraft launcher's runtimes, and NovoMC's own runtimes.

use std::collections::HashSet;
use std::env;
use std::path::{Path, PathBuf};
use std::process::Command;

use serde::Serialize;

use crate::paths;

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct JavaInstall {
    /// The Java binary to launch (`javaw.exe` on Windows).
    pub path: PathBuf,
    pub major: u32,
    pub version: String,
    pub arch: String,
    /// Downloaded and managed by NovoMC.
    pub managed: bool,
}

impl JavaInstall {
    pub fn is_64bit(&self) -> bool {
        matches!(self.arch.as_str(), "amd64" | "x86_64" | "aarch64" | "arm64")
    }
}

/// Binary NovoMC launches the game with. `javaw` avoids a console window on Windows.
const BIN: &str = if cfg!(windows) { "javaw.exe" } else { "java" };
const CONSOLE_BIN: &str = if cfg!(windows) { "java.exe" } else { "java" };

pub fn java_bin(home: &Path) -> PathBuf {
    home.join("bin").join(BIN)
}

/// Parses a Java version string (`1.8.0_392`, `17.0.8`, `21`) into its major version.
pub fn parse_major(version: &str) -> Option<u32> {
    let mut parts = version.split(['.', '_', '-', '+']);
    let first: u32 = parts.next()?.parse().ok()?;
    if first == 1 {
        parts.next()?.parse().ok()
    } else {
        Some(first)
    }
}

/// Folders whose subfolders are Java homes.
fn roots() -> Vec<PathBuf> {
    let mut roots = Vec::new();
    let home = dirs::home_dir();
    if let Ok(data) = paths::data_dir() {
        roots.push(data.join("runtimes"));
    }
    if cfg!(windows) {
        for var in ["ProgramFiles", "ProgramFiles(x86)", "ProgramW6432"] {
            if let Some(base) = env::var_os(var).map(PathBuf::from) {
                for vendor in [
                    "Java",
                    "Eclipse Adoptium",
                    "Eclipse Foundation",
                    "AdoptOpenJDK",
                    "Microsoft",
                    "Zulu",
                    "BellSoft",
                    "Amazon Corretto",
                    "Semeru",
                ] {
                    roots.push(base.join(vendor));
                }
            }
        }
    } else if cfg!(target_os = "macos") {
        roots.push("/Library/Java/JavaVirtualMachines".into());
        if let Some(h) = &home {
            roots.push(h.join("Library/Java/JavaVirtualMachines"));
        }
    } else {
        for dir in ["/usr/lib/jvm", "/usr/java", "/opt/java", "/opt/jdk"] {
            roots.push(dir.into());
        }
    }
    if let Some(h) = &home {
        roots.push(h.join(".jdks"));
    }
    roots
}

/// Java homes inside the official Minecraft launcher's runtime folder:
/// `runtime/<component>/<platform>/<component>`.
fn minecraft_launcher_homes() -> Vec<PathBuf> {
    let mut bases = Vec::new();
    if let Some(appdata) = dirs::config_dir() {
        bases.push(appdata.join(".minecraft").join("runtime"));
    }
    if let Some(local) = dirs::data_local_dir() {
        bases.push(
            local
                .join("Packages")
                .join("Microsoft.4297127D64EC6_8wekyb3d8bbwe")
                .join("LocalCache")
                .join("Local")
                .join("runtime"),
        );
    }
    let mut homes = Vec::new();
    for base in bases {
        for component in read_dirs(&base) {
            for platform in read_dirs(&component) {
                if let Some(name) = component.file_name() {
                    homes.push(platform.join(name));
                }
            }
        }
    }
    homes
}

fn read_dirs(dir: &Path) -> Vec<PathBuf> {
    std::fs::read_dir(dir)
        .map(|rd| rd.flatten().map(|e| e.path()).filter(|p| p.is_dir()).collect())
        .unwrap_or_default()
}

fn candidate_homes() -> Vec<PathBuf> {
    let mut homes = Vec::new();
    if let Some(h) = env::var_os("JAVA_HOME") {
        homes.push(PathBuf::from(h));
    }
    if let Some(path) = env::var_os("PATH") {
        for dir in env::split_paths(&path) {
            if dir.join(CONSOLE_BIN).is_file() {
                if let Some(home) = dir.parent() {
                    homes.push(home.to_path_buf());
                }
            }
        }
    }
    for root in roots() {
        for dir in read_dirs(&root) {
            homes.push(dir.join("Contents").join("Home"));
            homes.push(dir.join("jre.bundle").join("Contents").join("Home"));
            homes.push(dir);
        }
    }
    homes.extend(minecraft_launcher_homes());
    homes
}

/// Reads `JAVA_VERSION` and `OS_ARCH` from a Java home's `release` file.
fn read_release(home: &Path) -> Option<(String, String)> {
    let text = std::fs::read_to_string(home.join("release")).ok()?;
    let get = |key: &str| {
        text.lines()
            .find_map(|l| l.strip_prefix(key)?.strip_prefix('='))
            .map(|v| v.trim().trim_matches('"').to_string())
    };
    Some((get("JAVA_VERSION")?, get("OS_ARCH").unwrap_or_default()))
}

/// Asks the Java binary itself, for homes without a `release` file.
fn probe(home: &Path) -> Option<(String, String)> {
    let mut cmd = Command::new(home.join("bin").join(CONSOLE_BIN));
    cmd.args(["-XshowSettings:properties", "-version"]);
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        cmd.creation_flags(0x0800_0000); // CREATE_NO_WINDOW
    }
    let out = cmd.output().ok()?;
    let text = String::from_utf8_lossy(&out.stderr);
    let get = |key: &str| {
        text.lines()
            .find_map(|l| l.trim().strip_prefix(key)?.trim_start().strip_prefix('='))
            .map(|v| v.trim().to_string())
    };
    Some((get("java.version")?, get("os.arch").unwrap_or_default()))
}

fn inspect(home: &Path, managed_root: Option<&Path>) -> Option<JavaInstall> {
    let path = java_bin(home);
    if !path.is_file() {
        return None;
    }
    let (version, arch) = read_release(home).or_else(|| probe(home))?;
    Some(JavaInstall {
        major: parse_major(&version)?,
        managed: managed_root.is_some_and(|r| home.starts_with(r)),
        path,
        version,
        arch,
    })
}

/// All Java installations found, newest major first. Blocking; can take a
/// moment when a Java has to be probed.
pub fn find_all() -> Vec<JavaInstall> {
    let managed_root = paths::data_dir().ok().map(|d| d.join("runtimes"));
    let mut seen = HashSet::new();
    let mut found: Vec<JavaInstall> = candidate_homes()
        .into_iter()
        .filter_map(|h| h.canonicalize().ok())
        .filter(|h| seen.insert(h.clone()))
        .filter_map(|h| inspect(&h, managed_root.as_deref()))
        .collect();
    found.sort_by(|a, b| b.major.cmp(&a.major).then(a.path.cmp(&b.path)));
    found
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_major_versions() {
        assert_eq!(parse_major("1.8.0_392"), Some(8));
        assert_eq!(parse_major("17.0.8"), Some(17));
        assert_eq!(parse_major("21"), Some(21));
        assert_eq!(parse_major("21-ea"), Some(21));
        assert_eq!(parse_major("garbage"), None);
    }
}
