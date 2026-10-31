//! Wire primitives shared by every other crate: VarInt/VarLong, length-prefixed
//! UTF-8 strings, position packing and the protocol's fixed-width helpers.
//!
//! These are the parts of the Java edition format that are easy to get subtly
//! wrong (negative VarInts, the 26+26+12 position layout), so they live in one
//! place with tests rather than being re-implemented per packet.

use std::io::{self, Read, Write};

pub const MAX_VARINT_BYTES: usize = 5;
pub const MAX_VARLONG_BYTES: usize = 10;

fn overflow(what: &str) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidData, format!("{what} is too long"))
}

/// Read a signed 32-bit VarInt.
///
/// The encoding is LEB128 with a five byte ceiling; the value is read as a
/// little-endian two's complement integer, which is why the accumulator is a
/// u32 and the cast happens at the end.
pub fn read_varint<R: Read>(reader: &mut R) -> io::Result<i32> {
    let mut value: u32 = 0;
    for index in 0..MAX_VARINT_BYTES {
        let mut byte = [0u8; 1];
        reader.read_exact(&mut byte)?;
        let payload = u32::from(byte[0] & 0x7f);
        // The fifth byte only contributes three meaningful bits; anything above
        // that does not fit in an i32 and is rejected rather than silently
        // truncated.
        if index == MAX_VARINT_BYTES - 1 && payload > 0x0f {
            return Err(overflow("VarInt"));
        }
        value |= payload << (7 * index);
        if byte[0] & 0x80 == 0 {
            return Ok(value as i32);
        }
    }
    Err(overflow("VarInt"))
}

/// Read a signed 64-bit VarLong.
pub fn read_varlong<R: Read>(reader: &mut R) -> io::Result<i64> {
    let mut value: u64 = 0;
    for index in 0..MAX_VARLONG_BYTES {
        let mut byte = [0u8; 1];
        reader.read_exact(&mut byte)?;
        let payload = u64::from(byte[0] & 0x7f);
        if index == MAX_VARLONG_BYTES - 1 && payload > 0x01 {
            return Err(overflow("VarLong"));
        }
        value |= payload << (7 * index);
        if byte[0] & 0x80 == 0 {
            return Ok(value as i64);
        }
    }
    Err(overflow("VarLong"))
}

/// Encode a signed 32-bit VarInt, appending to `out`.
pub fn write_varint<W: Write>(writer: &mut W, value: i32) -> io::Result<()> {
    let mut remaining = value as u32;
    loop {
        if remaining & !0x7f == 0 {
            return writer.write_all(&[remaining as u8]);
        }
        writer.write_all(&[((remaining & 0x7f) | 0x80) as u8])?;
        remaining >>= 7;
    }
}

/// Encode a signed 64-bit VarLong, appending to `out`.
pub fn write_varlong<W: Write>(writer: &mut W, value: i64) -> io::Result<()> {
    let mut remaining = value as u64;
    loop {
        if remaining & !0x7f == 0 {
            return writer.write_all(&[remaining as u8]);
        }
        writer.write_all(&[((remaining & 0x7f) | 0x80) as u8])?;
        remaining >>= 7;
    }
}

/// Convenience wrapper used by the tests and by packet writers that build a
/// buffer before framing it.
pub fn varint_bytes(value: i32) -> Vec<u8> {
    let mut out = Vec::with_capacity(MAX_VARINT_BYTES);
    write_varint(&mut out, value).expect("writing to a Vec cannot fail");
    out
}

pub fn encode_varint(value: i32) -> Vec<u8> {
    varint_bytes(value)
}

/// Read the length-prefixed UTF-8 string the protocol uses everywhere
/// (`VarInt` byte count, then that many bytes).
pub fn read_string<R: Read>(reader: &mut R) -> io::Result<String> {
    let len = read_varint(reader)?;
    if len < 0 {
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            "negative string length",
        ));
    }
    // A packet is at most 2 MiB on the wire, so a length above that is a
    // malformed frame rather than a string worth allocating for.
    if len > 2 * 1024 * 1024 {
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            "string length exceeds the packet limit",
        ));
    }
    let mut buf = vec![0u8; len as usize];
    reader.read_exact(&mut buf)?;
    String::from_utf8(buf).map_err(|err| io::Error::new(io::ErrorKind::InvalidData, err))
}

pub fn write_string<W: Write>(writer: &mut W, value: &str) -> io::Result<()> {
    let bytes = value.as_bytes();
    write_varint(writer, bytes.len() as i32)?;
    writer.write_all(bytes)
}

pub fn read_u16<R: Read>(reader: &mut R) -> io::Result<u16> {
    let mut buf = [0u8; 2];
    reader.read_exact(&mut buf)?;
    Ok(u16::from_be_bytes(buf))
}

pub fn read_i64<R: Read>(reader: &mut R) -> io::Result<i64> {
    let mut buf = [0u8; 8];
    reader.read_exact(&mut buf)?;
    Ok(i64::from_be_bytes(buf))
}

pub fn write_u16<W: Write>(writer: &mut W, value: u16) -> io::Result<()> {
    writer.write_all(&value.to_be_bytes())
}

pub fn write_i64<W: Write>(writer: &mut W, value: i64) -> io::Result<()> {
    writer.write_all(&value.to_be_bytes())
}

/// A block position as the protocol packs it: X in the high 26 bits, Z in the
/// next 26, Y in the low 12. All three are signed, and the narrow fields rely
/// on two's complement sign extension.
pub fn pack_position(x: i32, y: i32, z: i32) -> i64 {
    ((x as i64 & 0x3ff_ffff) << 38) | ((z as i64 & 0x3ff_ffff) << 12) | (y as i64 & 0xfff)
}

pub fn unpack_position(packed: i64) -> (i32, i32, i32) {
    // Sign-extend the 26-bit fields by shifting left then arithmetically right.
    let x = (packed >> 38) as i32;
    let y = ((packed << 52) >> 52) as i32;
    let z = ((packed << 26) >> 38) as i32;
    (x, y, z)
}

/// Rotation on the wire: one byte mapping 0..360 degrees onto 0..256.
pub fn pack_angle(degrees: f32) -> u8 {
    ((degrees.rem_euclid(360.0) / 360.0) * 256.0).round() as u8
}

pub fn unpack_angle(byte: u8) -> f32 {
    f32::from(byte) * 360.0 / 256.0
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Cursor;

    fn roundtrip(value: i32) {
        let mut buf = Vec::new();
        write_varint(&mut buf, value).unwrap();
        assert!(
            buf.len() <= MAX_VARINT_BYTES,
            "{value} used {} bytes",
            buf.len()
        );
        let mut cursor = Cursor::new(buf);
        assert_eq!(read_varint(&mut cursor).unwrap(), value);
    }

    #[test]
    fn varint_roundtrips_boundaries() {
        for value in [
            0,
            1,
            2,
            127,
            128,
            255,
            256,
            16_383,
            16_384,
            2_097_151,
            2_097_152,
            i32::MAX,
            -1,
            -2,
            -128,
            -16_384,
            i32::MIN,
        ] {
            roundtrip(value);
        }
    }

    #[test]
    fn varint_matches_known_encodings() {
        assert_eq!(varint_bytes(0), vec![0x00]);
        assert_eq!(varint_bytes(1), vec![0x01]);
        assert_eq!(varint_bytes(127), vec![0x7f]);
        assert_eq!(varint_bytes(128), vec![0x80, 0x01]);
        assert_eq!(varint_bytes(255), vec![0xff, 0x01]);
        assert_eq!(
            varint_bytes(2_147_483_647),
            vec![0xff, 0xff, 0xff, 0xff, 0x07]
        );
        // Negative values always use the full five bytes.
        assert_eq!(varint_bytes(-1), vec![0xff, 0xff, 0xff, 0xff, 0x0f]);
    }

    #[test]
    fn varlong_roundtrips() {
        for value in [0i64, 1, -1, i64::MAX, i64::MIN, 1 << 40, -(1 << 40)] {
            let mut buf = Vec::new();
            write_varlong(&mut buf, value).unwrap();
            let mut cursor = Cursor::new(buf);
            assert_eq!(read_varlong(&mut cursor).unwrap(), value);
        }
    }

    #[test]
    fn overlong_varint_is_rejected() {
        // Six continuation bytes: invalid even though the low bits would fit.
        let mut cursor = Cursor::new(vec![0x80, 0x80, 0x80, 0x80, 0x80, 0x01]);
        assert!(read_varint(&mut cursor).is_err());

        // Five bytes whose last byte carries more than the three allowed bits.
        let mut cursor = Cursor::new(vec![0xff, 0xff, 0xff, 0xff, 0x7f]);
        assert!(read_varint(&mut cursor).is_err());
    }

    #[test]
    fn truncated_varint_is_an_error() {
        let mut cursor = Cursor::new(vec![0x80]);
        assert_eq!(
            read_varint(&mut cursor).unwrap_err().kind(),
            io::ErrorKind::UnexpectedEof
        );
    }

    #[test]
    fn string_roundtrips() {
        let mut buf = Vec::new();
        write_string(&mut buf, "mc.hypixel.net").unwrap();
        assert_eq!(buf[0], 14);
        let mut cursor = Cursor::new(buf);
        assert_eq!(read_string(&mut cursor).unwrap(), "mc.hypixel.net");

        let mut buf = Vec::new();
        write_string(&mut buf, "").unwrap();
        assert_eq!(buf, vec![0x00]);
    }

    #[test]
    fn strings_over_the_packet_limit_are_rejected() {
        let mut cursor = Cursor::new(varint_bytes(3 * 1024 * 1024));
        assert!(read_string(&mut cursor).is_err());
    }

    #[test]
    fn positions_roundtrip_including_negative() {
        for (x, y, z) in [
            (0, 0, 0),
            (1, 2, 3),
            (-1, -1, -1),
            (29_999_984, 200, -29_999_984),
            (-33_554_432, 0, 33_554_431),
            (100, 319, -100),
        ] {
            assert_eq!(unpack_position(pack_position(x, y, z)), (x, y, z));
        }
    }

    #[test]
    fn position_layout_matches_vanilla() {
        // x=1, y=2, z=3 packs as (1 << 38) | (3 << 12) | 2.
        assert_eq!(pack_position(1, 2, 3), (1 << 38) | (3 << 12) | 2);
    }

    #[test]
    fn angles_wrap() {
        assert_eq!(pack_angle(0.0), 0);
        assert_eq!(pack_angle(90.0), 64);
        assert_eq!(pack_angle(180.0), 128);
        assert_eq!(pack_angle(360.0), 0);
        assert_eq!(pack_angle(-90.0), 192);
        assert!((unpack_angle(64) - 90.0).abs() < 0.01);
    }
}
