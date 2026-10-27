// Novo Client news: the launcher's Home page shows these; a Discord bot (or
// anything holding NEWS_TOKEN) publishes them.
//
//   GET    /news             -> {items: [...]}  newest first, public
//   POST   /news  {id?, title, body, url?, image?, date?}  -> {ok, item}
//          Creates the item, or replaces the one with the same id (pass the
//          Discord message id to make edits update the card).
//   DELETE /news/<id>        -> {ok}
//
// POST and DELETE need "Authorization: Bearer <NEWS_TOKEN>".
//
// Environment:
//   NEWS        KV namespace; everything lives under the key "items"
//   NEWS_TOKEN  (secret) the bot's token

const KEY = 'items';
const MAX_ITEMS = 20;
const MAX_BODY = 8 * 1024;
const LIMITS = { id: 64, title: 100, body: 400, url: 500, image: 500 };
const ID = /^[A-Za-z0-9_-]{1,64}$/;

export async function handleNews(request, env, path) {
  if (!env.NEWS) return json({ error: 'News not configured' }, 500);
  if (request.method === 'GET' && path === '/news') {
    // Launchers poll on start; a minute of edge caching is plenty.
    return json({ items: await load(env) }, 200, { 'Cache-Control': 'public, max-age=60' });
  }
  if (request.method !== 'POST' && request.method !== 'DELETE') return json({ error: 'Use GET, POST or DELETE' }, 405);
  if (!authorized(request, env)) return json({ error: 'Unauthorized' }, 401);

  if (request.method === 'DELETE') {
    const id = path.startsWith('/news/') ? decodeURIComponent(path.slice('/news/'.length)) : '';
    if (!ID.test(id)) return json({ error: 'Use DELETE /news/<id>' }, 400);
    const items = await load(env);
    const kept = items.filter((i) => i.id !== id);
    if (kept.length === items.length) return json({ error: 'No such item' }, 404);
    await env.NEWS.put(KEY, JSON.stringify(kept));
    return json({ ok: true });
  }

  if (path !== '/news') return json({ error: 'Not found' }, 404);
  const raw = await request.text();
  if (raw.length > MAX_BODY) return json({ error: 'Too large' }, 413);
  let item;
  try {
    item = cleanItem(JSON.parse(raw), Date.now());
  } catch (e) {
    return json({ error: `Bad news item: ${e.message}` }, 400);
  }
  const items = (await load(env)).filter((i) => i.id !== item.id);
  items.push(item);
  items.sort((a, b) => b.date.localeCompare(a.date));
  await env.NEWS.put(KEY, JSON.stringify(items.slice(0, MAX_ITEMS)));
  return json({ ok: true, item });
}

/** Validates a posted item into the shape the launcher reads. */
export function cleanItem(input, now) {
  if (!input || typeof input !== 'object') throw new Error('expected an object');
  const text = (key, required) => {
    const v = input[key];
    if (v == null || v === '') {
      if (required) throw new Error(`${key} is required`);
      return null;
    }
    if (typeof v !== 'string') throw new Error(`${key} must be text`);
    const t = v.trim();
    if (t.length > LIMITS[key]) throw new Error(`${key} is longer than ${LIMITS[key]} characters`);
    return t;
  };
  const link = (key) => {
    const v = text(key, false);
    if (v && !/^https:\/\/[^\s"]+$/.test(v)) throw new Error(`${key} must be an https link`);
    return v;
  };
  const id = text('id', false) ?? `n${now.toString(36)}`;
  if (!ID.test(id)) throw new Error('id may only use letters, digits, - and _');
  const date = text('date', false) ?? new Date(now).toISOString();
  if (Number.isNaN(Date.parse(date))) throw new Error('date must be an ISO date');
  return {
    id,
    title: text('title', true),
    body: text('body', true),
    date,
    tag: 'novomc',
    url: link('url'),
    image: link('image'),
  };
}

async function load(env) {
  const items = await env.NEWS.get(KEY, 'json');
  return Array.isArray(items) ? items : [];
}

function authorized(request, env) {
  const header = request.headers.get('Authorization') || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : '';
  return Boolean(env.NEWS_TOKEN) && token.length > 0 && timingSafeEqual(token, env.NEWS_TOKEN);
}

/** Compares without leaking where the strings differ. */
function timingSafeEqual(a, b) {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

function json(data, status = 200, headers = {}) {
  return new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json', ...headers } });
}
