// A local relay for the in-game checks, without wrangler: the real Worker
// with presence in memory and D1 played by SQLite. Every player who says
// hello is linked to a test Novo account wearing the given cosmetics.
//
//   node relay/test/local-relay.mjs [port=8787] [cape=tidal] [wing=angel_wings] [hat=wizard_hat]
//   NOVOMC_TEST_RELAY=http://127.0.0.1:8787 NOVOMC_TEST_MODE=modcheck ... cargo test launch_real -- --ignored
import { createServer } from 'node:http';
import worker from '../src/worker.js';
import { Presence } from '../src/presence.js';
import { d1 } from './d1.mjs';

const port = Number(process.argv[2] || 8787);
const cape = process.argv[3] || 'tidal';
const wing = process.argv[4] || 'angel_wings';
const hat = process.argv[5] || 'wizard_hat';
const db = d1();
db.sqlite
  .prepare("INSERT INTO users (id, discord_id, discord_name, avatar, display_name, cape, wing, hat, created_at) VALUES ('tester', '1', 'tester', NULL, 'Tester', ?, ?, ?, 0)")
  .run(cape, wing, hat);
const presence = new Presence(null, null);
const env = {
  NOVO_DB: db,
  PRESENCE: { idFromName: () => 'global', get: () => ({ fetch: (url, init) => presence.fetch(new Request(url, init)) }) },
};

createServer(async (req, res) => {
  const chunks = [];
  for await (const c of req) chunks.push(c);
  const body = Buffer.concat(chunks).toString('utf8');
  if (req.url === '/presence/hello') {
    try {
      const uuid = JSON.parse(body).uuid;
      db.sqlite.prepare("INSERT OR REPLACE INTO links (uuid, user_id, name, linked_at) VALUES (?, 'tester', 'test', 0)").run(String(uuid).toLowerCase());
    } catch {}
  }
  const request = new Request(`http://127.0.0.1:${port}${req.url}`, {
    method: req.method,
    headers: { 'Content-Type': 'application/json', 'CF-Connecting-IP': '127.0.0.1' },
    body: req.method === 'GET' || req.method === 'HEAD' ? undefined : body,
  });
  const out = await worker.fetch(request, env);
  const text = await out.text();
  console.log(`${req.method} ${req.url} -> ${out.status} ${text.slice(0, 160)}`);
  res.writeHead(out.status, { 'Content-Type': out.headers.get('Content-Type') || 'application/json' });
  res.end(text);
}).listen(port, '127.0.0.1', () => console.log(`Local relay on http://127.0.0.1:${port} (${cape}, ${wing}, ${hat})`));
