-- Apply to the existing novo-accounts D1 database before deploying the shop catalog API.
-- Seeds the launch catalog; INSERT OR IGNORE keeps later admin edits safe if this is re-run.
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

-- Retire the placeholder cape ids from before the art pack landed. Re-running this
-- file after an earlier version also cleans them up.
DELETE FROM shop_items WHERE id IN (
  'coral', 'graphite', 'orchid', 'dune', 'prism', 'solar_flare', 'deep_space', 'pulse', 'comet'
);

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
  ('ember_flow', 'cape', 'Ember Flow', 'Living flames climbing the cape.', 750, 1, 13, 0);
