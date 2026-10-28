-- Novo Client accounts (D1). Paste into the D1 console, or:
--   npx wrangler d1 execute novo-accounts --remote --file relay/schema.sql
CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  discord_id TEXT NOT NULL UNIQUE,
  discord_name TEXT,
  avatar TEXT,
  display_name TEXT NOT NULL,
  cape TEXT,
  banner TEXT,
  created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS sessions (
  token_hash TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  expires_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS sessions_user ON sessions (user_id);
CREATE TABLE IF NOT EXISTS links (
  uuid TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  name TEXT NOT NULL,
  linked_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS links_user ON links (user_id);
CREATE TABLE IF NOT EXISTS settings (
  user_id TEXT NOT NULL,
  slot TEXT NOT NULL,
  data TEXT NOT NULL,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (user_id, slot)
);
CREATE TABLE IF NOT EXISTS grants (
  user_id TEXT NOT NULL,
  cape TEXT NOT NULL,
  PRIMARY KEY (user_id, cape)
);
