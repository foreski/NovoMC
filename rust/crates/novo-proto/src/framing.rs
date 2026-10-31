//! Length-prefixed packet framing: every packet is a VarInt byte length followed
//! by a VarInt packet id and the body.

use std::io::{self, Read, Write};

use novo_core::{read_varint, write_varint};

/// Vanilla refuses a packet over 2 MiB; matching that keeps a malicious or
/// corrupt peer from making us allocate without bound.
pub const MAX_PACKET_LEN: i32 = 2 * 1024 * 1024;

pub fn write_packet<W: Write>(writer: &mut W, id: i32, body: &[u8]) -> io::Result<()> {
    let mut payload = Vec::with_capacity(body.len() + 5);
    write_varint(&mut payload, id)?;
    payload.extend_from_slice(body);
    write_varint(writer, payload.len() as i32)?;
    writer.write_all(&payload)
}

/// Read one packet, returning its id and body.
pub fn read_packet<R: Read>(reader: &mut R) -> io::Result<(i32, Vec<u8>)> {
    let len = read_varint(reader)?;
    if !(0..=MAX_PACKET_LEN).contains(&len) {
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            format!("packet length {len} outside 0..={MAX_PACKET_LEN}"),
        ));
    }
    let mut payload = vec![0u8; len as usize];
    reader.read_exact(&mut payload)?;
    if payload.is_empty() {
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            "packet has no id",
        ));
    }
    let mut cursor = std::io::Cursor::new(payload);
    let id = read_varint(&mut cursor)?;
    let start = cursor.position() as usize;
    let body = cursor.into_inner().split_off(start);
    Ok((id, body))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn round_trips_a_packet() {
        let mut buf = Vec::new();
        write_packet(&mut buf, 0x00, b"hello").unwrap();
        assert_eq!(buf[0], 6, "length prefix covers id plus body");
        let mut cursor = std::io::Cursor::new(buf);
        let (id, body) = read_packet(&mut cursor).unwrap();
        assert_eq!(id, 0);
        assert_eq!(body, b"hello");
    }

    #[test]
    fn round_trips_a_multi_byte_id() {
        let mut buf = Vec::new();
        write_packet(&mut buf, 300, &[]).unwrap();
        let mut cursor = std::io::Cursor::new(buf);
        let (id, body) = read_packet(&mut cursor).unwrap();
        assert_eq!(id, 300);
        assert!(body.is_empty());
    }

    #[test]
    fn rejects_an_oversized_length() {
        let mut buf = Vec::new();
        novo_core::write_varint(&mut buf, MAX_PACKET_LEN + 1).unwrap();
        let mut cursor = std::io::Cursor::new(buf);
        assert!(read_packet(&mut cursor).is_err());
    }

    #[test]
    fn rejects_an_empty_packet() {
        let mut cursor = std::io::Cursor::new(vec![0u8]);
        assert!(read_packet(&mut cursor).is_err());
    }
}
