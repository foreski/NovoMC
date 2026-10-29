// Renders the in-game menu icons (Lucide, ISC license) and the NovoMC logo
// into the mod's resources as PNGs. Re-run after changing the icon list.
//
//   node scripts/mod-icons.mjs
import { execFileSync } from 'node:child_process';
import { copyFileSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const out = join(root, 'client-mod', 'src', 'main', 'resources', 'assets', 'novomc', 'textures', 'gui');
const icons = [
  'user', 'x', 'settings', 'globe', 'puzzle', 'layout-grid', 'swords',
  // Mods screen, matching the launcher's Mod Library
  'layout-dashboard', 'sparkles', 'gauge', 'search', 'chevron-down', 'move', 'rotate-ccw',
  // Resource Packs screen
  'layers', 'download', 'folder-open', 'check', 'arrow-up-down', 'image',
  // Trainers
  'target', 'timer', 'mouse-pointer-click', 'flask-conical', 'trophy',
  // The RightShift menu: cosmetics gets its own shirt icon, beside Mods
  'shirt', 'palette', 'panel-left',
];
const work = join(tmpdir(), `novomc-icons-${Date.now()}`);
mkdirSync(join(out, 'icons'), { recursive: true });
mkdirSync(work, { recursive: true });

/** Reads an icon's [tag, attrs] list out of lucide-react's module source. */
function iconNode(name) {
  const src = readFileSync(join(root, 'node_modules', 'lucide-react', 'dist', 'esm', 'icons', `${name}.js`), 'utf8');
  const start = src.indexOf('[', src.indexOf('createLucideIcon('));
  const end = src.lastIndexOf(']);');
  return Function(`return ${src.slice(start, end + 1)}`)();
}

function svg(name) {
  const body = iconNode(name)
    .map(([tag, attrs]) => {
      const a = Object.entries(attrs)
        .filter(([k]) => k !== 'key')
        .map(([k, v]) => `${k}="${v}"`)
        .join(' ');
      return `<${tag} ${a}/>`;
    })
    .join('');
  return `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#FFFFFF" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${body}</svg>`;
}

const tauri = join(root, 'node_modules', '.bin', process.platform === 'win32' ? 'tauri.cmd' : 'tauri');
for (const name of icons) {
  const file = join(work, `${name}.svg`);
  writeFileSync(file, svg(name));
  const dir = join(work, name);
  execFileSync(`"${tauri}"`, ['icon', `"${file}"`, '-o', `"${dir}"`, '-p', '64'], { stdio: 'ignore', shell: true });
  copyFileSync(join(dir, '64x64.png'), join(out, 'icons', `${name}.png`));
  console.log(`icons/${name}.png`);
}

const logoDir = join(work, 'logo');
execFileSync(`"${tauri}"`, ['icon', `"${join(root, 'assets', 'branding', 'novomc-logo.svg')}"`, '-o', `"${logoDir}"`, '-p', '256'], {
  stdio: 'ignore',
  shell: true,
});
copyFileSync(join(logoDir, '256x256.png'), join(out, 'logo.png'));
console.log('logo.png');
rmSync(work, { recursive: true, force: true });
