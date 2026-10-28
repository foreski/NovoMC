// Tests accounts with Discord mocked and D1 played by node:sqlite:
// `node relay/test/account.test.mjs`
import assert from 'node:assert/strict';
import worker from '../src/worker.js';
import { Presence } from '../src/presence.js';
import { d1 } from './d1.mjs';

const db = d1();
const presence = new Presence(null, null);
const env = {
  NOVO_DB: db,
  DISCORD_CLIENT_ID: '123',
  DISCORD_CLIENT_SECRET: 'shh',
  PRESENCE: { idFromName: () => 'global', get: () => ({ fetch: (url, init) => presence.fetch(new Request(url, init)) }) },
};

const STEVE = '069a79f4-44e9-4726-a5be-fca90e38aaf5';
const calls = [];
globalThis.fetch = async (url, init = {}) => {
  url = String(url);
  calls.push({ url, init });
  if (url === 'https://discord.com/api/oauth2/token') {
    const form = new URLSearchParams(init.body);
    if (form.get('code') !== 'good-code' || form.get('client_secret') !== 'shh') return new Response('{"error":"invalid_grant"}', { status: 400 });
    assert.equal(form.get('redirect_uri'), 'https://relay.example/account/discord/callback');
    return new Response(JSON.stringify({ access_token: 'discord-token' }));
  }
  if (url === 'https://discord.com/api/users/@me') {
    assert.equal(init.headers.Authorization, 'Bearer discord-token');
    return new Response(JSON.stringify({ id: '42', username: 'steve', global_name: 'Steve 🎮', avatar: 'abc123' }));
  }
  throw new Error(`unexpected fetch ${url}`);
};

const call = async (method, path, data, token) => {
  const headers = { 'CF-Connecting-IP': '1.1.1.1' };
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await worker.fetch(
    new Request(`https://relay.example${path}`, { method, headers, body: data === undefined ? undefined : JSON.stringify(data) }),
    env,
  );
  const text = await res.text();
  let body;
  try {
    body = JSON.parse(text);
  } catch {
    body = text;
  }
  return { status: res.status, body };
};

// The redirect target is a plain page; the launcher catches it before it loads.
let r = await call('GET', '/account/discord/callback?code=x');
assert.equal(r.status, 200);
assert.match(r.body, /close this window/);

// A bad code is refused; a good one creates the account.
r = await call('POST', '/account/login', { code: 'bad' });
assert.equal(r.status, 403);
assert.match(r.body.error, /invalid_grant/);
r = await call('POST', '/account/login', { code: 'good-code' });
assert.equal(r.status, 200);
const token = r.body.token;
assert.match(token, /^[A-Za-z0-9_-]{43}$/);
assert.equal(r.body.account.displayName, 'Steve');
assert.equal(r.body.account.avatarUrl, 'https://cdn.discordapp.com/avatars/42/abc123.png?size=128');
assert.equal(r.body.account.cape, null);
assert.deepEqual(r.body.account.minecraft, []);
assert.ok(r.body.account.capes.find((c) => c.id === 'novo').owned);
assert.ok(!r.body.account.capes.find((c) => c.id === 'developer').owned);
assert.ok(r.body.account.banners.find((b) => b.id === 'aurora').owned);
assert.ok(!r.body.account.banners.find((b) => b.id === 'developer').owned);
// Only the token's hash is stored.
assert.ok(!JSON.stringify(db.sqlite.prepare('SELECT * FROM sessions').all()).includes(token));

// Signing in again finds the same account.
const again = (await call('POST', '/account/login', { code: 'good-code' })).body;
assert.equal(again.account.id, r.body.account.id);

// Without a valid token: 401.
assert.equal((await call('GET', '/account/me')).status, 401);
assert.equal((await call('GET', '/account/me', undefined, 'x'.repeat(43))).status, 401);

// Profile edits.
r = await call('PATCH', '/account/me', { displayName: '  Novo Steve ' }, token);
assert.equal(r.body.displayName, 'Novo Steve');
assert.equal((await call('PATCH', '/account/me', { displayName: '<script>' }, token)).status, 400);
assert.equal((await call('PATCH', '/account/me', { cape: 'developer' }, token)).status, 403);
assert.equal((await call('PATCH', '/account/me', { cape: 'nope' }, token)).status, 403);
r = await call('PATCH', '/account/me', { cape: 'ember' }, token);
assert.equal(r.body.cape, 'ember');
assert.equal((await call('PATCH', '/account/me', { banner: 'developer' }, token)).status, 403);
r = await call('PATCH', '/account/me', { banner: 'aurora' }, token);
assert.equal(r.body.banner, 'aurora');

// Granted capes become owned.
db.sqlite.prepare("INSERT INTO grants (user_id, cape) VALUES (?, 'developer')").run(r.body.id);
db.sqlite.prepare("INSERT INTO grants (user_id, cape) VALUES (?, 'banner:developer')").run(r.body.id);
r = await call('PATCH', '/account/me', { cape: 'developer' }, token);
assert.equal(r.status, 200);
assert.equal(r.body.cape, 'developer');
r = await call('PATCH', '/account/me', { banner: 'developer' }, token);
assert.equal(r.body.banner, 'developer');

// Linking takes the Minecraft account as given (Mojang blocks Workers); junk is refused.
assert.equal((await call('POST', '/account/link', { name: 'Steve', uuid: 'junk' }, token)).status, 400);
assert.equal((await call('POST', '/account/link', { name: 'Steve; drop', uuid: STEVE }, token)).status, 400);
r = await call('POST', '/account/link', { name: 'Steve', uuid: STEVE }, token);
assert.deepEqual(r.body.minecraft, [{ uuid: STEVE, name: 'Steve' }]);

// Online players' capes come back with presence; offline ones don't.
let sync = await call('POST', '/presence/sync', { uuids: [STEVE] });
assert.deepEqual(sync.body.capes, {});
const hello = await call('POST', '/presence/hello', { name: 'Steve', uuid: STEVE });
sync = await call('POST', '/presence/sync', { session: hello.body.session, uuids: [STEVE] });
assert.deepEqual(sync.body.online, [STEVE]);
assert.deepEqual(sync.body.capes, { [STEVE]: 'developer' });
// A revoked grant stops showing at once.
db.sqlite.prepare('DELETE FROM grants').run();
sync = await call('POST', '/presence/sync', { uuids: [STEVE] });
assert.deepEqual(sync.body.capes, {});
r = await call('PATCH', '/account/me', { cape: null }, token);
assert.equal(r.body.cape, null);
r = await call('PATCH', '/account/me', { banner: null }, token);
assert.equal(r.body.banner, null);

// Settings: nothing yet, then save and read back per slot.
assert.equal((await call('GET', '/account/settings/1.8.9', undefined, token)).status, 404);
assert.equal((await call('PUT', '/account/settings/1.8.9', { data: 'not json' }, token)).status, 400);
assert.equal((await call('PUT', '/account/settings/a%2F..', { data: '{}' }, token)).status, 400);
r = await call('PUT', '/account/settings/1.8.9', { data: '{"modules":{}}' }, token);
assert.equal(r.status, 200);
const savedAt = r.body.updatedAt;
r = await call('GET', '/account/settings/1.8.9', undefined, token);
assert.deepEqual(r.body, { data: '{"modules":{}}', updatedAt: savedAt });
assert.equal((await call('GET', '/account/settings/26.3', undefined, token)).status, 404);
assert.equal((await call('PUT', '/account/settings/1.8.9', { data: JSON.stringify({ x: 'y'.repeat(300 * 1024) }) }, token)).status, 413);

// Unlinking, then signing out ends the session.
r = await call('DELETE', `/account/link/${STEVE}`, undefined, token);
assert.deepEqual(r.body.minecraft, []);
assert.equal((await call('POST', '/account/logout', undefined, token)).status, 200);
assert.equal((await call('GET', '/account/me', undefined, token)).status, 401);
// The other session is unaffected.
assert.equal((await call('GET', '/account/me', undefined, again.token)).status, 200);

// Discord's token is only ever sent to Discord.
assert.ok(calls.filter((c) => JSON.stringify(c.init).includes('discord-token')).every((c) => c.url.startsWith('https://discord.com/')));

console.log('account tests passed');
