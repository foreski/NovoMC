//! Shared HTTP client and retry helper.

use std::future::Future;
use std::sync::OnceLock;
use std::time::Duration;

use reqwest::Client;

use crate::error::AppResult;

/// Attempts after the first one before giving up.
const RETRIES: u32 = 3;

pub fn client() -> &'static Client {
    static CLIENT: OnceLock<Client> = OnceLock::new();
    CLIENT.get_or_init(|| {
        Client::builder()
            .user_agent(concat!("NovoMC/", env!("CARGO_PKG_VERSION")))
            .connect_timeout(Duration::from_secs(15))
            .read_timeout(Duration::from_secs(30))
            .build()
            .expect("failed to build HTTP client")
    })
}

/// GETs `url` and returns the body, retrying transient failures.
pub async fn get_bytes(url: &str) -> AppResult<Vec<u8>> {
    retry(|| async {
        let resp = client().get(url).send().await?.error_for_status()?;
        Ok(resp.bytes().await?.to_vec())
    })
    .await
}

/// Like [`get_bytes`], but a 404 is `None` (and not retried), for probing
/// which repository has a file.
pub async fn get_optional(url: &str) -> AppResult<Option<Vec<u8>>> {
    retry(|| async {
        let resp = client().get(url).send().await?;
        if resp.status() == reqwest::StatusCode::NOT_FOUND {
            return Ok(None);
        }
        Ok(Some(resp.error_for_status()?.bytes().await?.to_vec()))
    })
    .await
}

/// Whether `url` exists (HEAD request; a 404 is `false`).
pub async fn exists(url: &str) -> AppResult<bool> {
    retry(|| async {
        let resp = client().head(url).send().await?;
        if resp.status() == reqwest::StatusCode::NOT_FOUND {
            return Ok(false);
        }
        resp.error_for_status()?;
        Ok(true)
    })
    .await
}

/// Runs `op` until it succeeds, it fails with a non-retryable error, or the
/// retries run out. Waits 1s, 2s, 4s between attempts.
pub async fn retry<T, F, Fut>(mut op: F) -> AppResult<T>
where
    F: FnMut() -> Fut,
    Fut: Future<Output = AppResult<T>>,
{
    let mut attempt = 0;
    loop {
        match op().await {
            Err(e) if attempt < RETRIES && e.is_retryable() => {
                attempt += 1;
                tokio::time::sleep(Duration::from_millis(500 << attempt)).await;
            }
            result => return result,
        }
    }
}
