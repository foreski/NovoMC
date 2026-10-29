// Tests the shop catalog API (and that purchases use the stored price) with an
// in-memory D1: `node relay/test/shop-admin.test.mjs`
import assert from 'node:assert/strict';
import worker from '../src/worker.js';
import { d1 } from './d1.mjs';
import { purchase, shopFor } from '../src/shop.js';
import { hatsOf, wingsOf } from '../src/account.js';

const db = d1();
const env = { NOVO_DB: db, SHOP_TOKEN: 'shop-secret' };
const call = async (method, path, data, token = 'shop-secret') => {
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  const res = await worker.fetch(
    new Request(`https://relay.example${path}`, {
      method,
      headers,
      body: data === undefined ? undefined : JSON.stringify(data),
    }),
    env,
  );
  return { status: res.status, body: await res.json() };
};

// Only the shop token opens the catalog, and it lists the seeded launch items in order.
assert.equal((await call('GET', '/admin/shop/items', undefined, null)).status, 401);
assert.equal((await call('GET', '/admin/shop/items', undefined, 'news-token')).status, 401);
let r = await call('GET', '/admin/shop/items');
assert.equal(r.status, 200);
assert.deepEqual(
  r.body.items.map((item) => item.id),
  [
    'circuit', 'starfield', 'inferno', 'venom', 'lunar_rose', 'blazeborn', 'amethyst_core', 'gilded_crown', 'drake', 'skywing',
    'solar_eclipse', 'aurora_flow', 'ember_flow', 'wizard_hat', 'demon_horns', 'angel_halo', 'royal_crown', 'crystal_antlers',
    'violet_wings', 'verdant_wings', 'monochrome_wings', 'azure_wings',
    'ender_dragon_wings', 'phantom_wings',
    'ender_crown', 'phantom_hood', 'miner_helmet', 'golden_laurel',
  ],
);
assert.deepEqual(r.body.items.find((item) => item.id === 'ember_flow'), {
  id: 'ember_flow',
  kind: 'cape',
  name: 'Ember Flow',
  description: 'Living flames climbing the cape.',
  price: 750,
  active: true,
  position: 13,
  updatedAt: 0,
});

// Removing frees the id; adding it back takes the next position.
assert.equal((await call('DELETE', '/admin/shop/items/ember_flow')).status, 200);
assert.equal((await call('DELETE', '/admin/shop/items/ember_flow')).status, 404);
r = await call('POST', '/admin/shop/items', {
  id: 'ember_flow',
  kind: 'cape',
  name: 'Ember Flow',
  description: 'Living flames climbing the cape.',
  price: 900,
});
assert.equal(r.status, 201);
assert.equal(r.body.item.position, 29);
assert.equal(
  (await call('POST', '/admin/shop/items', { id: 'ember_flow', kind: 'cape', name: 'Ember Flow', description: 'Again', price: 900 })).status,
  409,
);

// Unknown cosmetics, free cosmetics, odd kinds, bad prices and stray fields are refused.
const bad = [
  { id: 'nope', kind: 'cape', name: 'Nope', description: 'Not a cape', price: 100 },
  { id: 'tidal', kind: 'cape', name: 'Tidal', description: 'Free already', price: 100 },
  { id: 'ember_flow', kind: 'pet', name: 'Ember', description: 'Not a kind', price: 100 },
  { id: 'ember_flow', kind: 'cape', name: 'Ember', description: 'Bad price', price: 0 },
  { id: 'ember_flow', kind: 'cape', name: 'Ember', description: 'Stray field', price: 10, priceCents: 1 },
];
for (const body of bad) assert.equal((await call('POST', '/admin/shop/items', body)).status, 400, JSON.stringify(body));

// Property edits apply at once, and hiding pulls an item out of the shop.
r = await call('PATCH', '/admin/shop/items/ember_flow', { name: 'Ember Flow II', price: 850, active: false });
assert.equal(r.status, 200);
assert.equal(r.body.item.name, 'Ember Flow II');
assert.equal(r.body.item.price, 850);
assert.equal(r.body.item.active, false);
assert.equal((await call('PATCH', '/admin/shop/items/ember_flow', { kind: 'banner' })).status, 400);
assert.equal((await call('PATCH', '/admin/shop/items/ember_flow', { price: '850' })).status, 400);

db.sqlite.prepare('INSERT INTO users (id, discord_id, display_name, created_at) VALUES (?, ?, ?, ?)').run('u1', '1', 'Tester', Date.now());
db.sqlite.prepare('INSERT INTO wallets (user_id, balance) VALUES (?, ?)').run('u1', 2000);
const user = { id: 'u1' };

let shop = await shopFor(db, user, false);
assert.ok(!shop.items.some((item) => item.id === 'ember_flow'), 'hidden items stay out of the shop');
assert.equal(shop.items.find((item) => item.id === 'aurora_flow').price, 650);
assert.deepEqual(shop.items.find((item) => item.id === 'aurora_flow').animated, { frames: 24, fps: 12 });

// Purchases cost the price stored in D1, and grant the cosmetic.
await call('PATCH', '/admin/shop/items/ember_flow', { active: true, price: 5 });
assert.equal((await purchase(db, user, 'ember_flow')).status, 200);
assert.equal(db.sqlite.prepare('SELECT balance FROM wallets WHERE user_id = ?').get('u1').balance, 1995);
shop = await shopFor(db, user, false);
assert.ok(shop.items.find((item) => item.id === 'ember_flow').owned);

// Hidden items can't be bought.
await call('PATCH', '/admin/shop/items/venom', { active: false });
assert.equal((await purchase(db, user, 'venom')).status, 404);

// Wings are shop items too: the price comes from D1, and ownership gates wearing them.
await call('PATCH', '/admin/shop/items/violet_wings', { price: 5 });
assert.equal((await purchase(db, user, 'violet_wings')).status, 200);
assert.equal(db.sqlite.prepare('SELECT balance FROM wallets WHERE user_id = ?').get('u1').balance, 1990);

const STEVE = '069a79f4-44e9-4726-a5be-fca90e38aaf5';
db.sqlite.prepare("INSERT INTO links (uuid, user_id, name, linked_at) VALUES (?, 'u1', 'Tester', 0)").run(STEVE);
db.sqlite.prepare("UPDATE users SET wing = 'violet_wings' WHERE id = 'u1'").run();
assert.deepEqual(await wingsOf(db, [STEVE]), { [STEVE]: 'violet_wings' });
assert.deepEqual(await hatsOf(db, [STEVE]), {});

// Wings nobody bought are not worn in the presence feed.
db.sqlite.prepare("UPDATE users SET wing = 'verdant_wings' WHERE id = 'u1'").run();
assert.deepEqual(await wingsOf(db, [STEVE]), {});

console.log('shop admin tests passed');
