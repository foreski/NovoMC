// The slice of Cloudflare D1's API the relay uses, over an in-memory SQLite
// database with relay/schema.sql applied (Node 22.5+).
import { readFileSync } from 'node:fs';
import { DatabaseSync } from 'node:sqlite';

export function d1() {
  const sqlite = new DatabaseSync(':memory:');
  sqlite.exec(readFileSync(new URL('../schema.sql', import.meta.url), 'utf8'));
  const statement = (sql, args = []) => ({
    bind: (...a) => statement(sql, a),
    run: async () => (sqlite.prepare(sql).run(...args), { success: true }),
    first: async () => sqlite.prepare(sql).get(...args) ?? null,
    all: async () => ({ results: sqlite.prepare(sql).all(...args) }),
  });
  return { prepare: (sql) => statement(sql), sqlite };
}
