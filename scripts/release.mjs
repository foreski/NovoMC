// Builds a signed NovoMC release and (optionally) publishes it to GitHub.
//
//   npm run release                         build mod + signed installer + latest.json
//   npm run release -- --version 0.4.0     set the public app version first
//   npm run release -- --bugfix             increment the private build revision without changing 0.3.3
//   npm run release -- --publish --notes "What changed"
//                                           also create GitHub release v<version> with the
//                                           installer and latest.json (the update feed)
//
// Signing uses the updater key in %USERPROFILE%\.tauri\novomc-updater.key (or
// TAURI_SIGNING_PRIVATE_KEY). Keep that file secret and backed up: without it
// you can't publish updates that existing installs will accept.
//
// The site's fallback version is refreshed too: with `--publish` the `website`
// worktree in .website is committed and pushed, otherwise it is only edited.
import { execFileSync } from 'node:child_process';
import { copyFileSync, existsSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { siteDir, syncFallbackVersion } from './lib/website.mjs';

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

/** Writes `version` into launcher and both in-game client version sources. */
function setVersion(version) {
  if (!/^\d+\.\d+\.\d+(?:\+\d+)?$/.test(version)) throw new Error(`Version must look like 1.2.3 or 1.2.3+N, got ${version}`);
  const publicVersion = version.split('+')[0];
  const conf = join(root, 'src-tauri', 'tauri.conf.json');
  const tauri = JSON.parse(readFileSync(conf, 'utf8'));
  tauri.version = version;
  writeFileSync(conf, JSON.stringify(tauri, null, 2) + '\n');
  const pkgFile = join(root, 'package.json');
  const pkg = JSON.parse(readFileSync(pkgFile, 'utf8'));
  pkg.version = version;
  writeFileSync(pkgFile, JSON.stringify(pkg, null, 2) + '\n');
  const lockFile = join(root, 'package-lock.json');
  const lock = JSON.parse(readFileSync(lockFile, 'utf8'));
  lock.version = version;
  if (lock.packages?.['']) lock.packages[''].version = version;
  writeFileSync(lockFile, JSON.stringify(lock, null, 2) + '\n');
  const cargoFile = join(root, 'src-tauri', 'Cargo.toml');
  writeFileSync(cargoFile, readFileSync(cargoFile, 'utf8').replace(/^version = ".*"$/m, `version = "${version}"`));
  const forgeProperties = join(root, 'client-mod', 'gradle.properties');
  writeFileSync(forgeProperties, readFileSync(forgeProperties, 'utf8').replace(/^version=.*$/m, `version=${publicVersion}`));
  const modernProperties = join(root, 'client-mod-modern', 'gradle.properties');
  writeFileSync(modernProperties, readFileSync(modernProperties, 'utf8').replace(/^version=.*$/m, `version=${publicVersion}`));
  const forgeEntry = join(root, 'client-mod', 'src', 'main', 'java', 'com', 'novomc', 'client', 'NovoMC.java');
  writeFileSync(forgeEntry, readFileSync(forgeEntry, 'utf8').replace(/(public static final String VERSION = ")[^"]*(";)/, `$1${publicVersion}$2`));
  console.log(`Version set to ${version}`);
}

function gh() {
  const installed = 'C:\\Program Files\\GitHub CLI\\gh.exe';
  return existsSync(installed) ? `"${installed}"` : 'gh';
}

/** Git in the site worktree, without a shell so paths need no quoting. */
function gitIn(dir, gitArgs) {
  execFileSync('git', ['-C', dir, ...gitArgs], { cwd: root, stdio: 'inherit' });
}

const confVersion = JSON.parse(readFileSync(join(root, 'src-tauri', 'tauri.conf.json'), 'utf8')).version;
if (flag('--bugfix')) {
  const base = option('--version') ?? confVersion.split('+')[0];
  if (!/^\d+\.\d+\.\d+$/.test(base)) throw new Error(`Version must look like 1.2.3, got ${base}`);
  const currentRevision = confVersion.startsWith(`${base}+`) ? Number(confVersion.slice(base.length + 1)) || 0 : 0;
  setVersion(`${base}+${currentRevision + 1}`);
} else if (option('--version')) {
  setVersion(option('--version'));
}
const version = JSON.parse(readFileSync(join(root, 'src-tauri', 'tauri.conf.json'), 'utf8')).version;
const publicVersion = version.split('+')[0];

const site = siteDir(root);
const siteUpdated = site ? syncFallbackVersion(site, publicVersion) : null;
if (siteUpdated !== null) {
  console.log(siteUpdated ? `Website fallback set to ${publicVersion}` : `Website fallback already on ${publicVersion}`);
}

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
const installerName = `NovoMC_${publicVersion}_x64-setup.exe`;
const installer = join(bundle, installerName);
const expectedBuild = `NovoMC_${version}_x64-setup.exe`;
const builtName = existsSync(join(bundle, expectedBuild))
  ? expectedBuild
  : readdirSync(bundle).find((name) => name.startsWith(`NovoMC_${publicVersion}+`) && name.endsWith('_x64-setup.exe'));
if (version !== publicVersion && !builtName) {
  throw new Error(`The signed NSIS artifact for build ${version} was not found in ${bundle}`);
}
if (builtName && builtName !== installerName) {
  copyFileSync(join(bundle, builtName), installer);
  copyFileSync(join(bundle, `${builtName}.sig`), `${installer}.sig`);
}
const sigFile = `${installer}.sig`;
if (!existsSync(installer) || !existsSync(sigFile)) throw new Error(`Missing ${installerName} or its .sig in ${bundle}`);

// GitHub turns spaces in asset names into dots; this name has none.
const notes = option('--notes') ?? `NovoMC ${publicVersion}`;
const feed = {
  version,
  notes,
  pub_date: new Date().toISOString(),
  platforms: {
    'windows-x86_64': {
      signature: readFileSync(sigFile, 'utf8').trim(),
      url: `https://github.com/${REPO}/releases/download/v${publicVersion}/${installerName}`,
    },
  },
};
const feedFile = join(bundle, 'latest.json');
writeFileSync(feedFile, JSON.stringify(feed, null, 2) + '\n');
console.log(`\nBuilt ${installerName} and latest.json in\n  ${bundle}`);

if (flag('--publish')) {
  const tag = `v${publicVersion}`;
  const releases = JSON.parse(execFileSync(gh(), ['release', 'list', '--repo', REPO, '--limit', '100', '--json', 'tagName'], {
    cwd: root,
    encoding: 'utf8',
    shell: true,
  }));
  if (releases.some((release) => release.tagName === tag)) {
    run(gh(), ['release', 'upload', tag, `"${installer}"`, `"${feedFile}"`, '--repo', REPO, '--clobber']);
    run(gh(), ['release', 'edit', tag, '--repo', REPO, '--title', `"NovoMC ${publicVersion}"`, '--notes', JSON.stringify(notes)]);
    console.log(`\nUpdated existing GitHub release ${tag}.`);
  } else {
    run(gh(), ['release', 'create', tag, `"${installer}"`, `"${feedFile}"`, '--repo', REPO, '--title', `"NovoMC ${publicVersion}"`, '--notes', JSON.stringify(notes)]);
    console.log(`\nPublished ${tag}.`);
  }
  console.log(`Published signed build ${version} on public release ${tag}; installed launchers will detect it automatically.`);
  if (siteUpdated) {
    gitIn(site, ['add', 'index.html']);
    gitIn(site, ['commit', '-m', `Point the site's fallback at v${publicVersion}`]);
    gitIn(site, ['push', 'origin', 'website']);
    console.log(`Pushed the site fallback for v${publicVersion}.`);
  }
} else {
  console.log('\nNot published. Re-run with --publish to create the GitHub release.');
}
