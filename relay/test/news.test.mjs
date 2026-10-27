// Tests the news API with an in-memory KV: `node relay/test/news.test.mjs`
import assert from 'node:assert/strict';
import worker from '../src/worker.js';

const kv = new Map();
const env = {
  NEWS_TOKEN: 'bot-secret',
  NEWS: {
    get: async (k, type) => (kv.has(k) ? (type === 'json' ? JSON.parse(kv.get(k)) : kv.get(k)) : null),
    put: async (k, v) => void kv.set(k, v),
  },
};
const call = async (method, path, data, token) => {
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  const res = await worker.fetch(
    new Request(`https://relay.example${path}`, { method, headers, body: data === undefined ? undefined : JSON.stringify(data) }),
    env,
  );
  return { status: res.status, body: await res.json() };
};

// Empty to start; reading needs no token.
assert.deepEqual((await call('GET', '/news')).body, { items: [] });

// Publishing needs the right token.
assert.equal((await call('POST', '/news', { title: 'Hi', body: 'There' })).status, 401);
assert.equal((await call('POST', '/news', { title: 'Hi', body: 'There' }, 'wrong-secret')).status, 401);

// A post shows up, with defaults filled in.
let r = await call('POST', '/news', { id: '111', title: 'Season 2', body: 'New maps', date: '2026-10-01', url: 'https://discord.gg/novo' }, 'bot-secret');
assert.equal(r.status, 200);
assert.equal(r.body.item.tag, 'novomc');
assert.equal(r.body.item.image, null);

// Newest first; posting the same id again replaces it (Discord edits).
await call('POST', '/news', { id: '222', title: 'Patch', body: 'Fixes', date: '2026-10-05' }, 'bot-secret');
await call('POST', '/news', { id: '111', title: 'Season 2 (edited)', body: 'New maps', date: '2026-10-01' }, 'bot-secret');
let items = (await call('GET', '/news')).body.items;
assert.deepEqual(items.map((i) => i.id), ['222', '111']);
assert.equal(items[1].title, 'Season 2 (edited)');

// Bad input is refused with a reason.
r = await call('POST', '/news', { title: 'x', body: 'y', url: 'http://insecure' }, 'bot-secret');
assert.equal(r.status, 400);
assert.match(r.body.error, /https/);
assert.equal((await call('POST', '/news', { title: '', body: 'y' }, 'bot-secret')).status, 400);
assert.equal((await call('POST', '/news', { title: 'x'.repeat(101), body: 'y' }, 'bot-secret')).status, 400);

// Delete removes one item; unknown ids are a 404.
assert.equal((await call('DELETE', '/news/222', undefined, 'bot-secret')).status, 200);
assert.equal((await call('DELETE', '/news/222', undefined, 'bot-secret')).status, 404);
assert.deepEqual((await call('GET', '/news')).body.items.map((i) => i.id), ['111']);

// Only the 20 newest are kept.
for (let d = 1; d <= 25; d++) {
  await call('POST', '/news', { id: `bulk${d}`, title: 't', body: 'b', date: `2026-11-${String(d).padStart(2, '0')}` }, 'bot-secret');
}
items = (await call('GET', '/news')).body.items;
assert.equal(items.length, 20);
assert.equal(items[0].id, 'bulk25');

console.log('news tests passed');
