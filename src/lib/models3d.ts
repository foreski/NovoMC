// Three.js models for the launcher: the wing and hat meshes the 1.8.9 client
// ships, a cape built from its texture, and the player model for the skin
// changer. Everything the launcher shows is the same art the game draws, so a
// preview cannot drift from what a player actually gets.
import * as THREE from 'three';

const CAPES = import.meta.glob('../../client-mod/src/main/resources/assets/novomc/textures/capes/*.png', {
  eager: true,
  query: '?url',
  import: 'default',
}) as Record<string, string>;
const WING_OBJ = import.meta.glob('../../client-mod/src/main/resources/assets/novomc/models/wings/*.obj', {
  eager: true,
  query: '?url',
  import: 'default',
}) as Record<string, string>;
const WING_MTL = import.meta.glob('../../client-mod/src/main/resources/assets/novomc/models/wings/*.mtl', {
  eager: true,
  query: '?url',
  import: 'default',
}) as Record<string, string>;
const HAT_OBJ = import.meta.glob('../../client-mod/src/main/resources/assets/novomc/models/hats/*.obj', {
  eager: true,
  query: '?url',
  import: 'default',
}) as Record<string, string>;
const HAT_MTL = import.meta.glob('../../client-mod/src/main/resources/assets/novomc/models/hats/*.mtl', {
  eager: true,
  query: '?url',
  import: 'default',
}) as Record<string, string>;

/** `<id>.png` for a still cape, `<id>_<frame>.png` for one animation frame. */
function lookup(urls: Record<string, string>, name: string): string | undefined {
  return Object.entries(urls).find(([path]) => path.endsWith(`/${name}`))?.[1];
}

export function capeFrames(id: string): string[] {
  const frames: string[] = [];
  for (let frame = 0; frame < 64; frame++) {
    const url = lookup(CAPES, `${id}_${frame}.png`);
    if (!url) break;
    frames.push(url);
  }
  const still = lookup(CAPES, `${id}.png`);
  return frames.length > 0 ? frames : still ? [still] : [];
}

/**
 * A loaded cosmetic, ready to drop into a scene: its meshes, the extent it
 * occupies, and the texture loader for anything it needs later.
 */
export interface MaterialColors {
  color: THREE.Color;
  shininess: number;
}

function parseMtl(text: string): Record<string, MaterialColors> {
  const out: Record<string, MaterialColors> = {};
  let current: MaterialColors | null = null;
  for (const raw of text.split('\n')) {
    const line = raw.trim();
    if (line.startsWith('newmtl ')) {
      current = { color: new THREE.Color(0.8, 0.8, 0.8), shininess: 24 };
      out[line.slice(7).trim()] = current;
    } else if (current && line.startsWith('Kd ')) {
      const [r, g, b] = line.slice(3).trim().split(/\s+/).map(Number);
      current.color.setRGB(r, g, b);
    } else if (current && line.startsWith('Ns ')) {
      current.shininess = Number(line.slice(3).trim());
    }
  }
  return out;
}

/**
 * Groups an OBJ's triangles by material. The files carry positions and faces
 * only — no normals, no UVs — which is why the game bakes shading at load time
 * and why the preview can just compute smooth normals.
 */
function parseObj(text: string): { material: string; positions: number[] }[] {
  const vertices: number[][] = [];
  const groups = new Map<string, number[]>();
  let material = 'default';
  for (const raw of text.split('\n')) {
    const line = raw.trim();
    if (line.startsWith('v ')) {
      const p = line.slice(2).trim().split(/\s+/).map(Number);
      if (p.length >= 3) vertices.push([p[0], p[1], p[2]]);
    } else if (line.startsWith('usemtl ')) {
      material = line.slice(7).trim();
    } else if (line.startsWith('f ')) {
      const corners = line
        .slice(2)
        .trim()
        .split(/\s+/)
        .map((token) => {
          const index = Number(token.split('/')[0]);
          return index < 0 ? vertices.length + index : index - 1;
        })
        .filter((index) => index >= 0 && index < vertices.length);
      const list = groups.get(material) ?? [];
      for (let i = 1; i + 1 < corners.length; i++) {
        for (const index of [corners[0], corners[i], corners[i + 1]]) {
          list.push(...vertices[index]);
        }
      }
      groups.set(material, list);
    }
  }
  return [...groups.entries()].map(([name, positions]) => ({ material: name, positions }));
}

const objectCache = new Map<string, Promise<THREE.Group>>();

/** Builds a wing pair or a hat from the meshes the game ships, centred and normalised. */
export async function loadCosmeticMesh(kind: 'wings' | 'hat', id: string): Promise<THREE.Group> {
  const key = `${kind}:${id}`;
  const cached = objectCache.get(key);
  if (cached) return (await cached).clone(true);

  const loading = (async () => {
    const objUrl = lookup(kind === 'wings' ? WING_OBJ : HAT_OBJ, `${id}.obj`);
    const mtlUrl = lookup(kind === 'wings' ? WING_MTL : HAT_MTL, `${kind === 'wings' ? id : 'materials'}.mtl`);
    if (!objUrl) throw new Error(`no ${kind} mesh for ${id}`);
    const [objText, mtlText] = await Promise.all([
      fetch(objUrl).then((r) => r.text()),
      mtlUrl ? fetch(mtlUrl).then((r) => r.text()) : Promise.resolve(''),
    ]);
    const materials = parseMtl(mtlText);
    const group = new THREE.Group();
    for (const part of parseObj(objText)) {
      const geometry = new THREE.BufferGeometry();
      geometry.setAttribute('position', new THREE.Float32BufferAttribute(part.positions, 3));
      geometry.computeVertexNormals();
      const found = materials[part.material];
      const mesh = new THREE.Mesh(
        geometry,
        new THREE.MeshStandardMaterial({
          color: found ? found.color : new THREE.Color(0.8, 0.8, 0.8),
          roughness: 0.62,
          metalness: 0.05,
          side: THREE.DoubleSide,
        }),
      );
      group.add(mesh);
    }
    // Centre it and scale the longest side to a unit, so a hat and a wing pair
    // both fill the same preview box.
    const box = new THREE.Box3().setFromObject(group);
    const size = box.getSize(new THREE.Vector3());
    const centre = box.getCenter(new THREE.Vector3());
    group.position.copy(centre.multiplyScalar(-1));
    const wrapper = new THREE.Group();
    wrapper.add(group);
    wrapper.scale.setScalar(1 / Math.max(size.x, size.y, size.z, 0.001));
    return wrapper;
  })();

  objectCache.set(key, loading);
  return (await loading).clone(true);
}

/**
 * A cape as a cloth: a 10x16 sheet, gently bowed and flared at the hem so it
 * reads as fabric rather than a billboard, with the outer face's pixels from
 * the 64x32 cape sheet.
 */
export function buildCape(texture: THREE.Texture): THREE.Mesh {
  const geometry = new THREE.PlaneGeometry(10, 16, 10, 16);
  const position = geometry.attributes.position as THREE.BufferAttribute;
  for (let i = 0; i < position.count; i++) {
    const x = position.getX(i);
    const y = position.getY(i);
    // Down the cape the cloth drifts back and widens; across it a slow wave
    // catches the light.
    const t = (8 - y) / 16;
    position.setZ(i, -1.2 * t * t + Math.sin(x * 0.55 + t * 3) * 0.35 * t);
    position.setX(i, x * (1 + 0.35 * t));
  }
  position.needsUpdate = true;
  geometry.computeVertexNormals();
  // The outer face is the 10x16 block at (1,1) of the 64x32 sheet.
  const uv = geometry.attributes.uv as THREE.BufferAttribute;
  for (let i = 0; i < uv.count; i++) {
    uv.setXY(i, (1 + uv.getX(i) * 10) / 64, 1 - (1 + (1 - uv.getY(i)) * 16) / 32);
  }
  texture.magFilter = THREE.NearestFilter;
  texture.minFilter = THREE.NearestFilter;
  texture.colorSpace = THREE.SRGBColorSpace;
  const mesh = new THREE.Mesh(
    geometry,
    new THREE.MeshStandardMaterial({ map: texture, roughness: 0.85, metalness: 0, side: THREE.DoubleSide }),
  );
  mesh.scale.setScalar(0.055);
  return mesh;
}

export function loadTexture(url: string): THREE.Texture {
  const texture = new THREE.TextureLoader().load(url);
  texture.magFilter = THREE.NearestFilter;
  texture.minFilter = THREE.NearestFilter;
  texture.colorSpace = THREE.SRGBColorSpace;
  return texture;
}

/** A texture that has actually arrived, for the one-off thumbnail renders. */
function loadedTexture(url: string): Promise<THREE.Texture> {
  return new Promise((resolve, reject) => {
    new THREE.TextureLoader().load(
      url,
      (texture) => {
        texture.magFilter = THREE.NearestFilter;
        texture.minFilter = THREE.NearestFilter;
        texture.colorSpace = THREE.SRGBColorSpace;
        resolve(texture);
      },
      undefined,
      () => reject(new Error(`could not load ${url}`)),
    );
  });
}

let stillRenderer: THREE.WebGLRenderer | null = null;
const stills = new Map<string, Promise<string>>();

/**
 * A still, three-quarter view of a cosmetic as a PNG data URL.
 *
 * Small grids (the collection strip, dozens of tiles at once) cannot each own a
 * live WebGL canvas: browsers hand out around sixteen contexts per page and
 * quietly drop the oldest, which leaves blank cards. Everything here is drawn
 * once in a single shared offscreen renderer and cached.
 */
export function cosmeticStill(kind: 'cape' | 'wings' | 'hat', id: string): Promise<string> {
  const key = `${kind}:${id}`;
  const hit = stills.get(key);
  if (hit) return hit;
  const job = (async () => {
    const size = 160;
    if (!stillRenderer) {
      stillRenderer = new THREE.WebGLRenderer({ alpha: true, antialias: true, preserveDrawingBuffer: true });
      stillRenderer.setPixelRatio(1);
      stillRenderer.setSize(size, size, false);
      stillRenderer.setClearColor(0x000000, 0);
      stillRenderer.outputColorSpace = THREE.SRGBColorSpace;
    }
    const scene = new THREE.Scene();
    scene.add(new THREE.HemisphereLight(0xffffff, 0x2a3348, 1.5));
    const keyLight = new THREE.DirectionalLight(0xffffff, 2.1);
    keyLight.position.set(2.5, 3.5, 4);
    scene.add(keyLight);
    const fill = new THREE.DirectionalLight(0x8fb6ff, 0.9);
    fill.position.set(-3, -1, -3);
    scene.add(fill);

    let object: THREE.Object3D;
    if (kind === 'cape') {
      const frames = capeFrames(id);
      if (frames.length === 0) throw new Error(`no cape texture for ${id}`);
      const mesh = buildCape(await loadedTexture(frames[0]));
      mesh.rotation.y = Math.PI;
      object = mesh;
    } else {
      object = await loadCosmeticMesh(kind, id);
    }

    const box = new THREE.Box3().setFromObject(object);
    const centre = box.getCenter(new THREE.Vector3());
    const radius = Math.max(box.getSize(new THREE.Vector3()).length() / 2, 0.001);
    object.position.sub(centre);
    object.rotation.y += Math.PI * 0.22;
    scene.add(object);

    const camera = new THREE.PerspectiveCamera(34, 1, 0.05, 100);
    camera.position.set(0, 0, (radius / Math.tan((camera.fov * Math.PI) / 360)) * 1.16);
    camera.lookAt(0, 0, 0);
    stillRenderer.render(scene, camera);
    const png = stillRenderer.domElement.toDataURL('image/png');
    object.traverse((child) => {
      const mesh = child as THREE.Mesh;
      mesh.geometry?.dispose();
      const material = mesh.material as THREE.Material | THREE.Material[] | undefined;
      if (Array.isArray(material)) material.forEach((m) => m.dispose());
      else material?.dispose();
    });
    return png;
  })();
  stills.set(key, job);
  // A failed render must not poison the cache.
  job.catch(() => stills.delete(`${kind}:${id}`));
  return job;
}

/** A face of a player-model box, cut out of the skin at `u,v` with size `w,h` (64x64 units). */
function skinFace(skin: THREE.Texture, u: number, v: number, w: number, h: number, mirror = false): THREE.Mesh {
  const geometry = new THREE.PlaneGeometry(w, h);
  const uv = geometry.attributes.uv as THREE.BufferAttribute;
  for (let i = 0; i < uv.count; i++) {
    const x = mirror ? 1 - uv.getX(i) : uv.getX(i);
    uv.setXY(i, (u + x * w) / 64, 1 - (v + (1 - uv.getY(i)) * h) / 64);
  }
  return new THREE.Mesh(geometry, new THREE.MeshStandardMaterial({ map: skin, roughness: 0.9, metalness: 0 }));
}

/**
 * One box of a Minecraft player model, textured from the skin's standard
 * layout: the six faces wrap around a corner at (u,v).
 */
function skinBox(
  skin: THREE.Texture,
  u: number,
  v: number,
  w: number,
  h: number,
  d: number,
  centre: [number, number, number],
): THREE.Group {
  const box = new THREE.Group();
  const add = (mesh: THREE.Mesh, x: number, y: number, z: number, rx = 0, ry = 0) => {
    mesh.position.set(x, y, z);
    mesh.rotation.set(rx, ry, 0);
    box.add(mesh);
  };
  // Front, back, right, left, top, bottom, in the order the layout packs them.
  // The model's right side is -x (the right arm sits there in ModelPlayer), and
  // the layout's right-hand strip at (u, v+d) is the one that belongs on it.
  add(skinFace(skin, u + d, v + d, w, h), 0, 0, d / 2);
  add(skinFace(skin, u + d + w + d, v + d, w, h), 0, 0, -d / 2, 0, Math.PI);
  add(skinFace(skin, u, v + d, d, h), -w / 2, 0, 0, 0, -Math.PI / 2);
  add(skinFace(skin, u + d + w, v + d, d, h), w / 2, 0, 0, 0, Math.PI / 2);
  add(skinFace(skin, u + d, v, w, d), 0, h / 2, 0, -Math.PI / 2, 0);
  add(skinFace(skin, u + d + w, v, w, d), 0, -h / 2, 0, Math.PI / 2, 0);
  box.position.set(centre[0], centre[1], centre[2]);
  return box;
}

/**
 * The player model for a skin, standing on y = 0 and 32 units tall (the same
 * proportions as the game's ModelPlayer, at 1 pixel = 1 unit), scaled down for
 * a preview. `slim` narrows the arms, which is the only difference between the
 * two skin models.
 */
export function buildPlayer(skin: THREE.Texture, slim: boolean): THREE.Group {
  const player = new THREE.Group();
  const armW = slim ? 3 : 4;
  player.add(skinBox(skin, 0, 0, 8, 8, 8, [0, 28, 0]));
  player.add(skinBox(skin, 16, 16, 8, 12, 4, [0, 18, 0]));
  // Both arm strips start where the 64x64 layout puts them; the slim model only
  // narrows them, which shifts the faces packed along the strip, not the strip.
  player.add(skinBox(skin, 40, 16, armW, 12, 4, [-(4 + armW / 2) + 1, 18, 0]));
  player.add(skinBox(skin, 32, 48, armW, 12, 4, [4 + armW / 2 - 1, 18, 0]));
  player.add(skinBox(skin, 0, 16, 4, 12, 4, [-2, 6, 0]));
  player.add(skinBox(skin, 16, 48, 4, 12, 4, [2, 6, 0]));
  player.scale.setScalar(0.05);
  return player;
}

/** A pale grey skin, so the skin changer has something to show before a file is picked. */
export function placeholderSkin(): THREE.Texture {
  const canvas = document.createElement('canvas');
  canvas.width = 64;
  canvas.height = 64;
  const ctx = canvas.getContext('2d')!;
  ctx.fillStyle = '#cfd3dc';
  ctx.fillRect(0, 0, 64, 64);
  ctx.fillStyle = '#b9bec9';
  ctx.fillRect(8, 8, 8, 8);
  const texture = new THREE.CanvasTexture(canvas);
  texture.magFilter = THREE.NearestFilter;
  texture.minFilter = THREE.NearestFilter;
  texture.colorSpace = THREE.SRGBColorSpace;
  return texture;
}
