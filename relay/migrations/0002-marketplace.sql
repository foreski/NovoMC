-- Apply to the existing novo-accounts D1 database before deploying the shop.
CREATE TABLE IF NOT EXISTS wallets (
  user_id TEXT PRIMARY KEY,
  balance INTEGER NOT NULL DEFAULT 0 CHECK (balance >= 0),
  last_txn TEXT
);
CREATE TABLE IF NOT EXISTS coin_ledger (
  source_id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  delta INTEGER NOT NULL,
  reason TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  applied INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS coin_ledger_user ON coin_ledger (user_id, created_at DESC);
CREATE TABLE IF NOT EXISTS gift_codes (
  code_hash TEXT PRIMARY KEY,
  coins INTEGER NOT NULL CHECK (coins > 0),
  max_uses INTEGER NOT NULL DEFAULT 1 CHECK (max_uses > 0),
  uses INTEGER NOT NULL DEFAULT 0,
  expires_at INTEGER,
  created_at INTEGER NOT NULL,
  last_claim TEXT
);
CREATE TABLE IF NOT EXISTS gift_redemptions (
  code_hash TEXT NOT NULL,
  user_id TEXT NOT NULL,
  claim_id TEXT NOT NULL UNIQUE,
  coins INTEGER NOT NULL,
  redeemed_at INTEGER NOT NULL,
  PRIMARY KEY (code_hash, user_id)
);
