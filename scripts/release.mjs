// Builds a signed NovoMC release and (optionally) publishes it to GitHub.
//
//   npm run release                         build mod + signed installer + latest.json
//   npm run release -- --version 0.2.0     set the version first (tauri.conf.json, Cargo.toml, package.json)
//   npm run release -- --publish --notes "What changed"
//                                           also create GitHub release v<version> with the
//                                           installer and latest.json (the update feed)
//
// Signing uses the updater key in %USERPROFILE%\.tauri\novomc-updater.key (or
// TAURI_SIGNING_PRIVATE_KEY). Keep that file secret and backed up: without it
// you can't publish updates that existing installs will accept.
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const REPO = 'foreski/NovoMC';
const args = process.argv.slice(2);
const flag = (name) => args.includes(name);
const option = (name) => {
  const i = args.indexOf(name);
  return i >= 0 ? args[i + 1] : undefined;
};

function run(cmd, cmdArgs, env = {}) {
  execFileSync(cmd, cmdArgs, { cwd: root, stdio: 'inherit', shell: true, env: { ...process.env, ...env } });
}

/** Writes `version` into the three places that carry it. */
function setVersion(version) {
  if (!/^\d+\.\d+\.\d+$/.test(version)) throw new Error(`Version must look like 1.2.3, got ${version}`);
  const conf = join(root, 'src-tauri', 'tauri.conf.json');
  const tauri = JSON.parse(readFileSync(conf, 'utf8'));
  tauri.version = version;
  writeFileSync(conf, JSON.stringify(tauri, null, 2) + '\n');
  const pkgFile = join(root, 'package.json');
  const pkg = JSON.parse(readFileSync(pkgFile, 'utf8'));
  pkg.version = version;
  writeFileSync(pkgFile, JSON.stringify(pkg, null, 2) + '\n');
  const cargoFile = join(root, 'src-tauri', 'Cargo.toml');
  writeFileSync(cargoFile, readFileSync(cargoFile, 'utf8').replace(/^version = ".*"$/m, `version = "${version}"`));
  console.log(`Version set to ${version}`);
}

function gh() {
  const installed = 'C:\\Program Files\\GitHub CLI\\gh.exe';
  return existsSync(installed) ? `"${installed}"` : 'gh';
}

if (option('--version')) setVersion(option('--version'));
const version = JSON.parse(readFileSync(join(root, 'src-tauri', 'tauri.conf.json'), 'utf8')).version;

const keyFile = join(homedir(), '.tauri', 'novomc-updater.key');
const signing = process.env.TAURI_SIGNING_PRIVATE_KEY
  ? {}
  : existsSync(keyFile)
    ? { TAURI_SIGNING_PRIVATE_KEY: keyFile, TAURI_SIGNING_PRIVATE_KEY_PASSWORD: process.env.TAURI_SIGNING_PRIVATE_KEY_PASSWORD ?? '' }
    : null;
if (!signing) throw new Error(`No updater signing key: expected ${keyFile} or TAURI_SIGNING_PRIVATE_KEY`);

run('npm', ['run', 'mod']);
run('npx', ['tauri', 'build'], signing);

const bundle = join(root, 'src-tauri', 'target', 'release', 'bundle', 'nsis');
const installerName = `NovoMC_${version}_x64-setup.exe`;
const installer = join(bundle, installerName);
const sigFile = `${installer}.sig`;
if (!existsSync(installer) || !existsSync(sigFile)) throw new Error(`Missing ${installerName} or its .sig in ${bundle}`);

// GitHub turns spaces in asset names into dots; this name has none.
const notes = option('--notes') ?? `NovoMC ${version}`;
const feed = {
  version,
  notes,
  pub_date: new Date().toISOString(),
  platforms: {
    'windows-x86_64': {
      signature: readFileSync(sigFile, 'utf8').trim(),
      url: `https://github.com/${REPO}/releases/download/v${version}/${installerName}`,
    },
  },
};
const feedFile = join(bundle, 'latest.json');
writeFileSync(feedFile, JSON.stringify(feed, null, 2) + '\n');
console.log(`\nBuilt ${installerName} and latest.json in\n  ${bundle}`);

if (flag('--publish')) {
  run(gh(), ['release', 'create', `v${version}`, `"${installer}"`, `"${feedFile}"`, '--repo', REPO, '--title', `"NovoMC ${version}"`, '--notes', JSON.stringify(notes)]);
  console.log(`\nPublished v${version}. Installed copies of NovoMC will offer this update on their next start.`);
} else {
  console.log('\nNot published. Re-run with --publish to create the GitHub release.');
}
