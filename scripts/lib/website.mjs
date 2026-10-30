// The site lives in its own worktree (branch `website`). Its markup carries a
// version in the download links and the hero, but only as a fallback: main.js
// asks GitHub for the newest release on load and rewrites both. Those fallback
// values are the thing that rots, so the release script refreshes them.
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const ASSET = /(\/releases\/download\/v)\d+\.\d+\.\d+(\/NovoMC_)\d+\.\d+\.\d+(_x64-setup\.exe)/g;
const EYEBROW = /(<span data-version>)\d+\.\d+\.\d+(<\/span>)/g;

/** The site worktree, or null when this checkout doesn't have one. */
export function siteDir(root) {
  const dir = join(root, '.website');
  return existsSync(join(dir, 'index.html')) ? dir : null;
}

/**
 * Rewrites every download href and the hero version in `dir/index.html` to
 * `version`. Returns true when the file actually changed.
 */
export function syncFallbackVersion(dir, version) {
  const file = join(dir, 'index.html');
  const before = readFileSync(file, 'utf8');
  const after = before
    .replace(ASSET, `$1${version}$2${version}$3`)
    .replace(EYEBROW, `$1${version}$2`);
  if (after === before) return false;
  writeFileSync(file, after);
  return true;
}
