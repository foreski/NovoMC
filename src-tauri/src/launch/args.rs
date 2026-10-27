//! Builds the Java command line from a version JSON.
//!
//! Handles the modern `arguments` format (1.13+, with rules) and the legacy
//! `minecraftArguments` string, then fills in `${placeholders}`.

use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::OnceLock;

use regex::Regex;

use crate::auth::account::Account;
use crate::download::rules::{self, Features};
use crate::download::version::{maven_path, ArgValue, Argument, VersionJson};
use crate::settings::Resolution;

/// Everything the arguments depend on.
pub struct LaunchContext<'a> {
    pub version: &'a VersionJson,
    pub account: &'a Account,
    /// Minecraft access token for Microsoft accounts; None launches offline.
    pub access_token: Option<&'a str>,
    pub root: &'a Path,
    pub game_dir: &'a Path,
    pub natives_dir: &'a Path,
    /// Where pre-1.7 versions read assets by name, if different from `assets/`.
    pub game_assets: &'a Path,
    pub memory_mb: u32,
    /// Add [`PERFORMANCE_FLAGS`] and a fixed-size heap.
    pub optimized_flags: bool,
    /// The instance's own JVM arguments; placed after NovoMC's so they win.
    pub extra_jvm_args: &'a [String],
    /// Game window size, if not Minecraft's default.
    pub resolution: Option<Resolution>,
    pub fullscreen: bool,
    /// Join this server (`host[:port]`) as soon as the game starts.
    pub server: Option<&'a str>,
}

/// GC tuning for a smooth client: short, predictable G1 pauses instead of
/// stutters. Mojang's launcher defaults plus Aikar's low-pause G1 settings;
/// every flag is accepted by Java 8 through 21+.
pub const PERFORMANCE_FLAGS: &[&str] = &[
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+UseG1GC",
    "-XX:MaxGCPauseMillis=50",
    "-XX:G1NewSizePercent=20",
    "-XX:G1ReservePercent=20",
    "-XX:G1HeapRegionSize=32M",
    "-XX:G1MixedGCCountTarget=4",
    "-XX:G1MixedGCLiveThresholdPercent=90",
    "-XX:G1RSetUpdatingPauseTimePercent=5",
    "-XX:SurvivorRatio=32",
    "-XX:MaxTenuringThreshold=1",
    "-XX:+ParallelRefProcEnabled",
    // Mods calling System.gc() cause full-GC freezes.
    "-XX:+DisableExplicitGC",
    // Avoids disk writes for JVM stats, a source of random hitches.
    "-XX:+PerfDisableSharedMem",
];

/// Jars on the classpath: every library artifact for this OS, then the client jar.
/// Legacy native-only libraries are extracted instead (see `natives`).
pub fn classpath(root: &Path, v: &VersionJson) -> Vec<PathBuf> {
    let libs = root.join("libraries");
    let mut seen = std::collections::HashSet::new();
    let mut out: Vec<PathBuf> = v
        .libraries
        .iter()
        .filter(|l| l.applies())
        .filter_map(|lib| {
            let rel = match lib.downloads.as_ref() {
                Some(d) => {
                    let artifact = d.artifact.as_ref()?;
                    artifact.path.clone().or_else(|| maven_path(&lib.name))?
                }
                None => maven_path(&lib.name)?,
            };
            Some(libs.join(rel))
        })
        .filter(|p| seen.insert(p.clone()))
        .collect();
    let jar = v.jar_id();
    out.push(root.join("versions").join(jar).join(format!("{jar}.jar")));
    out
}

fn placeholder() -> &'static Regex {
    static RE: OnceLock<Regex> = OnceLock::new();
    RE.get_or_init(|| Regex::new(r"\$\{([A-Za-z0-9_]+)\}").unwrap())
}

/// Replaces `${name}` with its value; unknown placeholders are left as-is.
fn substitute(arg: &str, vars: &HashMap<&str, String>) -> String {
    placeholder()
        .replace_all(arg, |c: &regex::Captures| vars.get(&c[1]).cloned().unwrap_or_else(|| c[0].to_string()))
        .into_owned()
}

/// Flattens modern arguments, keeping only those whose rules allow them.
fn flatten(args: &[Argument], features: &Features) -> Vec<String> {
    let mut out = Vec::new();
    for arg in args {
        match arg {
            Argument::Plain(s) => out.push(s.clone()),
            Argument::Conditional { rules: r, value } if rules::allowed(Some(r), features) => match value {
                ArgValue::One(s) => out.push(s.clone()),
                ArgValue::Many(v) => out.extend(v.iter().cloned()),
            },
            Argument::Conditional { .. } => {}
        }
    }
    out
}

fn path_str(p: &Path) -> String {
    p.display().to_string()
}

/// The full argument list after the Java binary.
pub fn build(ctx: &LaunchContext) -> Vec<String> {
    let v = ctx.version;
    let sep = if cfg!(windows) { ";" } else { ":" };
    let classpath = classpath(ctx.root, v).iter().map(|p| path_str(p)).collect::<Vec<_>>().join(sep);
    let assets_index = v.asset_index.as_ref().map(|a| a.id.clone()).or_else(|| v.assets.clone()).unwrap_or_default();

    let mut vars: HashMap<&str, String> = HashMap::from([
        ("auth_player_name", ctx.account.username.clone()),
        ("auth_uuid", ctx.account.uuid.replace('-', "")),
        // Offline accounts have no real token; the game only needs a non-empty value.
        ("auth_access_token", ctx.access_token.unwrap_or("0").to_string()),
        ("auth_session", ctx.access_token.map(|t| format!("token:{t}:{}", ctx.account.uuid.replace('-', ""))).unwrap_or_else(|| "0".into())),
        ("auth_xuid", "0".into()),
        ("clientid", "0".into()),
        ("user_type", if ctx.access_token.is_some() { "msa" } else { "legacy" }.into()),
        ("user_properties", "{}".into()),
        ("version_name", v.id.clone()),
        ("version_type", v.kind.clone().unwrap_or_else(|| "release".into())),
        ("game_directory", path_str(ctx.game_dir)),
        ("assets_root", path_str(&ctx.root.join("assets"))),
        ("game_assets", path_str(ctx.game_assets)),
        ("assets_index_name", assets_index),
        ("natives_directory", path_str(ctx.natives_dir)),
        ("library_directory", path_str(&ctx.root.join("libraries"))),
        ("classpath", classpath),
        ("classpath_separator", sep.into()),
        ("launcher_name", "NovoMC".into()),
        ("launcher_version", env!("CARGO_PKG_VERSION").into()),
    ]);

    let mut features = Features::new();
    if let Some(r) = ctx.resolution {
        features.insert("has_custom_resolution".into(), true);
        vars.insert("resolution_width", r.width.to_string());
        vars.insert("resolution_height", r.height.to_string());
    }
    let (jvm, mut game) = match &v.arguments {
        Some(a) if !a.jvm.is_empty() => (flatten(&a.jvm, &features), flatten(&a.game, &features)),
        _ => {
            let mut jvm = Vec::new();
            if cfg!(windows) {
                // Vanilla adds this for old versions to dodge an Intel driver bug.
                jvm.push("-XX:HeapDumpPath=MojangTricksIntelDriversForPerformance_javaw.exe_minecraft.exe.heapdump".into());
            }
            jvm.extend(["-Djava.library.path=${natives_directory}".into(), "-cp".into(), "${classpath}".into()]);
            let mut game: Vec<String> = v
                .minecraft_arguments
                .as_deref()
                .unwrap_or_default()
                .split_whitespace()
                .map(String::from)
                .collect();
            // Legacy argument strings never include the size; the game still accepts it.
            if ctx.resolution.is_some() {
                game.extend(["--width", "${resolution_width}", "--height", "${resolution_height}"].map(String::from));
            }
            (jvm, game)
        }
    };
    if ctx.fullscreen {
        game.push("--fullscreen".into());
    }
    if let Some((host, port)) = ctx.server.and_then(crate::servers::split) {
        game.extend(["--server".into(), host, "--port".into(), port.to_string()]);
    }

    // A fixed-size heap avoids resize pauses; the OS only backs pages as they're used.
    let min_heap = if ctx.optimized_flags { ctx.memory_mb } else { ctx.memory_mb.min(512) };
    let mut out = vec![format!("-Xmx{}M", ctx.memory_mb), format!("-Xms{min_heap}M")];
    if ctx.optimized_flags {
        out.extend(PERFORMANCE_FLAGS.iter().map(|f| f.to_string()));
    }
    out.extend(jvm.iter().map(|a| substitute(a, &vars)));
    out.extend(ctx.extra_jvm_args.iter().cloned());
    // The log config also patches Log4Shell on old versions.
    if let Some(client) = v.logging.as_ref().and_then(|l| l.client.as_ref()) {
        let path = ctx.root.join("assets").join("log_configs").join(&client.file.id);
        out.push(client.argument.replace("${path}", &path_str(&path)));
    }
    out.push(v.main_class.clone());
    out.extend(game.iter().map(|a| substitute(a, &vars)));
    out
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::auth::account::AccountKind;

    fn account() -> Account {
        Account { uuid: "b50ad385-829d-3141-a216-7e7d7539ba7f".into(), username: "Notch".into(), kind: AccountKind::Offline }
    }

    fn build_for(json: &str) -> Vec<String> {
        let v: VersionJson = serde_json::from_str(json).unwrap();
        let root = Path::new("/data");
        build(&LaunchContext {
            version: &v,
            account: &account(),
            access_token: None,
            root,
            game_dir: Path::new("/game"),
            natives_dir: Path::new("/natives"),
            game_assets: Path::new("/data/assets"),
            memory_mb: 2048,
            optimized_flags: true,
            extra_jvm_args: &["-XX:MaxGCPauseMillis=20".into()],
            resolution: Some(Resolution { width: 1280, height: 720 }),
            fullscreen: false,
            server: None,
        })
    }

    #[test]
    fn joins_server_on_start() {
        let v: VersionJson = serde_json::from_str(r#"{"id":"1.8.9","mainClass":"Main","minecraftArguments":"--username x"}"#).unwrap();
        let args = build(&LaunchContext {
            version: &v,
            account: &account(),
            access_token: Some("tok"),
            root: Path::new("/data"),
            game_dir: Path::new("/game"),
            natives_dir: Path::new("/natives"),
            game_assets: Path::new("/data/assets"),
            memory_mb: 2048,
            optimized_flags: false,
            extra_jvm_args: &[],
            resolution: None,
            fullscreen: false,
            server: Some("mc.hypixel.net"),
        });
        assert!(args.ends_with(&["--server".into(), "mc.hypixel.net".into(), "--port".into(), "25565".into()]));
    }

    #[test]
    fn resolution_for_both_formats() {
        let legacy = build_for(r#"{"id":"1.8.9","mainClass":"Main","minecraftArguments":"--username ${auth_player_name}"}"#);
        assert!(legacy.ends_with(&["--width".into(), "1280".into(), "--height".into(), "720".into()]));
        let modern = build_for(
            r#"{"id":"1.21","mainClass":"Main","arguments":{"jvm":["-cp","${classpath}"],"game":[
              {"rules":[{"action":"allow","features":{"has_custom_resolution":true}}],
               "value":["--width","${resolution_width}","--height","${resolution_height}"]}]}}"#,
        );
        assert!(modern.ends_with(&["--width".into(), "1280".into(), "--height".into(), "720".into()]));
        assert_eq!(modern.iter().filter(|a| *a == "--width").count(), 1);
    }

    #[test]
    fn instance_jvm_args_override_defaults() {
        let args = build_for(r#"{"id":"1.8.9","mainClass":"Main","minecraftArguments":""}"#);
        let ours = args.iter().position(|a| a == "-XX:MaxGCPauseMillis=50").unwrap();
        let theirs = args.iter().position(|a| a == "-XX:MaxGCPauseMillis=20").unwrap();
        let main = args.iter().position(|a| a == "Main").unwrap();
        assert!(ours < theirs && theirs < main);
    }

    #[test]
    fn performance_flags_come_before_main_class() {
        let args = build_for(r#"{"id":"1.21","mainClass":"net.minecraft.client.main.Main","minecraftArguments":""}"#);
        assert_eq!(args[..2], ["-Xmx2048M", "-Xms2048M"]);
        let main = args.iter().position(|a| a == "net.minecraft.client.main.Main").unwrap();
        for flag in PERFORMANCE_FLAGS {
            let i = args.iter().position(|a| a == flag).unwrap();
            assert!(i < main, "{flag} must be a JVM argument");
        }
    }

    #[test]
    fn modern_arguments() {
        let args = build_for(
            r#"{"id":"1.21","type":"release","mainClass":"net.minecraft.client.main.Main",
            "assetIndex":{"id":"17","sha1":"x","size":1,"url":"u"},
            "arguments":{
              "game":["--username","${auth_player_name}","--uuid","${auth_uuid}",
                {"rules":[{"action":"allow","features":{"is_demo_user":true}}],"value":"--demo"}],
              "jvm":["-Djava.library.path=${natives_directory}","-cp","${classpath}"]}}"#,
        );
        assert_eq!(args[0], "-Xmx2048M");
        let main = args.iter().position(|a| a == "net.minecraft.client.main.Main").unwrap();
        assert_eq!(args[main + 1..], ["--username", "Notch", "--uuid", "b50ad385829d3141a2167e7d7539ba7f"]);
        assert!(args.iter().any(|a| a == &format!("-Djava.library.path={}", Path::new("/natives").display())));
        assert!(!args.iter().any(|a| a == "--demo"));
    }

    #[test]
    fn legacy_arguments() {
        let args = build_for(
            r#"{"id":"1.8.9","mainClass":"net.minecraft.client.main.Main","assets":"1.8",
            "minecraftArguments":"--username ${auth_player_name} --assetIndex ${assets_index_name} --version ${version_name}"}"#,
        );
        let main = args.iter().position(|a| a == "net.minecraft.client.main.Main").unwrap();
        assert_eq!(
            args[main + 1..],
            ["--username", "Notch", "--assetIndex", "1.8", "--version", "1.8.9", "--width", "1280", "--height", "720"]
        );
        let cp = args.iter().position(|a| a == "-cp").unwrap();
        assert!(args[cp + 1].ends_with("1.8.9.jar"));
    }
}
