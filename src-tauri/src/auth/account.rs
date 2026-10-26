//! Account types shared by offline and Microsoft login.

use serde::{Deserialize, Serialize};

/// How an account signs in.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum AccountKind {
    /// Username only, no password. Works in singleplayer and on servers
    /// running with `online-mode=false`.
    Offline,
    /// Microsoft account (step 2). Its tokens live in the OS credential store.
    Microsoft,
}

/// A saved account. Holds no secrets, so it is safe to keep in `accounts.json`.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Account {
    /// Minecraft profile UUID (hyphenated). Also the account's ID in NovoMC.
    pub uuid: String,
    pub username: String,
    pub kind: AccountKind,
}
