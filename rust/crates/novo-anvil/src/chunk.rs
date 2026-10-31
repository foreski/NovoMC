//! Chunk decoding: from the NBT of one chunk to a per-section palette plus 4096
//! block indices, which is what every consumer above this crate actually wants.
//!
//! Three on-disk layouts are recognised, because a save can contain all of them
//! at once after an upgrade:
//!
//! * 1.8 — `Level.Sections[].Blocks`, one byte per block, a numeric block id;
//! * 1.13 – 1.17 — `Palette` + `BlockStates`, with values allowed to straddle
//!   two longs;
//! * 1.18+ — `block_states.palette` + `block_states.data`, packed so a value
//!   never crosses a long.

use std::collections::BTreeMap;
use std::io;

use novo_nbt::{Compound, Tag};

pub const SECTION_VOLUME: usize = 4096;

const AIR: [&str; 3] = ["minecraft:air", "minecraft:cave_air", "minecraft:void_air"];

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ChunkFormat {
    /// 1.8 and earlier: numeric block ids, 128 blocks tall.
    Legacy,
    /// 1.13 – 1.17: palettes, values packed across longs.
    Paletted,
    /// 1.18+: palettes, values padded so they never cross a long.
    Modern,
}

#[derive(Debug, Clone)]
pub struct Section {
    pub y: i32,
    pub palette: Vec<String>,
    /// One palette index per block, ordered `y * 256 + z * 16 + x` like vanilla.
    pub values: Vec<u16>,
    pub format: ChunkFormat,
}

impl Section {
    pub fn name_at(&self, index: usize) -> Option<&str> {
        let palette_index = *self.values.get(index)? as usize;
        self.palette.get(palette_index).map(String::as_str)
    }

    /// Block name at chunk-local coordinates, if that block exists in this
    /// section.
    pub fn block_at(&self, x: usize, y: usize, z: usize) -> Option<&str> {
        if x >= 16 || y >= 16 || z >= 16 {
            return None;
        }
        self.name_at((y * 16 + z) * 16 + x)
    }
}

#[derive(Debug, Clone)]
pub struct Chunk {
    pub x: i32,
    pub z: i32,
    pub data_version: Option<i32>,
    pub status: Option<String>,
    pub format: ChunkFormat,
    pub sections: Vec<Section>,
}

impl Chunk {
    pub fn palette(&self) -> impl Iterator<Item = &str> {
        self.sections
            .iter()
            .flat_map(|s| s.palette.iter().map(String::as_str))
    }

    /// Non-air block counts by name.
    pub fn block_counts(&self) -> BTreeMap<String, u64> {
        let mut counts = BTreeMap::new();
        for section in &self.sections {
            for value in &section.values {
                if let Some(name) = section.palette.get(*value as usize)
                    && !is_air(name)
                {
                    *counts.entry(name.clone()).or_insert(0) += 1;
                }
            }
        }
        counts
    }

    /// Every position in this chunk holding `name`, in world coordinates.
    pub fn find(&self, name: &str) -> Vec<(i32, i32, i32)> {
        let mut found = Vec::new();
        for section in &self.sections {
            for (index, value) in section.values.iter().enumerate() {
                if section.palette.get(*value as usize).map(String::as_str) != Some(name) {
                    continue;
                }
                let y_local = (index / 256) as i32;
                let z_local = ((index / 16) % 16) as i32;
                let x_local = (index % 16) as i32;
                found.push((
                    self.x * 16 + x_local,
                    section.y * 16 + y_local,
                    self.z * 16 + z_local,
                ));
            }
        }
        found
    }

    /// Name of the block at world coordinates inside this chunk, if loaded.
    pub fn block_at(&self, x: i32, y: i32, z: i32) -> Option<&str> {
        let x_local = x - self.x * 16;
        let z_local = z - self.z * 16;
        if !(0..16).contains(&x_local) || !(0..16).contains(&z_local) {
            return None;
        }
        let section_y = y.div_euclid(16);
        let y_local = y.rem_euclid(16) as usize;
        let section = self.sections.iter().find(|s| s.y == section_y)?;
        section.block_at(x_local as usize, y_local, z_local as usize)
    }

    pub fn lowest_y(&self) -> Option<i32> {
        self.sections.iter().map(|s| s.y).min().map(|y| y * 16)
    }

    pub fn highest_y(&self) -> Option<i32> {
        self.sections.iter().map(|s| s.y).max().map(|y| y * 16 + 15)
    }

    /// Parse one chunk's NBT. `fallback_x`/`fallback_z` are the region
    /// coordinates, used only when the chunk itself omits `xPos`/`zPos`.
    pub fn parse(tag: &Tag, fallback_x: i32, fallback_z: i32) -> io::Result<Chunk> {
        let root = tag.as_compound().ok_or_else(|| {
            io::Error::new(io::ErrorKind::InvalidData, "chunk root is not a compound")
        })?;

        // 1.18 dropped the `Level` wrapper; before that every field the reader
        // needs lives inside it.
        let (container, format) = if root.get_list("sections").is_some() {
            (root, ChunkFormat::Modern)
        } else if let Some(level) = root.get_compound("Level") {
            let format = if level.get_list("Sections").is_some_and(|sections| {
                sections
                    .iter()
                    .any(|s| s.as_compound().is_some_and(|c| c.get("Blocks").is_some()))
            }) {
                ChunkFormat::Legacy
            } else {
                ChunkFormat::Paletted
            };
            (level, format)
        } else {
            return Err(io::Error::new(
                io::ErrorKind::InvalidData,
                "chunk has neither `sections` nor `Level`",
            ));
        };

        let x = container
            .get_i32("xPos")
            .or_else(|| root.get_i32("xPos"))
            .unwrap_or(fallback_x);
        let z = container
            .get_i32("zPos")
            .or_else(|| root.get_i32("zPos"))
            .unwrap_or(fallback_z);
        let data_version = root
            .get_i32("DataVersion")
            .or_else(|| container.get_i32("DataVersion"));
        let status = container
            .get_str("Status")
            .or_else(|| root.get_str("Status"))
            .map(str::to_owned);

        let section_tag = if format == ChunkFormat::Modern {
            container.get("sections")
        } else {
            container.get("Sections")
        };
        let section_entries = section_tag.and_then(Tag::as_list).unwrap_or(&[]);

        let mut sections = Vec::new();
        for entry in section_entries {
            let Some(section) = entry.as_compound() else {
                continue;
            };
            // Empty-padding sections in a 1.8 save have a Y and nothing else.
            let Some(y) = section_y(section) else {
                continue;
            };
            let decoded = match format {
                ChunkFormat::Modern => parse_modern_section(section, y),
                ChunkFormat::Paletted | ChunkFormat::Legacy => parse_legacy_section(section, y),
            };
            if let Some(decoded) = decoded {
                sections.push(decoded);
            }
        }

        sections.sort_by_key(|s| s.y);
        Ok(Chunk {
            x,
            z,
            data_version,
            status,
            format,
            sections,
        })
    }
}

fn section_y(section: &Compound) -> Option<i32> {
    match section.get("Y")? {
        Tag::Byte(y) => Some(i32::from(*y)),
        Tag::Int(y) => Some(*y),
        _ => None,
    }
}

fn is_air(name: &str) -> bool {
    AIR.contains(&name)
}

fn palette_names(section: &Compound, field: &str) -> Vec<String> {
    section
        .get_list(field)
        .map(|entries| {
            entries
                .iter()
                .filter_map(|entry| entry.as_compound())
                .map(|compound| {
                    compound
                        .get_str("Name")
                        .unwrap_or("minecraft:air")
                        .to_owned()
                })
                .collect()
        })
        .unwrap_or_default()
}

fn parse_modern_section(section: &Compound, y: i32) -> Option<Section> {
    let block_states = section.get_compound("block_states")?;
    let palette = palette_names(block_states, "palette");
    if palette.is_empty() {
        return None;
    }
    let values = match block_states.get("data").and_then(Tag::as_longs) {
        Some(data) => {
            let bits = bits_for(palette.len());
            unpack_modern(data, bits, SECTION_VOLUME)
        }
        // A one-entry palette is written with no data array at all, meaning
        // every block is palette entry 0.
        None => vec![0u16; SECTION_VOLUME],
    };
    Some(Section {
        y,
        palette,
        values,
        format: ChunkFormat::Modern,
    })
}

fn parse_legacy_section(section: &Compound, y: i32) -> Option<Section> {
    // 1.8: one byte per block, a numeric id.
    if let Some(blocks) = section.get_bytes("Blocks") {
        if blocks.len() != SECTION_VOLUME {
            return None;
        }
        // Ids above 255 live in the `Add` nibbles; without them a block reads as
        // a different id, so a chunk that needs `Add` is reported rather than
        // half-read.
        // `palette[0]` is air; ids are appended as they are seen, so the table
        // stays the size of the ids the chunk actually uses.
        let mut palette = vec!["minecraft:air".to_string()];
        let mut lookup: BTreeMap<u8, u16> = BTreeMap::new();
        lookup.insert(0, 0);
        let mut values = Vec::with_capacity(SECTION_VOLUME);
        for id in blocks {
            let next = lookup.len() as u16;
            let index = *lookup.entry(*id).or_insert(next);
            values.push(index);
        }
        let mut ordered: Vec<(u8, u16)> = lookup.into_iter().collect();
        ordered.sort_by_key(|(_, index)| *index);
        for (id, index) in ordered {
            let index = index as usize;
            if index >= palette.len() {
                palette.resize(index + 1, String::new());
            }
            palette[index] = if id == 0 {
                "minecraft:air".to_string()
            } else {
                format!("legacy:{id}")
            };
        }
        return Some(Section {
            y,
            palette,
            values,
            format: ChunkFormat::Legacy,
        });
    }

    // 1.13 – 1.17: palette + block states packed across long boundaries.
    let palette = palette_names(section, "Palette");
    if palette.is_empty() {
        return None;
    }
    let values = match section.get("BlockStates").and_then(Tag::as_longs) {
        Some(data) => {
            let bits = bits_for(palette.len());
            unpack_spanning(data, bits, SECTION_VOLUME)
        }
        None => vec![0u16; SECTION_VOLUME],
    };
    Some(Section {
        y,
        palette,
        values,
        format: ChunkFormat::Paletted,
    })
}

/// Bits per palette index. Vanilla clamps to a four-bit minimum even for a
/// two-entry palette.
pub fn bits_for(palette_len: usize) -> u32 {
    let needed = usize::BITS - (palette_len.max(1) - 1).leading_zeros();
    needed.max(4)
}

/// 1.18+ packing: `64 / bits` entries per long, the last long padded.
pub fn unpack_modern(data: &[i64], bits: u32, count: usize) -> Vec<u16> {
    let bits = bits.clamp(1, 32);
    let per_long = (64 / bits) as usize;
    let mask = (1u64 << bits) - 1;
    let mut values = Vec::with_capacity(count);
    for index in 0..count {
        let long = data.get(index / per_long).copied().unwrap_or(0) as u64;
        let shift = ((index % per_long) * bits as usize) as u32;
        values.push(((long >> shift) & mask) as u16);
    }
    values
}

pub fn pack_modern(values: &[u16], bits: u32) -> Vec<i64> {
    let bits = bits.clamp(1, 32);
    let per_long = (64 / bits) as usize;
    let mut longs = vec![0u64; values.len().div_ceil(per_long)];
    for (index, value) in values.iter().enumerate() {
        let shift = ((index % per_long) * bits as usize) as u32;
        longs[index / per_long] |= u64::from(*value) << shift;
    }
    longs.into_iter().map(|long| long as i64).collect()
}

/// 1.13 – 1.17 packing: entries are contiguous in the bit stream, so a value may
/// start in one long and finish in the next.
pub fn unpack_spanning(data: &[i64], bits: u32, count: usize) -> Vec<u16> {
    let bits = bits.clamp(1, 32);
    let mask = (1u64 << bits) - 1;
    let mut values = Vec::with_capacity(count);
    for index in 0..count {
        let bit_index = index * bits as usize;
        let long_index = bit_index / 64;
        let offset = (bit_index % 64) as u32;
        let mut value = (data.get(long_index).copied().unwrap_or(0) as u64) >> offset;
        if offset + bits > 64 {
            let next = data.get(long_index + 1).copied().unwrap_or(0) as u64;
            value |= next << (64 - offset);
        }
        values.push((value & mask) as u16);
    }
    values
}

pub fn pack_spanning(values: &[u16], bits: u32) -> Vec<i64> {
    let bits = bits.clamp(1, 32);
    let total_bits = values.len() * bits as usize;
    let mut longs = vec![0u64; total_bits.div_ceil(64)];
    for (index, value) in values.iter().enumerate() {
        let bit_index = index * bits as usize;
        let long_index = bit_index / 64;
        let offset = (bit_index % 64) as u32;
        longs[long_index] |= u64::from(*value) << offset;
        if offset + bits > 64 {
            let spill = (*value as u64) >> (64 - offset);
            if long_index + 1 < longs.len() {
                longs[long_index + 1] |= spill;
            }
        }
    }
    longs.into_iter().map(|long| long as i64).collect()
}

#[cfg(test)]
mod tests {
    use super::*;
    use novo_nbt::{Compound, Tag, TagType};

    fn modern_chunk() -> Tag {
        let mut palette = Vec::new();
        for name in ["minecraft:air", "minecraft:stone", "minecraft:oak_log"] {
            let mut entry = Compound::new();
            entry.insert("Name", Tag::String(name.into()));
            palette.push(Tag::Compound(entry));
        }

        // 3-entry palette -> 4 bits, 16 entries per long.
        let bits = bits_for(3);
        assert_eq!(bits, 4);
        let mut values = vec![0u16; SECTION_VOLUME];
        values[0] = 1; // 0,0,0 stone
        values[1] = 1; // 1,0,0 stone
        values[256] = 2; // 0,1,0 log
        let packed = pack_modern(&values, bits);

        let mut block_states = Compound::new();
        block_states.insert("palette", Tag::List(TagType::Compound, palette));
        block_states.insert("data", Tag::LongArray(packed));

        let mut section = Compound::new();
        section.insert("Y", Tag::Byte(0));
        section.insert("block_states", Tag::Compound(block_states));

        let mut root = Compound::new();
        root.insert("xPos", Tag::Int(3));
        root.insert("zPos", Tag::Int(-2));
        root.insert("DataVersion", Tag::Int(4189));
        root.insert("Status", Tag::String("minecraft:full".into()));
        root.insert(
            "sections",
            Tag::List(TagType::Compound, vec![Tag::Compound(section)]),
        );
        Tag::Compound(root)
    }

    #[test]
    fn decodes_a_modern_chunk() {
        let chunk = Chunk::parse(&modern_chunk(), 0, 0).unwrap();
        assert_eq!(chunk.format, ChunkFormat::Modern);
        assert_eq!(chunk.x, 3);
        assert_eq!(chunk.z, -2);
        assert_eq!(chunk.data_version, Some(4189));
        assert_eq!(chunk.status.as_deref(), Some("minecraft:full"));
        assert_eq!(chunk.sections.len(), 1);

        // World coordinates: chunk (3, -2), so x = 48.., z = -32..
        assert_eq!(chunk.block_at(48, 0, -32), Some("minecraft:stone"));
        assert_eq!(chunk.block_at(49, 0, -32), Some("minecraft:stone"));
        assert_eq!(chunk.block_at(48, 1, -32), Some("minecraft:oak_log"));
        assert_eq!(chunk.block_at(50, 0, -32), Some("minecraft:air"));
        assert_eq!(chunk.block_at(0, 0, 0), None, "outside the chunk");

        let mut found = chunk.find("minecraft:stone");
        found.sort();
        assert_eq!(found, vec![(48, 0, -32), (49, 0, -32)]);

        let counts = chunk.block_counts();
        assert_eq!(counts.get("minecraft:stone"), Some(&2));
        assert_eq!(counts.get("minecraft:oak_log"), Some(&1));
        assert!(!counts.contains_key("minecraft:air"), "air is not counted");
    }

    #[test]
    fn decodes_a_legacy_1_8_chunk() {
        // One section, Blocks with id 1 (stone) at the bottom corner.
        let mut blocks = vec![0u8; SECTION_VOLUME];
        blocks[0] = 1;
        blocks[1] = 1;
        blocks[17] = 2;

        let mut section = Compound::new();
        section.insert("Y", Tag::Byte(0));
        section.insert("Blocks", Tag::ByteArray(blocks));

        // A padding section with no Blocks, as a 1.8 save writes it.
        let mut empty = Compound::new();
        empty.insert("Y", Tag::Byte(1));

        let mut level = Compound::new();
        level.insert("xPos", Tag::Int(0));
        level.insert("zPos", Tag::Int(0));
        level.insert(
            "Sections",
            Tag::List(
                TagType::Compound,
                vec![Tag::Compound(section), Tag::Compound(empty)],
            ),
        );

        let mut root = Compound::new();
        root.insert("Level", Tag::Compound(level));

        let chunk = Chunk::parse(&Tag::Compound(root), 0, 0).unwrap();
        assert_eq!(chunk.format, ChunkFormat::Legacy);
        assert_eq!(
            chunk.sections.len(),
            1,
            "the empty padding section is skipped"
        );
        assert_eq!(chunk.block_at(0, 0, 0), Some("legacy:1"));
        assert_eq!(chunk.block_at(1, 0, 0), Some("legacy:1"));
        assert_eq!(chunk.block_at(1, 0, 1), Some("legacy:2"));
        assert_eq!(chunk.block_at(2, 0, 0), Some("minecraft:air"));
        assert_eq!(chunk.find("legacy:2"), vec![(1, 0, 1)]);
    }

    #[test]
    fn decodes_a_1_13_palette_chunk() {
        let bits = bits_for(2);
        assert_eq!(bits, 4);
        let mut values = vec![0u16; SECTION_VOLUME];
        values[17] = 1;
        let packed = pack_spanning(&values, bits);

        let palette = vec![
            Tag::Compound({
                let mut entry = Compound::new();
                entry.insert("Name", Tag::String("minecraft:air".into()));
                entry
            }),
            Tag::Compound({
                let mut entry = Compound::new();
                entry.insert("Name", Tag::String("minecraft:dirt".into()));
                entry
            }),
        ];

        let mut section = Compound::new();
        section.insert("Y", Tag::Byte(4));
        section.insert("Palette", Tag::List(TagType::Compound, palette));
        section.insert("BlockStates", Tag::LongArray(packed));

        let mut level = Compound::new();
        level.insert(
            "Sections",
            Tag::List(TagType::Compound, vec![Tag::Compound(section)]),
        );
        let mut root = Compound::new();
        root.insert("Level", Tag::Compound(level));

        let chunk = Chunk::parse(&Tag::Compound(root), 0, 0).unwrap();
        assert_eq!(chunk.format, ChunkFormat::Paletted);
        assert_eq!(chunk.sections[0].y, 4);
        assert_eq!(chunk.block_at(1, 64, 1), Some("minecraft:dirt"));
        assert_eq!(chunk.block_at(2, 64, 0), Some("minecraft:air"));
        assert_eq!(chunk.lowest_y(), Some(64));
    }

    #[test]
    fn spanning_packing_round_trips_across_long_boundaries() {
        for bits in [5u32, 6, 7, 9, 13, 32] {
            let mut values = Vec::new();
            for index in 0..SECTION_VOLUME {
                values.push(((index * 7) % ((1 << bits.min(16)) - 1)) as u16);
            }
            let packed = pack_spanning(&values, bits);
            assert_eq!(unpack_spanning(&packed, bits, SECTION_VOLUME), values);
        }
    }

    #[test]
    fn modern_packing_round_trips() {
        for bits in [4u32, 5, 8, 16] {
            let limit = (1u32 << bits) - 1;
            let values: Vec<u16> = (0..SECTION_VOLUME)
                .map(|index| ((index as u32 * 3) % limit) as u16)
                .collect();
            let packed = pack_modern(&values, bits);
            assert_eq!(unpack_modern(&packed, bits, SECTION_VOLUME), values);
        }
    }

    #[test]
    fn a_one_entry_palette_needs_no_data() {
        let mut block_states = Compound::new();
        block_states.insert(
            "palette",
            Tag::List(
                TagType::Compound,
                vec![Tag::Compound({
                    let mut entry = Compound::new();
                    entry.insert("Name", Tag::String("minecraft:stone".into()));
                    entry
                })],
            ),
        );
        let mut section = Compound::new();
        section.insert("Y", Tag::Byte(0));
        section.insert("block_states", Tag::Compound(block_states));
        let mut root = Compound::new();
        root.insert(
            "sections",
            Tag::List(TagType::Compound, vec![Tag::Compound(section)]),
        );

        let chunk = Chunk::parse(&Tag::Compound(root), 0, 0).unwrap();
        assert_eq!(chunk.block_at(0, 0, 0), Some("minecraft:stone"));

        let counts = chunk.block_counts();
        assert_eq!(counts.get("minecraft:stone"), Some(&4096));
    }

    #[test]
    fn rejects_a_root_without_chunk_fields() {
        let mut root = Compound::new();
        root.insert("hello", Tag::String("world".into()));
        assert!(Chunk::parse(&Tag::Compound(root), 0, 0).is_err());
    }
}
