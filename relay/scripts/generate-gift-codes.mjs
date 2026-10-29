// Creates cryptographically random coin codes, stores only their SHA-256
// hashes in D1, then prints the raw codes once for the developer to share.
import { randomBytes, createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const [countArg, coinsArg, usesArg = '1'] = process.argv.slice(2);
const count = Number(countArg);
const coins = Number(coinsArg);
const uses = Number(usesArg);
if (!Number.isInteger(count) || count < 1 || count > 100 || !Number.isInteger(coins) || coins < 1 || coins > 1_000_000
  || !Number.isInteger(uses) || uses < 1 || uses > 100_000) {
  console.error('Usage: node relay/scripts/generate-gift-codes.mjs <count 1-100> <coins> [uses-per-code, default 1]');
  process.exit(2);
}

const codes = Array.from({ length: count }, () => {
  const raw = randomBytes(12).toString('hex').toUpperCase();
  return {
    display: `NOVO-${raw.match(/.{4}/g).join('-')}`,
    hash: createHash('sha256').update(raw).digest('hex'),
  };
});
const now = Date.now();
const rows = codes.map(({ hash }) => `('${hash}', ${coins}, ${uses}, 0, NULL, ${now})`).join(',\n');
const sql = `INSERT INTO gift_codes (code_hash, coins, max_uses, uses, expires_at, created_at) VALUES\n${rows};\n`;
const temp = mkdtempSync(join(tmpdir(), 'novo-gifts-'));
const sqlFile = join(temp, 'gift-codes.sql');
writeFileSync(sqlFile, sql, { flag: 'wx' });
try {
  const npx = process.platform === 'win32' ? 'npx.cmd' : 'npx';
  const root = resolve(fileURLToPath(new URL('../..', import.meta.url)));
  execFileSync(npx, ['-y', 'wrangler@4', 'd1', 'execute', 'novo-accounts', '--remote', '--file', sqlFile], {
    cwd: root,
    stdio: 'inherit',
    shell: process.platform === 'win32',
  });
} finally {
  rmSync(temp, { recursive: true, force: true });
}
console.log(`Created ${count} gift code${count === 1 ? '' : 's'} worth ${coins.toLocaleString()} coins each (${uses} redemption${uses === 1 ? '' : 's'} per code):`);
for (const { display } of codes) console.log(display);
