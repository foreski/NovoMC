-- The hats were rebuilt from scratch and four more joined them. The five
-- existing ids keep their rows (the meshes behind them were replaced, so
-- nothing a player owns changes), and the new four are added here.
--
-- The 1.8.9 client ships the meshes (assets/novomc/models/hats/<id>.obj).
-- Run once on an existing database before deploying the relay.

INSERT OR IGNORE INTO shop_items (id, kind, name, description, price, active, position, updated_at) VALUES
  ('ender_crown', 'hat', 'Ender Crown', 'Obsidian spikes crowned with floating end crystal shards.', 900, 1, 25, 0),
  ('phantom_hood', 'hat', 'Phantom Hood', 'The tattered hood of a phantom, torn along the back.', 750, 1, 26, 0),
  ('miner_helmet', 'hat', "Miner's Helmet", 'A battered iron helmet with a lamp that never goes out.', 500, 1, 27, 0),
  ('golden_laurel', 'hat', 'Golden Laurel', 'A wreath of gilded leaves for the champion of the arena.', 650, 1, 28, 0);
