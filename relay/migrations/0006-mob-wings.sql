-- Two more wings, themed after Minecraft's own mobs: the ender dragon's cracked
-- hide and the phantom's shredded membrane. Both are already the same geometry
-- the earlier four use, so this only adds catalog rows — the 1.8.9 client ships
-- the meshes (models/wings/<id>.obj) and their textures.
--
-- Run once on an existing database before deploying the relay.

INSERT OR IGNORE INTO shop_items (id, kind, name, description, price, active, position, updated_at) VALUES
  ('ender_dragon_wings', 'wings', 'Ender Dragon Wings', 'Black dragon hide webbed with glowing magenta cracks.', 2000, 1, 23, 0),
  ('phantom_wings', 'wings', 'Phantom Wings', 'Tattered teal membrane that never quite holds together.', 1400, 1, 24, 0);
