// The Marketplace catalog lives in D1 (`shop_items`, seeded by
// relay/migrations/0003-shop-items.sql) so it can change without a deploy:
// the Discord bot (docs/SHOP_BOT.md) manages it through /admin/shop/items with
// the SHOP_TOKEN secret. Prices are in Novo Coins (NC).
//
// A cape or banner item must name a paid id from relay/src/capes.js, so a typo
// can never leave an item nobody can wear in the shop; animation data comes
// from there too.
import { animationOf, catalogFor } from './capes.js';

const KINDS = ['cape', 'banner', 'wings', 'hat'];
const ITEM_ID = /^[a-z0-9_]{1,32}$/;
const ITEM_BODY_MAX = 4 * 1024;
const ITEM_FIELDS = ['id', 'kind', 'name', 'description', 'price', 'active', 'position'];
const ITEM_LIMITS = { name: 40, description: 160, price: 100000, position: 10000 };

export const TAX_CODE = 'txcd_10201000';

// These starter bundles were approved by the owner.
export const BUNDLES = [
  { id: 'starter', name: 'Starter', coins: 200, cents: 199 },
  { id: 'boost', name: 'Boost', coins: 550, cents: 499, badge: 'POPULAR' },
  { id: 'vault', name: 'Vault', coins: 1200, cents: 999, badge: 'BEST VALUE' },
];

export async function shopFor(db, user, paymentReady) {
  await ensureWallet(db, user.id);
  const wallet = await db.prepare('SELECT balance FROM wallets WHERE user_id = ?').bind(user.id).first();
  const { results: grants } = await db.prepare('SELECT cape FROM grants WHERE user_id = ?').bind(user.id).all();
  const owned = new Set(grants.map((row) => row.cape));
  const { results: items } = await db
    .prepare('SELECT id, kind, name, description, price FROM shop_items WHERE active = 1 ORDER BY position, id')
    .all();
  return {
    balance: Number(wallet?.balance || 0),
    paymentReady,
    bundles: BUNDLES.map(({ cents, ...bundle }) => ({ ...bundle, price: cents / 100, currency: 'USD' })),
    items: items.map((item) => ({
      ...item,
      price: Number(item.price),
      animated: animationOf(item.id),
      owned: owned.has(grantKey(item)),
    })),
  };
}

export async function ensureWallet(db, userId) {
  await db.prepare('INSERT OR IGNORE INTO wallets (user_id, balance) VALUES (?, 0)').bind(userId).run();
}

/** The `grants` key an item unlocks: capes keep their bare id, extras are prefixed. */
export function grantKey(item) {
  const prefix = { banner: 'banner', wings: 'wing', hat: 'hat' }[item.kind];
  return prefix ? `${prefix}:${item.id}` : item.id;
}

export async function purchase(db, user, id) {
  // The catalog (and the price) is read per purchase, so shop edits apply at once.
  const item = await db.prepare('SELECT id, kind, price FROM shop_items WHERE id = ? AND active = 1').bind(id).first();
  if (!item) return json({ error: 'That item is no longer in the shop' }, 404);
  const price = Number(item.price);
  await ensureWallet(db, user.id);
  const key = grantKey(item);
  if (await db.prepare('SELECT 1 AS owned FROM grants WHERE user_id = ? AND cape = ?').bind(user.id, key).first()) {
    return json({ error: 'You already own this cosmetic' }, 409);
  }
  const wallet = await db.prepare('SELECT balance FROM wallets WHERE user_id = ?').bind(user.id).first();
  if ((wallet?.balance || 0) < price) return json({ error: 'You need more Novo Coins for this item' }, 409);

  const now = Date.now();
  const tx = `shop:${crypto.randomUUID()}`;
  const results = await db.batch([
    db.prepare(`UPDATE wallets SET balance = balance - ?, last_txn = ?
      WHERE user_id = ? AND balance >= ? AND NOT EXISTS (
        SELECT 1 FROM grants WHERE user_id = ? AND cape = ?
      )`).bind(price, tx, user.id, price, user.id, key),
    db.prepare(`INSERT OR IGNORE INTO grants (user_id, cape)
      SELECT user_id, ? FROM wallets WHERE user_id = ? AND last_txn = ?`).bind(key, user.id, tx),
    db.prepare(`INSERT OR IGNORE INTO coin_ledger (source_id, user_id, delta, reason, created_at, applied)
      SELECT ?, ?, ?, 'cosmetic_purchase', ?, 1 FROM wallets WHERE user_id = ? AND last_txn = ?`).bind(tx, user.id, -price, now, user.id, tx),
  ]);
  if (!changed(results[0])) return json({ error: 'The purchase could not be completed; refresh your balance and try again' }, 409);
  return json({ ok: true, coinsSpent: price });
}

export async function redeemGift(db, user, rawCode) {
  const code = typeof rawCode === 'string'
    ? rawCode.toUpperCase().replace(/^\s*NOVO[\s-]*/, '').replace(/[^A-Z0-9]/g, '')
    : '';
  if (!/^[A-F0-9]{24}$/.test(code)) return json({ error: 'Enter a valid Novo gift code' }, 400);
  const hash = await sha256(code);
  const now = Date.now();
  const gift = await db.prepare('SELECT coins, max_uses, uses, expires_at FROM gift_codes WHERE code_hash = ?').bind(hash).first();
  if (!gift || gift.uses >= gift.max_uses || (gift.expires_at && gift.expires_at <= now)) return json({ error: 'This gift code is invalid or has expired' }, 404);
  if (await db.prepare('SELECT 1 AS redeemed FROM gift_redemptions WHERE code_hash = ? AND user_id = ?').bind(hash, user.id).first()) {
    return json({ error: 'You have already redeemed this code' }, 409);
  }
  await ensureWallet(db, user.id);

  const claim = crypto.randomUUID();
  const tx = `gift:${claim}`;
  const results = await db.batch([
    db.prepare(`UPDATE gift_codes SET uses = uses + 1, last_claim = ?
      WHERE code_hash = ? AND uses < max_uses AND (expires_at IS NULL OR expires_at > ?)
        AND NOT EXISTS (SELECT 1 FROM gift_redemptions WHERE code_hash = ? AND user_id = ?)`)
      .bind(claim, hash, now, hash, user.id),
    db.prepare(`INSERT OR IGNORE INTO gift_redemptions (code_hash, user_id, claim_id, coins, redeemed_at)
      SELECT code_hash, ?, ?, coins, ? FROM gift_codes WHERE code_hash = ? AND last_claim = ?`)
      .bind(user.id, claim, now, hash, claim),
    db.prepare(`UPDATE wallets SET balance = balance + (SELECT coins FROM gift_redemptions WHERE claim_id = ?), last_txn = ?
      WHERE user_id = ? AND EXISTS (SELECT 1 FROM gift_redemptions WHERE claim_id = ?)`)
      .bind(claim, tx, user.id, claim),
    db.prepare(`INSERT OR IGNORE INTO coin_ledger (source_id, user_id, delta, reason, created_at, applied)
      SELECT ?, ?, coins, 'gift_code', ?, 1 FROM gift_redemptions WHERE claim_id = ?`)
      .bind(tx, user.id, now, claim),
  ]);
  if (!changed(results[1])) return json({ error: 'This gift code is invalid, expired, used up, or already redeemed' }, 409);
  return json({ ok: true, coinsAdded: Number(gift.coins) });
}

export async function createCheckout(request, env, user, body) {
  if (!env.STRIPE_SECRET_KEY || !env.STRIPE_WEBHOOK_SECRET) return json({ error: 'Coin checkout is not configured yet' }, 503);
  const bundle = BUNDLES.find((candidate) => candidate.id === body.bundleId);
  if (!bundle) return json({ error: 'Choose a valid coin bundle' }, 400);
  const origin = new URL(request.url).origin;
  const form = new URLSearchParams({
    mode: 'payment',
    'line_items[0][quantity]': '1',
    'line_items[0][price_data][currency]': 'usd',
    'line_items[0][price_data][unit_amount]': String(bundle.cents),
    'line_items[0][price_data][product_data][name]': `Novo Coins · ${bundle.coins}`,
    'line_items[0][price_data][product_data][description]': `${bundle.coins} coins for the Novo Marketplace`,
    // Stripe Managed Payments is on by default for this account and rejects a
    // Checkout line item without a tax code. Permanent in-game goods = the
    // "Video Games - downloaded - non subscription - with permanent rights" code.
    'line_items[0][price_data][product_data][tax_code]': TAX_CODE,
    client_reference_id: user.id,
    success_url: `${origin}/account/checkout/return?session_id={CHECKOUT_SESSION_ID}`,
    cancel_url: `${origin}/account/checkout/return?canceled=1`,
    'metadata[user_id]': user.id,
    'metadata[bundle_id]': bundle.id,
    'metadata[coin_amount]': String(bundle.coins),
  });
  const response = await fetch('https://api.stripe.com/v1/checkout/sessions', {
    method: 'POST',
    headers: { Authorization: `Bearer ${env.STRIPE_SECRET_KEY}`, 'Content-Type': 'application/x-www-form-urlencoded' },
    body: form,
  });
  const result = await response.json().catch(() => ({}));
  if (!response.ok || typeof result.url !== 'string' || !result.url.startsWith('https://checkout.stripe.com/')) {
    // Stripe's own reason, so a bad key, mode mismatch or rejected parameter is
    // diagnosable from `wrangler tail`. Never logs the key itself.
    const why = result.error || {};
    const keyType = String(env.STRIPE_SECRET_KEY).slice(0, 3);
    console.error(`Stripe Checkout could not be created (HTTP ${response.status}, key ${keyType}…)`
      + `${why.type ? ` ${why.type}` : ''}${why.code ? `/${why.code}` : ''}`
      + `${why.param ? ` param=${why.param}` : ''}: ${why.message || 'no message'}`
      + `${why.request_log_url ? ` (${why.request_log_url})` : ''}`);
    return json({ error: 'Could not start checkout. Please try again.' }, 502);
  }
  // The link only works verbatim: Stripe puts an encrypted fragment after the
  // session id. Logged as stats (never the link) so a truncated one is obvious.
  console.log(`Checkout session created (${result.url.length} chars, fragment: ${result.url.includes('#')})`);
  return json({ url: result.url });
}

export async function stripeWebhook(request, env) {
  if (!env.NOVO_DB || !env.STRIPE_WEBHOOK_SECRET) return json({ error: 'Payments are not configured' }, 503);
  const raw = await request.text();
  const signature = request.headers.get('Stripe-Signature') || '';
  if (!(await verifyStripeSignature(raw, signature, env.STRIPE_WEBHOOK_SECRET))) return json({ error: 'Invalid signature' }, 400);
  let event;
  try { event = JSON.parse(raw); } catch { return json({ error: 'Bad event' }, 400); }
  if (event.type !== 'checkout.session.completed') return json({ ok: true });
  const session = event.data?.object;
  const bundle = BUNDLES.find((candidate) => candidate.id === session?.metadata?.bundle_id);
  const userId = session?.metadata?.user_id;
  if (!bundle || !userId || session.client_reference_id !== userId || session.payment_status !== 'paid'
    || session.currency !== 'usd' || session.amount_total !== bundle.cents
    || Number(session.metadata.coin_amount) !== bundle.coins || typeof session.id !== 'string') {
    return json({ error: 'Checkout details did not match a Novo coin bundle' }, 400);
  }
  const user = await env.NOVO_DB.prepare('SELECT id FROM users WHERE id = ?').bind(userId).first();
  if (!user) return json({ error: 'Novo account not found' }, 404);
  await ensureWallet(env.NOVO_DB, userId);
  const source = `stripe:${session.id}`;
  await env.NOVO_DB.batch([
    env.NOVO_DB.prepare(`INSERT OR IGNORE INTO coin_ledger (source_id, user_id, delta, reason, created_at, applied)
      VALUES (?, ?, ?, 'stripe_checkout', ?, 0)`).bind(source, userId, bundle.coins, Date.now()),
    env.NOVO_DB.prepare(`UPDATE wallets SET balance = balance + ?, last_txn = ?
      WHERE user_id = ? AND EXISTS (SELECT 1 FROM coin_ledger WHERE source_id = ? AND applied = 0)`)
      .bind(bundle.coins, source, userId, source),
    env.NOVO_DB.prepare(`UPDATE coin_ledger SET applied = 1 WHERE source_id = ? AND applied = 0`)
      .bind(source),
  ]);
  return json({ ok: true });
}

export function checkoutReturn() {
  return new Response('<!doctype html><meta charset="utf-8"><title>Novo Marketplace</title><body style="font:16px system-ui;background:#0b0e14;color:#eee;text-align:center;padding:18vh 20px"><h1>Thanks for supporting Novo</h1><p>Payment confirmation is being processed. Return to Novo Client; your coin balance will refresh shortly.</p></body>', {
    headers: { 'Content-Type': 'text/html; charset=utf-8' },
  });
}

// ---------------------------------------------------------------------------
// Catalog management for the Discord bot (docs/SHOP_BOT.md) or curl. The
// caller holds SHOP_TOKEN; the news token can't change prices.
//
//   GET    /admin/shop/items          -> {items}  every item, hidden ones too
//   POST   /admin/shop/items          -> add {id, kind, name, description, price, active?, position?}
//   PATCH  /admin/shop/items/<id>     -> change {name?, description?, price?, active?, position?}
//   DELETE /admin/shop/items/<id>     -> remove
//
// Hiding (active: false) keeps the row — and the item's position — for later;
// removing frees the id. Neither affects cosmetics players already own.
export async function handleShopAdmin(request, env, path) {
  if (!env.NOVO_DB) return json({ error: 'Shop not configured' }, 500);
  if (!env.SHOP_TOKEN) return json({ error: 'Shop management is not configured' }, 503);
  if (!tokenMatches(request, env.SHOP_TOKEN)) return json({ error: 'Unauthorized' }, 401);
  const db = env.NOVO_DB;

  if (path === '/admin/shop/items') {
    if (request.method === 'GET') {
      const { results } = await db.prepare('SELECT * FROM shop_items ORDER BY position, id').all();
      return json({ items: results.map(itemRow) });
    }
    if (request.method !== 'POST') return json({ error: 'Use GET or POST' }, 405);
    const body = await readItem(request);
    if (body.error) return json({ error: body.error }, 400);
    const item = body.item;
    if (await db.prepare('SELECT 1 AS found FROM shop_items WHERE id = ?').bind(item.id).first()) {
      return json({ error: 'That item already exists; PATCH it to change its properties' }, 409);
    }
    const next = await db.prepare('SELECT COALESCE(MAX(position), 0) + 1 AS next FROM shop_items').first();
    const position = item.position ?? Number(next?.next || 1);
    const now = Date.now();
    await db
      .prepare('INSERT INTO shop_items (id, kind, name, description, price, active, position, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)')
      .bind(item.id, item.kind, item.name, item.description, item.price, item.active, position, now)
      .run();
    return json({ ok: true, item: { ...item, position, updatedAt: now } }, 201);
  }

  if (!path.startsWith('/admin/shop/items/')) return json({ error: 'Not found' }, 404);
  const id = decodeURIComponent(path.slice('/admin/shop/items/'.length));
  if (!ITEM_ID.test(id)) return json({ error: 'Bad item id' }, 400);
  const existing = await db.prepare('SELECT * FROM shop_items WHERE id = ?').bind(id).first();
  if (!existing) return json({ error: 'No such item' }, 404);

  if (request.method === 'DELETE') {
    await db.prepare('DELETE FROM shop_items WHERE id = ?').bind(id).run();
    return json({ ok: true });
  }
  if (request.method !== 'PATCH') return json({ error: 'Use PATCH or DELETE' }, 405);
  const body = await readItem(request, existing);
  if (body.error) return json({ error: body.error }, 400);
  const item = { ...itemRow(existing), ...body.item };
  const now = Date.now();
  await db
    .prepare('UPDATE shop_items SET name = ?, description = ?, price = ?, active = ?, position = ?, updated_at = ? WHERE id = ?')
    .bind(item.name, item.description, item.price, item.active, item.position, now, id)
    .run();
  return json({ ok: true, item: { ...item, updatedAt: now } });
}

// ---------------------------------------------------------------------------
// Coin gift codes for the Discord bot (docs/SHOP_BOT.md). A code is 24 random
// hex characters, shown once as NOVO-xxxx-xxxx-…; only its SHA-256 is stored,
// so a lost code can never be looked up again — mint a new one instead. The
// players spend them in the launcher's Marketplace (redeemGift above).
//
//   GET  /admin/gift-codes   -> {codes}  every code, newest first
//   POST /admin/gift-codes   -> {codes: [display…]}  mint {coins, uses?, count?, expiresInDays?}
//
// `id` in the listing is the start of the hash: enough to talk about a code,
// useless for redeeming one.
export async function handleGiftAdmin(request, env, path) {
  if (!env.NOVO_DB) return json({ error: 'Gift codes not configured' }, 500);
  if (!env.SHOP_TOKEN) return json({ error: 'Gift codes are not configured' }, 503);
  if (!tokenMatches(request, env.SHOP_TOKEN)) return json({ error: 'Unauthorized' }, 401);
  const db = env.NOVO_DB;
  if (path !== '/admin/gift-codes') return json({ error: 'Not found' }, 404);

  if (request.method === 'GET') {
    const { results } = await db
      .prepare('SELECT code_hash, coins, max_uses, uses, expires_at, created_at FROM gift_codes ORDER BY created_at DESC, code_hash LIMIT 50')
      .all();
    return json({ codes: results.map(giftRow) });
  }
  if (request.method !== 'POST') return json({ error: 'Use GET or POST' }, 405);

  const parsed = await readGift(request);
  if (parsed.error) return json({ error: parsed.error }, 400);
  const { coins, uses, count, expiresAt } = parsed.gift;
  const minted = await Promise.all(Array.from({ length: count }, mintCode));
  const now = Date.now();
  await db.batch(minted.map(({ hash }) => db
    .prepare('INSERT INTO gift_codes (code_hash, coins, max_uses, uses, expires_at, created_at) VALUES (?, ?, ?, 0, ?, ?)')
    .bind(hash, coins, uses, expiresAt, now)));
  // The display codes leave here exactly once; only their hashes are kept.
  return json({ codes: minted.map(({ display }) => display), coins, uses, count, expiresAt }, 201);
}

const GIFT_LIMITS = { coins: 1_000_000, uses: 100_000, count: 25, days: 365 };
const GIFT_FIELDS = ['coins', 'uses', 'count', 'expiresInDays'];

/** A new code: the 24 uppercase hex characters players type, and their hash. */
async function mintCode() {
  const raw = [...crypto.getRandomValues(new Uint8Array(12))]
    .map((byte) => byte.toString(16).padStart(2, '0'))
    .join('')
    .toUpperCase();
  return { display: `NOVO-${raw.match(/.{4}/g).join('-')}`, hash: await sha256(raw) };
}

/** A stored code as the API reports it (never the code itself). */
function giftRow(row) {
  const maxUses = Number(row.max_uses);
  const uses = Number(row.uses);
  return {
    id: String(row.code_hash).slice(0, 8),
    coins: Number(row.coins),
    uses,
    maxUses,
    expiresAt: row.expires_at === null || row.expires_at === undefined ? null : Number(row.expires_at),
    createdAt: Number(row.created_at),
  };
}

/** The validated body of a POST /admin/gift-codes. */
async function readGift(request) {
  const raw = await request.text();
  if (raw.length > ITEM_BODY_MAX) return { error: 'Too large' };
  let input;
  try {
    input = JSON.parse(raw);
  } catch {
    return { error: 'Send JSON' };
  }
  if (!input || typeof input !== 'object' || Array.isArray(input)) return { error: 'Send a JSON object' };
  const unknown = Object.keys(input).find((key) => !GIFT_FIELDS.includes(key));
  if (unknown) return { error: `Unknown field: ${unknown}` };
  if (!Number.isInteger(input.coins) || input.coins < 1 || input.coins > GIFT_LIMITS.coins) {
    return { error: `coins must be a whole number from 1 to ${GIFT_LIMITS.coins}` };
  }
  const uses = input.uses ?? 1;
  if (!Number.isInteger(uses) || uses < 1 || uses > GIFT_LIMITS.uses) {
    return { error: `uses must be a whole number from 1 to ${GIFT_LIMITS.uses}` };
  }
  const count = input.count ?? 1;
  if (!Number.isInteger(count) || count < 1 || count > GIFT_LIMITS.count) {
    return { error: `count must be a whole number from 1 to ${GIFT_LIMITS.count}` };
  }
  const days = input.expiresInDays ?? 0;
  if (!Number.isInteger(days) || days < 0 || days > GIFT_LIMITS.days) {
    return { error: `expiresInDays must be 0 (never expires) to ${GIFT_LIMITS.days}` };
  }
  return { gift: { coins: input.coins, uses, count, expiresAt: days === 0 ? null : Date.now() + days * 86400000 } };
}

/** A stored row as the API reports it. */
function itemRow(row) {
  return {
    id: row.id,
    kind: row.kind,
    name: row.name,
    description: row.description,
    price: Number(row.price),
    active: Boolean(row.active),
    position: Number(row.position),
    updatedAt: Number(row.updated_at),
  };
}

/** The validated body of a POST (no `existing`) or PATCH (`existing` row). */
async function readItem(request, existing) {
  const raw = await request.text();
  if (raw.length > ITEM_BODY_MAX) return { error: 'Too large' };
  let input;
  try {
    input = JSON.parse(raw);
  } catch {
    return { error: 'Send JSON' };
  }
  if (!input || typeof input !== 'object' || Array.isArray(input)) return { error: 'Send a JSON object' };
  const unknown = Object.keys(input).find((key) => !ITEM_FIELDS.includes(key));
  if (unknown) return { error: `Unknown field: ${unknown}` };
  if (existing && 'kind' in input) return { error: 'kind cannot change; remove the item and add it again' };
  if (existing && 'id' in input) return { error: 'id cannot change; remove the item and add it again' };

  const item = existing ? { ...itemRow(existing) } : { active: true };
  if (!existing) {
    if (typeof input.id !== 'string' || !ITEM_ID.test(input.id)) return { error: 'id must be 1-32 lowercase letters, digits or _' };
    if (!KINDS.includes(input.kind)) return { error: `kind must be one of ${KINDS.join(', ')}` };
    item.id = input.id;
    item.kind = input.kind;
  }
  for (const [key, max] of [['name', ITEM_LIMITS.name], ['description', ITEM_LIMITS.description]]) {
    if (existing && !(key in input)) continue;
    const value = typeof input[key] === 'string' ? input[key].trim() : '';
    if (!value || value.length > max) return { error: `${key} must be 1-${max} characters` };
    item[key] = value;
  }
  if (!existing || 'price' in input) {
    if (!Number.isInteger(input.price) || input.price < 1 || input.price > ITEM_LIMITS.price) {
      return { error: `price must be a whole number of coins from 1 to ${ITEM_LIMITS.price}` };
    }
    item.price = input.price;
  }
  if ('active' in input) {
    if (typeof input.active !== 'boolean') return { error: 'active must be true or false' };
    item.active = input.active;
  }
  if ('position' in input) {
    if (!Number.isInteger(input.position) || input.position < 0 || input.position > ITEM_LIMITS.position) {
      return { error: `position must be a whole number from 0 to ${ITEM_LIMITS.position}` };
    }
    item.position = input.position;
  }
  if (!existing) {
    const catalog = catalogFor(item.kind);
    const entry = catalog.find((cosmetic) => cosmetic.id === item.id);
    if (!entry) return { error: `"${item.id}" is not a ${item.kind} in relay/src/capes.js` };
    if (entry.free) return { error: `"${item.id}" is a free ${item.kind}; everyone already owns it` };
  }
  return { item };
}

/** Constant-time Bearer check that never reveals where a token differs. */
function tokenMatches(request, expected) {
  const header = request.headers.get('Authorization') || '';
  const token = header.startsWith('Bearer ') ? header.slice(7).trim() : '';
  if (token.length !== expected.length) return false;
  let diff = 0;
  for (let i = 0; i < token.length; i++) diff |= token.charCodeAt(i) ^ expected.charCodeAt(i);
  return diff === 0;
}

async function verifyStripeSignature(payload, header, secret) {
  const parts = header.split(',').map((part) => part.split('=', 2).map((value) => value.trim()));
  const timestamp = Number(parts.find(([key]) => key === 't')?.[1]);
  const signatures = parts.filter(([key]) => key === 'v1').map(([, value]) => value);
  if (!timestamp || Math.abs(Date.now() / 1000 - timestamp) > 300 || signatures.length === 0) return false;
  const key = await crypto.subtle.importKey('raw', new TextEncoder().encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  // Stripe signs "<timestamp>.<raw body>"; the timestamp comes from the header, not from `parts`.
  const bytes = new Uint8Array(await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(`${timestamp}.${payload}`)));
  const expected = [...bytes].map((byte) => byte.toString(16).padStart(2, '0')).join('');
  return signatures.some((candidate) => candidate.length === expected.length
    && [...candidate].reduce((diff, char, i) => diff | (char.charCodeAt(0) ^ expected.charCodeAt(i)), 0) === 0);
}

async function sha256(text) {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text));
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, '0')).join('');
}

function changed(result) { return Number(result?.meta?.changes ?? result?.changes ?? 0) > 0; }
function json(data, status = 200) { return new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } }); }

