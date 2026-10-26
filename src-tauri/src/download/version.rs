//! Types for a version JSON (`versions/<id>/<id>.json`) and an asset index.
//! Only the fields NovoMC uses are modeled; the raw file is saved as-is.

use std::collections::HashMap;
use std::path::Path;

use serde::Deserialize;

use super::rules::{self, Features, Rule};
use crate::error::AppResult;

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct VersionJson {
    pub id: String,
    /// `release`, `snapshot`, `old_beta`, ...
    #[serde(rename = "type")]
    pub kind: Option<String>,
    pub main_class: String,
    /// Set by loader profiles (Fabric) that extend a vanilla version.
    pub inherits_from: Option<String>,
    /// Modern argument format (1.13+).
    pub arguments: Option<Arguments>,
    /// Legacy argument format (before 1.13): one space-separated string.
    pub minecraft_arguments: Option<String>,
    pub asset_index: Option<AssetIndexRef>,
    pub assets: Option<String>,
    pub downloads: Option<VersionDownloads>,
    #[serde(default)]
    pub libraries: Vec<Library>,
    pub java_version: Option<JavaVersion>,
    pub logging: Option<Logging>,
    /// Which version's client jar to use; loader profiles point at their parent.
    pub jar: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct Arguments {
    #[serde(default)]
    pub game: Vec<Argument>,
    #[serde(default)]
    pub jvm: Vec<Argument>,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(untagged)]
pub enum Argument {
    Plain(String),
    Conditional { rules: Vec<Rule>, value: ArgValue },
}

#[derive(Debug, Clone, Deserialize)]
#[serde(untagged)]
pub enum ArgValue {
    One(String),
    Many(Vec<String>),
}

#[derive(Debug, Clone, Deserialize)]
pub struct Download {
    pub sha1: String,
    pub size: u64,
    pub url: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct VersionDownloads {
    pub client: Download,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AssetIndexRef {
    pub id: String,
    pub sha1: String,
    pub size: u64,
    pub total_size: Option<u64>,
    pub url: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct Artifact {
    pub path: Option<String>,
    pub sha1: Option<String>,
    pub size: Option<u64>,
    pub url: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct LibraryDownloads {
    pub artifact: Option<Artifact>,
    pub classifiers: Option<HashMap<String, Artifact>>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct Library {
    /// Maven coordinate, `group:artifact:version[:classifier]`.
    pub name: String,
    pub downloads: Option<LibraryDownloads>,
    /// Legacy natives: OS name -> classifier (may contain `${arch}`).
    pub natives: Option<HashMap<String, String>>,
    pub rules: Option<Vec<Rule>>,
    pub extract: Option<Extract>,
    /// Maven repository base URL (loader libraries without `downloads`).
    pub url: Option<String>,
    pub sha1: Option<String>,
    pub size: Option<u64>,
    /// Old Forge profiles: `false` marks server-only libraries.
    pub clientreq: Option<bool>,
    /// Old Forge profiles: accepted SHA1s (for the jar and its old packed form).
    #[serde(default)]
    pub checksums: Vec<String>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct Extract {
    #[serde(default)]
    pub exclude: Vec<String>,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct JavaVersion {
    pub component: String,
    pub major_version: u32,
}

#[derive(Debug, Clone, Deserialize)]
pub struct Logging {
    pub client: Option<LoggingClient>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct LoggingClient {
    pub argument: String,
    pub file: LogFile,
}

#[derive(Debug, Clone, Deserialize)]
pub struct LogFile {
    pub id: String,
    pub sha1: String,
    pub size: u64,
    pub url: String,
}

impl VersionJson {
    /// The version whose client jar this one runs.
    pub fn jar_id(&self) -> &str {
        self.jar.as_deref().unwrap_or(&self.id)
    }

    /// Combines a loader profile (`self`) with the version it inherits from:
    /// the child's main class and arguments win, libraries are child-first,
    /// everything else comes from the parent.
    pub fn merge_onto(self, parent: VersionJson) -> VersionJson {
        let mut libraries = self.libraries;
        libraries.extend(parent.libraries);
        let arguments = match (parent.arguments, self.arguments) {
            (Some(mut p), Some(c)) => {
                p.game.extend(c.game);
                p.jvm.extend(c.jvm);
                Some(p)
            }
            (p, c) => c.or(p),
        };
        let jar = self.jar.or(parent.jar).unwrap_or(parent.id);
        VersionJson {
            id: self.id,
            kind: self.kind.or(parent.kind),
            main_class: self.main_class,
            inherits_from: None,
            arguments,
            minecraft_arguments: self.minecraft_arguments.or(parent.minecraft_arguments),
            asset_index: self.asset_index.or(parent.asset_index),
            assets: self.assets.or(parent.assets),
            downloads: self.downloads.or(parent.downloads),
            libraries,
            java_version: self.java_version.or(parent.java_version),
            logging: self.logging.or(parent.logging),
            jar: Some(jar),
        }
    }
}

/// Reads `versions/<id>/<id>.json` and merges in everything it inherits from.
pub fn load_resolved(root: &Path, id: &str) -> AppResult<VersionJson> {
    let path = root.join("versions").join(id).join(format!("{id}.json"));
    let v: VersionJson = serde_json::from_slice(&std::fs::read(path)?)?;
    match v.inherits_from.clone() {
        Some(parent) => Ok(v.merge_onto(load_resolved(root, &parent)?)),
        None => Ok(v),
    }
}

impl Library {
    /// Whether this library is used by the client on the current OS.
    pub fn applies(&self) -> bool {
        self.clientreq != Some(false) && rules::allowed(self.rules.as_deref(), &Features::new())
    }

    /// Legacy native classifier for this OS, e.g. `natives-windows`.
    pub fn native_classifier(&self) -> Option<String> {
        let classifier = self.natives.as_ref()?.get(rules::os_name())?;
        Some(classifier.replace("${arch}", rules::arch_bits()))
    }
}

/// Relative repository path for a Maven coordinate
/// `group:artifact:version[:classifier][@ext]`.
pub fn maven_path(coord: &str) -> Option<String> {
    let (coord, ext) = coord.split_once('@').unwrap_or((coord, "jar"));
    let mut parts = coord.split(':');
    let group = parts.next()?;
    let artifact = parts.next()?;
    let version = parts.next()?;
    let file = match parts.next() {
        Some(classifier) => format!("{artifact}-{version}-{classifier}.{ext}"),
        None => format!("{artifact}-{version}.{ext}"),
    };
    Some(format!("{}/{artifact}/{version}/{file}", group.replace('.', "/")))
}

/// `assets/indexes/<id>.json`.
#[derive(Debug, Clone, Deserialize)]
pub struct AssetIndex {
    pub objects: HashMap<String, AssetObject>,
    /// Very old versions read assets from `resources/` in the game folder.
    #[serde(default)]
    pub map_to_resources: bool,
    /// Old versions read assets from `assets/virtual/<id>/` by name.
    #[serde(default, rename = "virtual")]
    pub is_virtual: bool,
}

#[derive(Debug, Clone, Deserialize)]
pub struct AssetObject {
    pub hash: String,
    pub size: u64,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn maven_paths() {
        assert_eq!(
            maven_path("org.lwjgl:lwjgl:3.3.3").unwrap(),
            "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar"
        );
        assert_eq!(
            maven_path("org.lwjgl:lwjgl:3.3.3:natives-windows").unwrap(),
            "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows.jar"
        );
        assert_eq!(maven_path("a.b:c:1@zip").unwrap(), "a/b/c/1/c-1.zip");
    }

    #[test]
    fn forge_profile_merges_onto_vanilla() {
        let vanilla: VersionJson = serde_json::from_str(
            r#"{"id":"1.8.9","mainClass":"net.minecraft.client.main.Main","minecraftArguments":"--username ${auth_player_name}",
            "assetIndex":{"id":"1.8","sha1":"x","size":1,"url":"u"},"javaVersion":{"component":"jre-legacy","majorVersion":8},
            "libraries":[{"name":"org.lwjgl.lwjgl:lwjgl:2.9.4"}]}"#,
        )
        .unwrap();
        let forge: VersionJson = serde_json::from_str(
            r#"{"id":"1.8.9-forge1.8.9-11.15.1.2318-1.8.9","inheritsFrom":"1.8.9","jar":"1.8.9",
            "mainClass":"net.minecraft.launchwrapper.Launch",
            "minecraftArguments":"--username ${auth_player_name} --tweakClass net.minecraftforge.fml.common.launcher.FMLTweaker",
            "libraries":[{"name":"net.minecraft:launchwrapper:1.12"},{"name":"server:only:1","clientreq":false}]}"#,
        )
        .unwrap();
        let merged = forge.merge_onto(vanilla);
        assert_eq!(merged.main_class, "net.minecraft.launchwrapper.Launch");
        assert!(merged.minecraft_arguments.as_deref().unwrap().contains("FMLTweaker"));
        assert_eq!(merged.jar_id(), "1.8.9");
        assert_eq!(merged.asset_index.as_ref().unwrap().id, "1.8");
        assert_eq!(merged.java_version.as_ref().unwrap().major_version, 8);
        let names: Vec<_> = merged.libraries.iter().filter(|l| l.applies()).map(|l| l.name.as_str()).collect();
        assert_eq!(names, ["net.minecraft:launchwrapper:1.12", "org.lwjgl.lwjgl:lwjgl:2.9.4"]);
    }
}
