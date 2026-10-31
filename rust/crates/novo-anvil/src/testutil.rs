//! Helpers that build real `.mca` bytes for the tests: these write the same
//! layout the reader expects, so a failing test means a reader bug and not a
//! missing fixture.
#![cfg(test)]

use std::path::PathBuf;

use crate::SECTOR;

/// Build a region file from `(local_x, local_z, uncompressed_payload)` entries,
/// stored with compression type 3 (uncompressed).
pub fn build_region(entries: &[(usize, usize, Vec<u8>)]) -> Vec<u8> {
    build_region_with(entries, 3)
}

pub fn build_region_with(entries: &[(usize, usize, Vec<u8>)], compression: u8) -> Vec<u8> {
    let mut bytes = vec![0u8; 2 * SECTOR];
    let mut next_sector = 2usize;

    for (x, z, payload) in entries {
        let declared = payload.len() as u32;
        let mut block = Vec::with_capacity(5 + payload.len());
        block.extend_from_slice(&declared.to_be_bytes());
        block.push(compression);
        block.extend_from_slice(payload);

        let sectors = block.len().div_ceil(SECTOR);
        let offset = next_sector;
        let slot = (x + z * 32) * 4;
        let entry = ((offset as u32) << 8) | sectors as u32;
        bytes[slot..slot + 4].copy_from_slice(&entry.to_be_bytes());

        bytes.extend_from_slice(&block);
        let padding = sectors * SECTOR - block.len();
        bytes.extend(std::iter::repeat_n(0u8, padding));
        next_sector += sectors;
    }

    bytes
}

/// A fresh directory per call, so parallel tests do not collide.
pub fn temp_dir(label: &str) -> PathBuf {
    let unique = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_nanos())
        .unwrap_or(0);
    let dir = std::env::temp_dir().join(format!(
        "novo-anvil-{label}-{}-{unique}",
        std::process::id()
    ));
    std::fs::create_dir_all(&dir).unwrap();
    dir
}

pub fn write_temp_region(bytes: &[u8]) -> PathBuf {
    let path = temp_dir("region").join("r.0.0.mca");
    std::fs::write(&path, bytes).unwrap();
    path
}
