-- Wings and hats: two more equipped slots on the account, and their shop items.
-- Run once on an existing database (SQLite has no ADD COLUMN IF NOT EXISTS).
ALTER TABLE users ADD COLUMN wing TEXT;
ALTER TABLE users ADD COLUMN hat TEXT;

INSERT OR IGNORE INTO shop_items (id, kind, name, description, price, active, position, updated_at) VALUES
  ('wizard_hat', 'hat', 'Wizard Hat', 'A towering midnight hat with a gold brim.', 350, 1, 14, 0),
  ('demon_horns', 'hat', 'Demon Horns', 'Curved obsidian horns with crimson tips.', 400, 1, 15, 0),
  ('angel_halo', 'hat', 'Angel Halo', 'A floating ring of gold above the head.', 450, 1, 16, 0),
  ('royal_crown', 'hat', 'Royal Crown', 'A jewelled gold crown for the rightful ruler.', 600, 1, 17, 0),
  ('crystal_antlers', 'hat', 'Crystal Antlers', 'Branching ice crystals that catch the light.', 700, 1, 18, 0),
  ('angel_wings', 'wings', 'Celestial Wings', 'Layered ivory feathers that beat slowly.', 900, 1, 19, 0),
  ('demon_wings', 'wings', 'Infernal Wings', 'Broad obsidian wings with crimson edges.', 1000, 1, 20, 0),
  ('crystal_wings', 'wings', 'Prism Wings', 'Angular ice shards that fan out and snap.', 1200, 1, 21, 0);
