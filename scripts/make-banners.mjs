// Generate launcher profile banner artwork (300x80 PNG, no dependencies).
import { mkdirSync, writeFileSync } from 'node:fs';
import { deflateSync } from 'node:zlib';

const W = 300, H = 80;
const OUT = new URL('../client-mod/src/main/resources/assets/novomc/textures/banners/', import.meta.url);
const designs = {
  aurora: { stops: [[7, 23, 48], [16, 185, 180], [99, 102, 241]], seed: 1 },
  nebula: { stops: [[25, 8, 54], [130, 54, 210], [35, 75, 170]], seed: 3 },
  sunset: { stops: [[75, 20, 50], [240, 106, 73], [255, 195, 98]], seed: 7 },
  forest: { stops: [[5, 42, 35], [22, 125, 82], [145, 191, 107]], seed: 11 },
  circuit: { stops: [[3, 18, 42], [8, 145, 178], [74, 222, 128]], seed: 23 },
  starfield: { stops: [[5, 8, 28], [48, 45, 112], [160, 104, 214]], seed: 31 },
  developer: { stops: [[10, 24, 22], [20, 100, 68], [30, 212, 130]], seed: 17 },
};
const crc32 = (buf) => { let c = ~0; for (const b of buf) { c ^= b; for (let k = 0; k < 8; k++) c = c & 1 ? (c >>> 1) ^ 0xedb88320 : c >>> 1; } return ~c >>> 0; };
function chunk(type, data) { const n = Buffer.alloc(4); n.writeUInt32BE(data.length); const body = Buffer.concat([Buffer.from(type), data]); const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(body)); return Buffer.concat([n, body, crc]); }
function png(data) {
  const raw = Buffer.alloc((W * 4 + 1) * H);
  for (let y = 0; y < H; y++) { raw[y * (W * 4 + 1)] = 0; Buffer.from(data.buffer, y * W * 4, W * 4).copy(raw, y * (W * 4 + 1) + 1); }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(W, 0); ihdr.writeUInt32BE(H, 4); ihdr.set([8, 6, 0, 0, 0], 8);
  return Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]), chunk('IHDR', ihdr), chunk('IDAT', deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}
function render({ stops, seed }) {
  const rgba = new Uint8Array(W * H * 4);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const t = Math.max(0, Math.min(1, x / W + Math.sin(y / 13 + x / 29 + seed) * 0.13));
    const p = t * 2, i = Math.min(1, Math.floor(p)), f = p - i;
    const wave = Math.max(0, Math.sin(x / 27 + y / 9 + seed) - 0.72) * 0.28;
    const idx = (y * W + x) * 4;
    for (let c = 0; c < 3; c++) rgba[idx + c] = Math.min(255, Math.round(stops[i][c] + (stops[i + 1][c] - stops[i][c]) * f + wave * 255));
    const edge = Math.min(1, x / 25, (W - x) / 18, y / 13, (H - y) / 13);
    rgba[idx + 3] = Math.round(220 * Math.max(0.52, edge));
  }
  return png(rgba);
}
mkdirSync(OUT, { recursive: true });
for (const [id, design] of Object.entries(designs)) writeFileSync(new URL(`${id}.png`, OUT), render(design));
console.log(`Wrote ${Object.keys(designs).length} profile banners`);
