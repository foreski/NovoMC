//! Region file (`.mca`) reading: the 8 KiB header that points at chunk payloads,
//! the per-chunk compression byte, and the three compression schemes vanilla has
//! used for chunks.

use std::fs;
use std::io::{self, Read};
use std::path::Path;

use flate2::read::{GzDecoder, ZlibDecoder};

pub const SECTOR: usize = 4096;
const HEADER: usize = 2 * SECTOR;
const CHUNKS_PER_AXIS: usize = 32;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Compression {
    Gzip,
    Zlib,
    None,
    /// Vanilla has shipped LZ4 chunks since 1.20.5. Reading them needs an LZ4
    /// block decoder, which this crate does not carry yet, so it says so.
    Lz4,
    /// Compression type 127: the payload lives in an external `.mcc` file.
    External,
}

impl Compression {
    fn from_byte(byte: u8) -> io::Result<Self> {
        Ok(match byte {
            1 => Compression::Gzip,
            2 => Compression::Zlib,
            3 => Compression::None,
            4 => Compression::Lz4,
            127 => Compression::External,
            other => {
                return Err(io::Error::new(
                    io::ErrorKind::InvalidData,
                    format!("unknown chunk compression type {other}"),
                ));
            }
        })
    }
}

/// A region file held in memory. Region files are at most 256 MiB and in
/// practice a few MiB, so mapping the whole thing is simpler than seeking.
pub struct RegionFile {
    path: std::path::PathBuf,
    bytes: Vec<u8>,
}

impl RegionFile {
    pub fn open(path: impl AsRef<Path>) -> io::Result<Self> {
        let path = path.as_ref().to_path_buf();
        let bytes = fs::read(&path)?;
        if bytes.len() < HEADER {
            return Err(io::Error::new(
                io::ErrorKind::InvalidData,
                format!("{} is shorter than a region header", path.display()),
            ));
        }
        Ok(Self { path, bytes })
    }

    pub fn path(&self) -> &Path {
        &self.path
    }

    /// Decompress the chunk at local coordinates (0..32, 0..32), or `None` when
    /// that slot is empty.
    pub fn chunk_nbt_bytes(&self, x: usize, z: usize) -> io::Result<Option<Vec<u8>>> {
        if x >= CHUNKS_PER_AXIS || z >= CHUNKS_PER_AXIS {
            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "chunk coordinates must be 0..32 inside a region",
            ));
        }
        let slot = (x + z * CHUNKS_PER_AXIS) * 4;
        let entry = u32::from_be_bytes([
            self.bytes[slot],
            self.bytes[slot + 1],
            self.bytes[slot + 2],
            self.bytes[slot + 3],
        ]);
        // The location table packs a 3-byte sector offset and a 1-byte sector
        // count; zero offset means the slot was never written.
        let offset = (entry >> 8) as usize;
        let sectors = (entry & 0xff) as usize;
        if offset == 0 || sectors == 0 {
            return Ok(None);
        }
        let start = offset * SECTOR;
        if start + 5 > self.bytes.len() {
            return Err(io::Error::new(
                io::ErrorKind::InvalidData,
                "chunk offset points past the end of the region file",
            ));
        }
        let declared = u32::from_be_bytes([
            self.bytes[start],
            self.bytes[start + 1],
            self.bytes[start + 2],
            self.bytes[start + 3],
        ]) as usize;
        let compression = Compression::from_byte(self.bytes[start + 4])?;
        let payload_start = start + 5;
        let payload_end = payload_start
            .checked_add(declared)
            .ok_or_else(|| io::Error::new(io::ErrorKind::InvalidData, "chunk length overflow"))?;
        if declared == 0 || payload_end > self.bytes.len() {
            return Err(io::Error::new(
                io::ErrorKind::InvalidData,
                "chunk payload runs past the end of the region file",
            ));
        }
        let payload = &self.bytes[payload_start..payload_end];
        let mut out = Vec::new();
        match compression {
            Compression::Gzip => {
                GzDecoder::new(payload).read_to_end(&mut out)?;
            }
            Compression::Zlib => {
                ZlibDecoder::new(payload).read_to_end(&mut out)?;
            }
            Compression::None => out.extend_from_slice(payload),
            Compression::Lz4 => {
                return Err(io::Error::new(
                    io::ErrorKind::Unsupported,
                    "this chunk is LZ4 compressed (1.20.5+); that decoder is not implemented yet",
                ));
            }
            Compression::External => {
                return Err(io::Error::new(
                    io::ErrorKind::Unsupported,
                    "this chunk lives in an external .mcc file, which is not supported yet",
                ));
            }
        }
        Ok(Some(out))
    }

    /// Which of the 1024 slots hold a chunk.
    pub fn occupied_slots(&self) -> Vec<(usize, usize)> {
        let mut slots = Vec::new();
        for z in 0..CHUNKS_PER_AXIS {
            for x in 0..CHUNKS_PER_AXIS {
                let slot = (x + z * CHUNKS_PER_AXIS) * 4;
                if self.bytes[slot + 3] != 0 {
                    slots.push((x, z));
                }
            }
        }
        slots
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::testutil::{build_region, write_temp_region};

    #[test]
    fn reads_a_chunk_written_by_the_test_builder() {
        let chunk = build_region(&[(0, 0, vec![1, 2, 3])]);
        let path = write_temp_region(&chunk);
        let region = RegionFile::open(&path).unwrap();
        assert_eq!(region.chunk_nbt_bytes(0, 0).unwrap(), Some(vec![1, 2, 3]));
        // Empty slot.
        assert_eq!(region.chunk_nbt_bytes(1, 0).unwrap(), None);
        // Out of range.
        assert!(region.chunk_nbt_bytes(32, 0).is_err());
    }

    #[test]
    fn accepts_a_region_full_of_slots() {
        // Every slot written: the header arithmetic is the same, the sector
        // offsets are not.
        let entries: Vec<(usize, usize, Vec<u8>)> = (0..32)
            .flat_map(|z| (0..32).map(move |x| (x, z)))
            .map(|(x, z)| (x, z, vec![(x as u8), (z as u8)]))
            .collect();
        let bytes = build_region(&entries);
        let path = write_temp_region(&bytes);
        let region = RegionFile::open(&path).unwrap();
        assert_eq!(region.occupied_slots().len(), 1024);
        assert_eq!(region.chunk_nbt_bytes(31, 31).unwrap(), Some(vec![31, 31]));
    }

    #[test]
    fn rejects_a_truncated_file() {
        let bytes = build_region(&[(0, 0, vec![1])]);
        let path = write_temp_region(&bytes[..100]);
        assert!(RegionFile::open(&path).is_err());
    }

    #[test]
    fn rejects_a_chunk_whose_length_runs_past_the_file() {
        let mut bytes = build_region(&[(0, 0, vec![1, 2, 3])]);
        // Corrupt the declared length of the chunk, which starts at sector 2
        // (the first two sectors are the location table and timestamps).
        bytes[2 * SECTOR..2 * SECTOR + 4].copy_from_slice(&999_999u32.to_be_bytes());
        let path = write_temp_region(&bytes);
        let region = RegionFile::open(&path).unwrap();
        assert!(region.chunk_nbt_bytes(0, 0).is_err());
    }
}
