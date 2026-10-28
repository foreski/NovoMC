// Novo Client accounts: a Novo profile signed in with Discord, linked to the
// player's Minecraft accounts, with a cape and cloud-saved settings.
//
//   POST   /account/login  {code}            -> {token, account}
//          The launcher catches Discord's redirect to /account/discord/callback
//          and hands us the code; we swap it for the Discord profile with the
//          client secret, then forget Discord's token.
//   GET    /account/discord/callback          a page saying the window can close
//   GET    /account/me                        -> account
//   PATCH  /account/me     {displayName?, cape?}  -> account   (cape: an owned id or null)
//   POST   /account/link   {name, uuid}       -> account
//          Not checked with Mojang, exactly like /presence/hello.
//   DELETE /account/link/<uuid>               -> account
//   POST   /account/logout
//   GET    /account/settings/<slot>           -> {data, updatedAt} | 404
//   PUT    /account/settings/<slot> {data}    -> {updatedAt}
//
// Everything but login and the callback needs "Authorization: Bearer <token>".
// Tokens are 32 random bytes; only their SHA-256 is stored.
//
// Environment:
//   NOVO_DB                D1 database (schema in relay/schema.sql)
//   DISCORD_CLIENT_ID      the Discord application's id (a var)
//   DISCORD_CLIENT_SECRET  (secret)

import { verify, normalizeUuid } from './presence.js';
import { CAPES, ownedCapes } from './capes.js';

const MAX_BODY = 8 * 1024;
const MAX_SETTINGS = 256 * 1024;
const SESSION_MS = 180 * 24 * 3600 * 1000;
const MAX_LINKS = 10;
const LOGINS_PER_HOUR = 20;
const SLOT = /^[0-9a-z._-]{1,32}$/;
const DISPLAY_NAME = /^[\p{L}\p{N}_ .-]{2,24}$/u;

const loginHits = new Map();
// Discord asks API clients to identify themselves this way.
const DISCORD_UA = 'DiscordBot (https://github.com/foreski/NovoMC, 1)';

export async function handleAccount(request, env, path) {
  if (request.method === 'GET' && path === '/account/discord/callback') return closePage();
  if (!env.NOVO_DB) return json({ error: 'Accounts not configured' }, 500);
  const db = env.NOVO_DB;

  if (path === '/account/login') {
    if (request.method !== 'POST') return json({ error: 'Use POST' }, 405);
    const ip = request.headers.get('CF-Connecting-IP') || 'unknown';
    if (!allowLogin(ip)) return json({ error: 'Too many sign-ins; try again later' }, 429);
    const body = await readSmall(request);
    if (!body || typeof body.code !== 'string' || body.code.length > 200) return json({ error: 'Bad request' }, 400);
    return login(env, db, body.code, new URL(request.url).origin);
  }

  const user = await authenticate(db, request);
  if (!user) return json({ error: 'Signed out' }, 401);

  if (path === '/account/me') {
    if (request.method === 'GET') return json(await account(db, user));
    if (request.method !== 'PATCH') return json({ error: 'Use GET or PATCH' }, 405);
    const body = await readSmall(request);
    if (!body) return json({ error: 'Bad JSON' }, 400);
    if ('displayName' in body) {
      const name = typeof body.displayName === 'string' ? body.displayName.trim() : '';
      if (!DISPLAY_NAME.test(name)) return json({ error: 'Names are 2-24 letters, digits, spaces, _ . or -' }, 400);
      await db.prepare('UPDATE users SET display_name = ? WHERE id = ?').bind(name, user.id).run();
      user.display_name = name;
    }
    if ('cape' in body) {
      const cape = body.cape === null ? null : String(body.cape);
      if (cape !== null && !(await ownedCapes(db, user.id)).includes(cape)) return json({ error: "You don't own that cape" }, 403);
      await db.prepare('UPDATE users SET cape = ? WHERE id = ?').bind(cape, user.id).run();
      user.cape = cape;
    }
    return json(await account(db, user));
  }

  if (path === '/account/link' && request.method === 'POST') {
    const body = await readSmall(request);
    if (!body) return json({ error: 'Bad JSON' }, 400);
    const uuid = await verify(env, body);
    if (!uuid) return json({ error: 'Bad Minecraft account' }, 400);
    const name = typeof body.name === 'string' ? body.name.slice(0, 16) : '?';
    const existing = await db.prepare('SELECT COUNT(*) AS n FROM links WHERE user_id = ? AND uuid != ?').bind(user.id, uuid).first();
    if (existing && existing.n >= MAX_LINKS) return json({ error: `At most ${MAX_LINKS} Minecraft accounts per Novo account` }, 400);
    // The latest Novo account to link a Minecraft account gets it.
    await db
      .prepare('INSERT INTO links (uuid, user_id, name, linked_at) VALUES (?, ?, ?, ?) ON CONFLICT(uuid) DO UPDATE SET user_id = excluded.user_id, name = excluded.name, linked_at = excluded.linked_at')
      .bind(uuid, user.id, name, Date.now())
      .run();
    return json(await account(db, user));
  }

  if (path.startsWith('/account/link/') && request.method === 'DELETE') {
    const uuid = normalizeUuid(decodeURIComponent(path.slice('/account/link/'.length)));
    if (!uuid) return json({ error: 'Bad uuid' }, 400);
    await db.prepare('DELETE FROM links WHERE uuid = ? AND user_id = ?').bind(uuid, user.id).run();
    return json(await account(db, user));
  }

  if (path === '/account/logout' && request.method === 'POST') {
    await db.prepare('DELETE FROM sessions WHERE token_hash = ?').bind(user.tokenHash).run();
    return json({ ok: true });
  }

  if (path.startsWith('/account/settings/')) {
    const slot = path.slice('/account/settings/'.length);
    if (!SLOT.test(slot)) return json({ error: 'Bad slot' }, 400);
    if (request.method === 'GET') {
      const row = await db.prepare('SELECT data, updated_at FROM settings WHERE user_id = ? AND slot = ?').bind(user.id, slot).first();
      return row ? json({ data: row.data, updatedAt: row.updated_at }) : json({ error: 'No saved settings' }, 404);
    }
    if (request.method !== 'PUT') return json({ error: 'Use GET or PUT' }, 405);
    const body = await readJson(request, MAX_SETTINGS * 2);
    if (body === TOO_LARGE || (body && typeof body.data === 'string' && body.data.length > MAX_SETTINGS)) return json({ error: 'Settings too large' }, 413);
    if (!body || typeof body.data !== 'string') return json({ error: 'Bad request' }, 400);
    try {
      JSON.parse(body.data);
    } catch {
      return json({ error: 'Settings must be JSON' }, 400);
    }
    const updatedAt = Date.now();
    await db
      .prepare('INSERT INTO settings (user_id, slot, data, updated_at) VALUES (?, ?, ?, ?) ON CONFLICT(user_id, slot) DO UPDATE SET data = excluded.data, updated_at = excluded.updated_at')
      .bind(user.id, slot, body.data, updatedAt)
      .run();
    return json({ updatedAt });
  }

  return json({ error: 'Not found' }, 404);
}

async function login(env, db, code, origin) {
  if (!env.DISCORD_CLIENT_ID || !env.DISCORD_CLIENT_SECRET) return json({ error: 'Discord sign-in not configured' }, 500);
  const tokenRes = await fetch('https://discord.com/api/oauth2/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'User-Agent': DISCORD_UA },
    body: new URLSearchParams({
      client_id: env.DISCORD_CLIENT_ID.trim(),
      client_secret: discordSecret(env),
      grant_type: 'authorization_code',
      code,
      redirect_uri: `${origin}/account/discord/callback`,
    }),
  });
  if (!tokenRes.ok) {
    // Discord's error code (invalid_client = wrong secret, invalid_grant = stale code or redirect mismatch).
    const detail = await tokenRes.json().catch(() => ({}));
    const reason = /^[a-z_]{1,40}$/.test(detail.error || '') ? detail.error : `HTTP ${tokenRes.status}`;
    console.log(`Discord token exchange failed: ${reason} (secret length ${discordSecret(env).length})`);
    return json({ error: `Discord refused the sign-in (${reason}); try again` }, 403);
  }
  const { access_token: discordToken } = await tokenRes.json();
  const meRes = await fetch('https://discord.com/api/users/@me', { headers: { Authorization: `Bearer ${discordToken}`, 'User-Agent': DISCORD_UA } });
  if (!meRes.ok) return json({ error: "Couldn't read your Discord profile" }, 502);
  const me = await meRes.json();
  if (!me || !/^\d{1,24}$/.test(String(me.id))) return json({ error: "Couldn't read your Discord profile" }, 502);

  const discordName = String(me.global_name || me.username || 'Player').slice(0, 32);
  const avatar = typeof me.avatar === 'string' && /^[a-z0-9_]{1,40}$/.test(me.avatar) ? me.avatar : null;
  let user = await db.prepare('SELECT * FROM users WHERE discord_id = ?').bind(String(me.id)).first();
  if (user) {
    await db.prepare('UPDATE users SET discord_name = ?, avatar = ? WHERE id = ?').bind(discordName, avatar, user.id).run();
    user = { ...user, discord_name: discordName, avatar };
  } else {
    user = { id: crypto.randomUUID(), discord_id: String(me.id), discord_name: discordName, avatar, display_name: defaultName(discordName), cape: null };
    await db
      .prepare('INSERT INTO users (id, discord_id, discord_name, avatar, display_name, cape, created_at) VALUES (?, ?, ?, ?, ?, NULL, ?)')
      .bind(user.id, user.discord_id, discordName, avatar, user.display_name, Date.now())
      .run();
  }

  const token = randomToken();
  const now = Date.now();
  await db.prepare('DELETE FROM sessions WHERE user_id = ? AND expires_at < ?').bind(user.id, now).run();
  await db.prepare('INSERT INTO sessions (token_hash, user_id, expires_at) VALUES (?, ?, ?)').bind(await sha256(token), user.id, now + SESSION_MS).run();
  return json({ token, account: await account(db, user) });
}

/** The signed-in user (with tokenHash), or null. */
async function authenticate(db, request) {
  const header = request.headers.get('Authorization') || '';
  const token = header.startsWith('Bearer ') ? header.slice(7).trim() : '';
  if (!/^[A-Za-z0-9_-]{43}$/.test(token)) return null;
  const tokenHash = await sha256(token);
  const row = await db
    .prepare('SELECT u.*, s.expires_at FROM sessions s JOIN users u ON u.id = s.user_id WHERE s.token_hash = ?')
    .bind(tokenHash)
    .first();
  if (!row || row.expires_at < Date.now()) return null;
  return { ...row, tokenHash };
}

async function account(db, user) {
  const { results: links } = await db.prepare('SELECT uuid, name FROM links WHERE user_id = ? ORDER BY linked_at').bind(user.id).all();
  const owned = await ownedCapes(db, user.id);
  return {
    id: user.id,
    displayName: user.display_name,
    discordName: user.discord_name,
    avatarUrl: user.avatar ? `https://cdn.discordapp.com/avatars/${user.discord_id}/${user.avatar}.png?size=128` : null,
    cape: user.cape && owned.includes(user.cape) ? user.cape : null,
    capes: CAPES.map((c) => ({ id: c.id, name: c.name, owned: owned.includes(c.id) })),
    minecraft: links.map((l) => ({ uuid: l.uuid, name: l.name })),
  };
}

/** Equipped capes of the given (normalized) UUIDs, as {uuid: capeId}. */
export async function capesOf(db, uuids) {
  const out = {};
  if (!db || uuids.length === 0) return out;
  const marks = uuids.map(() => '?').join(',');
  const { results } = await db
    .prepare(`SELECT l.uuid, u.id AS user_id, u.cape FROM links l JOIN users u ON u.id = l.user_id WHERE u.cape IS NOT NULL AND l.uuid IN (${marks})`)
    .bind(...uuids)
    .all();
  const grants = new Map();
  for (const r of results) {
    const cape = CAPES.find((c) => c.id === r.cape);
    if (!cape) continue;
    if (!cape.free) {
      // Someone could lose a grant after equipping it; check exclusive capes are still theirs.
      if (!grants.has(r.user_id)) grants.set(r.user_id, await ownedCapes(db, r.user_id));
      if (!grants.get(r.user_id).includes(cape.id)) continue;
    }
    out[r.uuid] = cape.id;
  }
  return out;
}

/** The client secret without what a paste can add (spaces, line breaks, a Ctrl+V control character); Discord secrets are [A-Za-z0-9_-]. */
function discordSecret(env) {
  return env.DISCORD_CLIENT_SECRET.replace(/[^A-Za-z0-9_-]/g, '');
}

function defaultName(discordName) {
  const cleaned = discordName.replace(/[^\p{L}\p{N}_ .-]/gu, '').trim().slice(0, 24);
  return DISPLAY_NAME.test(cleaned) ? cleaned : 'Novo Player';
}

function allowLogin(ip) {
  const key = `${ip}:${Math.floor(Date.now() / 3600000)}`;
  const n = loginHits.get(key) || 0;
  if (n >= LOGINS_PER_HOUR) return false;
  loginHits.set(key, n + 1);
  if (loginHits.size > 10000) loginHits.clear();
  return true;
}

function randomToken() {
  const bytes = crypto.getRandomValues(new Uint8Array(32));
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

async function sha256(text) {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

async function readSmall(request) {
  const body = await readJson(request, MAX_BODY);
  return body === TOO_LARGE ? null : body;
}

const TOO_LARGE = Symbol('too large');

/** The JSON object in the body, null if it isn't one, or TOO_LARGE. */
async function readJson(request, max) {
  const raw = await request.text();
  if (raw.length > max) return TOO_LARGE;
  try {
    const body = JSON.parse(raw);
    return body && typeof body === 'object' ? body : null;
  } catch {
    return null;
  }
}

function closePage() {
  const html = '<!doctype html><meta charset="utf-8"><title>Novo Client</title><body style="font-family:sans-serif;background:#111;color:#eee;text-align:center;padding-top:20vh">Signed in. You can close this window and go back to Novo Client.</body>';
  return new Response(html, { headers: { 'Content-Type': 'text/html; charset=utf-8' } });
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } });
}
