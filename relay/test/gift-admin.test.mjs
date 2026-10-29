// Tests minting and listing coin gift codes from the bot API, and that a minted
// code actually pays out through the launcher's redeem path.
// `node relay/test/gift-admin.test.mjs`
import assert from 'node:assert/strict';
import worker from '../src/worker.js';
import { d1 } from './d1.mjs';
import { redeemGift } from '../src/shop.js';

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

// The news token can't mint coins, and neither can an anonymous caller.
assert.equal((await call('POST', '/admin/gift-codes', { coins: 500 }, null)).status, 401);
assert.equal((await call('POST', '/admin/gift-codes', { coins: 500 }, 'news-token')).status, 401);
assert.equal((await call('GET', '/admin/gift-codes', undefined, null)).status, 401);

// An empty catalog to begin with.
let r = await call('GET', '/admin/gift-codes');
assert.equal(r.status, 200);
assert.deepEqual(r.body.codes, []);

// Mint one code: it looks like a gift code, and only its hash is stored.
r = await call('POST', '/admin/gift-codes', { coins: 500 });
assert.equal(r.status, 201);
assert.equal(r.body.codes.length, 1);
assert.equal(r.body.expiresAt, null);
const code = r.body.codes[0];
assert.match(code, /^NOVO(-[0-9A-F]{4}){6}$/, code);
const stored = db.sqlite.prepare('SELECT code_hash, coins, max_uses, uses FROM gift_codes').all();
assert.equal(stored.length, 1);
assert.match(stored[0].code_hash, /^[0-9a-f]{64}$/);
assert.ok(!JSON.stringify(stored).includes(code.replace(/-/g, '').slice(4)), 'the code itself is never stored');
assert.equal(stored[0].coins, 500);
assert.equal(stored[0].max_uses, 1);
assert.equal(stored[0].uses, 0);

// It pays out once, through the same path the launcher uses.
db.sqlite.prepare('INSERT INTO users (id, discord_id, display_name, created_at) VALUES (?, ?, ?, ?)').run('u1', '1', 'Tester', 0);
db.sqlite.prepare('INSERT INTO users (id, discord_id, display_name, created_at) VALUES (?, ?, ?, ?)').run('u2', '2', 'Other', 0);
assert.equal((await redeemGift(db, { id: 'u1' }, code)).status, 200);
assert.equal(db.sqlite.prepare('SELECT balance FROM wallets WHERE user_id = ?').get('u1').balance, 500);
assert.equal((await redeemGift(db, { id: 'u2' }, code)).status, 404, 'a single-use code is spent');

// Listing shows what was used, without the code or its full hash.
r = await call('GET', '/admin/gift-codes');
assert.equal(r.body.codes.length, 1);
const listed = r.body.codes[0];
assert.equal(listed.coins, 500);
assert.equal(listed.uses, 1);
assert.equal(listed.maxUses, 1);
assert.equal(listed.expiresAt, null);
assert.equal(listed.id, stored[0].code_hash.slice(0, 8));
assert.ok(!JSON.stringify(r.body).includes(stored[0].code_hash), 'the full hash stays server-side');

// Ten codes for a hundred players each, expiring in a week.
r = await call('POST', '/admin/gift-codes', { coins: 250, uses: 100, count: 10, expiresInDays: 7 });
assert.equal(r.status, 201);
assert.equal(r.body.codes.length, 10);
assert.equal(new Set(r.body.codes).size, 10, 'every code is distinct');
assert.ok(r.body.expiresAt > Date.now() + 6 * 86400000 && r.body.expiresAt < Date.now() + 8 * 86400000);
assert.equal(db.sqlite.prepare('SELECT COUNT(*) AS n FROM gift_codes').get().n, 11);

// A shared code stops paying once its uses run out.
db.sqlite.prepare('INSERT INTO users (id, discord_id, display_name, created_at) VALUES (?, ?, ?, ?)').run('u3', '3', 'Third', 0);
await call('POST', '/admin/gift-codes', { coins: 10, uses: 1 });
const shared = (await call('POST', '/admin/gift-codes', { coins: 10, uses: 2 })).body.codes[0];
assert.equal((await redeemGift(db, { id: 'u1' }, shared)).status, 200);
assert.equal((await redeemGift(db, { id: 'u1' }, shared)).status, 409, 'the same account cannot redeem twice');
assert.equal((await redeemGift(db, { id: 'u2' }, shared)).status, 200);
assert.equal((await redeemGift(db, { id: 'u3' }, shared)).status, 404, 'uses=2 is spent after two players');

// Odds and ends are refused.
for (const body of [
  { coins: 0 },
  { coins: 500, uses: 0 },
  { coins: 500, count: 26 },
  { coins: 500, expiresInDays: 366 },
  { coins: 500, coins2: 1 },
  { coins: '500' },
]) {
  assert.equal((await call('POST', '/admin/gift-codes', body)).status, 400, JSON.stringify(body));
}
assert.equal((await call('POST', '/admin/gift-codes', { coins: 500, expiresInDays: -1 })).status, 400);

// With no SHOP_TOKEN secret configured, minting is closed rather than open.
assert.equal((await call('POST', '/admin/gift-codes', { coins: 500 }, '')).status, 401);
delete env.NOVO_DB;
assert.equal((await call('GET', '/admin/gift-codes')).status, 500);

console.log('gift admin tests passed');
