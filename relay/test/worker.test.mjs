// Tests the relay with GitHub's API mocked: `node relay/test/worker.test.mjs`
import assert from 'node:assert/strict';
import worker, { body, clean } from '../src/worker.js';

const env = { GITHUB_TOKEN: 'test-token', GITHUB_REPO: 'owner/repo' };
const calls = [];
let searchResult = { items: [] };
globalThis.fetch = async (url, init) => {
  calls.push({ url, method: init.method, body: init.body ? JSON.parse(init.body) : null, auth: init.headers.Authorization });
  if (url.includes('/search/issues')) return new Response(JSON.stringify(searchResult));
  return new Response(JSON.stringify({ number: 7, html_url: 'https://github.com/owner/repo/issues/7' }));
};

const post = (data, ip = '1.1.1.1') =>
  worker.fetch(
    new Request('https://relay.example/report', { method: 'POST', body: JSON.stringify(data), headers: { 'CF-Connecting-IP': ip } }),
    env,
  );

// A crash report becomes a labeled issue.
let res = await post({ kind: 'crash', title: 'NullPointerException in Freelook', version: '0.1.1', crash: 'java.lang.NPE\n```evil```', signature: 'abc123' });
assert.equal(res.status, 200);
const created = calls.find((c) => c.method === 'POST' && c.url.endsWith('/issues'));
assert.equal(created.body.title, '[Crash] NullPointerException in Freelook');
assert.deepEqual(created.body.labels, ['bug', 'auto-report']);
assert.equal(created.auth, 'Bearer test-token');
assert.ok(created.body.body.includes('novomc-signature:abc123'));
assert.ok(!created.body.body.includes('```evil```'), 'fences inside logs are neutralized');

// The same crash again becomes a comment, not a new issue.
calls.length = 0;
searchResult = { items: [{ number: 7, html_url: 'https://github.com/owner/repo/issues/7' }] };
res = await post({ kind: 'crash', title: 'NullPointerException in Freelook', signature: 'abc123' });
assert.equal((await res.json()).duplicate, true);
assert.ok(calls.some((c) => c.url.endsWith('/issues/7/comments')));
assert.ok(!calls.some((c) => c.method === 'POST' && c.url.endsWith('/issues')));

// Validation and limits.
assert.equal((await post({ description: 'no title' })).status, 400);
assert.equal((await worker.fetch(new Request('https://relay.example/report'), env)).status, 405);
assert.equal((await post({ title: 'x'.repeat(70000) })).status, 413);
for (let i = 0; i < 5; i++) await post({ title: 'spam' }, '9.9.9.9');
assert.equal((await post({ title: 'spam' }, '9.9.9.9')).status, 429);
assert.equal(clean({ title: 't', signature: 'NOT-HEX' }).signature, '');
assert.ok(body(clean({ title: 't', description: 'hi @everyone' })).includes('@​everyone'), 'mentions are defused');

console.log('relay tests passed');
