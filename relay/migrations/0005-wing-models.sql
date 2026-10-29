-- Replaces the three hand-built wings with the four mesh wings the 1.8.9 client
-- now ships (assets/novomc/models/wings/<id>.obj). The old ids no longer have a
-- model, so they leave the catalog. Anyone who owned or was wearing one is
-- moved to the closest new pair, so nothing already paid for is lost.
--
-- Run once on an existing database before deploying the relay.

-- 1. Hand out the replacement wings first, so the grant check can't strand an owner.
INSERT OR IGNORE INTO grants (user_id, cape)
  SELECT user_id, 'wing:' || CASE cape
      WHEN 'wing:angel_wings'   THEN 'monochrome_wings'
      WHEN 'wing:demon_wings'   THEN 'violet_wings'
      WHEN 'wing:crystal_wings' THEN 'azure_wings'
    END
    FROM grants
   WHERE cape IN ('wing:angel_wings', 'wing:demon_wings', 'wing:crystal_wings');

-- 2. Drop the old grants and anything still worn in the wing slot.
DELETE FROM grants WHERE cape IN ('wing:angel_wings', 'wing:demon_wings', 'wing:crystal_wings');

UPDATE users
   SET wing = CASE wing
       WHEN 'angel_wings'   THEN 'monochrome_wings'
       WHEN 'demon_wings'   THEN 'violet_wings'
       WHEN 'crystal_wings' THEN 'azure_wings'
       ELSE wing
     END
 WHERE wing IN ('angel_wings', 'demon_wings', 'crystal_wings');

-- 3. Swap the catalog rows: the old wings can no longer be rendered or sold.
DELETE FROM shop_items WHERE id IN ('angel_wings', 'demon_wings', 'crystal_wings');

INSERT OR IGNORE INTO shop_items (id, kind, name, description, price, active, position, updated_at) VALUES
  ('violet_wings', 'wings', 'Violet Wings', 'Deep violet membrane on a bright lavender frame.', 900, 1, 19, 0),
  ('verdant_wings', 'wings', 'Verdant Wings', 'Mossy green membrane with glowing leaf-green ribs.', 900, 1, 20, 0),
  ('monochrome_wings', 'wings', 'Monochrome Wings', 'Bone-white membrane on gunmetal ribs.', 1200, 1, 21, 0),
  ('azure_wings', 'wings', 'Azure Wings', 'Ocean-blue membrane on steel-blue spars.', 1000, 1, 22, 0);
