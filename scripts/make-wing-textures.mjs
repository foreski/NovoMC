// Draws the wing membrane texture into
// client-mod/src/main/resources/assets/novomc/textures/wings/.
//
// The four wing meshes are the same geometry with different `.mtl` colours
// (`violet`, `verdant`, `monochrome`, `azure`), so one greyscale texture serves
// all of them: `WingModel` maps it across each wing and multiplies it by the
// material colour, the way the capes are tinted. That is why the art here is
// white with grey detail instead of being coloured.
//
// What it adds: the membrane is a nearly flat sheet, so lighting by normal
// gives it one flat colour no matter how good the shading is. Veins spreading
// from the shoulder, a darker root and a little grain are what make it read as
// skin stretched between bones rather than a cut-out.
//
// Run: node scripts/make-wing-textures.mjs
import { mkdirSync, writeFileSync } from 'node:fs';
import { deflateSync } from 'node:zlib';

const OUT = new URL('../client-mod/src/main/resources/assets/novomc/textures/wings/', import.meta.url);
const W = 256;
const H = 128;

/**
 * Where the veins converge: the shoulder. The wing's hinge sits about halfway
 * down its drop in the OBJ, and u 0 is the hinge end, so this lands on the
 * inner edge a little above the middle.
 */
const ORIGIN = { x: 2, y: 67 };
const VEIN_COUNT = 11;
// Kept gentle: the meshes have real rib geometry of their own, so the painted
// veins support those instead of competing with them.
const VEIN_STRENGTH = 0.30;
const VEIN_WIDTH = 0.026;
/** Faint creases running across the veins, to break the fan into panels. */
const CREASES = [0.34, 0.55, 0.76];

const smooth = (t) => {
  const c = Math.max(0, Math.min(1, t));
  return c * c * (3 - 2 * c);
};

/** Deterministic value noise, so reruns produce the same file. */
const noise = (x, y) => {
  let h = x * 374761393 + y * 668265263;
  h = (h ^ (h >> 13)) * 1274126177;
  return ((h ^ (h >> 16)) >>> 0) / 4294967295;
};

function draw() {
  const px = new Uint8Array(W * H * 4);
  // The fan's angles: the wing spans from the shoulder rightwards and down.
  // Each vein gets its own bend, weight and width, so the fan is not a
  // protractor: the values come from the index, so the file is reproducible.
  const veins = [];
  for (let i = 0; i < VEIN_COUNT; i++) {
    const t = i / (VEIN_COUNT - 1);
    const r = noise(i + 1, 7) * 2 - 1;
    veins.push({
      angle: -0.58 + t * 1.16 + r * 0.035,
      bend: r * 0.20,
      strength: VEIN_STRENGTH * (0.8 + 0.35 * noise(i + 3, 11)),
      width: VEIN_WIDTH * (0.8 + 0.5 * noise(i + 5, 13)),
    });
  }
  for (let y = 0; y < H; y++) {
    for (let x = 0; x < W; x++) {
      const dx = x - ORIGIN.x;
      const dy = y - ORIGIN.y;
      const dist = Math.hypot(dx, dy) / W;
      const angle = Math.atan2(dy, Math.abs(dx));

      let v = 1;
      // Veins: a dark core with a soft falloff either side.
      for (const vein of veins) {
        const d = Math.abs(angle - (vein.angle + vein.bend * dist));
        if (d < vein.width * 4) {
          v -= vein.strength * Math.exp(-((d / vein.width) ** 2)) * smooth(dist * 3);
        }
      }
      // Creases across the fan, very faint.
      for (const c of CREASES) {
        const d = Math.abs(dist - c);
        if (d < 0.02) {
          v -= 0.10 * Math.exp(-((d / 0.008) ** 2));
        }
      }
      // The root is the darkest part of a membrane, and the trailing edge
      // (bottom) picks up a little shade too.
      v *= 0.90 + 0.10 * smooth((x / W) * 1.4);
      v *= 0.94 + 0.06 * smooth(y / H);
      // Grain, so large flat areas are not perfectly even.
      v *= 0.985 + 0.03 * noise(x, y);

      const value = Math.max(0, Math.min(255, Math.round(252 * v)));
      const i = (y * W + x) * 4;
      px[i] = value;
      px[i + 1] = value;
      px[i + 2] = value;
      px[i + 3] = 255;
    }
  }
  return png(px);
}

const CRC = (() => {
  const table = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    table[n] = c;
  }
  return table;
})();

function crc32(buf) {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) c = CRC[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const out = Buffer.alloc(data.length + 12);
  out.writeUInt32BE(data.length, 0);
  out.write(type, 4, 'ascii');
  data.copy ? data.copy(out, 8) : out.set(data, 8);
  out.writeUInt32BE(crc32(out.subarray(4, 8 + data.length)), 8 + data.length);
  return out;
}

function png(rgba) {
  const header = Buffer.alloc(13);
  header.writeUInt32BE(W, 0);
  header.writeUInt32BE(H, 4);
  header[8] = 8; // bits per channel
  header[9] = 6; // RGBA
  const raw = Buffer.alloc(H * (W * 4 + 1));
  for (let y = 0; y < H; y++) {
    raw[y * (W * 4 + 1)] = 0; // no filter
    Buffer.from(rgba.buffer, y * W * 4, W * 4).copy(raw, y * (W * 4 + 1) + 1);
  }
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', header),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

mkdirSync(OUT, { recursive: true });
const file = new URL('membrane.png', OUT);
writeFileSync(file, draw());
console.log(`wrote ${file.pathname}`);
