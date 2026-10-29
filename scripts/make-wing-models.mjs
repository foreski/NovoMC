// Builds the wing OBJ/MTL pairs the 1.8.9 client ships, into
// client-mod/src/main/resources/assets/novomc/models/wings/.
//
// The wings used to be one smooth fan sheet with ribs drawn on top of it,
// which reads as a paper cut-out however good the shading is: the silhouette
// had no finger bones, the trailing edge was a straight arc, and the membrane
// was flat enough that every normal pointed the same way. These are built like
// a bat wing instead, which is what every dragon in Minecraft actually has:
//
//   - an arm spar from the shoulder to the wrist, and five finger spars fanning
//     out from the wrist, each one a faceted tube that tapers and bulges at the
//     knuckles;
//   - a membrane strung between the finger tips whose trailing edge scallops
//     inward between them, so the outline is a series of concave arcs;
//   - a hip spar running back to the body, closing the wing root;
//   - a membrane that billows toward the viewer between the spars and sweeps
//     away at the tips, so it curves instead of being planar;
//   - claws at the wrist and on every finger tip.
//
// Geometry is authored for the right wing in OBJ space (x out, y up, z toward
// the viewer) and mirrored for the left, with the winding flipped so both sides
// lit the same way. The wrist sits at 48% of the span because that is where
// `WingModel` splits the pair into two hinged halves: the bend lands on the
// real joint instead of halfway along a finger.
//
// Run: node scripts/make-wing-models.mjs            (writes the models)
//      node scripts/make-wing-models.mjs --preview  (also renders a side view)
import { mkdirSync, writeFileSync } from 'node:fs';
import { encodePng } from './lib/png.mjs';

const OUT = new URL('../client-mod/src/main/resources/assets/novomc/models/wings/', import.meta.url);
const PREVIEW = process.argv.includes('--preview');
const DEG = Math.PI / 180;

/** Where the wing meets the back, near the OBJ origin the renderer pivots on. */
const SHOULDER = { x: 0.035, y: 0.075, z: 0.01 };
/** The wrist: the renderer's elbow split lands here, so the wing bends at the joint. */
const WRIST = { x: 0.706, y: 0.05, z: 0.03 };

/**
 * The finger spars, as an angle below the +X axis and a reach from the wrist.
 * The first is the leading finger, the last the short trailing one.
 */
const FINGERS = [
  { angle: 10, length: 0.84, radius: 0.062 },
  { angle: -14, length: 0.87, radius: 0.057 },
  { angle: -40, length: 0.8, radius: 0.052 },
  { angle: -66, length: 0.66, radius: 0.046 },
  { angle: -92, length: 0.5, radius: 0.041 },
];
/** The trailing spar, reaching down and back to the wing's attachment on the body. */
const HIP = { angle: -118, length: 0.44, radius: 0.042 };
/** The arm, from the wrist back to the shoulder. Its angle continues past -180. */
const ARM = (() => {
  const dx = SHOULDER.x - WRIST.x;
  const dy = SHOULDER.y - WRIST.y;
  return { angle: Math.atan2(dy, dx) / DEG - 360, length: Math.hypot(dx, dy) };
})();
/** How far the membrane's free edge sags inward between each pair of spars. */
const SCALLOPS = [0.13, 0.12, 0.11, 0.09, 0.06, 0.05];
/** Angular subdivisions per panel, and radial rings across the whole fan. */
const PANEL_STEPS = [7, 7, 6, 5, 4, 6];
const RINGS = 8;
/** The membrane starts just off the wrist, where a fan would otherwise collapse to a point. */
const T_MIN = 0.06;
/** Tips trail behind the shoulder, and panels billow toward the viewer. */
const SWEEP = 0.085;
const BILLOW = 0.072;
/**
 * Shallow corrugations running across each panel: skin stretched between two
 * spars is never flat, and the ridges are what catch the light between them.
 */
const WRINKLE = 0.006;
const WRINKLE_COUNT = 3;

/** A greyscale texture per pair, tinted by the material colour; see make-wing-textures.mjs. */
const VARIANTS = [
  {
    id: 'violet_wings',
    name: 'Violet Wings',
    palette: { membrane: [0.42, 0.12, 0.78], frame: [0.76, 0.47, 1.0], accent: [0.62, 0.29, 0.94] },
  },
  {
    id: 'verdant_wings',
    name: 'Verdant Wings',
    palette: { membrane: [0.12, 0.62, 0.28], frame: [0.42, 0.92, 0.48], accent: [0.2, 0.78, 0.34] },
  },
  {
    id: 'monochrome_wings',
    name: 'Monochrome Wings',
    palette: { membrane: [0.68, 0.7, 0.74], frame: [0.91, 0.92, 0.94], accent: [0.43, 0.46, 0.5] },
  },
  {
    id: 'azure_wings',
    name: 'Azure Wings',
    palette: { membrane: [0.1, 0.4, 0.88], frame: [0.42, 0.72, 1.0], accent: [0.2, 0.55, 0.98] },
  },
  {
    id: 'ender_dragon_wings',
    name: 'Ender Dragon Wings',
    palette: { membrane: [0.4, 0.17, 0.48], frame: [0.12, 0.1, 0.16], accent: [1.0, 0.36, 0.95] },
  },
  {
    id: 'phantom_wings',
    name: 'Phantom Wings',
    palette: { membrane: [0.28, 0.66, 0.68], frame: [0.82, 0.96, 0.94], accent: [0.55, 0.98, 0.95] },
  },
];

const RIBS = [FINGERS[0], FINGERS[1], FINGERS[2], FINGERS[3], FINGERS[4], HIP, ARM];

/** Distance from the wrist to the membrane's free edge, at one angle. */
function radiusAt(angle) {
  for (let i = 0; i + 1 < RIBS.length; i++) {
    const a = RIBS[i].angle;
    const b = RIBS[i + 1].angle;
    if (angle <= a && angle >= b) {
      const w = (a - angle) / (a - b);
      const base = RIBS[i].length + (RIBS[i + 1].length - RIBS[i].length) * w;
      return base - SCALLOPS[i] * Math.pow(Math.sin(Math.PI * w), 1.15);
    }
  }
  return RIBS[RIBS.length - 1].length;
}

/**
 * One point on the membrane: along `angle` from the wrist, out to `t` of the
 * way to the free edge, with `w` the position across its panel (0 and 1 sit on
 * the spars, where the skin is pinned flat).
 */
function surface(angle, t, w) {
  const r = radiusAt(angle) * t;
  const x = WRIST.x + Math.cos(angle * DEG) * r;
  const y = WRIST.y + Math.sin(angle * DEG) * r;
  const z = WRIST.z
    - SWEEP * t * t
    + BILLOW * Math.pow(Math.sin(Math.PI * t), 0.9) * Math.sin(Math.PI * w)
    + WRINKLE * Math.sin(Math.PI * w * WRINKLE_COUNT) * Math.sin(Math.PI * t);
  return [x, y, z];
}

/** Collects triangles for one side, keeping every face wound to the viewer. */
function mesh() {
  const verts = [];
  const tris = [];
  const index = (p) => {
    verts.push(p);
    return verts.length - 1;
  };
  const tri = (a, b, c, material) => {
    // OBJ is y-up, so the xy cross product's z is the facing: a negative one
    // would light the far side of a single-sided membrane.
    const ux = b[0] - a[0], uy = b[1] - a[1];
    const vx = c[0] - a[0], vy = c[1] - a[1];
    if (ux * vy - uy * vx < 0) tris.push({ v: [a, c, b], material });
    else tris.push({ v: [a, b, c], material });
  };
  return { verts, tris, index, tri };
}

/** The skin: a polar grid from the wrist out to the scalloped free edge. */
function membrane(m) {
  for (let p = 0; p + 1 < RIBS.length; p++) {
    const steps = PANEL_STEPS[p];
    for (let s = 0; s < steps; s++) {
      const w0 = s / steps;
      const w1 = (s + 1) / steps;
      const a0 = RIBS[p].angle + (RIBS[p + 1].angle - RIBS[p].angle) * w0;
      const a1 = RIBS[p].angle + (RIBS[p + 1].angle - RIBS[p].angle) * w1;
      for (let k = 0; k + 1 < RINGS; k++) {
        const t0 = T_MIN + (1 - T_MIN) * Math.pow(k / (RINGS - 1), 1.2);
        const t1 = T_MIN + (1 - T_MIN) * Math.pow((k + 1) / (RINGS - 1), 1.2);
        const a = surface(a0, t0, w0);
        const b = surface(a1, t0, w1);
        const c = surface(a1, t1, w1);
        const d = surface(a0, t1, w0);
        m.tri(m.index(a), m.index(b), m.index(c), 'membrane');
        m.tri(m.index(a), m.index(c), m.index(d), 'membrane');
      }
    }
  }
}

/** Radius of a spar at `t`, with a bulge at the knuckles and a taper to the tip. */
function sparRadius(base, t) {
  let r = base * (1 - 0.55 * t);
  for (const joint of [0.34, 0.67]) {
    r += base * 0.4 * Math.exp(-Math.pow((t - joint) / 0.07, 2));
  }
  return r;
}

/**
 * A faceted spar: four-sided rings along a line, each ring a diamond so the
 * bone reads as square-cut instead of round, and the whole thing lifted to sit
 * proud of the membrane it holds.
 */
function spar(m, from, direction, length, base, material, claw) {
  const SEGMENTS = 9;
  const tan = [-direction[1], direction[0]];
  let previous = null;
  for (let i = 0; i <= SEGMENTS; i++) {
    const t = i / SEGMENTS;
    const r = sparRadius(base, t);
    const cx = from[0] + direction[0] * length * t;
    const cy = from[1] + direction[1] * length * t;
    const cz = WRIST.z - SWEEP * t * t + r * 0.9;
    const ring = [
      m.index([cx + tan[0] * r, cy + tan[1] * r, cz]),
      m.index([cx, cy, cz + r * 0.85]),
      m.index([cx - tan[0] * r, cy - tan[1] * r, cz]),
      m.index([cx, cy, cz - r * 0.85]),
    ];
    if (previous) {
      for (let k = 0; k < 4; k++) {
        const n = (k + 1) % 4;
        m.tri(previous[k], previous[n], ring[n], material);
        m.tri(previous[k], ring[n], ring[k], material);
      }
    }
    previous = ring;
  }
  if (claw) {
    const tipX = from[0] + direction[0] * (length + 0.06);
    const tipY = from[1] + direction[1] * (length + 0.06);
    const tip = m.index([tipX, tipY, WRIST.z - SWEEP + 0.02]);
    for (let k = 0; k < 4; k++) {
      m.tri(previous[k], previous[(k + 1) % 4], tip, 'accent');
    }
  }
}

/** A spike: a square base and a point, used for the wrist claw and the shoulder cap. */
function spike(m, base, halfWidth, apex, material) {
  const ring = [
    m.index([base[0] + halfWidth, base[1], base[2]]),
    m.index([base[0], base[1], base[2] + halfWidth]),
    m.index([base[0] - halfWidth, base[1], base[2]]),
    m.index([base[0], base[1], base[2] - halfWidth]),
  ];
  const tip = m.index(apex);
  for (let k = 0; k < 4; k++) {
    m.tri(ring[k], ring[(k + 1) % 4], tip, material);
  }
}

function buildSide() {
  const m = mesh();
  membrane(m);
  for (const finger of FINGERS) {
    const phi = finger.angle * DEG;
    spar(m, [WRIST.x, WRIST.y], [Math.cos(phi), Math.sin(phi)], finger.length, finger.radius, 'frame', true);
  }
  const hip = HIP.angle * DEG;
  spar(m, [WRIST.x, WRIST.y], [Math.cos(hip), Math.sin(hip)], HIP.length, HIP.radius, 'frame', false);
  const arm = ARM.angle * DEG;
  spar(m, [WRIST.x, WRIST.y], [Math.cos(arm), Math.sin(arm)], ARM.length * 0.98, 0.082, 'frame', false);
  // The thumb claw: the one spike that points away from the membrane.
  spike(m, [WRIST.x, WRIST.y, WRIST.z + 0.01], 0.05, [WRIST.x + 0.03, WRIST.y + 0.16, WRIST.z + 0.1], 'accent');
  // A knuckle at the shoulder, where the arm meets the body.
  spike(m, [SHOULDER.x, SHOULDER.y, SHOULDER.z], 0.075, [SHOULDER.x, SHOULDER.y + 0.03, SHOULDER.z + 0.06], 'frame');
  return m;
}

function obj(variant) {
  const side = buildSide();
  const lines = [
    `# ${variant.name} - bat/dragon wing built by scripts/make-wing-models.mjs`,
    '# Units: model-space blocks; origin between the shoulder attachments.',
    `mtllib ${variant.id}.mtl`,
  ];
  let offset = 1;
  for (const [name, sign] of [['LeftWing', -1], ['RightWing', 1]]) {
    lines.push(`o ${name}`);
    for (const v of side.verts) {
      lines.push(`v ${(v[0] * sign).toFixed(6)} ${v[1].toFixed(6)} ${v[2].toFixed(6)}`);
    }
    let material = null;
    for (const t of side.tris) {
      if (t.material !== material) {
        material = t.material;
        lines.push(`usemtl ${material}`);
      }
      const [a, b, c] = t.v;
      // Mirroring reverses the winding; swapping two corners puts it back.
      const faces = sign < 0 ? [c, b, a] : [a, b, c];
      lines.push(`f ${faces[0] + offset} ${faces[1] + offset} ${faces[2] + offset}`);
    }
    offset += side.verts.length;
  }
  return lines.join('\n') + '\n';
}

function mtl(variant) {
  const block = (name, kd, gloss, ns) => [
    `newmtl ${name}`,
    `Ka ${(kd[0] * 0.22).toFixed(4)} ${(kd[1] * 0.22).toFixed(4)} ${(kd[2] * 0.22).toFixed(4)}`,
    `Kd ${kd[0].toFixed(4)} ${kd[1].toFixed(4)} ${kd[2].toFixed(4)}`,
    `Ks ${gloss} ${gloss} ${gloss}`,
    `Ns ${ns.toFixed(1)}`,
    'd 1.0',
    'illum 2',
    '',
  ];
  return [
    '# Fixed-color materials for Novo Client wing cosmetics',
    ...block('membrane', variant.palette.membrane, 0.12, 18),
    ...block('frame', variant.palette.frame, 0.2, 30),
    ...block('accent', variant.palette.accent, 0.26, 48),
  ].join('\n');
}

/** Side-on render of one pair, for eyeballing a change without launching the game. */
function preview(variant, side) {
  const W = 900;
  const H = 520;
  const px = new Uint8Array(W * H * 4);
  for (let i = 0; i < px.length; i += 4) {
    px[i] = 18;
    px[i + 1] = 20;
    px[i + 2] = 28;
    px[i + 3] = 255;
  }
  const span = Math.max(...side.verts.map((v) => v[0])) * 2.1;
  const scale = W / span;
  const project = (v, mirror) => [W / 2 + v[0] * mirror * scale, H / 2 - v[1] * scale + 60];
  const light = [-0.35, 0.55, 0.76];
  const length = Math.hypot(...light);
  const [lx, ly, lz] = light.map((c) => c / length);
  const faces = [];
  for (const mirror of [-1, 1]) {
    side.tris.forEach((t) => {
      const p = t.v.map((i) => side.verts[i]);
      const ux = p[1][0] - p[0][0], uy = p[1][1] - p[0][1], uz = p[1][2] - p[0][2];
      const vx = p[2][0] - p[0][0], vy = p[2][1] - p[0][1], vz = p[2][2] - p[0][2];
      const nx = uy * vz - uz * vy;
      const ny = uz * vx - ux * vz;
      const nz = ux * vy - uy * vx;
      const nl = Math.hypot(nx, ny, nz) || 1;
      const lambert = Math.max(0, (nx * lx + ny * ly + nz * lz) / nl);
      const kd = variant.palette[t.material];
      const shade = 0.34 + 0.66 * lambert;
      faces.push({
        z: (p[0][2] + p[1][2] + p[2][2]) / 3,
        p: p.map((v) => project(v, mirror)),
        colour: kd.map((c) => Math.round(255 * Math.min(1, c * shade))),
      });
    });
  }
  faces.sort((a, b) => a.z - b.z);
  for (const face of faces) {
    const [a, b, c] = face.p;
    const minY = Math.max(0, Math.floor(Math.min(a[1], b[1], c[1])));
    const maxY = Math.min(H - 1, Math.ceil(Math.max(a[1], b[1], c[1])));
    const minX = Math.max(0, Math.floor(Math.min(a[0], b[0], c[0])));
    const maxX = Math.min(W - 1, Math.ceil(Math.max(a[0], b[0], c[0])));
    const area = (b[0] - a[0]) * (c[1] - a[1]) - (c[0] - a[0]) * (b[1] - a[1]);
    if (Math.abs(area) < 1e-6) continue;
    for (let y = minY; y <= maxY; y++) {
      for (let x = minX; x <= maxX; x++) {
        const w0 = ((b[0] - a[0]) * (y - a[1]) - (b[1] - a[1]) * (x - a[0])) / area;
        const w1 = ((x - a[0]) * (c[1] - a[1]) - (y - a[1]) * (c[0] - a[0])) / area;
        if (w0 < 0 || w1 < 0 || w0 + w1 > 1) continue;
        const i = (y * W + x) * 4;
        px[i] = face.colour[0];
        px[i + 1] = face.colour[1];
        px[i + 2] = face.colour[2];
      }
    }
  }
  const path = new URL(`../.preview/${variant.id}.png`, import.meta.url);
  writeFileSync(path, encodePng(W, H, px));
  console.log(`preview ${path.pathname}`);
}

mkdirSync(OUT, { recursive: true });
mkdirSync(new URL('../.preview/', import.meta.url), { recursive: true });
for (const variant of VARIANTS) {
  writeFileSync(new URL(`${variant.id}.obj`, OUT), obj(variant));
  writeFileSync(new URL(`${variant.id}.mtl`, OUT), mtl(variant));
  console.log(`wrote ${variant.id}.obj (${buildSide().tris.length} triangles per side)`);
  if (PREVIEW) preview(variant, buildSide());
}
