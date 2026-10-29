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
  wing TEXT,
  hat TEXT,
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

-- Wallets and an idempotent coin ledger for shop purchases and Stripe webhooks.
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

-- The Marketplace catalog, kept in D1 so admins can change it without a deploy
-- (`/admin/shop/items` with SHOP_TOKEN; see docs/SHOP_BOT.md). The seed is the
-- launch catalog; INSERT OR IGNORE lets this file be re-applied safely.
CREATE TABLE IF NOT EXISTS shop_items (
  id TEXT PRIMARY KEY,
  kind TEXT NOT NULL,
  name TEXT NOT NULL,
  description TEXT NOT NULL,
  price INTEGER NOT NULL CHECK (price > 0),
  active INTEGER NOT NULL DEFAULT 1,
  position INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS shop_items_order ON shop_items (active, position);
INSERT OR IGNORE INTO shop_items (id, kind, name, description, price, active, position, updated_at) VALUES
  ('circuit', 'banner', 'Circuit', 'Electric lines on a midnight-blue profile banner.', 150, 1, 1, 0),
  ('starfield', 'banner', 'Starfield', 'A quiet sweep of light across deep space.', 150, 1, 2, 0),
  ('inferno', 'cape', 'Inferno', 'Crackling flames on a molten red cape.', 200, 1, 3, 0),
  ('venom', 'cape', 'Venom', 'Toxic green scales with a serpent edge.', 250, 1, 4, 0),
  ('lunar_rose', 'cape', 'Lunar Rose', 'Moonlit rose stripes with a soft pink glow.', 250, 1, 5, 0),
  ('blazeborn', 'cape', 'Blazeborn', 'A fiery sigil on a dark ember cloak.', 300, 1, 6, 0),
  ('amethyst_core', 'cape', 'Amethyst Core', 'Faceted amethyst bands around a bright core.', 350, 1, 7, 0),
  ('gilded_crown', 'cape', 'Gilded Crown', 'Gold filigree on royal purple.', 400, 1, 8, 0),
  ('drake', 'cape', 'Drake', 'A dragon mark in moss and ember.', 450, 1, 9, 0),
  ('skywing', 'cape', 'Skywing', 'Wing-light blue with a silver crest.', 500, 1, 10, 0),
  ('solar_eclipse', 'cape', 'Solar Eclipse', 'A black sun ringed in gold.', 600, 1, 11, 0),
  ('aurora_flow', 'cape', 'Aurora Flow', 'A cyan ribbon flowing down the cape.', 650, 1, 12, 0),
  ('ember_flow', 'cape', 'Ember Flow', 'Living flames climbing the cape.', 750, 1, 13, 0),
  ('wizard_hat', 'hat', 'Wizard Hat', 'A towering midnight hat with a gold brim.', 350, 1, 14, 0),
  ('demon_horns', 'hat', 'Demon Horns', 'Curved obsidian horns with crimson tips.', 400, 1, 15, 0),
  ('angel_halo', 'hat', 'Angel Halo', 'A floating ring of gold above the head.', 450, 1, 16, 0),
  ('royal_crown', 'hat', 'Royal Crown', 'A jewelled gold crown for the rightful ruler.', 600, 1, 17, 0),
  ('crystal_antlers', 'hat', 'Crystal Antlers', 'Branching ice crystals that catch the light.', 700, 1, 18, 0),
  ('violet_wings', 'wings', 'Violet Wings', 'Deep violet membrane on a bright lavender frame.', 900, 1, 19, 0),
  ('verdant_wings', 'wings', 'Verdant Wings', 'Mossy green membrane with glowing leaf-green ribs.', 900, 1, 20, 0),
  ('monochrome_wings', 'wings', 'Monochrome Wings', 'Bone-white membrane on gunmetal ribs.', 1200, 1, 21, 0),
  ('azure_wings', 'wings', 'Azure Wings', 'Ocean-blue membrane on steel-blue spars.', 1000, 1, 22, 0);
