// Tests presence with Mojang's session server mocked: `node relay/test/presence.test.mjs`
import assert from 'node:assert/strict';
import worker from '../src/worker.js';
import { Presence, normalizeUuid, ONLINE_MS } from '../src/presence.js';

let clock = 1_000_000;
const store = new Presence(null, null, () => clock);
const env = { PRESENCE: { idFromName: () => 'global', get: () => ({ fetch: (url, init) => store.fetch(new Request(url, init)) }) } };

const STEVE = '069a79f4-44e9-4726-a5be-fca90e38aaf5';
const ALEX = 'ec561538-f3fd-461d-aff5-086b22154bce';
const mojang = [];
globalThis.fetch = async (url) => {
  mojang.push(url);
  // Mojang confirms only Steve, who "joined" with server id abcdef12.
  if (url.includes('username=Steve') && url.includes('serverId=abcdef12')) {
    return new Response(JSON.stringify({ id: STEVE.replace(/-/g, ''), name: 'Steve' }));
  }
  return new Response(null, { status: 204 });
};

const post = async (path, data, ip = '1.1.1.1') => {
  const res = await worker.fetch(
    new Request(`https://relay.example${path}`, { method: 'POST', body: JSON.stringify(data), headers: { 'CF-Connecting-IP': ip } }),
    env,
  );
  return { status: res.status, body: await res.json() };
};

assert.equal(normalizeUuid(STEVE.replace(/-/g, '').toUpperCase()), STEVE);
assert.equal(normalizeUuid('not-a-uuid'), null);

// A claim Mojang doesn't confirm gets no session, and Steve isn't marked online by it.
let r = await post('/presence/hello', { name: 'Steve', serverId: 'ffffffff' });
assert.equal(r.status, 403);
assert.deepEqual((await post('/presence/sync', { uuids: [STEVE] })).body.online, []);

// A verified hello makes Steve visible to everyone who asks.
r = await post('/presence/hello', { name: 'Steve', serverId: 'abcdef12' });
assert.equal(r.status, 200);
const session = r.body.session;
assert.ok(session);
r = await post('/presence/sync', { uuids: [STEVE, ALEX, 'junk'] }, '2.2.2.2');
assert.deepEqual(r.body.online, [STEVE]);
assert.equal(r.body.session, false);

// Syncing keeps him online; going quiet for 3 minutes drops him.
clock += ONLINE_MS - 1000;
assert.equal((await post('/presence/sync', { session, uuids: [] })).body.session, true);
clock += ONLINE_MS - 1000;
assert.deepEqual((await post('/presence/sync', { uuids: [STEVE] })).body.online, [STEVE]);
clock += ONLINE_MS;
assert.deepEqual((await post('/presence/sync', { uuids: [STEVE] })).body.online, []);

// Bye removes him at once.
r = await post('/presence/hello', { name: 'Steve', serverId: 'abcdef12' });
await post('/presence/bye', { session: r.body.session });
assert.deepEqual((await post('/presence/sync', { uuids: [STEVE] })).body.online, []);

// Bad input and hello floods are refused.
assert.equal((await post('/presence/hello', { name: 'Steve; drop', serverId: 'abcdef12' })).status, 403);
assert.equal((await post('/presence/sync', { uuids: 'x' })).status, 400);
let last;
for (let i = 0; i < 12; i++) last = await post('/presence/hello', { name: 'Alex', serverId: 'abcdef12' }, '9.9.9.9');
assert.equal(last.status, 429);
assert.ok(mojang.every((u) => u.startsWith('https://sessionserver.mojang.com/session/minecraft/hasJoined?')));

console.log('presence tests passed');
