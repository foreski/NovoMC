//! Packet framing and the server-list ping.
//!
//! The status exchange has not changed since 1.7, so the same code talks to a
//! 1.8.9 server and to 26.3: a handshake with next-state 1, a status request,
//! and a ping/pong for the round trip. Everything after that — login,
//! encryption, play packets — is deliberately absent.

pub mod framing;
pub mod status;

pub use framing::{read_packet, write_packet};
pub use status::{Status, ping};
