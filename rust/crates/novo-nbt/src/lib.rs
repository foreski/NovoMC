//! Named Binary Tag: the format behind `level.dat`, every chunk in a region
//! file, and (unnamed) the payloads of a few packets.
//!
//! Both the on-disk form (a named root, gzip or zlib around it) and the newer
//! unnamed root used on the network are supported. Strings are read as plain
//! UTF-8; Java's modified UTF-8 only differs for U+0000, which no world data
//! contains.

use std::collections::BTreeMap;
use std::io::{self, Cursor, Read, Write};

use flate2::Compression;
use flate2::read::{GzDecoder, ZlibDecoder};
use flate2::write::{GzEncoder, ZlibEncoder};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TagType {
    End,
    Byte,
    Short,
    Int,
    Long,
    Float,
    Double,
    ByteArray,
    String,
    List,
    Compound,
    IntArray,
    LongArray,
}

impl TagType {
    pub fn id(self) -> u8 {
        match self {
            TagType::End => 0,
            TagType::Byte => 1,
            TagType::Short => 2,
            TagType::Int => 3,
            TagType::Long => 4,
            TagType::Float => 5,
            TagType::Double => 6,
            TagType::ByteArray => 7,
            TagType::String => 8,
            TagType::List => 9,
            TagType::Compound => 10,
            TagType::IntArray => 11,
            TagType::LongArray => 12,
        }
    }

    pub fn from_id(id: u8) -> io::Result<Self> {
        Ok(match id {
            0 => TagType::End,
            1 => TagType::Byte,
            2 => TagType::Short,
            3 => TagType::Int,
            4 => TagType::Long,
            5 => TagType::Float,
            6 => TagType::Double,
            7 => TagType::ByteArray,
            8 => TagType::String,
            9 => TagType::List,
            10 => TagType::Compound,
            11 => TagType::IntArray,
            12 => TagType::LongArray,
            other => {
                return Err(io::Error::new(
                    io::ErrorKind::InvalidData,
                    format!("unknown NBT tag id {other}"),
                ));
            }
        })
    }
}

#[derive(Debug, Clone, PartialEq)]
pub enum Tag {
    Byte(i8),
    Short(i16),
    Int(i32),
    Long(i64),
    Float(f32),
    Double(f64),
    ByteArray(Vec<u8>),
    String(String),
    /// Element type is kept so a list round-trips byte for byte, including an
    /// empty one, which vanilla writes with an element type of `End`.
    List(TagType, Vec<Tag>),
    Compound(Compound),
    IntArray(Vec<i32>),
    LongArray(Vec<i64>),
}

impl Tag {
    pub fn type_of(&self) -> TagType {
        match self {
            Tag::Byte(_) => TagType::Byte,
            Tag::Short(_) => TagType::Short,
            Tag::Int(_) => TagType::Int,
            Tag::Long(_) => TagType::Long,
            Tag::Float(_) => TagType::Float,
            Tag::Double(_) => TagType::Double,
            Tag::ByteArray(_) => TagType::ByteArray,
            Tag::String(_) => TagType::String,
            Tag::List(..) => TagType::List,
            Tag::Compound(_) => TagType::Compound,
            Tag::IntArray(_) => TagType::IntArray,
            Tag::LongArray(_) => TagType::LongArray,
        }
    }

    pub fn as_compound(&self) -> Option<&Compound> {
        match self {
            Tag::Compound(c) => Some(c),
            _ => None,
        }
    }

    pub fn as_list(&self) -> Option<&[Tag]> {
        match self {
            Tag::List(_, items) => Some(items),
            _ => None,
        }
    }

    pub fn as_str(&self) -> Option<&str> {
        match self {
            Tag::String(s) => Some(s),
            _ => None,
        }
    }

    pub fn as_i32(&self) -> Option<i32> {
        match self {
            Tag::Int(v) => Some(*v),
            _ => None,
        }
    }

    pub fn as_i64(&self) -> Option<i64> {
        match self {
            Tag::Long(v) => Some(*v),
            _ => None,
        }
    }

    pub fn as_bytes(&self) -> Option<&[u8]> {
        match self {
            Tag::ByteArray(v) => Some(v),
            _ => None,
        }
    }

    pub fn as_longs(&self) -> Option<&[i64]> {
        match self {
            Tag::LongArray(v) => Some(v),
            _ => None,
        }
    }
}

/// Insertion-ordered compound with a name index, so writing preserves the order
/// vanilla wrote and lookups stay cheap on the 60-field chunk roots.
#[derive(Debug, Clone, Default, PartialEq)]
pub struct Compound {
    entries: Vec<(String, Tag)>,
    index: BTreeMap<String, usize>,
}

impl Compound {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn with_capacity(capacity: usize) -> Self {
        Self {
            entries: Vec::with_capacity(capacity),
            index: BTreeMap::new(),
        }
    }

    pub fn insert(&mut self, name: impl Into<String>, tag: Tag) -> Option<Tag> {
        let name = name.into();
        match self.index.get(&name) {
            Some(&at) => {
                let old = std::mem::replace(&mut self.entries[at].1, tag);
                Some(old)
            }
            None => {
                self.index.insert(name.clone(), self.entries.len());
                self.entries.push((name, tag));
                None
            }
        }
    }

    pub fn get(&self, name: &str) -> Option<&Tag> {
        self.index.get(name).map(|&at| &self.entries[at].1)
    }

    pub fn get_i32(&self, name: &str) -> Option<i32> {
        self.get(name).and_then(Tag::as_i32)
    }

    pub fn get_i64(&self, name: &str) -> Option<i64> {
        self.get(name).and_then(Tag::as_i64)
    }

    pub fn get_str(&self, name: &str) -> Option<&str> {
        self.get(name).and_then(Tag::as_str)
    }

    pub fn get_compound(&self, name: &str) -> Option<&Compound> {
        self.get(name).and_then(Tag::as_compound)
    }

    pub fn get_list(&self, name: &str) -> Option<&[Tag]> {
        self.get(name).and_then(Tag::as_list)
    }

    pub fn get_bytes(&self, name: &str) -> Option<&[u8]> {
        self.get(name).and_then(Tag::as_bytes)
    }

    pub fn iter(&self) -> impl Iterator<Item = (&str, &Tag)> {
        self.entries.iter().map(|(name, tag)| (name.as_str(), tag))
    }

    pub fn len(&self) -> usize {
        self.entries.len()
    }

    pub fn is_empty(&self) -> bool {
        self.entries.is_empty()
    }
}

impl FromIterator<(String, Tag)> for Compound {
    fn from_iter<I: IntoIterator<Item = (String, Tag)>>(iter: I) -> Self {
        let mut compound = Compound::new();
        for (name, tag) in iter {
            compound.insert(name, tag);
        }
        compound
    }
}

fn invalid(message: impl Into<String>) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidData, message.into())
}

fn write_raw_string<W: Write>(writer: &mut W, value: &str) -> io::Result<()> {
    let bytes = value.as_bytes();
    let len =
        u16::try_from(bytes.len()).map_err(|_| invalid("NBT string longer than 65535 bytes"))?;
    writer.write_all(&len.to_be_bytes())?;
    writer.write_all(bytes)
}

fn read_raw_string<R: Read>(reader: &mut R) -> io::Result<String> {
    let mut len = [0u8; 2];
    reader.read_exact(&mut len)?;
    let len = u16::from_be_bytes(len) as usize;
    let mut buf = vec![0u8; len];
    reader.read_exact(&mut buf)?;
    String::from_utf8(buf).map_err(|err| invalid(err.to_string()))
}

/// Read one named tag. Returns the name and the tag.
pub fn read_tag<R: Read>(reader: &mut R) -> io::Result<(String, Tag)> {
    let mut header = [0u8; 1];
    reader.read_exact(&mut header)?;
    if header[0] == 0 {
        return Err(invalid("root tag is End"));
    }
    let tag_type = TagType::from_id(header[0])?;
    let name = read_raw_string(reader)?;
    let tag = read_payload(reader, tag_type)?;
    Ok((name, tag))
}

/// Read a payload of a known type, for callers that already consumed the header
/// (network NBT omits the name, for instance).
pub fn read_payload<R: Read>(reader: &mut R, tag_type: TagType) -> io::Result<Tag> {
    Ok(match tag_type {
        TagType::End => return Err(invalid("End has no payload")),
        TagType::Byte => {
            let mut b = [0u8; 1];
            reader.read_exact(&mut b)?;
            Tag::Byte(b[0] as i8)
        }
        TagType::Short => {
            let mut b = [0u8; 2];
            reader.read_exact(&mut b)?;
            Tag::Short(i16::from_be_bytes(b))
        }
        TagType::Int => {
            let mut b = [0u8; 4];
            reader.read_exact(&mut b)?;
            Tag::Int(i32::from_be_bytes(b))
        }
        TagType::Long => {
            let mut b = [0u8; 8];
            reader.read_exact(&mut b)?;
            Tag::Long(i64::from_be_bytes(b))
        }
        TagType::Float => {
            let mut b = [0u8; 4];
            reader.read_exact(&mut b)?;
            Tag::Float(f32::from_be_bytes(b))
        }
        TagType::Double => {
            let mut b = [0u8; 8];
            reader.read_exact(&mut b)?;
            Tag::Double(f64::from_be_bytes(b))
        }
        TagType::ByteArray => {
            let len = read_len(reader)?;
            let mut buf = vec![0u8; len];
            reader.read_exact(&mut buf)?;
            Tag::ByteArray(buf)
        }
        TagType::String => Tag::String(read_raw_string(reader)?),
        TagType::List => {
            let mut element = [0u8; 1];
            reader.read_exact(&mut element)?;
            let element_type = TagType::from_id(element[0])?;
            let len = read_len(reader)?;
            if element_type == TagType::End && len > 0 {
                return Err(invalid("list of End with a non-zero length"));
            }
            let mut items = Vec::with_capacity(len.min(4096));
            for _ in 0..len {
                items.push(read_payload(reader, element_type)?);
            }
            Tag::List(element_type, items)
        }
        TagType::Compound => Tag::Compound(read_compound_payload(reader)?),
        TagType::IntArray => {
            let len = read_len(reader)?;
            let mut items = Vec::with_capacity(len.min(4096));
            let mut buf = [0u8; 4];
            for _ in 0..len {
                reader.read_exact(&mut buf)?;
                items.push(i32::from_be_bytes(buf));
            }
            Tag::IntArray(items)
        }
        TagType::LongArray => {
            let len = read_len(reader)?;
            let mut items = Vec::with_capacity(len.min(4096));
            let mut buf = [0u8; 8];
            for _ in 0..len {
                reader.read_exact(&mut buf)?;
                items.push(i64::from_be_bytes(buf));
            }
            Tag::LongArray(items)
        }
    })
}

fn read_len<R: Read>(reader: &mut R) -> io::Result<usize> {
    let mut b = [0u8; 4];
    reader.read_exact(&mut b)?;
    let len = i32::from_be_bytes(b);
    if len < 0 {
        return Err(invalid("negative array length"));
    }
    Ok(len as usize)
}

pub fn read_compound_payload<R: Read>(reader: &mut R) -> io::Result<Compound> {
    let mut compound = Compound::new();
    loop {
        let mut header = [0u8; 1];
        reader.read_exact(&mut header)?;
        if header[0] == 0 {
            return Ok(compound);
        }
        let tag_type = TagType::from_id(header[0])?;
        let name = read_raw_string(reader)?;
        let tag = read_payload(reader, tag_type)?;
        compound.insert(name, tag);
    }
}

pub fn write_tag<W: Write>(writer: &mut W, name: &str, tag: &Tag) -> io::Result<()> {
    writer.write_all(&[tag.type_of().id()])?;
    write_raw_string(writer, name)?;
    write_payload(writer, tag)
}

/// Write a payload with no header, for network NBT and nested values.
pub fn write_payload<W: Write>(writer: &mut W, tag: &Tag) -> io::Result<()> {
    match tag {
        Tag::Byte(v) => writer.write_all(&v.to_be_bytes()),
        Tag::Short(v) => writer.write_all(&v.to_be_bytes()),
        Tag::Int(v) => writer.write_all(&v.to_be_bytes()),
        Tag::Long(v) => writer.write_all(&v.to_be_bytes()),
        Tag::Float(v) => writer.write_all(&v.to_be_bytes()),
        Tag::Double(v) => writer.write_all(&v.to_be_bytes()),
        Tag::ByteArray(items) => {
            writer.write_all(&(items.len() as i32).to_be_bytes())?;
            writer.write_all(items)
        }
        Tag::String(value) => write_raw_string(writer, value),
        Tag::List(element_type, items) => {
            let element = if items.is_empty() {
                *element_type
            } else {
                items[0].type_of()
            };
            writer.write_all(&[element.id()])?;
            writer.write_all(&(items.len() as i32).to_be_bytes())?;
            for item in items {
                write_payload(writer, item)?;
            }
            Ok(())
        }
        Tag::Compound(compound) => write_compound_payload(writer, compound),
        Tag::IntArray(items) => {
            writer.write_all(&(items.len() as i32).to_be_bytes())?;
            for item in items {
                writer.write_all(&item.to_be_bytes())?;
            }
            Ok(())
        }
        Tag::LongArray(items) => {
            writer.write_all(&(items.len() as i32).to_be_bytes())?;
            for item in items {
                writer.write_all(&item.to_be_bytes())?;
            }
            Ok(())
        }
    }
}

pub fn write_compound_payload<W: Write>(writer: &mut W, compound: &Compound) -> io::Result<()> {
    for (name, tag) in compound.iter() {
        write_tag(writer, name, tag)?;
    }
    writer.write_all(&[0])
}

/// Parse a named root tag from decompressed bytes.
pub fn from_bytes(bytes: &[u8]) -> io::Result<(String, Tag)> {
    let mut cursor = Cursor::new(bytes);
    let parsed = read_tag(&mut cursor)?;
    Ok(parsed)
}

/// Parse an unnamed root (network NBT, and how 1.20.2+ writes some payloads).
pub fn from_unnamed_bytes(bytes: &[u8]) -> io::Result<Tag> {
    let mut cursor = Cursor::new(bytes);
    let mut header = [0u8; 1];
    cursor.read_exact(&mut header)?;
    let tag_type = TagType::from_id(header[0])?;
    read_payload(&mut cursor, tag_type)
}

pub fn to_bytes(name: &str, tag: &Tag) -> io::Result<Vec<u8>> {
    let mut out = Vec::new();
    write_tag(&mut out, name, tag)?;
    Ok(out)
}

pub fn to_unnamed_bytes(tag: &Tag) -> io::Result<Vec<u8>> {
    let mut out = Vec::new();
    out.write_all(&[tag.type_of().id()])?;
    write_payload(&mut out, tag)?;
    Ok(out)
}

pub fn from_gzip(bytes: &[u8]) -> io::Result<(String, Tag)> {
    let mut decoder = GzDecoder::new(bytes);
    let mut out = Vec::new();
    decoder.read_to_end(&mut out)?;
    from_bytes(&out)
}

pub fn to_gzip(name: &str, tag: &Tag) -> io::Result<Vec<u8>> {
    let mut encoder = GzEncoder::new(Vec::new(), Compression::default());
    encoder.write_all(&to_bytes(name, tag)?)?;
    encoder.finish()
}

pub fn from_zlib(bytes: &[u8]) -> io::Result<(String, Tag)> {
    let mut decoder = ZlibDecoder::new(bytes);
    let mut out = Vec::new();
    decoder.read_to_end(&mut out)?;
    from_bytes(&out)
}

pub fn to_zlib(name: &str, tag: &Tag) -> io::Result<Vec<u8>> {
    let mut encoder = ZlibEncoder::new(Vec::new(), Compression::default());
    encoder.write_all(&to_bytes(name, tag)?)?;
    encoder.finish()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sample() -> Tag {
        let mut root = Compound::new();
        root.insert("byte", Tag::Byte(-3));
        root.insert("short", Tag::Short(1234));
        root.insert("int", Tag::Int(-70000));
        root.insert("long", Tag::Long(9_000_000_000));
        root.insert("float", Tag::Float(1.5));
        root.insert("double", Tag::Double(-2.25));
        root.insert("string", Tag::String("minecraft:stone".into()));
        root.insert("unicode", Tag::String("héllo — 石".into()));
        root.insert("bytes", Tag::ByteArray(vec![0, 1, 127, 128, 255]));
        root.insert("ints", Tag::IntArray(vec![1, -1, i32::MAX]));
        root.insert("longs", Tag::LongArray(vec![1, -1, i64::MIN]));
        root.insert(
            "list",
            Tag::List(
                TagType::Compound,
                vec![
                    Tag::Compound({
                        let mut inner = Compound::new();
                        inner.insert("Y", Tag::Byte(4));
                        inner
                    }),
                    Tag::Compound(Compound::new()),
                ],
            ),
        );
        root.insert("empty_list", Tag::List(TagType::End, Vec::new()));
        root.insert(
            "list_of_strings",
            Tag::List(
                TagType::String,
                vec![Tag::String("a".into()), Tag::String("b".into())],
            ),
        );
        Tag::Compound(root)
    }

    #[test]
    fn roundtrips_every_type() {
        let tag = sample();
        let bytes = to_bytes("", &tag).unwrap();
        let (name, parsed) = from_bytes(&bytes).unwrap();
        assert_eq!(name, "");
        assert_eq!(parsed, tag);
    }

    #[test]
    fn roundtrips_through_gzip_and_zlib() {
        let tag = sample();
        let gz = to_gzip("root", &tag).unwrap();
        assert_eq!(gz[0], 0x1f, "gzip magic");
        assert_eq!(from_gzip(&gz).unwrap().1, tag);

        let zl = to_zlib("", &tag).unwrap();
        assert_eq!(from_zlib(&zl).unwrap().1, tag);
    }

    #[test]
    fn reads_a_hand_built_compound() {
        // Written out by hand, not by our writer, so the reader is checked
        // against the spec rather than against itself: unnamed compound root
        // holding one Int `xPos` = 7.
        let bytes = [
            10, // Compound
            3, 0, 4, b'x', b'P', b'o', b's', // Int, name "xPos"
            0, 0, 0, 7, // value
            0, // end of compound
        ];
        let tag = from_unnamed_bytes(&bytes).unwrap();
        assert_eq!(tag.as_compound().unwrap().get_i32("xPos"), Some(7));
    }

    #[test]
    fn rejects_truncated_data() {
        let bytes = to_bytes("", &sample()).unwrap();
        for cut in [1, 5, 20, bytes.len() - 1] {
            assert!(from_bytes(&bytes[..cut]).is_err(), "cut at {cut} parsed");
        }
    }

    #[test]
    fn rejects_unknown_tag_id() {
        assert!(from_bytes(&[0x0d, 0, 0]).is_err());
    }

    #[test]
    fn rejects_negative_lengths() {
        let mut bytes = vec![9u8]; // list
        bytes.extend_from_slice(&[0, 0]);
        bytes.push(1); // element type Byte
        bytes.extend_from_slice(&(-1i32).to_be_bytes());
        assert!(from_unnamed_bytes(&bytes).is_err());
    }

    #[test]
    fn compound_getters_and_order() {
        let tag = sample();
        let root = tag.as_compound().unwrap();
        assert_eq!(root.get_i32("int"), Some(-70000));
        assert_eq!(root.get_i64("long"), Some(9_000_000_000));
        assert_eq!(root.get_str("string"), Some("minecraft:stone"));
        assert_eq!(root.get_bytes("bytes").unwrap().len(), 5);
        assert_eq!(root.get_list("list").unwrap().len(), 2);
        assert_eq!(root.iter().next().unwrap().0, "byte");

        // Overwriting keeps the original position.
        let mut copy = root.clone();
        copy.insert("int", Tag::Int(1));
        assert_eq!(copy.iter().nth(3).unwrap().0, "long");
        assert_eq!(copy.get_i32("int"), Some(1));
    }
}
