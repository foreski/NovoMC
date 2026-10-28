// Novo capes. Textures ship inside both clients and the launcher
// (assets/novomc/textures/capes/<id>.png); the relay only knows ids.
// Free capes are everyone's; the rest are granted per account:
//   INSERT INTO grants (user_id, cape) VALUES ('<users.id>', 'developer');

export const CAPES = [
  { id: 'novo', name: 'Novo', free: true },
  { id: 'aurora', name: 'Aurora', free: true },
  { id: 'ember', name: 'Ember', free: true },
  { id: 'frost', name: 'Frost', free: true },
  { id: 'verdant', name: 'Verdant', free: true },
  { id: 'midnight', name: 'Midnight', free: true },
  { id: 'early_supporter', name: 'Early Supporter', free: false },
  { id: 'developer', name: 'Developer', free: false },
];

export const BANNERS = [
  { id: 'aurora', name: 'Aurora', free: true },
  { id: 'nebula', name: 'Nebula', free: true },
  { id: 'sunset', name: 'Sunset', free: true },
  { id: 'forest', name: 'Forest', free: true },
  { id: 'developer', name: 'Developer', free: false },
];

export async function ownedCapes(db, userId) {
  const { results } = await db.prepare('SELECT cape FROM grants WHERE user_id = ?').bind(userId).all();
  const granted = new Set(results.map((r) => r.cape));
  return CAPES.filter((c) => c.free || granted.has(c.id)).map((c) => c.id);
}

export async function ownedBanners(db, userId) {
  const { results } = await db.prepare("SELECT cape FROM grants WHERE user_id = ? AND cape LIKE 'banner:%'").bind(userId).all();
  const granted = new Set(results.map((r) => r.cape.slice('banner:'.length)));
  return BANNERS.filter((b) => b.free || granted.has(b.id)).map((b) => b.id);
}
