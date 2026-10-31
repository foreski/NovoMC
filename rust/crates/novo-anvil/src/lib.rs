//! Save reading: `level.dat`, region files, and chunks, from the same
//! directories the Java game writes. Nothing here writes a world; this is the
//! read path a native client needs before it can render or simulate anything.

pub mod chunk;
pub mod region;

#[cfg(test)]
mod testutil;

use std::fs;
use std::io;
use std::path::{Path, PathBuf};

pub use chunk::{Chunk, ChunkFormat, Section};
pub use region::{Compression, RegionFile, SECTOR};

/// `r.<region_x>.<region_z>.mca`, where a region covers 32x32 chunks.
pub fn region_file_name(chunk_x: i32, chunk_z: i32) -> String {
    format!("r.{}.{}.mca", chunk_x >> 5, chunk_z >> 5)
}

#[derive(Debug, Clone, Default, PartialEq)]
pub struct LevelInfo {
    pub level_name: String,
    /// Vanilla release name, e.g. `26.3` or `1.8.9`.
    pub version_name: Option<String>,
    pub data_version: Option<i32>,
    pub spawn: (i32, i32, i32),
    /// 0 survival, 1 creative, 2 adventure, 3 spectator.
    pub game_type: Option<i32>,
    /// Milliseconds since the epoch, as vanilla stores it.
    pub last_played: Option<i64>,
}

impl LevelInfo {
    pub fn game_mode(&self) -> &'static str {
        match self.game_type {
            Some(1) => "creative",
            Some(2) => "adventure",
            Some(3) => "spectator",
            Some(_) => "survival",
            None => "unknown",
        }
    }
}

#[derive(Debug)]
pub struct Save {
    dir: PathBuf,
    pub level: LevelInfo,
}

impl Save {
    /// Open a save directory (the folder holding `level.dat`).
    pub fn open(dir: impl AsRef<Path>) -> io::Result<Self> {
        let dir = dir.as_ref().to_path_buf();
        let level_data = dir.join("level.dat");
        let bytes = fs::read(&level_data).map_err(|err| {
            io::Error::new(
                err.kind(),
                format!("reading {}: {err}", level_data.display()),
            )
        })?;
        let level = read_level_info(&bytes)?;
        Ok(Self { dir, level })
    }

    pub fn dir(&self) -> &Path {
        &self.dir
    }

    pub fn region_path(&self, chunk_x: i32, chunk_z: i32) -> PathBuf {
        self.dir
            .join("region")
            .join(region_file_name(chunk_x, chunk_z))
    }

    /// One chunk, or `None` if the region file or the chunk slot is missing.
    pub fn chunk(&self, chunk_x: i32, chunk_z: i32) -> io::Result<Option<Chunk>> {
        let path = self.region_path(chunk_x, chunk_z);
        if !path.exists() {
            return Ok(None);
        }
        let region = RegionFile::open(&path)?;
        let local_x = chunk_x.rem_euclid(32) as usize;
        let local_z = chunk_z.rem_euclid(32) as usize;
        let Some(bytes) = region.chunk_nbt_bytes(local_x, local_z)? else {
            return Ok(None);
        };
        let (_, tag) = novo_nbt::from_bytes(&bytes)?;
        Ok(Some(Chunk::parse(&tag, chunk_x, chunk_z)?))
    }

    /// Region coordinates present on disk, from the `r.X.Z.mca` names.
    pub fn region_coords(&self) -> io::Result<Vec<(i32, i32)>> {
        let region_dir = self.dir.join("region");
        let mut coords = Vec::new();
        let entries = match fs::read_dir(&region_dir) {
            Ok(entries) => entries,
            Err(err) if err.kind() == io::ErrorKind::NotFound => return Ok(coords),
            Err(err) => return Err(err),
        };
        for entry in entries {
            let path = entry?.path();
            let Some(name) = path.file_name().and_then(|name| name.to_str()) else {
                continue;
            };
            let Some(rest) = name.strip_suffix(".mca") else {
                continue;
            };
            let Some(rest) = rest.strip_prefix("r.") else {
                continue;
            };
            let mut parts = rest.split('.');
            let (Some(x), Some(z), None) = (parts.next(), parts.next(), parts.next()) else {
                continue;
            };
            if let (Ok(x), Ok(z)) = (x.parse::<i32>(), z.parse::<i32>()) {
                coords.push((x, z));
            }
        }
        coords.sort_unstable();
        Ok(coords)
    }

    /// Number of chunks actually present, by reading each region header.
    pub fn chunk_count(&self) -> io::Result<usize> {
        let mut total = 0;
        for (x, z) in self.region_coords()? {
            let path = self.dir.join("region").join(format!("r.{x}.{z}.mca"));
            total += RegionFile::open(path)?.occupied_slots().len();
        }
        Ok(total)
    }
}

fn read_level_info(bytes: &[u8]) -> io::Result<LevelInfo> {
    let (_, tag) = novo_nbt::from_gzip(bytes)?;
    let root = tag.as_compound().ok_or_else(|| {
        io::Error::new(
            io::ErrorKind::InvalidData,
            "level.dat root is not a compound",
        )
    })?;
    let data = root.get_compound("Data").unwrap_or(root);

    let version = data.get_compound("Version");
    Ok(LevelInfo {
        level_name: data.get_str("LevelName").unwrap_or("").to_string(),
        version_name: version
            .and_then(|v| v.get_str("Name"))
            .or_else(|| data.get_str("Version"))
            .map(str::to_owned),
        data_version: root
            .get_i32("DataVersion")
            .or_else(|| data.get_i32("DataVersion"))
            .or_else(|| version.and_then(|v| v.get_i32("Id"))),
        spawn: (
            data.get_i32("SpawnX").unwrap_or(0),
            data.get_i32("SpawnY").unwrap_or(64),
            data.get_i32("SpawnZ").unwrap_or(0),
        ),
        game_type: data.get_i32("GameType"),
        last_played: data.get_i64("LastPlayed"),
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use novo_nbt::{Compound, Tag, TagType};
    use testutil::{build_region_with, temp_dir};

    fn modern_chunk_tag() -> Tag {
        let palette = ["minecraft:air", "minecraft:stone"]
            .iter()
            .map(|name| {
                Tag::Compound({
                    let mut entry = Compound::new();
                    entry.insert("Name", Tag::String((*name).into()));
                    entry
                })
            })
            .collect();

        let bits = chunk::bits_for(2);
        let mut values = vec![0u16; chunk::SECTION_VOLUME];
        values[0] = 1;
        values[15] = 1;
        let packed = chunk::pack_modern(&values, bits);

        let mut block_states = Compound::new();
        block_states.insert("palette", Tag::List(TagType::Compound, palette));
        block_states.insert("data", Tag::LongArray(packed));

        let mut section = Compound::new();
        section.insert("Y", Tag::Byte(0));
        section.insert("block_states", Tag::Compound(block_states));

        let mut root = Compound::new();
        root.insert("xPos", Tag::Int(-5));
        root.insert("zPos", Tag::Int(7));
        root.insert("DataVersion", Tag::Int(4440));
        root.insert(
            "sections",
            Tag::List(TagType::Compound, vec![Tag::Compound(section)]),
        );
        Tag::Compound(root)
    }

    fn level_dat() -> Vec<u8> {
        let mut version = Compound::new();
        version.insert("Name", Tag::String("26.3".into()));
        version.insert("Id", Tag::Int(4440));

        let mut data = Compound::new();
        data.insert("LevelName", Tag::String("Novo test world".into()));
        data.insert("GameType", Tag::Int(1));
        data.insert("SpawnX", Tag::Int(16));
        data.insert("SpawnY", Tag::Int(71));
        data.insert("SpawnZ", Tag::Int(-8));
        data.insert("LastPlayed", Tag::Long(1_790_000_000_000));
        data.insert("Version", Tag::Compound(version));

        let mut root = Compound::new();
        root.insert("Data", Tag::Compound(data));
        root.insert("DataVersion", Tag::Int(4440));
        novo_nbt::to_gzip("", &Tag::Compound(root)).unwrap()
    }

    fn sample_save() -> PathBuf {
        let dir = temp_dir("save");
        fs::create_dir_all(dir.join("region")).unwrap();
        fs::write(dir.join("level.dat"), level_dat()).unwrap();

        // Chunk (-5, 7) lives in region (-1, 0), local slot (27, 7).
        let payload = novo_nbt::to_zlib("", &modern_chunk_tag()).unwrap();
        let region = build_region_with(&[(27, 7, payload)], 2);
        fs::write(dir.join("region").join("r.-1.0.mca"), region).unwrap();
        dir
    }

    #[test]
    fn reads_level_dat() {
        let save = Save::open(sample_save()).unwrap();
        assert_eq!(save.level.level_name, "Novo test world");
        assert_eq!(save.level.version_name.as_deref(), Some("26.3"));
        assert_eq!(save.level.data_version, Some(4440));
        assert_eq!(save.level.spawn, (16, 71, -8));
        assert_eq!(save.level.game_mode(), "creative");
        assert_eq!(save.level.last_played, Some(1_790_000_000_000));
    }

    #[test]
    fn reads_a_chunk_through_the_region_file() {
        let save = Save::open(sample_save()).unwrap();
        let chunk = save.chunk(-5, 7).unwrap().expect("chunk present");
        assert_eq!((chunk.x, chunk.z), (-5, 7));
        assert_eq!(chunk.data_version, Some(4440));
        assert_eq!(chunk.block_at(-80, 0, 112), Some("minecraft:stone"));
        assert_eq!(chunk.block_at(-65, 0, 112), Some("minecraft:stone"));
        // A chunk inside the same region that was never written.
        assert!(save.chunk(-4, 7).unwrap().is_none());
        // A region file that does not exist.
        assert!(save.chunk(100, 100).unwrap().is_none());
    }

    #[test]
    fn counts_chunks_and_regions() {
        let save = Save::open(sample_save()).unwrap();
        assert_eq!(save.region_coords().unwrap(), vec![(-1, 0)]);
        assert_eq!(save.chunk_count().unwrap(), 1);
        assert_eq!(region_file_name(-5, 7), "r.-1.0.mca");
        assert_eq!(region_file_name(0, 0), "r.0.0.mca");
        assert_eq!(region_file_name(31, 31), "r.0.0.mca");
        assert_eq!(region_file_name(32, 32), "r.1.1.mca");
    }

    #[test]
    fn a_directory_without_level_dat_is_an_error_that_names_the_file() {
        let dir = temp_dir("empty");
        let err = Save::open(&dir).unwrap_err();
        assert!(err.to_string().contains("level.dat"), "{err}");
    }
}
