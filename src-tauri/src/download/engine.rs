//! Parallel, verified file downloads.
//!
//! Each file is skipped if it already exists with the right size and SHA1;
//! otherwise it is streamed to `<file>.part`, hashed on the fly, checked, and
//! renamed into place. Failed downloads are retried; cancelling stops all
//! in-flight transfers.

use std::collections::HashSet;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering::Relaxed};

use futures_util::{stream, TryStreamExt};
use sha1::{Digest, Sha1};
use tokio::io::AsyncWriteExt;
use tokio_util::sync::CancellationToken;

use super::http;
use super::plan::FileTask;
use crate::error::{AppError, AppResult};

/// Files downloaded at the same time.
pub const CONCURRENCY: usize = 16;

/// Live counters, read by the UI progress ticker.
#[derive(Debug, Default)]
pub struct Progress {
    pub files_done: AtomicU64,
    pub files_total: AtomicU64,
    pub bytes_done: AtomicU64,
    pub bytes_total: AtomicU64,
}

/// Lowercase hex SHA1 of a file on disk (blocking).
pub fn hash_file(path: &Path) -> std::io::Result<String> {
    let mut file = std::fs::File::open(path)?;
    let mut hasher = Sha1::new();
    std::io::copy(&mut file, &mut hasher)?;
    Ok(hex(&hasher.finalize()))
}

/// Lowercase hex SHA1 of bytes in memory.
pub fn sha1_hex(bytes: &[u8]) -> String {
    hex(&Sha1::digest(bytes))
}

fn hex(bytes: &[u8]) -> String {
    bytes.iter().map(|b| format!("{b:02x}")).collect()
}

/// Whether `task.path` already holds the expected file.
async fn is_valid(task: &FileTask) -> AppResult<bool> {
    let meta = match tokio::fs::metadata(&task.path).await {
        Ok(m) => m,
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => return Ok(false),
        Err(e) => return Err(e.into()),
    };
    if task.size > 0 && meta.len() != task.size {
        return Ok(false);
    }
    if task.sha1.is_none() {
        return Ok(true);
    }
    let path = task.path.clone();
    let actual = tokio::task::spawn_blocking(move || hash_file(&path))
        .await
        .map_err(|e| AppError::Other(e.to_string()))??;
    Ok(task.accepts(&actual))
}

/// Streams `url` into `path`, counting bytes, and returns the SHA1.
async fn stream_to(
    url: &str,
    path: &Path,
    progress: &Progress,
    cancel: &CancellationToken,
    written: &mut u64,
) -> AppResult<String> {
    let mut resp = http::client().get(url).send().await?.error_for_status()?;
    let mut file = tokio::fs::File::create(path).await?;
    let mut hasher = Sha1::new();
    loop {
        let chunk = tokio::select! {
            _ = cancel.cancelled() => return Err(AppError::Cancelled),
            chunk = resp.chunk() => chunk?,
        };
        let Some(chunk) = chunk else { break };
        hasher.update(&chunk);
        file.write_all(&chunk).await?;
        *written += chunk.len() as u64;
        progress.bytes_done.fetch_add(chunk.len() as u64, Relaxed);
    }
    file.flush().await?;
    // Close the handle now so the rename below works on Windows.
    drop(file.into_std().await);
    Ok(hex(&hasher.finalize()))
}

/// One download attempt. On failure the partial file and its byte count are rolled back.
async fn download(task: &FileTask, progress: &Progress, cancel: &CancellationToken) -> AppResult<()> {
    if let Some(dir) = task.path.parent() {
        tokio::fs::create_dir_all(dir).await?;
    }
    let mut part = task.path.clone().into_os_string();
    part.push(".part");
    let part = PathBuf::from(part);

    let mut written = 0;
    let result = stream_to(&task.url, &part, progress, cancel, &mut written)
        .await
        .and_then(|digest| {
            if task.accepts(&digest) {
                Ok(())
            } else {
                Err(AppError::Checksum { file: task.path.file_name().unwrap_or_default().to_string_lossy().into_owned() })
            }
        });

    match result {
        Ok(()) => Ok(tokio::fs::rename(&part, &task.path).await?),
        Err(e) => {
            progress.bytes_done.fetch_sub(written, Relaxed);
            let _ = tokio::fs::remove_file(&part).await;
            Err(e)
        }
    }
}

/// Makes sure one file is present and valid, downloading it if needed.
pub async fn ensure_file(task: &FileTask, progress: &Progress, cancel: &CancellationToken) -> AppResult<()> {
    if cancel.is_cancelled() {
        return Err(AppError::Cancelled);
    }
    if is_valid(task).await? {
        progress.bytes_done.fetch_add(task.size, Relaxed);
    } else {
        http::retry(|| download(task, progress, cancel)).await?;
    }
    progress.files_done.fetch_add(1, Relaxed);
    Ok(())
}

/// Downloads and verifies every task, `CONCURRENCY` at a time. Stops at the
/// first file that still fails after its retries.
pub async fn download_all(tasks: Vec<FileTask>, progress: &Progress, cancel: &CancellationToken) -> AppResult<()> {
    let mut seen = HashSet::new();
    let tasks: Vec<FileTask> = tasks.into_iter().filter(|t| seen.insert(t.path.clone())).collect();

    progress.files_total.store(tasks.len() as u64, Relaxed);
    progress.bytes_total.store(tasks.iter().map(|t| t.size).sum(), Relaxed);
    progress.files_done.store(0, Relaxed);
    progress.bytes_done.store(0, Relaxed);

    stream::iter(tasks.into_iter().map(Ok::<_, AppError>))
        .try_for_each_concurrent(CONCURRENCY, |task| async move { ensure_file(&task, progress, cancel).await })
        .await
}
