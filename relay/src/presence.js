// NovoMC presence: which players are on NovoMC right now, so the client can
// show the NovoMC badge next to their names.
//
//   POST /presence/hello {name, uuid}  -> {session}
//     The client says which Minecraft account it plays as. It isn't checked:
//     Mojang refuses every request from Cloudflare Workers (HTTP 403), so the
//     old hasJoined check can't run here, and the owner chose to trust the
//     client's word rather than collect Minecraft tokens.
//   POST /presence/sync  {session?, uuids: [...]} -> {online: [...], session: bool, capes: {uuid: id}}
//     Keeps the caller's own session alive (if it has one) and says which of
//     the given players are on NovoMC, and the Novo capes they wear (from
//     accounts, see account.js; only online players' capes are shown).
//   POST /presence/bye   {session}
//
// State lives in memory in one Durable Object; if it restarts, clients see
// session:false and say hello again.

import { capesOf } from './account.js';

const NAME = /^[A-Za-z0-9_]{1,16}$/;
const UUID = /^[0-9a-f]{8}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{12}$/i;
const MAX_BODY = 8 * 1024;
const MAX_LOOKUP = 120;
const HELLOS_PER_HOUR = 10;
/** Clients sync every minute; a player counts as online for 3 minutes after their last sync. */
export const ONLINE_MS = 3 * 60 * 1000;

const helloHits = new Map();

/** Lowercase, dashed UUID, or null. */
export function normalizeUuid(value) {
  if (typeof value !== 'string' || !UUID.test(value)) return null;
  const h = value.replace(/-/g, '').toLowerCase();
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

export async function handlePresence(request, env, path) {
  if (request.method !== 'POST') return json({ error: 'Use POST' }, 405);
  if (!env.PRESENCE) return json({ error: 'Presence not configured' }, 500);
  const raw = await request.text();
  if (raw.length > MAX_BODY) return json({ error: 'Too large' }, 413);
  let body;
  try {
    body = JSON.parse(raw);
  } catch {
    return json({ error: 'Bad JSON' }, 400);
  }
  if (!body || typeof body !== 'object') return json({ error: 'Bad request' }, 400);
  const store = env.PRESENCE.get(env.PRESENCE.idFromName('global'));

  if (path === '/presence/hello') {
    const ip = request.headers.get('CF-Connecting-IP') || 'unknown';
    if (!allowHello(ip)) return json({ error: 'Too many requests' }, 429);
    const uuid = await verify(env, body);
    if (!uuid) return json({ error: 'Bad player' }, 400);
    return store.fetch('https://presence/hello', { method: 'POST', body: JSON.stringify({ uuid }) });
  }
  if (path === '/presence/sync') {
    if (!Array.isArray(body.uuids)) return json({ error: 'uuids must be a list' }, 400);
    const uuids = [...new Set(body.uuids.slice(0, MAX_LOOKUP).map(normalizeUuid).filter(Boolean))];
    const session = typeof body.session === 'string' ? body.session.slice(0, 64) : null;
    const res = await store.fetch('https://presence/sync', { method: 'POST', body: JSON.stringify({ session, uuids }) });
    const out = await res.json();
    try {
      out.capes = await capesOf(env.NOVO_DB, out.online);
    } catch (e) {
      // Accounts being down never hides badges.
      out.capes = {};
    }
    return json(out);
  }
  if (path === '/presence/bye') {
    const session = typeof body.session === 'string' ? body.session.slice(0, 64) : null;
    return store.fetch('https://presence/bye', { method: 'POST', body: JSON.stringify({ session }) });
  }
  return json({ error: 'Not found' }, 404);
}

/** The player's UUID from a hello or link request, or null if malformed. */
export async function verify(env, body) {
  // Trusted as given (see the top of this file); only the shape is checked.
  if (typeof body.name !== 'string' || !NAME.test(body.name)) return null;
  return normalizeUuid(body.uuid);
}

function allowHello(ip) {
  const key = `${ip}:${Math.floor(Date.now() / 3600000)}`;
  const n = helloHits.get(key) || 0;
  if (n >= HELLOS_PER_HOUR) return false;
  helloHits.set(key, n + 1);
  if (helloHits.size > 10000) helloHits.clear();
  return true;
}

/** Durable Object holding sessions and last-seen times in memory. */
export class Presence {
  constructor(state, env, now = () => Date.now()) {
    this.now = now;
    this.sessions = new Map(); // session -> uuid
    this.seen = new Map(); // uuid -> last sync time
    this.requests = 0;
  }

  async fetch(request) {
    const { pathname } = new URL(request.url);
    const body = await request.json();
    const now = this.now();
    if (++this.requests % 500 === 0) this.prune(now);

    if (pathname === '/hello') {
      const session = crypto.randomUUID();
      this.sessions.set(session, body.uuid);
      this.seen.set(body.uuid, now);
      return json({ session });
    }
    if (pathname === '/sync') {
      const own = body.session ? this.sessions.get(body.session) : undefined;
      if (own) this.seen.set(own, now);
      const online = body.uuids.filter((u) => now - (this.seen.get(u) || 0) < ONLINE_MS);
      return json({ online, session: Boolean(own) });
    }
    if (pathname === '/bye') {
      const own = body.session ? this.sessions.get(body.session) : undefined;
      if (own) {
        this.sessions.delete(body.session);
        this.seen.delete(own);
      }
      return json({ ok: true });
    }
    return json({ error: 'Not found' }, 404);
  }

  prune(now) {
    for (const [uuid, t] of this.seen) if (now - t >= ONLINE_MS) this.seen.delete(uuid);
    for (const [session, uuid] of this.sessions) if (!this.seen.has(uuid)) this.sessions.delete(session);
  }
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } });
}
