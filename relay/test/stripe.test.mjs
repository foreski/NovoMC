// Tests the coin money path — gift codes, purchases, equipping, and Stripe
// Checkout fulfillment — with an in-memory D1: `node relay/test/stripe.test.mjs`
import assert from 'node:assert/strict';
import { createHash, createHmac } from 'node:crypto';
import worker from '../src/worker.js';
import { TAX_CODE } from '../src/shop.js';
import { d1 } from './d1.mjs';

const db = d1();
const now = Date.now();
const token = 'A'.repeat(43);
const sha256 = (text) => createHash('sha256').update(text).digest('hex');
db.sqlite
  .prepare("INSERT INTO users (id, discord_id, discord_name, avatar, display_name, cape, created_at) VALUES ('u1', '1', 'tester', NULL, 'Tester', NULL, 0)")
  .run();
db.sqlite.prepare('INSERT INTO sessions (token_hash, user_id, expires_at) VALUES (?, ?, ?)').run(sha256(token), 'u1', now + 86400000);

const env = { NOVO_DB: db };
const call = async (method, path, data) => {
  const res = await worker.fetch(
    new Request(`https://relay.example${path}`, {
      method,
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: data === undefined ? undefined : JSON.stringify(data),
    }),
    env,
  );
  const text = await res.text();
  let body = null;
  try { body = JSON.parse(text); } catch { /* the checkout return page is HTML */ }
  return { status: res.status, body };
};
const balance = () => db.sqlite.prepare("SELECT balance FROM wallets WHERE user_id = 'u1'").get().balance;

// Checkout stays off, and says so, until both Stripe secrets exist.
let r = await call('GET', '/account/shop');
assert.equal(r.status, 200);
assert.equal(r.body.paymentReady, false);
assert.equal(r.body.balance, 0);
assert.equal(r.body.items.length, 22);
assert.equal((await call('POST', '/account/checkout', { bundleId: 'boost' })).status, 503);

// A developer gift code (as relay/scripts/generate-gift-codes.mjs writes it) credits coins once.
const code = 'A1B2C3D4E5F60718293A4B5C';
db.sqlite.prepare('INSERT INTO gift_codes (code_hash, coins, max_uses, uses, created_at) VALUES (?, 1200, 1, 0, 0)').run(sha256(code));
r = await call('POST', '/account/shop/redeem', { code });
assert.equal(r.status, 200);
assert.equal(r.body.coins, 1200);
assert.equal((await call('POST', '/account/shop/redeem', { code })).status, 404);
assert.equal(balance(), 1200);

// Purchases spend the stored price, grant the cosmetic once, and can be equipped.
r = await call('POST', '/account/shop/purchase', { id: 'violet_wings' });
assert.equal(r.status, 200);
assert.equal(r.body.coins, 300);
assert.equal(r.body.wings.find((w) => w.id === 'violet_wings').owned, true);
assert.equal((await call('POST', '/account/shop/purchase', { id: 'violet_wings' })).status, 409);
assert.equal((await call('POST', '/account/shop/purchase', { id: 'monochrome_wings' })).status, 409);
assert.equal((await call('POST', '/account/shop/purchase', { id: 'not_in_shop' })).status, 404);
assert.equal(balance(), 300);
r = await call('PATCH', '/account/me', { wing: 'violet_wings' });
assert.equal(r.body.wing, 'violet_wings');
assert.equal((await call('PATCH', '/account/me', { wing: 'verdant_wings' })).status, 403);

// Stripe: both secrets switch checkout on.
env.STRIPE_SECRET_KEY = 'sk_test_dummy';
env.STRIPE_WEBHOOK_SECRET = 'whsec_test_dummy';
assert.equal((await call('GET', '/account/shop')).body.paymentReady, true);

// Creating the session: Stripe Managed Payments is on by default on this account
// and rejects a line item without a tax code, so it must always be sent. The
// real Stripe call is stubbed — this only checks the request we build.
{
  const realFetch = globalThis.fetch;
  let form = null;
  globalThis.fetch = async (url, init) => {
    assert.equal(String(url), 'https://api.stripe.com/v1/checkout/sessions');
    form = new URLSearchParams(String(init.body));
    return new Response(JSON.stringify({ url: 'https://checkout.stripe.com/c/pay/cs_test_1' }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  };
  try {
    r = await call('POST', '/account/checkout', { bundleId: 'boost' });
  } finally {
    globalThis.fetch = realFetch;
  }
  assert.equal(r.status, 200);
  assert.equal(r.body.url, 'https://checkout.stripe.com/c/pay/cs_test_1');
  assert.equal(form.get('line_items[0][price_data][product_data][tax_code]'), TAX_CODE);
  assert.equal(form.get('line_items[0][price_data][unit_amount]'), '499');
  assert.equal(form.get('metadata[bundle_id]'), 'boost');
}

const session = (over) => ({
  id: 'cs_test_1',
  client_reference_id: 'u1',
  payment_status: 'paid',
  currency: 'usd',
  amount_total: 499,
  metadata: { user_id: 'u1', bundle_id: 'boost', coin_amount: '550' },
  ...over,
});
const event = (over) => JSON.stringify({ type: 'checkout.session.completed', data: { object: session(over) } });
const sign = (payload, secret, at = Math.floor(Date.now() / 1000)) =>
  `t=${at},v1=${createHmac('sha256', secret).update(`${at}.${payload}`).digest('hex')}`;
const webhook = async (payload, signature) => {
  const res = await worker.fetch(
    new Request('https://relay.example/stripe/webhook', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Stripe-Signature': signature },
      body: payload,
    }),
    env,
  );
  return res.status;
};

const paid = event();
assert.equal(await webhook(paid, sign(paid, 'whsec_test_dummy')), 200);
assert.equal(balance(), 850, 'the Boost bundle credited its 550 coins');
// Stripe retries deliveries; the ledger must not credit twice.
assert.equal(await webhook(paid, sign(paid, 'whsec_test_dummy')), 200);
assert.equal(balance(), 850);
assert.equal(await webhook(paid, sign(paid, 'whsec_wrong')), 400);
assert.equal(await webhook(paid, sign(paid, 'whsec_test_dummy', Math.floor(Date.now() / 1000) - 3600)), 400);
assert.equal(balance(), 850);
// Details that don't match the bundle the client asked for are refused.
for (const wrong of [{ amount_total: 199 }, { payment_status: 'unpaid' }, { currency: 'eur' }, { metadata: { user_id: 'u1', bundle_id: 'boost', coin_amount: '5000' } }]) {
  const forged = event(wrong);
  assert.equal(await webhook(forged, sign(forged, 'whsec_test_dummy')), 400, JSON.stringify(wrong));
}
assert.equal(balance(), 850);

// Every credit landed in the ledger exactly once: gift, purchase, checkout.
const applied = db.sqlite.prepare("SELECT source_id, delta, reason FROM coin_ledger WHERE user_id = 'u1' AND applied = 1 ORDER BY reason").all();
assert.deepEqual(applied.map((row) => [row.reason, row.delta]), [['cosmetic_purchase', -900], ['gift_code', 1200], ['stripe_checkout', 550]]);

console.log('stripe tests passed');
