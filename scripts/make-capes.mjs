// Draws the Novo cape textures (64x32, the vanilla cape layout) into
// client-mod/src/main/resources/assets/novomc/textures/capes/. Both clients
// and the launcher's cape picker use these files. Run: node scripts/make-capes.mjs
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
  flake: ['X.X.X', '.XXX.', 'XX.XX', '.XXX.', 'X.X.X'],
  leaf: ['...XX', '..XXX', '.XXX.', 'XXX..', 'X....'],
  flame: ['..X..', '.XX..', '.XXX.', 'XXXXX', '.XXX.'],
  prompt: ['X....', '.X...', '..X..', '.X...', 'X.XXX'],
};

/** Each design gives the outer side's color at (x, y) in 0..9, 0..15. */
const CAPES = {
  novo: { paint: (x, y) => ramp(['#5b8cff', '#7a6cff', '#b45bff'], y / 15), glyph: 'N', ink: '#ffffff' },
  aurora: {
    paint: (x, y) => ramp(['#0b1a2e', '#22d3ee', '#34d399', '#b45bff', '#0b1a2e'], (y + Math.sin(x / 1.6) * 1.6) / 16),
    glyph: null,
  },
  ember: {
    paint: (x, y) => {
      const c = ramp(['#1a0505', '#7f1d1d', '#ea580c', '#fbbf24'], y / 15);
      return (x * 7 + y * 13) % 11 === 0 ? shade(c, 1.35) : c;
    },
    glyph: 'flame',
    ink: '#fde68a',
  },
  frost: {
    paint: (x, y) => ((x * 3 + y * 7) % 13 === 0 ? hex('#ffffff') : ramp(['#e0f2fe', '#7dd3fc', '#0369a1'], y / 15)),
    glyph: 'flake',
    ink: '#ffffff',
  },
  verdant: { paint: (x, y) => shade(ramp(['#14532d', '#16a34a', '#4ade80'], y / 15), (x + Math.floor(y / 2)) % 3 === 0 ? 0.85 : 1), glyph: 'leaf', ink: '#dcfce7' },
  midnight: {
    paint: (x, y) => ((x * 5 + y * 3) % 17 === 0 ? hex('#e0e7ff') : ramp(['#020617', '#1e1b4b', '#312e81'], y / 15)),
    glyph: 'star',
    ink: '#fde68a',
  },
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
};

function draw({ paint, glyph, ink }) {
  const px = new Uint8Array(W * H * 4);
  const set = (x, y, [r, g, b], a = 255) => px.set([r, g, b, a], (y * W + x) * 4);
  const outer = (x, y) => {
    let c = paint(x, y);
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
      set(INNER.x + (OUTER.w - 1 - x), INNER.y + y, shade(paint(x, y), 0.6));
    }
    set(0, OUTER.y + y, shade(paint(0, y), 0.8)); // left edge
    set(11, OUTER.y + y, shade(paint(9, y), 0.8)); // right edge
  }
  for (let x = 0; x < OUTER.w; x++) {
    set(1 + x, 0, shade(paint(x, 0), 0.9)); // top
    set(11 + x, 0, shade(paint(x, 15), 0.7)); // bottom
  }
  for (let y = 0; y < 22; y++) {
    for (let x = 22; x < 46; x++) set(x, y, shade(paint((x - 22) % 10, Math.min(15, Math.floor(y * 0.75))), y < 2 || x < 24 ? 0.8 : 1));
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
for (const [id, design] of Object.entries(CAPES)) {
  writeFileSync(new URL(`${id}.png`, OUT), draw(design));
}
console.log(`Wrote ${Object.keys(CAPES).length} capes`);
