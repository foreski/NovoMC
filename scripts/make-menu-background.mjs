// Generates the title screen backdrop the Novo client draws behind its menu:
// a night sky over a silhouetted mountain range, in the client's own palette
// instead of vanilla's blurred panorama.
//
//   node scripts/make-menu-background.mjs
//
// Writes client-mod/src/main/resources/assets/novomc/textures/gui/menu_background.png
// (1600x900, opaque). Everything is procedural and seeded, so re-running it
// gives byte-identical art and the file can stay in version control.
import { mkdirSync, writeFileSync } from 'node:fs';
import { encodePng } from './lib/png.mjs';

const W = 1600;
const H = 900;
const OUT = new URL('../client-mod/src/main/resources/assets/novomc/textures/gui/menu_background.png', import.meta.url);

/** Small deterministic PRNG, so the sky is the same every build. */
function rng(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const px = new Uint8Array(W * H * 3);
const put = (x, y, r, g, b) => {
  if (x < 0 || y < 0 || x >= W || y >= H) return;
  const i = (y * W + x) * 3;
  px[i] = Math.min(255, Math.round(r));
  px[i + 1] = Math.min(255, Math.round(g));
  px[i + 2] = Math.min(255, Math.round(b));
};
const add = (x, y, r, g, b) => {
  if (x < 0 || y < 0 || x >= W || y >= H) return;
  const i = (y * W + x) * 3;
  px[i] = Math.min(255, px[i] + r);
  px[i + 1] = Math.min(255, px[i + 1] + g);
  px[i + 2] = Math.min(255, px[i + 2] + b);
};
const mul = (x, y, k) => {
  if (x < 0 || y < 0 || x >= W || y >= H) return;
  const i = (y * W + x) * 3;
  px[i] *= k;
  px[i + 1] *= k;
  px[i + 2] *= k;
};
const mix = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);

// ---- Sky: a vertical gradient with a violet band near the horizon --------

const TOP = [6, 8, 18];
const MID = [14, 19, 42];
const HORIZON = [46, 38, 92];
const BELOW = [10, 11, 24];
for (let y = 0; y < H; y++) {
  const t = y / H;
  let colour;
  if (t < 0.52) colour = mix(TOP, MID, t / 0.52);
  else if (t < 0.72) colour = mix(MID, HORIZON, (t - 0.52) / 0.2);
  else colour = mix(HORIZON, BELOW, Math.min(1, (t - 0.72) / 0.28));
  for (let x = 0; x < W; x++) put(x, y, colour[0], colour[1], colour[2]);
}

// ---- Nebula: a few warped, very soft clouds of colour --------------------

const CLOUDS = [
  { x: 0.22, y: 0.30, rx: 0.30, ry: 0.16, colour: [58, 34, 104], strength: 0.55 },
  { x: 0.72, y: 0.22, rx: 0.26, ry: 0.13, colour: [34, 62, 110], strength: 0.42 },
  { x: 0.52, y: 0.46, rx: 0.42, ry: 0.12, colour: [76, 44, 118], strength: 0.32 },
  { x: 0.88, y: 0.44, rx: 0.22, ry: 0.10, colour: [30, 90, 110], strength: 0.26 },
];
for (let y = 0; y < H; y++) {
  for (let x = 0; x < W; x++) {
    const u = x / W;
    const v = y / H;
    // Warp the lookup so the clouds have soft, uneven edges.
    const wx = u + Math.sin(v * 9.1 + u * 5.3) * 0.012;
    const wy = v + Math.cos(u * 7.7 - v * 4.1) * 0.02;
    let r = 0;
    let g = 0;
    let b = 0;
    for (const c of CLOUDS) {
      const dx = (wx - c.x) / c.rx;
      const dy = (wy - c.y) / c.ry;
      const d = Math.sqrt(dx * dx + dy * dy);
      if (d > 1.6) continue;
      const fall = Math.max(0, 1 - d / 1.6) ** 2.2;
      // Break the falloff into wisps.
      const wisp = 0.55 + 0.45 * Math.sin(wx * 41 + c.x * 30) * Math.sin(wy * 37 + c.y * 20);
      const k = fall * c.strength * (0.6 + wisp * 0.6);
      r += c.colour[0] * k;
      g += c.colour[1] * k;
      b += c.colour[2] * k;
    }
    add(x, y, r * 0.55, g * 0.55, b * 0.55);
  }
}

// ---- Aurora: two soft ribbons of light above the ridge line -------------- 

/** Smooth 2D value noise in 0..1, from the seeded PRNG. */
function noise2(seed, cell) {
  const rand = rng(seed);
  const grid = [];
  const size = 64;
  for (let i = 0; i < size * size; i++) grid.push(rand());
  const at = (i, j) => grid[((j % size) + size) % size * size + (((i % size) + size) % size)];
  const smooth = (t) => t * t * (3 - 2 * t);
  return (x, y) => {
    const gx = x / cell;
    const gy = y / cell;
    const x0 = Math.floor(gx);
    const y0 = Math.floor(gy);
    const fx = smooth(gx - x0);
    const fy = smooth(gy - y0);
    const a = at(x0, y0) + (at(x0 + 1, y0) - at(x0, y0)) * fx;
    const b = at(x0, y0 + 1) + (at(x0 + 1, y0 + 1) - at(x0, y0 + 1)) * fx;
    return a + (b - a) * fy;
  };
}
const auroraNoise = noise2(0x51ed2701, 210);
const bandNoise = noise2(0x2f9e1a43, 90);
for (let band = 0; band < 2; band++) {
  const phase = band === 0 ? 0.4 : 2.1;
  const baseY = H * (0.52 + band * 0.045);
  const colour = band === 0 ? [44, 186, 168] : [116, 92, 226];
  for (let x = 0; x < W; x++) {
    const centre = baseY + Math.sin(x / 300 + phase) * 40 + Math.sin(x / 91 + phase * 1.7) * 7;
    const thickness = 84 + Math.sin(x / 170 + phase * 2) * 24;
    for (let y = Math.floor(centre - thickness * 1.8); y < centre + thickness * 1.8; y++) {
      const d = (y - centre) / thickness;
      // A smooth vertical falloff, broken up by low-frequency noise only: high
      // frequencies here read as hatching rather than as light in the sky.
      const veil = 0.45 + 0.55 * auroraNoise(x, y);
      const k = Math.exp(-d * d * 1.9) * (0.16 + 0.14 * veil) * (0.7 + 0.6 * bandNoise(x * 0.6, y * 0.3));
      if (k < 0.002) continue;
      add(x, y, colour[0] * k, colour[1] * k, colour[2] * k);
    }
  }
}

// ---- Stars ---------------------------------------------------------------

const random = rng(0x9e3779b9);
const star = (cx, cy, radius, brightness, tint) => {
  const r = Math.ceil(radius * 3);
  for (let y = Math.floor(cy - r); y <= cy + r; y++) {
    for (let x = Math.floor(cx - r); x <= cx + r; x++) {
      const d = Math.hypot(x - cx, y - cy);
      if (d > r) continue;
      const core = Math.exp(-(d * d) / (radius * radius * 0.55));
      const halo = Math.exp(-d / (radius * 1.9)) * 0.22;
      const k = (core + halo) * brightness;
      add(x, y, tint[0] * k, tint[1] * k, tint[2] * k);
    }
  }
};

const TINTS = [
  [235, 240, 255],
  [210, 225, 255],
  [255, 240, 224],
  [226, 214, 255],
];
for (let i = 0; i < 760; i++) {
  const x = random() * W;
  // Stars fade out toward the ridge line, where the haze is.
  const y = random() ** 1.15 * H * 0.82;
  const bright = 0.12 + random() ** 2.4 * 0.85;
  const tint = TINTS[Math.floor(random() * TINTS.length)];
  star(x, y, random() < 0.12 ? 1.35 : 0.75, bright, tint);
}
// A handful of hero stars with a cross flare, as on the reference art.
for (let i = 0; i < 12; i++) {
  const x = 40 + random() * (W - 80);
  const y = 30 + random() * H * 0.6;
  const tint = TINTS[Math.floor(random() * TINTS.length)];
  star(x, y, 1.9, 0.95, tint);
  const flare = 12 + random() * 26;
  for (let d = 1; d < flare; d++) {
    const k = (1 - d / flare) ** 2 * 0.5;
    add(x + d, y, tint[0] * k, tint[1] * k, tint[2] * k);
    add(x - d, y, tint[0] * k, tint[1] * k, tint[2] * k);
    add(x, y + d, tint[0] * k * 0.8, tint[1] * k * 0.8, tint[2] * k * 0.8);
    add(x, y - d, tint[0] * k * 0.8, tint[1] * k * 0.8, tint[2] * k * 0.8);
  }
}

// ---- Moon: a lit crescent with a wide halo, high on the right ------------

{
  const mx = W * 0.80;
  const my = H * 0.17;
  const radius = 74;
  // Sample coverage a few times per pixel, so the crescent edge is smooth
  // instead of a staircase.
  const coverage = (x, y) => {
    let hits = 0;
    for (let sy = 0; sy < 3; sy++) {
      for (let sx = 0; sx < 3; sx++) {
        const px0 = x + (sx + 0.5) / 3;
        const py0 = y + (sy + 0.5) / 3;
        const inMoon = Math.hypot(px0 - mx, py0 - my) <= radius;
        const inCut = Math.hypot(px0 - (mx + radius * 0.46), py0 - (my - radius * 0.18)) <= radius * 0.94;
        if (inMoon && !inCut) hits++;
      }
    }
    return hits / 9;
  };
  const reach = radius * 5.4;
  for (let y = Math.floor(my - reach); y <= my + reach; y++) {
    for (let x = Math.floor(mx - reach); x <= mx + reach; x++) {
      const d = Math.hypot(x - mx, y - my);
      // The far halo is windowed down to nothing well inside the loop bounds,
      // otherwise the corner of the box shows up as a seam in the sky.
      const window = Math.max(0, 1 - d / reach) ** 1.4;
      const halo = (Math.exp(-d / (radius * 1.6)) * 0.2 + Math.exp(-d / (radius * 3.4)) * 0.07) * window;
      if (halo > 0.002) add(x, y, 150 * halo, 165 * halo, 235 * halo);
      const cover = coverage(x, y);
      if (cover <= 0) continue;
      // Shade it across, so the crescent has some form.
      const t = Math.max(0, Math.min(1, (x - (mx - radius)) / (radius * 2)));
      const shade = 0.7 + 0.3 * t;
      const i = (y * W + x) * 3;
      px[i] += (244 * shade - px[i]) * cover;
      px[i + 1] += (240 * shade - px[i + 1]) * cover;
      px[i + 2] += (224 * shade - px[i + 2]) * cover;
    }
  }
}

// ---- Clay hills / mountains, three layers, with a rim light --------------

/**
 * Fills everything below a height function with a gradient colour and lights
 * the top two pixels of the ridge.
 */
function range(seed, baseFraction, amplitude, detail, colourTop, colourBottom, rim, rimStrength) {
  const rand = rng(seed);
  const waves = [];
  for (let i = 0; i < 5; i++) {
    waves.push({ freq: (0.4 + rand() * 2.4) / W, amp: amplitude * (0.6 - i * 0.1), phase: rand() * Math.PI * 2 });
  }
  const heightAt = (x) => {
    let h = baseFraction * H;
    for (const w of waves) h -= Math.sin(x * w.freq * Math.PI * 2 + w.phase) * w.amp;
    // Jagged peaks, like distant terrain rather than a smooth wave.
    h -= Math.abs(Math.sin(x * 0.011 + seed)) * detail;
    return h;
  };
  for (let x = 0; x < W; x++) {
    const top = Math.round(heightAt(x));
    for (let y = top; y < H; y++) {
      const t = Math.min(1, (y - top) / (H - top + 1));
      const c = mix(colourTop, colourBottom, t ** 0.8);
      put(x, y, c[0], c[1], c[2]);
    }
    // A soft, two-pixel rim light along the ridge, so the layers read as
    // separate shapes without a hard drawn outline.
    for (let d = 0; d < 4; d++) {
      const k = (1 - d / 4) ** 2 * rimStrength * 0.8;
      add(x, top + d, rim[0] * k, rim[1] * k, rim[2] * k);
    }
  }
}

range(11, 0.66, 46, 26, [26, 26, 58], [16, 16, 38], [120, 96, 200], 0.5);
range(29, 0.735, 58, 34, [17, 17, 40], [10, 10, 26], [96, 76, 176], 0.42);
range(47, 0.82, 44, 22, [9, 9, 22], [4, 4, 10], [70, 54, 132], 0.3);

// ---- A warm lift behind the wordmark, then a soft vignette ---------------

{
  // The logo and title sit just above centre; a dim halo keeps them from
  // sinking into the sky without washing it out.
  const gx = W * 0.5;
  const gy = H * 0.34;
  for (let y = Math.floor(gy - 400); y <= gy + 400; y++) {
    for (let x = Math.floor(gx - 600); x <= gx + 600; x++) {
      const d = Math.hypot((x - gx) / 600, (y - gy) / 400);
      if (d > 1) continue;
      const k = (1 - d) ** 2.2 * 0.07;
      add(x, y, 96 * k, 92 * k, 190 * k);
    }
  }
}

for (let y = 0; y < H; y++) {
  for (let x = 0; x < W; x++) {
    const dx = (x / W - 0.5) * 2;
    const dy = (y / H - 0.5) * 2;
    const vignette = 1 - Math.max(0, Math.hypot(dx * 0.92, dy * 0.78) - 0.55) ** 1.6 * 0.85;
    // The menu's buttons sit over the lower half, so it gets darker there.
    const floor = 1 - Math.max(0, (y / H - 0.42)) * 0.55;
    mul(x, y, Math.max(0.18, vignette * floor));
  }
}

// ---- Write ---------------------------------------------------------------

const rgba = new Uint8Array(W * H * 4);
for (let i = 0, j = 0; i < px.length; i += 3, j += 4) {
  rgba[j] = px[i];
  rgba[j + 1] = px[i + 1];
  rgba[j + 2] = px[i + 2];
  rgba[j + 3] = 255;
}
mkdirSync(new URL('.', OUT), { recursive: true });
writeFileSync(OUT, encodePng(W, H, rgba));
console.log(`Wrote menu_background.png (${W}x${H})`);
