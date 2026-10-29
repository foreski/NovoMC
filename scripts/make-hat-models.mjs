// Builds the hat meshes the 1.8.9 client ships, into
// client-mod/src/main/resources/assets/novomc/models/hats/.
//
// The hats used to be one flat silhouette each — a cone, a ring, a pair of
// horns — which reads as a paper cut-out from any angle: no thickness, no
// band, nothing to catch the light. These are built like small props instead,
// out of the same primitives a modeller would block out with:
//
//   - lathed shells (brims, domes, crowns, hoods) revolved from a profile with
//     thickness, so the rim has an edge and the inside is closed;
//   - tapered tubes swept along a curved path for horns and antlers, with
//     knuckles that swell at every bend and a cut tip;
//   - faceted crystals and gems cut as bipyramids, so each facet takes its own
//     shade;
//   - trim: bands, buckles, straps and rivets sitting a hair proud of the shell.
//
// Every hat sits around the top of an 8-unit head: the base ring is a little
// wider than that (about 4.6 units radius) and the mesh rises in +Y. The
// renderer centres it on X/Z, scales it so the widest side is 10 units and flips
// Y, so the numbers here are only proportions.
//
// Run: node scripts/make-hat-models.mjs            (writes models + materials)
//      node scripts/make-hat-models.mjs --preview  (also renders front views)
import { mkdirSync, writeFileSync } from 'node:fs';
import { encodePng } from './lib/png.mjs';

const OUT = new URL('../client-mod/src/main/resources/assets/novomc/models/hats/', import.meta.url);
const PREVIEW_DIR = new URL('../.preview/', import.meta.url);
const PREVIEW = process.argv.includes('--preview');
const TAU = Math.PI * 2;

// ---- Materials -----------------------------------------------------------

/** name -> [r, g, b, specular, shininess]. Kept in step with the old palette. */
const MATERIALS = {
  midnight: [0.1098, 0.0706, 0.2588, 0.25, 48],
  violet: [0.4078, 0.2275, 0.8235, 0.25, 48],
  gold: [0.9608, 0.7451, 0.2157, 0.45, 72],
  ruby: [0.8235, 0.1176, 0.2157, 0.35, 64],
  obsidian: [0.1176, 0.1059, 0.1569, 0.3, 40],
  crimson: [0.7451, 0.0784, 0.1647, 0.28, 44],
  ivory: [1.0, 0.9569, 0.8039, 0.2, 30],
  ice: [0.3333, 0.8627, 1.0, 0.5, 90],
  ice_dark: [0.1176, 0.4706, 0.7451, 0.35, 60],
  silver: [0.6667, 0.8039, 0.902, 0.45, 70],
  // New with the rebuilt hats.
  leather: [0.3137, 0.2118, 0.1294, 0.08, 12],
  iron: [0.5412, 0.5608, 0.6, 0.4, 56],
  ember: [1.0, 0.702, 0.2353, 0.55, 96],
  emerald: [0.1176, 0.7529, 0.3843, 0.4, 72],
  ender: [0.2902, 0.0745, 0.3608, 0.3, 52],
  ender_glow: [0.8706, 0.302, 0.9176, 0.55, 96],
  phantom: [0.2196, 0.502, 0.5294, 0.14, 24],
  phantom_dark: [0.1059, 0.2784, 0.3059, 0.1, 20],
  phantom_hem: [0.1804, 0.4157, 0.4431, 0.16, 28],
  phantom_glow: [0.6392, 0.9451, 0.8824, 0.42, 84],
  bone: [0.8784, 0.8549, 0.7843, 0.1, 18],
};

// ---- Mesh building -------------------------------------------------------

/**
 * A mesh of triangles with a material name per face. Vertices are added
 * freely; duplicate positions are what give hard edges, so primitives that meet
 * at a crease do not share them.
 */
class Mesh {
  constructor() {
    this.verts = [];
    this.tris = [];
  }

  /** @returns the index of the new vertex */
  v(x, y, z) {
    this.verts.push([x, y, z]);
    return this.verts.length - 1;
  }

  tri(a, b, c, material) {
    this.tris.push({ v: [a, b, c], material });
  }

  quad(a, b, c, d, material) {
    this.tri(a, b, c, material);
    this.tri(a, c, d, material);
  }

  /** Adds four fresh vertices and a quad, so the face keeps a hard edge. */
  face(p0, p1, p2, p3, material) {
    const a = this.v(...p0);
    const b = this.v(...p1);
    const c = this.v(...p2);
    const d = this.v(...p3);
    this.quad(a, b, c, d, material);
  }

  /**
   * Revolves a profile of {r, y} points around the Y axis. `y` is height, `r`
   * the distance from the axis. With `thickness` the profile is treated as the
   * *outer* wall and mirrored inward, closing the rim and the inside, which is
   * what a brim or a hollow crown needs.
   */
  lathe(profile, { segments = 24, thickness = 0, material = 'midnight', inner = 'obsidian', capTop = false, capBottom = false, wobble = 0 } = {}) {
    const wall = (points, mat, flip) => {
      for (let i = 0; i < points.length - 1; i++) {
        const p = points[i];
        const q = points[i + 1];
        for (let s = 0; s < segments; s++) {
          const a0 = (s / segments) * TAU;
          const a1 = ((s + 1) / segments) * TAU;
          const wob = (angle) => (wobble ? Math.sin(angle * 5) * wobble : 0);
          const p0 = [Math.cos(a0) * p.r, p.y + wob(a0), Math.sin(a0) * p.r];
          const p1 = [Math.cos(a1) * p.r, p.y + wob(a1), Math.sin(a1) * p.r];
          const q0 = [Math.cos(a0) * q.r, q.y + wob(a0), Math.sin(a0) * q.r];
          const q1 = [Math.cos(a1) * q.r, q.y + wob(a1), Math.sin(a1) * q.r];
          if (flip) this.face(p0, q0, q1, p1, mat);
          else this.face(p0, p1, q1, q0, mat);
        }
      }
    };
    wall(profile, material, false);
    if (thickness > 0) {
      // The same profile pulled in and up, so the shell has a wall to see.
      const back = profile.map((p) => ({ r: Math.max(0, p.r - thickness), y: p.y - thickness * 0.6 })).reverse();
      wall(back, inner, true);
      // Close the rim at both ends of the profile.
      for (const [outer, innerP, flip] of [
        [profile[0], back[back.length - 1], true],
        [profile[profile.length - 1], back[0], false],
      ]) {
        for (let s = 0; s < segments; s++) {
          const a0 = (s / segments) * TAU;
          const a1 = ((s + 1) / segments) * TAU;
          const o0 = [Math.cos(a0) * outer.r, outer.y, Math.sin(a0) * outer.r];
          const o1 = [Math.cos(a1) * outer.r, outer.y, Math.sin(a1) * outer.r];
          const i0 = [Math.cos(a0) * innerP.r, innerP.y, Math.sin(a0) * innerP.r];
          const i1 = [Math.cos(a1) * innerP.r, innerP.y, Math.sin(a1) * innerP.r];
          if (flip) this.face(o0, i0, i1, o1, inner);
          else this.face(o0, o1, i1, i0, inner);
        }
      }
    } else {
      for (const [p, flip, mat] of [
        [profile[0], true, capBottom ? inner : null],
        [profile[profile.length - 1], false, capTop ? material : null],
      ]) {
        if (!mat) continue;
        const centre = this.v(0, p.y, 0);
        for (let s = 0; s < segments; s++) {
          const a0 = (s / segments) * TAU;
          const a1 = ((s + 1) / segments) * TAU;
          const v0 = this.v(Math.cos(a0) * p.r, p.y, Math.sin(a0) * p.r);
          const v1 = this.v(Math.cos(a1) * p.r, p.y, Math.sin(a1) * p.r);
          if (flip) this.tri(centre, v1, v0, mat);
          else this.tri(centre, v0, v1, mat);
        }
      }
    }
  }

  /**
   * Sweeps a tapered tube along a path. Each segment is a faceted ring, so a
   * bend reads as a knuckle; the ends are capped.
   */
  tube(path, radii, { sides = 8, material = 'obsidian', capStart = true, capEnd = true, ridges = 0 } = {}) {
    const rings = [];
    for (let i = 0; i < path.length; i++) {
      const here = path[i];
      const next = path[Math.min(i + 1, path.length - 1)];
      const prev = path[Math.max(i - 1, 0)];
      const dir = normalise([next[0] - prev[0], next[1] - prev[1], next[2] - prev[2]]);
      let up = [0, 1, 0];
      if (Math.abs(dot(dir, up)) > 0.92) up = [0, 0, 1];
      const u = normalise(cross(dir, up));
      const w = cross(dir, u);
      const swell = ridges && i > 0 && i < path.length - 1 ? 1 + 0.14 * Math.cos(i * Math.PI) : 1;
      const r = radii[i] * swell;
      const ring = [];
      for (let s = 0; s < sides; s++) {
        const a = (s / sides) * TAU;
        const offset = [u[0] * Math.cos(a) * r + w[0] * Math.sin(a) * r, u[1] * Math.cos(a) * r + w[1] * Math.sin(a) * r, u[2] * Math.cos(a) * r + w[2] * Math.sin(a) * r];
        ring.push([here[0] + offset[0], here[1] + offset[1], here[2] + offset[2]]);
      }
      rings.push(ring);
    }
    for (let i = 0; i < rings.length - 1; i++) {
      for (let s = 0; s < sides; s++) {
        const a0 = rings[i][s];
        const a1 = rings[i][(s + 1) % sides];
        const b0 = rings[i + 1][s];
        const b1 = rings[i + 1][(s + 1) % sides];
        this.face(a0, a1, b1, b0, material);
      }
    }
    if (capStart) this.cap(rings[0], path[0], material, true);
    if (capEnd) this.cap(rings[rings.length - 1], path[path.length - 1], material, false);
    return this;
  }

  /** Fans a ring closed to a point. */
  cap(ring, centre, material, flip) {
    const c = this.v(centre[0], centre[1], centre[2]);
    for (let s = 0; s < ring.length; s++) {
      const a = this.v(...ring[s]);
      const b = this.v(...ring[(s + 1) % ring.length]);
      if (flip) this.tri(c, b, a, material);
      else this.tri(c, a, b, material);
    }
  }

  /** A faceted crystal: a ring of points around the middle, apexes above and below. */
  crystal(centre, radius, height, { sides = 6, material = 'ice', waist = 0.42, tilt = 0 } = {}) {
    const top = [centre[0] + tilt * height, centre[1] + height, centre[2]];
    const bottom = [centre[0] - tilt * height * 0.4, centre[1] - height * 0.35, centre[2]];
    const ring = [];
    for (let s = 0; s < sides; s++) {
      const a = (s / sides) * TAU;
      ring.push([centre[0] + Math.cos(a) * radius, centre[1] + height * waist * 0.2, centre[2] + Math.sin(a) * radius]);
    }
    const t = this.v(...top);
    const b = this.v(...bottom);
    const idx = ring.map((p) => this.v(...p));
    for (let s = 0; s < sides; s++) {
      this.tri(t, idx[s], idx[(s + 1) % sides], material);
      this.tri(b, idx[(s + 1) % sides], idx[s], material);
    }
  }

  /** A box, from its centre and size. */
  box(centre, size, { material = 'gold', rotate = 0 } = {}) {
    const [sx, sy, sz] = size.map((s) => s / 2);
    const corners = [
      [-sx, -sy, -sz], [sx, -sy, -sz], [sx, sy, -sz], [-sx, sy, -sz],
      [-sx, -sy, sz], [sx, -sy, sz], [sx, sy, sz], [-sx, sy, sz],
    ].map(([x, y, z]) => {
      const c = Math.cos(rotate);
      const s = Math.sin(rotate);
      return [centre[0] + x * c - z * s, centre[1] + y, centre[2] + x * s + z * c];
    });
    const f = [
      [0, 3, 2, 1], [4, 5, 6, 7], [0, 1, 5, 4],
      [2, 3, 7, 6], [1, 2, 6, 5], [0, 4, 7, 3],
    ];
    for (const q of f) this.face(corners[q[0]], corners[q[1]], corners[q[2]], corners[q[3]], material);
  }

  /** Points the mesh along a path; used for antler branches. */
  branch(start, direction, length, radius, steps, material, { sides = 6, taper = 0.05, bend = 0 } = {}) {
    const path = [];
    const radii = [];
    for (let i = 0; i <= steps; i++) {
      const t = i / steps;
      const lift = bend * t * t;
      path.push([
        start[0] + direction[0] * length * t + lift,
        start[1] + direction[1] * length * t,
        start[2] + direction[2] * length * t,
      ]);
      radii.push(Math.max(0.01, radius * (1 - taper * i)));
    }
    this.tube(path, radii, { sides, material });
    return path[path.length - 1];
  }
}

function normalise(v) {
  const l = Math.hypot(v[0], v[1], v[2]) || 1;
  return [v[0] / l, v[1] / l, v[2] / l];
}
function cross(a, b) {
  return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
}
function dot(a, b) {
  return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
}

// ---- The hats ------------------------------------------------------------

/** Shared head-fitting base ring. */
const HEAD = 4.6;

/** Midnight cone with a drooping brim, a hat band, a buckle and a star charm. */
function wizardHat(m) {
  // Brim: a wide shell that dips away from the crown, with a rolled edge.
  m.lathe([
    { r: HEAD + 0.0, y: 0.56 },
    { r: HEAD + 1.2, y: 0.3 },
    { r: HEAD + 2.2, y: 0.02 },
    { r: HEAD + 2.8, y: -0.22 },
  ], { segments: 30, thickness: 0.26, material: 'midnight', inner: 'obsidian' });

  // The cone is one swept tube from the brim to the tip, curving over as it
  // rises: two primitive shapes meeting halfway is what leaves a seam spike.
  const tip = [];
  const tipRadius = [];
  for (let i = 0; i <= 14; i++) {
    const t = i / 14;
    tip.push([
      Math.pow(t, 3.0) * 1.15,
      0.5 + t * 6.5,
      Math.pow(t, 3.4) * 0.35,
    ]);
    tipRadius.push(Math.max(0.08, (HEAD + 0.3) * Math.pow(1 - t, 1.25)));
  }
  m.tube(tip, tipRadius, { sides: 20, material: 'midnight', capStart: false, capEnd: true });

  // Band and buckle, proud of the cone.
  m.lathe([
    { r: HEAD + 0.3, y: 0.5 },
    { r: HEAD + 0.3, y: 1.15 },
  ], { segments: 24, thickness: 0.12, material: 'violet', inner: 'midnight' });
  m.box([0, 0.82, HEAD + 0.28], [1.15, 0.95, 0.3], { material: 'gold' });
  m.box([0, 0.82, HEAD + 0.4], [0.6, 0.46, 0.16], { material: 'obsidian' });

  // A star charm on a short chain at the band's front.
  m.box([1.0, 0.38, HEAD + 0.22], [0.09, 0.62, 0.09], { material: 'gold' });
  m.crystal([1.0, 0.02, HEAD + 0.24], 0.3, 0.6, { sides: 6, material: 'gold' });
}

/** Obsidian horns curling up and back, ridges at every bend, crimson tips. */
function demonHorns(m) {
  for (const side of [-1, 1]) {
    const path = [];
    const radii = [];
    const steps = 9;
    for (let i = 0; i <= steps; i++) {
      const t = i / steps;
      path.push([
        side * (HEAD * 0.55 + t * 0.7 + Math.pow(t, 2) * 0.45),
        0.7 + t * 2.6 + Math.pow(t, 3) * 1.3,
        -0.3 + Math.pow(t, 2) * 1.3,
      ]);
      radii.push(1.02 * (1 - t * 0.86));
    }
    m.tube(path, radii, { sides: 8, material: 'obsidian', ridges: 1 });
    // Crimson tip, a shade wider than the last ring so it reads as a cap.
    m.crystal(path[steps], radii[steps] * 1.15, 0.7, { sides: 7, material: 'crimson', waist: 0.3, tilt: side * 0.1 });
  }
  // Brow band tying the pair to the head, with a gemstone at the front.
  m.lathe([
    { r: HEAD + 0.04, y: 0.12 },
    { r: HEAD + 0.12, y: 1.0 },
  ], { segments: 24, thickness: 0.22, material: 'obsidian', inner: 'midnight' });
  m.crystal([0, 0.62, HEAD + 0.16], 0.46, 0.95, { sides: 6, material: 'ruby' });
  for (const side of [-1, 1]) {
    m.crystal([side * 2.1, 0.6, HEAD - 0.5], 0.26, 0.55, { sides: 6, material: 'ruby' });
  }
}

/** A gold ring that floats clear of the head, with soft sparkles around it. */
function angelHalo(m) {
  const y = 7.2;
  const radius = 3.4;
  const tube = 0.44;
  const segments = 26;
  const rings = [];
  for (let i = 0; i <= segments; i++) {
    const a = (i / segments) * TAU;
    const centre = [Math.cos(a) * radius, y + Math.sin(a * 2) * 0.06, Math.sin(a) * radius];
    const tangent = normalise([-Math.sin(a), 0, Math.cos(a)]);
    let up = [0, 1, 0];
    const u = normalise(cross(tangent, up));
    const w = cross(tangent, u);
    const ring = [];
    for (let s = 0; s < 8; s++) {
      const b = (s / 8) * TAU;
      ring.push([
        centre[0] + u[0] * Math.cos(b) * tube + w[0] * Math.sin(b) * tube,
        centre[1] + u[1] * Math.cos(b) * tube + w[1] * Math.sin(b) * tube,
        centre[2] + u[2] * Math.cos(b) * tube + w[2] * Math.sin(b) * tube,
      ]);
    }
    rings.push(ring);
  }
  for (let i = 0; i < rings.length - 1; i++) {
    for (let s = 0; s < 8; s++) {
      m.face(rings[i][s], rings[i][(s + 1) % 8], rings[i + 1][(s + 1) % 8], rings[i + 1][s], 'gold');
    }
  }
  // Light motes: small ivory crystals hovering just off the ring.
  for (let i = 0; i < 6; i++) {
    const a = (i / 6) * TAU + 0.4;
    m.crystal([Math.cos(a) * (radius + 0.75), y + 0.5 + (i % 2) * 0.4, Math.sin(a) * (radius + 0.75)],
      0.16, 0.42, { sides: 5, material: 'ivory' });
  }
}

/** A jewelled crown: a band of points, gems in the gaps and a velvet cap. */
function royalCrown(m) {
  // Velvet cap so the head isn't visible through the ring.
  m.lathe([
    { r: HEAD - 0.1, y: 0.2 },
    { r: HEAD - 0.5, y: 1.6 },
    { r: HEAD - 1.4, y: 2.5 },
  ], { segments: 20, thickness: 0, material: 'crimson', capTop: true });

  // Band.
  m.lathe([
    { r: HEAD + 0.05, y: 0.1 },
    { r: HEAD + 0.1, y: 1.15 },
  ], { segments: 24, thickness: 0.18, material: 'gold', inner: 'midnight' });

  // Eight points around the band, each with a gem.
  const points = 8;
  for (let i = 0; i < points; i++) {
    const a = (i / points) * TAU;
    const x = Math.cos(a) * (HEAD + 0.06);
    const z = Math.sin(a) * (HEAD + 0.06);
    const tall = i % 2 === 0 ? 2.5 : 1.7;
    const width = i % 2 === 0 ? 0.62 : 0.5;
    const tangent = [-Math.sin(a), 0, Math.cos(a)];
    const normal = [Math.cos(a), 0, Math.sin(a)];
    base(m, [
      [x + tangent[0] * width, 1.05, z + tangent[2] * width],
      [x - tangent[0] * width, 1.05, z - tangent[2] * width],
      [x - tangent[0] * width * 0.55 + normal[0] * 0.1, 1.05 + tall, z - tangent[2] * width * 0.55 + normal[2] * 0.1],
      [x + tangent[0] * width * 0.55 + normal[0] * 0.1, 1.05 + tall, z + tangent[2] * width * 0.55 + normal[2] * 0.1],
    ], 0.22, 'gold', normal);
    m.crystal([x + normal[0] * 0.16, 0.75, z + normal[2] * 0.16], 0.24, 0.6, { sides: 6, material: i % 2 === 0 ? 'ruby' : 'emerald' });
    // Rivets on the band between the points.
    const b = a + Math.PI / points;
    m.crystal([Math.cos(b) * (HEAD + 0.2), 0.62, Math.sin(b) * (HEAD + 0.2)], 0.13, 0.3, { sides: 5, material: 'gold' });
  }
  // Finial on the front point.
  m.box([0, 1.05 + 2.5 + 0.18, HEAD + 0.06], [0.5, 0.36, 0.3], { material: 'gold' });
  m.crystal([0, 1.05 + 2.5 + 0.62, HEAD + 0.06], 0.3, 0.7, { sides: 6, material: 'ruby' });
}

/** An extruded flat shape (a crown point, a leaf): front face, back face, sides. */
function base(m, corners, thickness, material, normal) {
  const half = thickness / 2;
  const front = corners.map((p) => [p[0] + normal[0] * half, p[1], p[2] + normal[2] * half]);
  const back = corners.map((p) => [p[0] - normal[0] * half, p[1], p[2] - normal[2] * half]);
  for (let i = 1; i + 1 < front.length; i++) {
    m.face(front[0], front[i], front[i + 1], front[i + 1], material);
    m.face(back[0], back[i + 1], back[i], back[i], material);
  }
  for (let i = 0; i < corners.length; i++) {
    const j = (i + 1) % corners.length;
    m.face(front[i], back[i], back[j], front[j], material);
  }
}

/** Branching ice antlers, each branch tipped with a crystal. */
function crystalAntlers(m) {
  for (const side of [-1, 1]) {
    const root = [side * 1.9, 0.3, -0.35];
    const stemDir = normalise([side * 0.45, 1, -0.15]);
    const tip = m.branch(root, stemDir, 3.6, 0.52, 6, 'ice_dark', { sides: 6, taper: 0.07, bend: side * 0.25 });
    m.crystal(tip, 0.3, 0.9, { sides: 6, material: 'ice', tilt: side * 0.15 });
    // Tines coming off the stem, the way an antler forks.
    const forks = [
      { at: 0.34, dir: [side * 0.85, 0.75, 0.1], length: 1.7, radius: 0.26 },
      { at: 0.55, dir: [side * 0.2, 0.9, -0.6], length: 1.5, radius: 0.22 },
      { at: 0.74, dir: [side * 0.9, 0.55, 0.3], length: 1.15, radius: 0.18 },
    ];
    for (const fork of forks) {
      const start = [
        root[0] + stemDir[0] * 3.6 * fork.at + side * 0.25 * fork.at,
        root[1] + stemDir[1] * 3.6 * fork.at,
        root[2] + stemDir[2] * 3.6 * fork.at,
      ];
      const end = m.branch(start, normalise(fork.dir), fork.length, fork.radius, 4, 'ice_dark', { sides: 5, taper: 0.08 });
      m.crystal(end, fork.radius * 0.9, fork.length * 0.32, { sides: 6, material: 'ice' });
    }
  }
  // A silver circlet holding them on.
  m.lathe([
    { r: HEAD + 0.02, y: 0.12 },
    { r: HEAD + 0.1, y: 0.72 },
  ], { segments: 24, thickness: 0.26, material: 'silver', inner: 'obsidian' });
  m.crystal([0, 0.55, HEAD + 0.12], 0.28, 0.62, { sides: 6, material: 'ice' });
}

/** New: ender crystal crown — obsidian spikes with floating end-rod glows. */
function enderCrown(m) {
  m.lathe([
    { r: HEAD + 0.02, y: 0.12 },
    { r: HEAD + 0.12, y: 0.85 },
    { r: HEAD + 0.04, y: 1.3 },
  ], { segments: 20, thickness: 0.2, material: 'obsidian', inner: 'midnight' });

  for (let i = 0; i < 7; i++) {
    const a = (i / 7) * TAU + 0.2;
    const tall = i % 2 === 0 ? 2.9 : 1.9;
    const path = [];
    const radii = [];
    for (let k = 0; k <= 4; k++) {
      const t = k / 4;
      path.push([Math.cos(a) * (HEAD + 0.05 + t * 0.3), 1.2 + t * tall, Math.sin(a) * (HEAD + 0.05 + t * 0.3)]);
      radii.push(0.42 * (1 - t * 0.9));
    }
    m.tube(path, radii, { sides: 6, material: 'obsidian' });
    // End-rod glow hovering just above the spike.
    m.crystal([path[4][0], path[4][1] + 0.45, path[4][2]], 0.3, 0.75, { sides: 6, material: 'ender_glow' });
  }
  // Ender gem set at the front of the band.
  m.crystal([0, 0.75, HEAD + 0.18], 0.42, 1.0, { sides: 6, material: 'ender_glow' });
  for (const side of [-1, 1]) {
    m.crystal([side * 2.1, 0.72, HEAD - 0.55], 0.24, 0.5, { sides: 6, material: 'ender' });
  }
}

/**
 * A torn phantom hood: a cowl with a doubled wall, a rolled hem around the
 * face opening, cloth folds laid over the dome, a ragged mantle across the
 * shoulders and tattered strips down the back.
 *
 * The opening faces +Z, which is the front of the player model, and the face
 * is the one part of the mesh with no cloth over it, so the eyes and the
 * shadowed lining inside are what a viewer sees from the front.
 */
function phantomHood(m) {
  const FRONT = Math.PI / 2; // +Z
  const gapHalf = 0.42 * Math.PI; // the slice of the ring the face opening takes
  const segments = 30;
  const thickness = 0.28;
  // Front rim sits low on the brow, then the cloth swells over the crown and
  // tapers to a point behind it.
  const skin = [
    { r: HEAD + 0.36, y: 0.42 },
    { r: HEAD + 0.30, y: 1.95 },
    { r: HEAD - 0.05, y: 3.40 },
    { r: HEAD - 1.20, y: 4.75 },
    { r: HEAD - 2.60, y: 5.70 },
    { r: HEAD - 3.55, y: 6.15 },
  ];
  const lining = skin.map((p) => ({ r: Math.max(0.08, p.r - thickness), y: p.y - thickness * 0.7 }));
  const span = TAU - 2 * gapHalf;
  const start = FRONT + gapHalf;
  const at = (ring, i, s) => {
    const a = start + (s / segments) * span;
    return [Math.cos(a) * ring[i].r, ring[i].y, Math.sin(a) * ring[i].r];
  };

  for (let i = 0; i < skin.length - 1; i++) {
    for (let s = 0; s < segments; s++) {
      m.face(at(skin, i, s), at(skin, i, s + 1), at(skin, i + 1, s + 1), at(skin, i + 1, s), 'phantom');
      m.face(at(lining, i, s), at(lining, i + 1, s), at(lining, i + 1, s + 1), at(lining, i, s + 1), 'phantom_dark');
    }
    // The cut edge of the opening, so the hood has cloth thickness around the face.
    for (const edge of [0, segments]) {
      m.face(at(skin, i, edge), at(lining, i, edge), at(lining, i + 1, edge), at(skin, i + 1, edge), 'phantom_dark');
    }
  }
  // Crown point, and the bottom hem where the shell meets the mantle.
  const apex = [0, skin[skin.length - 1].y + 0.45, 0];
  for (let s = 0; s < segments; s++) {
    m.face(apex, at(skin, skin.length - 1, s), at(skin, skin.length - 1, s + 1), apex, 'phantom');
  }
  for (let s = 0; s < segments; s++) {
    m.face(at(skin, 0, s), at(lining, 0, s), at(lining, 0, s + 1), at(skin, 0, s + 1), 'phantom_dark');
  }

  // Rolled hem: a rounded seam running up each side of the opening.
  for (const side of [-1, 1]) {
    const path = skin.map((p) => [Math.cos(FRONT + side * gapHalf) * (p.r + 0.06), p.y, Math.sin(FRONT + side * gapHalf) * (p.r + 0.06)]);
    m.tube(path, skin.map((_, i) => 0.22 - i * 0.015), { sides: 6, material: 'phantom_hem', capStart: true, capEnd: true });
  }

  // Cloth folds: ribs of the same fabric laid over the dome, fading as they climb.
  for (const offset of [-0.85, 0.85, Math.PI]) {
    const angle = FRONT + offset;
    const path = skin.slice(1).map((p) => [Math.cos(angle) * (p.r + 0.04), p.y, Math.sin(angle) * (p.r + 0.04)]);
    m.tube(path, path.map((_, i) => 0.16 - i * 0.022), { sides: 5, material: 'phantom_dark', capStart: true, capEnd: true });
  }

  // Mantle: a skirt of cloth falling off the base of the hood and flaring out
  // over the shoulders, with a torn hem.
  const top = { r: HEAD + 0.32, y: 0.44 };
  const hem = { r: HEAD + 1.5, y: -1.05 };
  const mantleAt = (ring, s, drop) => {
    const a = (s / segments) * TAU;
    return [Math.cos(a) * ring.r, ring.y + drop, Math.sin(a) * ring.r];
  };
  for (let s = 0; s < segments; s++) {
    const wave = (k) => Math.sin(((k / segments) * TAU) * 3) * 0.22;
    m.face(mantleAt(top, s, 0), mantleAt(top, s + 1, 0), mantleAt(hem, s + 1, wave(s + 1)), mantleAt(hem, s, wave(s)), 'phantom');
    m.face(mantleAt(hem, s, wave(s)), mantleAt(hem, s + 1, wave(s + 1)), mantleAt({ r: hem.r - 0.24, y: hem.y + 0.12 }, s + 1, wave(s + 1)), mantleAt({ r: hem.r - 0.24, y: hem.y + 0.12 }, s, wave(s)), 'phantom_dark');
    m.face(mantleAt(top, s, 0), mantleAt({ r: top.r - 0.24, y: top.y + 0.1 }, s, 0), mantleAt({ r: top.r - 0.24, y: top.y + 0.1 }, s + 1, 0), mantleAt(top, s + 1, 0), 'phantom_dark');
  }

  // Torn strips off the mantle, longest at the back, each one a wedge of cloth
  // with an underside so it is not a single flat face.
  const strips = 16;
  for (let i = 0; i < strips; i++) {
    const a = (i / strips) * TAU + 0.11;
    const behind = Math.cos(a - FRONT) < -0.2;
    const length = (behind ? 2.0 : 1.05) + Math.sin(i * 2.3) * 0.45;
    const r = hem.r + 0.02;
    const centre = [Math.cos(a) * r, hem.y + Math.sin((i / strips) * TAU * 3) * 0.2, Math.sin(a) * r];
    const tail = [Math.cos(a) * (r + 0.18), centre[1] - length, Math.sin(a) * (r + 0.18)];
    clothStrip(m, centre, tail, 0.55, 0.1, [-Math.sin(a), 0, Math.cos(a)], [Math.cos(a), 0, Math.sin(a)], 0.13, 'phantom', 'phantom_dark');
  }

  // Two long tails down the back, further out than the rest.
  for (const offset of [-0.5, 0.42]) {
    const a = FRONT + Math.PI + offset;
    const r = hem.r + 0.3;
    const centre = [Math.cos(a) * r, hem.y + 0.2, Math.sin(a) * r];
    const tail = [Math.cos(a + 0.25) * (r + 0.5), hem.y - 3.1, Math.sin(a + 0.25) * (r + 0.5)];
    clothStrip(m, centre, tail, 0.78, 0.18, [-Math.sin(a), 0, Math.cos(a)], [Math.cos(a), 0, Math.sin(a)], 0.15, 'phantom', 'phantom_dark');
  }

  // A bone clasp at the throat, with a cold light in it.
  m.box([0, 0.5, HEAD + 0.52], [1.5, 0.42, 0.4], { material: 'bone' });
  m.crystal([0, 0.5, HEAD + 0.78], 0.3, 0.5, { sides: 6, material: 'phantom_glow', waist: 0.4 });
  // Two glowing eyes set back inside the hood's shadow.
  for (const side of [-1, 1]) {
    m.crystal([side * 1.15, 3.05, HEAD + 0.30], 0.26, 0.55, { sides: 6, material: 'phantom_glow', waist: 0.35 });
  }
}

/**
 * A strip of cloth hanging from `top` to `bottom`: an outer face, an inner face
 * and closed edges, so a torn hem reads as fabric from both sides.
 */
function clothStrip(m, top, bottom, halfWidth, tipWidth, side, normal, thickness, material, lining) {
  const edge = (c, k, w) => [c[0] + side[0] * w * k, c[1] + side[1] * w * k, c[2] + side[2] * w * k];
  const back = (p) => [p[0] + normal[0] * thickness, p[1] + normal[1] * thickness, p[2] + normal[2] * thickness];
  const tl = edge(top, 1, halfWidth);
  const tr = edge(top, -1, halfWidth);
  const bl = edge(bottom, 1, tipWidth);
  const br = edge(bottom, -1, tipWidth);
  m.face(tl, tr, br, bl, material);
  m.face(back(tl), back(bl), back(br), back(tr), lining);
  m.face(tl, bl, back(bl), back(tl), material);
  m.face(tr, back(tr), back(br), br, material);
  m.face(bl, br, back(br), back(bl), lining);
}

/** New: a miner's helmet — iron dome, wide brim, lamp on the brow, leather strap. */
function minerHelmet(m) {
  m.lathe([
    { r: HEAD - 0.15, y: 0.62 },
    { r: HEAD - 0.1, y: 1.8 },
    { r: HEAD - 0.9, y: 3.1 },
    { r: HEAD - 2.2, y: 4.0 },
  ], { segments: 22, thickness: 0.24, material: 'iron', inner: 'obsidian' });
  // Brim, running out past the shell.
  m.lathe([
    { r: HEAD - 0.1, y: 0.66 },
    { r: HEAD + 1.0, y: 0.4 },
    { r: HEAD + 1.6, y: 0.12 },
  ], { segments: 26, thickness: 0.22, material: 'iron', inner: 'obsidian' });
  // Rib over the crown.
  m.tube([[0, 1.4, -HEAD + 0.1], [0, 3.1, -HEAD * 0.7], [0, 4.05, 0], [0, 3.1, HEAD * 0.7], [0, 1.4, HEAD - 0.1]], [0.16, 0.18, 0.2, 0.18, 0.16], {
    sides: 6, material: 'iron', capStart: true, capEnd: true,
  });
  // Lamp on the front: a housing, a glowing lens and a hood over it.
  m.box([0, 1.95, HEAD + 0.42], [1.25, 1.0, 0.6], { material: 'iron' });
  m.crystal([0, 1.95, HEAD + 0.84], 0.4, 0.66, { sides: 6, material: 'ember', waist: 0.35 });
  m.box([0, 2.58, HEAD + 0.5], [1.5, 0.22, 0.85], { material: 'iron' });
  // Leather strap around the base, with a buckle on the side.
  m.lathe([
    { r: HEAD + 0.16, y: 0.62 },
    { r: HEAD + 0.16, y: 1.0 },
  ], { segments: 24, material: 'leather' });
  m.box([HEAD + 0.24, 0.82, 0], [0.34, 0.5, 0.9], { material: 'gold', rotate: Math.PI / 2 });
  for (let i = 0; i < 5; i++) {
    const a = Math.PI * 0.2 + i * Math.PI * 0.28;
    m.crystal([Math.cos(a) * (HEAD + 0.34), 0.8, Math.sin(a) * (HEAD + 0.34)], 0.12, 0.26, { sides: 5, material: 'silver' });
  }
}

/** New: a golden laurel wreath of overlapping leaves. */
function goldenLaurel(m) {
  const leaves = 18;
  for (let i = 0; i < leaves; i++) {
    const side = i < leaves / 2 ? -1 : 1;
    const k = i % (leaves / 2);
    const t = k / (leaves / 2 - 1);
    const a = Math.PI * 0.5 + side * (0.22 + t * 2.5);
    const r = HEAD + 0.22;
    const x = Math.cos(a) * r;
    const z = Math.sin(a) * r;
    const lift = 0.55 + Math.sin(t * Math.PI) * 0.55;
    // Leaves grow from the band, lying along the head and angling up.
    const along = Math.atan2(Math.cos(a), -Math.sin(a)) + side * (0.35 + t * 0.3);
    leaf(m, [x, lift, z], [Math.cos(along), 0.5 + t * 0.35, Math.sin(along)], 1.15, 0.55, side * 0.35, k % 2 === 0 ? 'gold' : 'ember');
  }
  // The band the leaves grow from.
  m.lathe([
    { r: HEAD + 0.12, y: 0.45 },
    { r: HEAD + 0.16, y: 0.78 },
  ], { segments: 24, thickness: 0.16, material: 'gold', inner: 'leather' });
  // Two berries at the front.
  for (const side of [-1, 1]) {
    m.crystal([side * 0.7, 0.62, HEAD + 0.18], 0.18, 0.4, { sides: 6, material: 'ruby' });
  }
}

/** One laurel leaf: two pointed halves with a raised spine, aimed along `out`. */
function leaf(m, centre, out, length, width, tilt, material) {
  const dir = normalise(out);
  const side = normalise(cross([0, 1, 0], dir));
  const up = [0, 1, 0];
  const tip = [centre[0] + dir[0] * length, centre[1] + length * 0.25 + tilt * length * 0.2, centre[2] + dir[2] * length];
  const root = [centre[0] - dir[0] * length * 0.15, centre[1], centre[2] - dir[2] * length * 0.15];
  const mid = [centre[0] + dir[0] * length * 0.45, centre[1] + 0.16, centre[2] + dir[2] * length * 0.45];
  const left = [mid[0] + side[0] * width, mid[1] + 0.04, mid[2] + side[2] * width];
  const right = [mid[0] - side[0] * width, mid[1] + 0.04, mid[2] - side[2] * width];
  const spineTop = [mid[0], mid[1] + 0.14, mid[2]];
  m.face(root, left, spineTop, spineTop, material);
  m.face(left, tip, spineTop, spineTop, material);
  m.face(tip, right, spineTop, spineTop, material);
  m.face(right, root, spineTop, spineTop, material);
  const under = [mid[0], mid[1] - 0.12, mid[2]];
  m.face(root, spineTop, left, under, material);
  m.face(left, spineTop, tip, under, material);
  m.face(tip, spineTop, right, under, material);
  m.face(right, spineTop, root, under, material);
}

const HATS = [
  { id: 'wizard_hat', name: 'Wizard Hat', build: wizardHat },
  { id: 'demon_horns', name: 'Demon Horns', build: demonHorns },
  { id: 'angel_halo', name: 'Angel Halo', build: angelHalo },
  { id: 'royal_crown', name: 'Royal Crown', build: royalCrown },
  { id: 'crystal_antlers', name: 'Crystal Antlers', build: crystalAntlers },
  { id: 'ender_crown', name: 'Ender Crown', build: enderCrown },
  { id: 'phantom_hood', name: 'Phantom Hood', build: phantomHood },
  { id: 'miner_helmet', name: "Miner's Helmet", build: minerHelmet },
  { id: 'golden_laurel', name: 'Golden Laurel', build: goldenLaurel },
];

// ---- Output --------------------------------------------------------------

function obj(hat, mesh) {
  const lines = [
    `# Novo Client cosmetic model: ${hat.name}`,
    '# Units are arbitrary; center X/Z, head sits near Y=0.',
    'mtllib materials.mtl',
    `o ${hat.id}`,
  ];
  for (const v of mesh.verts) lines.push(`v ${v[0].toFixed(5)} ${v[1].toFixed(5)} ${v[2].toFixed(5)}`);
  let material = null;
  for (const t of mesh.tris) {
    if (t.material !== material) {
      material = t.material;
      lines.push(`usemtl ${material}`);
    }
    lines.push(`f ${t.v[0] + 1} ${t.v[1] + 1} ${t.v[2] + 1}`);
  }
  return `${lines.join('\n')}\n`;
}

function materials() {
  const lines = ['# Fixed-colour materials for Novo Client hats and wings'];
  for (const [name, [r, g, b, specular, shininess]] of Object.entries(MATERIALS)) {
    lines.push(
      `newmtl ${name}`,
      'Ka 0.15 0.15 0.15',
      `Kd ${r.toFixed(4)} ${g.toFixed(4)} ${b.toFixed(4)}`,
      `Ks ${specular.toFixed(2)} ${specular.toFixed(2)} ${specular.toFixed(2)}`,
      `Ns ${shininess.toFixed(1)}`,
      'd 1.0',
      '',
    );
  }
  return lines.join('\n');
}

/** Front-ish view of one hat, so a change can be eyeballed without the game. */
function preview(hat, mesh) {
  const W = 460;
  const H = 420;
  const px = new Uint8Array(W * H * 4);
  for (let i = 0; i < px.length; i += 4) {
    px[i] = 18;
    px[i + 1] = 20;
    px[i + 2] = 28;
    px[i + 3] = 255;
  }
  const minY = Math.min(...mesh.verts.map((v) => v[1]));
  const maxY = Math.max(...mesh.verts.map((v) => v[1]));
  const span = Math.max(12, maxY - minY);
  const scale = (H - 40) / span;
  const yaw = -0.5;
  const cos = Math.cos(yaw);
  const sin = Math.sin(yaw);
  const project = (v) => {
    const x = v[0] * cos - v[2] * sin;
    const z = v[0] * sin + v[2] * cos;
    return [W / 2 + x * scale, H - 30 - (v[1] - minY) * scale, z];
  };
  const light = normalise([-0.4, 0.7, 0.6]);
  const faces = mesh.tris.map((t) => {
    const p = t.v.map((i) => mesh.verts[i]);
    const ux = p[1][0] - p[0][0], uy = p[1][1] - p[0][1], uz = p[1][2] - p[0][2];
    const vx = p[2][0] - p[0][0], vy = p[2][1] - p[0][1], vz = p[2][2] - p[0][2];
    const n = normalise([uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx]);
    const lambert = Math.abs(dot(n, light));
    const kd = MATERIALS[t.material] ?? [0.8, 0.8, 0.8];
    const shade = 0.32 + 0.68 * lambert;
    const proj = p.map(project);
    return { z: (proj[0][2] + proj[1][2] + proj[2][2]) / 3, p: proj, colour: kd.slice(0, 3).map((c) => Math.round(255 * Math.min(1, c * shade))) };
  });
  faces.sort((a, b) => a.z - b.z);
  for (const face of faces) {
    const [a, b, c] = face.p;
    const area = (b[0] - a[0]) * (c[1] - a[1]) - (c[0] - a[0]) * (b[1] - a[1]);
    if (Math.abs(area) < 1e-6) continue;
    const minX = Math.max(0, Math.floor(Math.min(a[0], b[0], c[0])));
    const maxX = Math.min(W - 1, Math.ceil(Math.max(a[0], b[0], c[0])));
    const minYp = Math.max(0, Math.floor(Math.min(a[1], b[1], c[1])));
    const maxYp = Math.min(H - 1, Math.ceil(Math.max(a[1], b[1], c[1])));
    for (let y = minYp; y <= maxYp; y++) {
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
  writeFileSync(new URL(`${hat.id}.png`, PREVIEW_DIR), encodePng(W, H, px));
}

mkdirSync(OUT, { recursive: true });
mkdirSync(PREVIEW_DIR, { recursive: true });
writeFileSync(new URL('materials.mtl', OUT), materials());
for (const hat of HATS) {
  const mesh = new Mesh();
  hat.build(mesh);
  writeFileSync(new URL(`${hat.id}.obj`, OUT), obj(hat, mesh));
  console.log(`wrote ${hat.id}.obj (${mesh.tris.length} triangles)`);
  if (PREVIEW) preview(hat, mesh);
}
