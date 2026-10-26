//! Offline (no-password) accounts.

use md5::{Digest, Md5};

use super::account::{Account, AccountKind};
use crate::error::{AppError, AppResult};

/// Checks a name against Minecraft's rules: 3-16 characters of `A-Z a-z 0-9 _`.
pub fn validate_username(name: &str) -> AppResult<()> {
    let ok_len = (3..=16).contains(&name.len());
    let ok_chars = name.chars().all(|c| c.is_ascii_alphanumeric() || c == '_');
    if ok_len && ok_chars {
        Ok(())
    } else {
        Err(AppError::Other(
            "Usernames must be 3-16 characters: letters, numbers and _".into(),
        ))
    }
}

/// The UUID vanilla servers assign to an offline player: Java's
/// `UUID.nameUUIDFromBytes("OfflinePlayer:<name>")`, a version 3 (MD5) UUID.
/// Using the same value keeps offline saves and server data consistent.
pub fn offline_uuid(name: &str) -> String {
    let digest = Md5::digest(format!("OfflinePlayer:{name}").as_bytes());
    let mut b = [0u8; 16];
    b.copy_from_slice(&digest);
    b[6] = (b[6] & 0x0f) | 0x30; // version 3
    b[8] = (b[8] & 0x3f) | 0x80; // IETF variant
    let h: String = b.iter().map(|x| format!("{x:02x}")).collect();
    format!("{}-{}-{}-{}-{}", &h[0..8], &h[8..12], &h[12..16], &h[16..20], &h[20..32])
}

/// Builds a validated offline account for `name`.
pub fn new_account(name: &str) -> AppResult<Account> {
    let name = name.trim();
    validate_username(name)?;
    Ok(Account {
        uuid: offline_uuid(name),
        username: name.to_string(),
        kind: AccountKind::Offline,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn matches_vanilla_offline_uuid() {
        assert_eq!(offline_uuid("Notch"), "b50ad385-829d-3141-a216-7e7d7539ba7f");
    }

    #[test]
    fn validates_names() {
        assert!(validate_username("Steve_123").is_ok());
        assert!(validate_username("ab").is_err());
        assert!(validate_username("this_name_is_too_long").is_err());
        assert!(validate_username("bad name").is_err());
        assert!(validate_username("näme").is_err());
    }
}
