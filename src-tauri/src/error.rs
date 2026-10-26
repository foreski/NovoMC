//! Shared error type. Serializes to a plain message so Tauri commands can
//! return `AppResult<T>` and the UI receives a readable string.

use serde::{Serialize, Serializer};

#[derive(Debug, thiserror::Error)]
pub enum AppError {
    #[error("I/O error: {0}")]
    Io(#[from] std::io::Error),
    #[error("JSON error: {0}")]
    Json(#[from] serde_json::Error),
    #[error("Network error: {0}")]
    Http(#[from] reqwest::Error),
    #[error("Checksum mismatch for {file}")]
    Checksum { file: String },
    #[error("Cancelled")]
    Cancelled,
    #[error("{0}")]
    Other(String),
}

impl AppError {
    /// Whether retrying the same operation might succeed.
    pub fn is_retryable(&self) -> bool {
        matches!(self, Self::Io(_) | Self::Http(_) | Self::Checksum { .. })
    }
}

impl Serialize for AppError {
    fn serialize<S: Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
        serializer.serialize_str(&self.to_string())
    }
}

pub type AppResult<T> = Result<T, AppError>;
