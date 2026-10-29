// Novo capes. Textures ship inside both clients and the launcher
// (assets/novomc/textures/capes/<id>.png); the relay only knows ids.
// Free capes are everyone's; the rest are granted per account:
//   INSERT INTO grants (user_id, cape) VALUES ('<users.id>', 'developer');
//
// Animated capes carry `animated: {frames, fps}` and ship one PNG per frame
// (`<id>_<frame>.png`, see scripts/make-capes.mjs); the clients swap frames
// only while the cape is on screen, so nothing ticks for unrendered capes.
// Adding an animated cape also means adding its frame count to the clients'
// ANIMATED tables (NovoCapesModule on 1.8.9, Cosmetics on modern).

export const CAPES = [
  // Everyone's capes, including the brand cape.
  { id: 'novo', name: 'Novo', free: true },
  { id: 'arctic_cipher', name: 'Arctic Cipher', free: true },
  { id: 'frostpeak', name: 'Frostpeak', free: true },
  { id: 'tidal', name: 'Tidal', free: true },
  { id: 'compass', name: 'Compass', free: true },
  { id: 'astral', name: 'Astral', free: true },
  { id: 'wolfshade', name: 'Wolfshade', free: true },
  // Sold in the Marketplace (prices live in the shop_items table).
  { id: 'inferno', name: 'Inferno', free: false },
  { id: 'venom', name: 'Venom', free: false },
  { id: 'lunar_rose', name: 'Lunar Rose', free: false },
  { id: 'blazeborn', name: 'Blazeborn', free: false },
  { id: 'amethyst_core', name: 'Amethyst Core', free: false },
  { id: 'gilded_crown', name: 'Gilded Crown', free: false },
  { id: 'drake', name: 'Drake', free: false },
  { id: 'skywing', name: 'Skywing', free: false },
  { id: 'solar_eclipse', name: 'Solar Eclipse', free: false },
  // Animated shop capes: 24 smooth frames at 12 fps (scripts/make-capes.mjs).
  { id: 'aurora_flow', name: 'Aurora Flow', free: false, animated: { frames: 24, fps: 12 } },
  { id: 'ember_flow', name: 'Ember Flow', free: false, animated: { frames: 24, fps: 12 } },
  // Granted directly, never sold.
  { id: 'early_supporter', name: 'Early Supporter', free: false },
  { id: 'developer', name: 'Developer', free: false },
];

// Wings: 3D models the 1.8.9 client draws behind the torso, from the meshes in
// assets/novomc/models/wings/<id>.obj. `flap` is the animation the model plays
// (cycles per second and radians); an id that isn't in the client's mesh table
// shows nothing, so only real meshes belong here.
export const WINGS = [
  { id: 'violet_wings', name: 'Violet Wings', free: false, flap: { rate: 1.45, amplitude: 0.6 } },
  { id: 'verdant_wings', name: 'Verdant Wings', free: false, flap: { rate: 1.6, amplitude: 0.66 } },
  { id: 'monochrome_wings', name: 'Monochrome Wings', free: false, flap: { rate: 1.3, amplitude: 0.54 } },
  { id: 'azure_wings', name: 'Azure Wings', free: false, flap: { rate: 1.75, amplitude: 0.72 } },
];

// Hats: OBJ meshes mounted on the head (assets/novomc/models/hats/<id>.obj).
export const HATS = [
  { id: 'wizard_hat', name: 'Wizard Hat', free: false },
  { id: 'demon_horns', name: 'Demon Horns', free: false },
  { id: 'angel_halo', name: 'Angel Halo', free: false },
  { id: 'royal_crown', name: 'Royal Crown', free: false },
  { id: 'crystal_antlers', name: 'Crystal Antlers', free: false },
];

export const BANNERS = [
  { id: 'aurora', name: 'Aurora', free: true },
  { id: 'nebula', name: 'Nebula', free: true },
  { id: 'sunset', name: 'Sunset', free: true },
  { id: 'forest', name: 'Forest', free: true },
  { id: 'circuit', name: 'Circuit', free: false },
  { id: 'starfield', name: 'Starfield', free: false },
  { id: 'developer', name: 'Developer', free: false },
];

export async function ownedCapes(db, userId) {
  const { results } = await db.prepare('SELECT cape FROM grants WHERE user_id = ?').bind(userId).all();
  const granted = new Set(results.map((r) => r.cape));
  return CAPES.filter((c) => c.free || granted.has(c.id)).map((c) => c.id);
}

/** What the clients need to animate a cape, or null for a still texture. */
export function animationOf(id) {
  return CAPES.find((c) => c.id === id)?.animated ?? null;
}

export async function ownedBanners(db, userId) {
  const { results } = await db.prepare("SELECT cape FROM grants WHERE user_id = ? AND cape LIKE 'banner:%'").bind(userId).all();
  const granted = new Set(results.map((r) => r.cape.slice('banner:'.length)));
  return BANNERS.filter((b) => b.free || granted.has(b.id)).map((b) => b.id);
}

/** Ids granted under a `prefix:` key, e.g. `wing:violet_wings`. */
async function grantedIds(db, userId, prefix) {
  const { results } = await db.prepare('SELECT cape FROM grants WHERE user_id = ? AND cape LIKE ?').bind(userId, `${prefix}:%`).all();
  return new Set(results.map((r) => r.cape.slice(prefix.length + 1)));
}

export async function ownedWings(db, userId) {
  const granted = await grantedIds(db, userId, 'wing');
  return WINGS.filter((w) => w.free || granted.has(w.id)).map((w) => w.id);
}

export async function ownedHats(db, userId) {
  const granted = await grantedIds(db, userId, 'hat');
  return HATS.filter((h) => h.free || granted.has(h.id)).map((h) => h.id);
}

/** The catalog a shop kind sells (shop kinds are `wings` and `hat`). */
export function catalogFor(kind) {
  if (kind === 'cape') return CAPES;
  if (kind === 'banner') return BANNERS;
  if (kind === 'wings') return WINGS;
  if (kind === 'hat') return HATS;
  return null;
}
