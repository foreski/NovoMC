// Checks the survival catalog against Modrinth: every entry must resolve to a
// real project of the declared type, with a downloadable version for each
// edition it claims. Run with `node scripts/check-survival-catalog.mjs`.
//
// This is the same resolution the launcher does at install time, so a green run
// means the catalogue ships nothing broken.
import { readFileSync } from 'node:fs';
import { inflateRawSync } from 'node:zlib';

const CATALOG = 'src-tauri/src/survival/catalog.json';
const API = 'https://api.modrinth.com/v2';
const UA = 'NovoMC-catalog-check/1.0';

const catalog = JSON.parse(readFileSync(CATALOG, 'utf8'));
const loaderFor = (kind) => (kind === 'mod' ? 'fabric' : null);

async function getJson(url) {
  const res = await fetch(url, { headers: { 'User-Agent': UA } });
  if (!res.ok) throw new Error(`${res.status} ${url}`);
  return res.json();
}

async function latest(slug, edition, loader) {
  const url = new URL(`${API}/project/${encodeURIComponent(slug)}/version`);
  url.searchParams.set('game_versions', JSON.stringify([edition]));
  if (loader) url.searchParams.set('loaders', JSON.stringify([loader]));
  const versions = await getJson(url);
  versions.sort((a, b) => b.date_published.localeCompare(a.date_published));
  return versions.find((v) => v.files?.length) ?? null;
}

const problems = [];
const rows = [];

/** Mod ids an instance always has. */
const BUILTIN = new Set(['minecraft', 'java', 'fabricloader', 'fabric', 'fabric-api', 'quilt_loader', 'quilted_fabric_api']);

/** Reads one file out of a ZIP/jar without a dependency. */
function unzipEntry(buf, wanted) {
  let eocd = -1;
  for (let i = buf.length - 22; i >= Math.max(0, buf.length - 66000); i--) {
    if (buf.readUInt32LE(i) === 0x06054b50) {
      eocd = i;
      break;
    }
  }
  if (eocd < 0) return null;
  const count = buf.readUInt16LE(eocd + 10);
  let offset = buf.readUInt32LE(eocd + 16);
  for (let n = 0; n < count; n++) {
    if (buf.readUInt32LE(offset) !== 0x02014b50) return null;
    const method = buf.readUInt16LE(offset + 10);
    const compressed = buf.readUInt32LE(offset + 20);
    const nameLength = buf.readUInt16LE(offset + 28);
    const extraLength = buf.readUInt16LE(offset + 30);
    const commentLength = buf.readUInt16LE(offset + 32);
    const localOffset = buf.readUInt32LE(offset + 42);
    const name = buf.toString('utf8', offset + 46, offset + 46 + nameLength);
    if (name === wanted) {
      const localName = buf.readUInt16LE(localOffset + 26);
      const localExtra = buf.readUInt16LE(localOffset + 28);
      const start = localOffset + 30 + localName + localExtra;
      const data = buf.subarray(start, start + compressed);
      return method === 0 ? data : inflateRawSync(data);
    }
    offset += 46 + nameLength + extraLength + commentLength;
  }
  return null;
}

/** Every jar a default install pulls in: the catalogue's mods plus Modrinth's declared dependencies. */
async function closure(edition) {
  const found = new Map();
  const seenProjects = new Set();
  const queue = catalog.items
    .filter((item) => item.kind === 'mod' && item.default && item.editions.includes(edition))
    .map((item) => item.slug);
  while (queue.length) {
    const slug = queue.shift();
    if (found.has(slug)) continue;
    const version = await latest(slug, edition, 'fabric');
    if (!version) {
      problems.push(`${slug}: no ${edition} build for the jar check`);
      continue;
    }
    const file = version.files.find((f) => f.primary) ?? version.files[0];
    const buf = Buffer.from(await (await fetch(file.url, { headers: { 'User-Agent': UA } })).arrayBuffer());
    const meta = unzipEntry(buf, 'fabric.mod.json');
    let id = slug;
    let depends = [];
    if (meta) {
      const json = JSON.parse(meta.toString('utf8'));
      id = json.id ?? slug;
      depends = Object.keys(json.depends ?? {});
    } else {
      problems.push(`${slug}: jar has no fabric.mod.json (${file.filename})`);
    }
    found.set(slug, { id, depends, file: file.filename });
    for (const dep of version.dependencies ?? []) {
      if (dep.dependency_type === 'required' && dep.project_id && !seenProjects.has(dep.project_id)) {
        seenProjects.add(dep.project_id);
        queue.push((await getJson(`${API}/project/${dep.project_id}`)).slug);
      }
    }
  }
  return found;
}

async function checkJars(edition) {
  const found = await closure(edition);
  const provided = new Set(BUILTIN);
  for (const info of found.values()) provided.add(info.id);
  // Fabric API is one jar that registers a whole family of `fabric-*` modules.
  // (Older Fabric API jars declare the id `fabric` rather than `fabric-api`.)
  const hasFabricApi = [...found.values()].some((info) => info.id === 'fabric-api' || info.id === 'fabric');
  let bad = 0;
  for (const [slug, info] of found) {
    for (const dep of info.depends) {
      if (provided.has(dep) || (hasFabricApi && dep.startsWith('fabric-'))) continue;
      problems.push(`${slug}: jar requires \`${dep}\`, which nothing in the ${edition} install provides`);
      bad++;
    }
  }
  console.log(`\n${edition}: ${found.size} jars, ${bad} missing declared dependencies`);
}

for (const item of catalog.items) {
  let project = null;
  try {
    project = await getJson(`${API}/project/${encodeURIComponent(item.slug)}`);
  } catch (e) {
    problems.push(`${item.slug}: not on Modrinth (${e.message})`);
    continue;
  }
  const want = item.kind === 'mod' ? 'mod' : item.kind === 'pack' ? 'resourcepack' : 'shader';
  if (project.project_type !== want) {
    problems.push(`${item.slug}: is a ${project.project_type}, catalogue says ${item.kind}`);
  }
  for (const edition of item.editions) {
    const version = await latest(item.slug, edition, loaderFor(item.kind));
    if (!version) {
      problems.push(`${item.slug}: no ${edition}${loaderFor(item.kind) ? ' fabric' : ''} version`);
      continue;
    }
    const file = version.files.find((f) => f.primary) ?? version.files[0];
    rows.push({
      slug: item.slug,
      edition,
      version: version.version_number,
      file: file.filename,
      sha512: !!file.hashes?.sha512,
    });
  }
  // Dependencies are installed by the launcher, so they must resolve too.
  if (item.kind === 'mod') {
    for (const edition of item.editions) {
      const version = await latest(item.slug, edition, 'fabric');
      for (const dep of (version?.dependencies ?? []).filter((d) => d.dependency_type === 'required' && d.project_id)) {
        try {
          const p = await getJson(`${API}/project/${dep.project_id}`);
          const depVersion = await latest(p.slug, edition, 'fabric');
          if (!depVersion) problems.push(`${item.slug} -> ${p.slug}: required, but no ${edition} build`);
        } catch (e) {
          problems.push(`${item.slug} -> ${dep.project_id}: dependency lookup failed (${e.message})`);
        }
      }
    }
  }
}

for (const row of rows) {
  console.log(`${row.slug.padEnd(32)} ${row.edition.padEnd(7)} ${row.version.padEnd(22)} ${row.sha512 ? 'sha512' : 'NO HASH'}  ${row.file}`);
}

console.log(`\n${catalog.items.length} entries, ${rows.length} published builds checked`);

// --jars downloads every default jar and checks the dependencies the jars
// themselves declare, which Modrinth's metadata sometimes omits.
const jarFlag = process.argv.indexOf('--jars');
if (jarFlag >= 0) {
  const wanted = process.argv[jarFlag + 1];
  for (const edition of wanted ? [wanted] : catalog.editions.map((e) => e.id)) {
    await checkJars(edition);
  }
}

if (problems.length) {
  console.error(`\n${problems.length} problem(s):`);
  for (const p of problems) console.error(`  - ${p}`);
  process.exitCode = 1;
} else {
  console.log('Every catalogue entry resolves for the editions it claims.');
}
