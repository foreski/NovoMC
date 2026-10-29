// Draws the wing membrane textures into
// client-mod/src/main/resources/assets/novomc/textures/wings/<id>.png.
//
// The membrane is a nearly planar sheet, so lighting by normal gives it one
// flat colour no matter how good the shading is. What makes it read as skin
// stretched between bones is surface detail: overlapping scales, veins along
// the spars, wrinkles where the skin is pulled, and a dark free edge.
//
// Everything is painted greyscale on a chunky pixel grid and multiplied by the
// material colour at draw time, the way the capes are tinted, so one file per
// pair follows its palette in `models/wings/<id>.mtl`. The grid is deliberately
// coarse: these textures cover under a block of geometry each, and Minecraft's
// own art is about eight texels to the block.
//
// u runs from the shoulder (0) to the wing tip (1); v runs from the leading
// edge (0) down to the free trailing edge (1). The wrist, where the spars meet,
// lands at about (0.46, 0.24) — see models/wings/<id>.obj: it is authored at
// 48% of the span and 20% down.
//
// Run: node scripts/make-wing-textures.mjs [--preview]
import { mkdirSync, writeFileSync } from 'node:fs';
import { encodePng } from './lib/png.mjs';

const OUT = new URL('../client-mod/src/main/resources/assets/novomc/textures/wings/', import.meta.url);
const W = 128;
const H = 64;
const WRIST = { x: W * 0.46, y: H * 0.24 };

/** Deterministic value noise, so reruns produce the same file. */
const noise = (x, y) => {
  let h = Math.imul(x | 0, 374761393) + Math.imul(y | 0, 668265263);
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967295;
};

const clamp = (v) => Math.max(0, Math.min(255, Math.round(v)));

/**
 * The shared pass: base tone, the gradient that darkens the root and the free
 * edge, overlapping scales, and the veins that echo the spars. `motif` adds the
 * character of the individual pair on top.
 */
function membrane({ base, veins, veinDark, scalePitch, scaleBias, motif }) {
  const px = new Uint8Array(W * H * 4);
  const set = (x, y, value) => {
    x |= 0;
    y |= 0;
    if (x < 0 || y < 0 || x >= W || y >= H) return;
    const i = (y * W + x) * 4;
    px[i] = clamp(value);
    px[i + 1] = clamp(value);
    px[i + 2] = clamp(value);
    px[i + 3] = 255;
  };
  const at = (x, y) => {
    const i = ((y | 0) * W + (x | 0)) * 4;
    return px[i];
  };

  // Spars radiating from the wrist: the leading edge runs left (the arm) and
  // the fingers fan down and out. Painted broad and low-contrast, because the
  // geometry already carries the real bones.
  const angles = [];
  for (let k = 0; k < veins; k++) angles.push(-0.12 + (k / (veins - 1)) * 2.35);
  angles.push(Math.PI - 0.06, Math.PI + 0.1);

  for (let y = 0; y < H; y++) {
    for (let x = 0; x < W; x++) {
      const u = x / (W - 1);
      const v = y / (H - 1);
      let value = base;
      // The root is the darkest part of a membrane; the free edge falls into
      // shadow too, because it curls away from everything.
      value *= 0.80 + 0.20 * Math.min(1, u * 1.5);
      value *= 1 - 0.20 * Math.pow(v, 2.6);

      const dx = x - WRIST.x;
      const dy = y - WRIST.y;
      const spread = Math.atan2(dy, dx);
      const reach = Math.hypot(dx, dy) / W;
      if (reach > 0.02) {
        for (const angle of angles) {
          let d = Math.abs(spread - angle);
          if (d > Math.PI) d = Math.PI * 2 - d;
          if (d < 0.16) {
            value -= veinDark * Math.exp(-((d / 0.075) ** 2)) * Math.min(1, reach * 4);
          }
        }
        // The membrane between the spars lightens slightly as it is stretched,
        // which is what separates one panel from the next.
        value *= 0.98 + 0.04 * Math.min(1, reach * 2.5);
      }
      // Wrinkles running across the span, soft enough to read as folds.
      const crease = Math.abs(((v * 6 + Math.sin(u * 4) * 0.16) % 1) - 0.5);
      value -= 8 * Math.exp(-((crease / 0.10) ** 2)) * (0.3 + 0.7 * u);
      // Grain, so no large area is perfectly even.
      value *= 0.985 + 0.03 * noise(x, y);
      set(x, y, value);
    }
  }

  // Overlapping scales, in rows that step half a scale each time like brickwork.
  const pitch = scalePitch;
  for (let row = 0; row * (pitch * 0.55) < H + pitch; row++) {
    const offset = (row % 2) * (pitch / 2);
    for (let col = -1; col * pitch < W + pitch; col++) {
      const cx = col * pitch + offset;
      const cy = row * pitch * 0.55;
      // Scales are smaller and tighter near the shoulder, where the skin is.
      const grow = 0.55 + 0.75 * (cx / W);
      const rx = (pitch / 2) * grow;
      const ry = (pitch / 3.2) * grow;
      const tint = (noise(col + 11, row + 3) - 0.5) * 22 * scaleBias;
      for (let y = Math.floor(cy - ry); y <= cy + ry; y++) {
        for (let x = Math.floor(cx - rx); x <= cx + rx; x++) {
          if (x < 0 || y < 0 || x >= W || y >= H) continue;
          // A diamond, lit from the top-left like every Minecraft texture.
          const dx = Math.abs(x - cx) / Math.max(1, rx);
          const dy = Math.abs(y - cy) / Math.max(1, ry);
          if (dx + dy > 1) continue;
          const lip = dy < 0.3 && dx < 0.75 ? 8 : 0;
          const seam = dx + dy > 0.80 ? -22 : 0;
          set(x, y, at(x, y) + tint + lip + seam);
        }
      }
    }
  }

  if (motif) motif({ px, set, at });
  return px;
}

/** A jagged line from the wrist outward: a lightning crack, one pixel core plus a glow. */
function crack({ set, at }, angle, length, seed, core, glow) {
  let x = WRIST.x;
  let y = WRIST.y;
  const stepX = Math.cos(angle);
  const stepY = Math.sin(angle);
  for (let i = 0; i < length; i++) {
    // A random walk perpendicular to the crack, so no two are parallel.
    const wobble = (noise(seed, i) - 0.5) * 1.6;
    x += stepX + wobble * -stepY;
    y += stepY + wobble * stepX;
    set(x, y, core);
    if (glow) {
      set(x + 1, y, at(x + 1, y) * 0.6 + glow);
      set(x, y + 1, at(x, y + 1) * 0.6 + glow);
      set(x - 1, y, at(x - 1, y) * 0.75 + glow * 0.5);
      set(x, y - 1, at(x, y - 1) * 0.75 + glow * 0.5);
    }
  }
}

/** Big plates with glowing cracks between them: the ender dragon's hide. */
const dragonScales = ({ set, at }) => {
  for (let k = 0; k < 5; k++) {
    crack({ set, at }, 0.15 + k * 0.5, 90, k * 17 + 3, 246, 60);
  }
  for (let x = 0; x < W; x++) {
    // The free edge is ragged where it has dried out. The teeth follow the
    // column, with a little jitter, instead of flickering pixel by pixel.
    const teeth = 0.84 + 0.09 * noise(x >> 2, 7) + 0.03 * noise(x, 21);
    for (let y = Math.floor(H * teeth); y < H; y++) {
      set(x, y, at(x, y) * 0.5);
    }
    // A row of bone-white nodules along the leading edge.
    if (x % 7 === 3) {
      for (let y = 2; y < 6; y++) set(x, y, 214 + 41 * noise(x, y));
    }
  }
};

/** Torn, wispy skin that fades out rather than ending in a clean line. */
const phantomTears = ({ set, at }) => {
  // Long tears that follow the span, deepening toward the free edge.
  for (let band = 0; band < 6; band++) {
    const start = H * (0.22 + band * 0.12);
    for (let x = 0; x < W; x++) {
      const y = start + Math.sin(x / 11 + band * 2) * 3;
      const weight = 0.25 + 0.6 * (x / W);
      if (noise(x >> 2, band) < 0.25) continue;
      set(x, y, at(x, y) * (1 - weight));
      set(x, y + 1, at(x, y + 1) * (1 - weight * 0.5));
    }
  }
  // The last fifth is shredded into strands of uneven length.
  for (let x = 0; x < W; x++) {
    const strand = 0.78 + 0.16 * noise(x >> 2, 3);
    for (let y = Math.floor(H * strand); y < H; y++) {
      set(x, y, at(x, y) * 0.42);
    }
    if (noise(x >> 1, 9) < 0.2) {
      for (let y = Math.floor(H * 0.62); y < H * strand; y++) set(x, y, at(x, y) * 0.72);
    }
  }
};

/** Long bone striations: the monochrome pair is meant to read as skeletal. */
const boneStriations = ({ set, at }) => {
  for (let y = 0; y < H; y++) {
    for (let x = 0; x < W; x++) {
      const u = x / (W - 1);
      const streak = Math.abs(((y * 13 + Math.sin(u * 9) * 3) % 17) / 17 - 0.5);
      if (streak < 0.05) set(x, y, at(x, y) * 0.82);
      if (streak > 0.42) set(x, y, at(x, y) * 1.05);
    }
  }
};

/** Ripples across the span, like water: the azure pair's motif. */
const ripples = ({ set, at }) => {
  for (let x = 0; x < W; x++) {
    for (let band = 0; band < 6; band++) {
      const y = H * (0.1 + band * 0.16) + Math.sin(x / 13 + band) * 4;
      set(x, y, at(x, y) * 0.8);
      set(x, y + 1, at(x, y + 1) * 0.93);
    }
  }
};

/** Leaf vane: a midrib down the span with lateral veins off it. */
const leafVane = ({ set, at }) => {
  for (let x = 4; x < W; x++) {
    const y = H * 0.44 + Math.sin(x / 26) * 5;
    set(x, y, at(x, y) * 0.7);
    for (let k = -4; k <= 4; k++) {
      set(x + 1, y + k * 3, at(x + 1, y + k * 3) * 0.86);
      set(x + 2, y + k * 3, at(x + 2, y + k * 3) * 0.93);
    }
  }
};

const VARIANTS = {
  violet_wings: membrane({ base: 236, veins: 6, veinDark: 36, scalePitch: 8, scaleBias: 1 }),
  verdant_wings: membrane({
    base: 232,
    veins: 7,
    veinDark: 42,
    scalePitch: 7,
    scaleBias: 1.4,
    motif: leafVane,
  }),
  monochrome_wings: membrane({
    base: 228,
    veins: 5,
    veinDark: 24,
    scalePitch: 10,
    scaleBias: 0.45,
    motif: boneStriations,
  }),
  azure_wings: membrane({
    base: 230,
    veins: 6,
    veinDark: 32,
    scalePitch: 8,
    scaleBias: 0.85,
    motif: ripples,
  }),
  ender_dragon_wings: membrane({
    base: 172,
    veins: 5,
    veinDark: 58,
    scalePitch: 11,
    scaleBias: 1.5,
    motif: dragonScales,
  }),
  phantom_wings: membrane({
    base: 224,
    veins: 6,
    veinDark: 44,
    scalePitch: 9,
    scaleBias: 0.7,
    motif: phantomTears,
  }),
};

mkdirSync(OUT, { recursive: true });
const PREVIEW = process.argv.includes('--preview');
for (const [id, px] of Object.entries(VARIANTS)) {
  writeFileSync(new URL(`${id}.png`, OUT), encodePng(W, H, px));
  console.log(`wrote ${id}.png`);
  if (PREVIEW) {
    // Nearest-neighbour blow-up, for looking at the pixels without the game.
    const ZOOM = 4;
    const big = new Uint8Array(W * ZOOM * H * ZOOM * 4);
    for (let y = 0; y < H * ZOOM; y++) {
      for (let x = 0; x < W * ZOOM; x++) {
        const from = (((y / ZOOM) | 0) * W + ((x / ZOOM) | 0)) * 4;
        const to = (y * W * ZOOM + x) * 4;
        big[to] = px[from];
        big[to + 1] = px[from + 1];
        big[to + 2] = px[from + 2];
        big[to + 3] = 255;
      }
    }
    mkdirSync(new URL('../.preview/', import.meta.url), { recursive: true });
    writeFileSync(new URL(`../.preview/tex-${id}.png`, import.meta.url), encodePng(W * ZOOM, H * ZOOM, big));
  }
}
