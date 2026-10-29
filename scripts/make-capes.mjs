// Draws the procedural Novo cape textures (64x32, the vanilla cape layout) into
// client-mod/src/main/resources/assets/novomc/textures/capes/. Both clients and
// the launcher read these files. Run: node scripts/make-capes.mjs
//
// The 15 shop capes (arctic_cipher ... solar_eclipse) are hand-drawn art and are
// not generated here; this covers the brand cape, the two grant-only capes and
// the two animated capes.
//
// Static capes are `<id>.png`. Animated capes are one PNG per frame,
// `<id>_<frame>.png` from 0, plus `frames`/`fps` in relay/src/capes.js; the
// clients swap frames by wall clock only while the cape is being drawn.
//
// Ids must match relay/src/capes.js.
import { mkdirSync, writeFileSync } from 'node:fs';
import { deflateSync } from 'node:zlib';

const OUT = new URL('../client-mod/src/main/resources/assets/novomc/textures/capes/', import.meta.url);
const W = 64;
const H = 32;

// Faces of the 10x16x1 cape box: the outer side (seen from behind the
// player), the inner side, and the 1-pixel edges. Modern versions draw a
// cape wearer's elytra from (22, 0)-(46, 22) of the same texture.
const OUTER = { x: 1, y: 1, w: 10, h: 16 };
const INNER = { x: 12, y: 1, w: 10, h: 16 };

const hex = (h) => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
const mix = (a, b, t) => a.map((v, i) => Math.round(v + (b[i] - v) * t));
const shade = (c, f) => c.map((v) => Math.max(0, Math.min(255, Math.round(v * f))));
/** Color at t (0..1) along a list of stops. */
const ramp = (stops, t) => {
  const cs = stops.map(hex);
  const p = Math.max(0, Math.min(1, t)) * (cs.length - 1);
  const i = Math.min(cs.length - 2, Math.floor(p));
  return mix(cs[i], cs[i + 1], p - i);
};

// 5x5 pixel glyphs drawn onto the outer side.
const GLYPHS = {
  N: ['X...X', 'XX..X', 'X.X.X', 'X..XX', 'X...X'],
  star: ['..X..', '.XXX.', 'XXXXX', '.XXX.', '.X.X.'],
  prompt: ['X....', '.X...', '..X...', '.X...', 'X.XXX'],
};

/** Each design gives the outer side's color at (x, y) in 0..9, 0..15. */
const CAPES = {
  novo: { paint: (x, y) => ramp(['#5b8cff', '#7a6cff', '#b45bff'], y / 15), glyph: 'N', ink: '#ffffff' },
  early_supporter: {
    paint: (x, y) => (x === 0 || x === 9 || y === 0 || y === 15 ? hex('#fbbf24') : ramp(['#3b0764', '#581c87'], y / 15)),
    glyph: 'star',
    ink: '#fbbf24',
  },
  developer: {
    paint: (x, y) => (x === 0 || x === 9 || y === 0 || y === 15 ? hex('#22c55e') : y % 3 === 0 && x % 4 === y % 4 ? hex('#166534') : hex('#111827')),
    glyph: 'prompt',
    ink: '#4ade80',
  },
  // Animated: paint gets t (0..1) through the loop and the cape ships `frames` PNGs.
  // Smooth 24-frame loops: a cyan ribbon climbing the cape, and a living flame.
  aurora_flow: {
    frames: 24,
    fps: 12,
    paint: (x, y, t) => {
      const phase = t * Math.PI * 2;
      const centre = 4.5 + Math.sin(phase + y / 3.2) * 2.6;
      const width = 2.4 + Math.sin(phase * 2 + y / 2) * 0.7;
      const glow = Math.pow(Math.max(0, 1 - Math.abs(x - centre) / width), 1.4);
      const base = ramp(['#050b1c', '#0b1733', '#14264d'], y / 15);
      return mix(mix(base, hex('#22d3ee'), glow), hex('#e0fbff'), Math.pow(glow, 5) * 0.9);
    },
    glyph: null,
  },
  ember_flow: {
    frames: 24,
    fps: 12,
    paint: (x, y, t) => {
      const phase = t * Math.PI * 2;
      const flicker = 0.5 + 0.5 * Math.sin(phase + x * 1.7 + y * 0.35);
      const rise = 1 - y / 15;
      const heat = Math.max(0, rise * 1.15 - Math.abs(x - 4.5) / 12 + flicker * 0.25 - 0.15);
      const base = ramp(['#0a0503', '#1c0a06', '#33120a'], y / 15);
      const cool = mix(base, hex('#7f1d1d'), Math.min(1, heat * 1.6));
      const hot = mix(cool, hex('#f97316'), Math.max(0, Math.min(1, heat - 0.25) * 2));
      return mix(hot, hex('#fde68a'), Math.max(0, Math.min(1, heat - 0.7)) * 2.4);
    },
    glyph: null,
  },
};

function draw({ paint, glyph, ink }, t = 0) {
  const px = new Uint8Array(W * H * 4);
  const set = (x, y, [r, g, b], a = 255) => px.set([r, g, b, a], (y * W + x) * 4);
  const outer = (x, y) => {
    let c = paint(x, y, t);
    const g = glyph && GLYPHS[glyph];
    if (g && x >= 2 && x < 7 && y >= 3 && y < 8) {
      const row = g[y - 3];
      if (row && row[x - 2] === 'X') c = hex(ink);
    }
    return c;
  };
  for (let y = 0; y < OUTER.h; y++) {
    for (let x = 0; x < OUTER.w; x++) {
      const c = outer(x, y);
      set(OUTER.x + x, OUTER.y + y, c);
      // The inner side is a darker mirror; the edges take the nearest color.
      set(INNER.x + (OUTER.w - 1 - x), INNER.y + y, shade(paint(x, y, t), 0.6));
    }
    set(0, OUTER.y + y, shade(paint(0, y, t), 0.8)); // left edge
    set(11, OUTER.y + y, shade(paint(9, y, t), 0.8)); // right edge
  }
  for (let x = 0; x < OUTER.w; x++) {
    set(1 + x, 0, shade(paint(x, 0, t), 0.9)); // top
    set(11 + x, 0, shade(paint(x, 15, t), 0.7)); // bottom
  }
  for (let y = 0; y < 22; y++) {
    for (let x = 22; x < 46; x++) set(x, y, shade(paint((x - 22) % 10, Math.min(15, Math.floor(y * 0.75)), t), y < 2 || x < 24 ? 0.8 : 1));
  }
  return png(px);
}

function png(rgba) {
  const raw = Buffer.alloc((W * 4 + 1) * H);
  for (let y = 0; y < H; y++) Buffer.from(rgba.buffer, y * W * 4, W * 4).copy(raw, y * (W * 4 + 1) + 1);
  const chunk = (type, data) => {
    const len = Buffer.alloc(4);
    len.writeUInt32BE(data.length);
    const body = Buffer.concat([Buffer.from(type), data]);
    const crc = Buffer.alloc(4);
    crc.writeUInt32BE(crc32(body));
    return Buffer.concat([len, body, crc]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(W, 0);
  ihdr.writeUInt32BE(H, 4);
  ihdr.set([8, 6, 0, 0, 0], 8);
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}

function crc32(buf) {
  let c = ~0;
  for (const b of buf) {
    c ^= b;
    for (let k = 0; k < 8; k++) c = c & 1 ? (c >>> 1) ^ 0xedb88320 : c >>> 1;
  }
  return ~c >>> 0;
}

mkdirSync(OUT, { recursive: true });
let files = 0;
for (const [id, design] of Object.entries(CAPES)) {
  const frames = design.frames || 1;
  for (let frame = 0; frame < frames; frame++) {
    const file = frames > 1 ? `${id}_${frame}` : id;
    writeFileSync(new URL(`${file}.png`, OUT), draw(design, frame / frames));
    files++;
  }
}
console.log(`Wrote ${Object.keys(CAPES).length} procedural capes (${files} files)`);
