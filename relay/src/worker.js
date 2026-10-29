// NovoMC bug-report relay (Cloudflare Worker).
//
// The launcher POSTs reports here; the Worker files them as GitHub issues with
// a token it keeps as a secret, so no token ever ships inside NovoMC.
//
//   POST /report  {kind, title, description, version, os, java, minecraft, loader, log, crash, signature}
//   POST /presence/...  who's on NovoMC, for the in-game badge (see presence.js)
//   GET|POST|DELETE /news  launcher news, published by the Discord bot (see news.js)
//   /account/...  Novo Client accounts, coin wallet, shop, gift codes, and Stripe Checkout
//   /admin/shop/items  shop catalog management for the Discord bot (SHOP_TOKEN)
//   POST /stripe/webhook  verified Stripe Checkout payment fulfillment
//
// Environment:
//   GITHUB_TOKEN  (secret) fine-grained token with Issues: read & write on the repo
//   GITHUB_REPO   e.g. "foreski/NovoMC"
//   RATE          (optional KV namespace) for rate limits shared across instances
//   PRESENCE      Durable Object namespace (class Presence)
//   NEWS          KV namespace for news; NEWS_TOKEN (secret) lets the bot publish
//   NOVO_DB       D1 database for accounts and store
//   SHOP_TOKEN    (secret) lets the Discord bot edit the shop catalog (see docs/SHOP_BOT.md)
//   STRIPE_SECRET_KEY and STRIPE_WEBHOOK_SECRET are secrets; both are required to enable checkout
//
// Abuse limits: 64 KB per report, 5 reports per IP per hour, and repeats of
// the same crash (same `signature`) become a comment on the existing issue.

import { handleAccount } from './account.js';
import { handleNews } from './news.js';
import { handlePresence } from './presence.js';
import { handleShopAdmin, stripeWebhook } from './shop.js';

export { Presence } from './presence.js';

const MAX_BODY = 64 * 1024;
const LIMITS = { title: 120, description: 4000, version: 32, os: 80, java: 80, minecraft: 40, loader: 80, log: 24000, crash: 24000, signature: 64 };
const PER_HOUR = 5;
const memoryHits = new Map();

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === '/stripe/webhook') return stripeWebhook(request, env);
    if (url.pathname.startsWith('/admin/shop/')) return handleShopAdmin(request, env, url.pathname);
    if (url.pathname.startsWith('/account/')) return handleAccount(request, env, url.pathname);
    if (url.pathname.startsWith('/presence/')) return handlePresence(request, env, url.pathname);
    if (url.pathname === '/news' || url.pathname.startsWith('/news/')) return handleNews(request, env, url.pathname);
    if (url.pathname !== '/report') return json({ error: 'Not found' }, 404);
    if (request.method !== 'POST') return json({ error: 'Use POST' }, 405);
    if (!env.GITHUB_TOKEN || !env.GITHUB_REPO) return json({ error: 'Relay not configured' }, 500);

    const raw = await request.text();
    if (raw.length > MAX_BODY) return json({ error: 'Report too large' }, 413);
    let report;
    try {
      report = clean(JSON.parse(raw));
    } catch (e) {
      return json({ error: `Bad report: ${e.message}` }, 400);
    }

    const ip = request.headers.get('CF-Connecting-IP') || 'unknown';
    if (!(await allow(env, ip))) return json({ error: 'Too many reports; try again later' }, 429);

    try {
      return await file(github(env), env.GITHUB_REPO, report);
    } catch (e) {
      // GitHub's message (never the token) so problems can be diagnosed from the launcher.
      return json({ error: `Couldn't file the issue: ${e.message}` }, 502);
    }
  },
};

async function file(gh, repo, report) {
  if (report.signature) {
    const existing = await findBySignature(gh, repo, report.signature);
    if (existing) {
      await gh('POST', `/repos/${repo}/issues/${existing.number}/comments`, { body: occurrence(report) });
      return json({ ok: true, duplicate: true, issue: existing.html_url });
    }
  }
  const issue = await gh('POST', `/repos/${repo}/issues`, {
    title: `[${report.kind === 'crash' ? 'Crash' : 'Bug'}] ${report.title}`,
    body: body(report),
    labels: ['bug', 'auto-report'],
  });
  return json({ ok: true, issue: issue.html_url });
}

/** Keeps known fields, as trimmed strings within their limits. */
export function clean(input) {
  if (!input || typeof input !== 'object') throw new Error('expected a JSON object');
  const out = { kind: input.kind === 'crash' ? 'crash' : 'manual' };
  for (const [key, max] of Object.entries(LIMITS)) {
    const v = input[key];
    out[key] = typeof v === 'string' ? v.slice(0, max).trim() : '';
  }
  if (!out.title) throw new Error('title is required');
  if (!/^[0-9a-f]{0,64}$/.test(out.signature)) out.signature = '';
  return out;
}

async function allow(env, ip) {
  const hour = Math.floor(Date.now() / 3600000);
  const key = `rate:${ip}:${hour}`;
  if (env.RATE) {
    const n = Number((await env.RATE.get(key)) || 0);
    if (n >= PER_HOUR) return false;
    await env.RATE.put(key, String(n + 1), { expirationTtl: 3700 });
    return true;
  }
  const n = memoryHits.get(key) || 0;
  if (n >= PER_HOUR) return false;
  memoryHits.set(key, n + 1);
  return true;
}

function github(env) {
  return async (method, path, payload) => {
    const res = await fetch(`https://api.github.com${path}`, {
      method,
      headers: {
        Authorization: `Bearer ${env.GITHUB_TOKEN}`,
        Accept: 'application/vnd.github+json',
        'User-Agent': 'NovoMC-bug-relay',
        'X-GitHub-Api-Version': '2022-11-28',
        ...(payload ? { 'Content-Type': 'application/json' } : {}),
      },
      body: payload ? JSON.stringify(payload) : undefined,
    });
    if (!res.ok) {
      const detail = await res.json().catch(() => ({}));
      throw new Error(`GitHub ${method} ${path.split('?')[0]}: HTTP ${res.status}${detail.message ? ` (${detail.message})` : ''}`);
    }
    return res.json();
  };
}

async function findBySignature(gh, repo, signature) {
  const q = encodeURIComponent(`repo:${repo} is:issue is:open label:auto-report "novomc-signature:${signature}" in:body`);
  const found = await gh('GET', `/search/issues?q=${q}&per_page=1`);
  return found.items && found.items[0];
}

/** Text from players goes inside fences or quotes so it can't break the layout or ping anyone. */
function fence(text) {
  return '```\n' + text.replace(/```/g, '`​``') + '\n```';
}

function quote(text) {
  return text
    .replace(/@/g, '@​')
    .split('\n')
    .map((l) => `> ${l}`)
    .join('\n');
}

function environment(r) {
  return [
    `| | |`,
    `| --- | --- |`,
    `| NovoMC | ${r.version || '?'} |`,
    `| OS | ${r.os || '?'} |`,
    `| Java | ${r.java || '?'} |`,
    `| Minecraft | ${r.minecraft || '?'} ${r.loader || ''} |`,
  ].join('\n');
}

export function body(r) {
  const parts = [
    r.kind === 'crash' ? 'Minecraft crashed and NovoMC sent this report automatically.' : 'A player reported a bug from NovoMC.',
    '',
    r.description ? `### What happened\n\n${quote(r.description)}` : '',
    '### Environment\n\n' + environment(r),
    r.crash ? `<details><summary>Crash report</summary>\n\n${fence(r.crash)}\n\n</details>` : '',
    r.log ? `<details><summary>Game log (last lines)</summary>\n\n${fence(r.log)}\n\n</details>` : '',
    r.signature ? `<sub>novomc-signature:${r.signature} · reports are redacted by the launcher before sending</sub>` : '',
  ];
  return parts.filter(Boolean).join('\n\n');
}

function occurrence(r) {
  return `Happened again: NovoMC ${r.version || '?'} on ${r.os || '?'}, Minecraft ${r.minecraft || '?'}.` + (r.description ? `\n\n${quote(r.description)}` : '');
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } });
}
