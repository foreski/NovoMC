// A spinning 3D preview of one cosmetic or one skin, on its own canvas.
//
// Every cape, hat and wing in the store gets one, so the launcher shows the
// item itself instead of an icon. Canvases only exist while they are on screen:
// browsers keep a handful of WebGL contexts alive at most, and a grid of twenty
// cards would otherwise drop the ones scrolled off first. The context is
// therefore created and disposed with visibility.
import { useEffect, useRef, useState } from 'react';
import * as THREE from 'three';
import {
  buildCape,
  buildPlayer,
  capeFrames,
  loadCosmeticMesh,
  loadTexture,
  placeholderSkin,
} from '../lib/models3d';

export type StageItem =
  | { type: 'cape'; id: string; animated?: { frames: number; fps: number } | null }
  | { type: 'wings'; id: string }
  | { type: 'hat'; id: string }
  | { type: 'skin'; skinUrl: string | null; slim?: boolean };

/** Radians per second of the idle rotation: slow enough to read the shape. */
const SPIN = 0.5;

/**
 * Live canvases, oldest first, shared by every stage on the page.
 *
 * A browser keeps about sixteen WebGL contexts and silently drops the oldest
 * beyond that, which shows up as cards that never draw. A store page has far
 * more items than that, so the ones that come into view take a context and the
 * least recently seen ones give theirs up.
 */
const LIVE: { drop: () => void }[] = [];
const MAX_LIVE = 10;

function acquire(drop: () => void) {
  while (LIVE.length >= MAX_LIVE) LIVE.shift()?.drop();
  LIVE.push({ drop });
}

function release(drop: () => void) {
  const at = LIVE.findIndex((entry) => entry.drop === drop);
  if (at >= 0) LIVE.splice(at, 1);
}

export function ModelStage({
  item,
  className = '',
  fallback = null,
  interactive = false,
}: {
  item: StageItem;
  className?: string;
  fallback?: React.ReactNode;
  /** Drag to turn it; the idle spin carries on when released. */
  interactive?: boolean;
}) {
  const host = useRef<HTMLDivElement>(null);
  const [failed, setFailed] = useState(false);
  const drag = useRef<{ active: boolean; startX: number; base: number }>({ active: false, startX: 0, base: 0 });
  const offset = useRef(0.6);

  useEffect(() => {
    const element = host.current;
    if (!element) return;
    let renderer: THREE.WebGLRenderer | null = null;
    let disposed = false;
    let frame = 0;
    let visible = false;
    let idle = 0;
    let object: THREE.Object3D | null = null;
    const scene = new THREE.Scene();
    const camera = new THREE.PerspectiveCamera(34, 1, 0.05, 100);

    scene.add(new THREE.HemisphereLight(0xffffff, 0x2a3348, 1.5));
    const key = new THREE.DirectionalLight(0xffffff, 2.1);
    key.position.set(2.5, 3.5, 4);
    scene.add(key);
    const fill = new THREE.DirectionalLight(0x8fb6ff, 0.9);
    fill.position.set(-3, -1, -3);
    scene.add(fill);

    /** The radius the camera has to clear: the item turns inside it, so it never clips. */
    let radius = 1;

    /**
     * Pulls the camera back far enough for the whole piece to sit inside the
     * frame, on the narrow axis whatever shape the box is. Fit to the bounding
     * sphere rather than the box, because the item spins and the box does not.
     */
    const pullBack = () => {
      const halfFov = Math.tan((camera.fov * Math.PI) / 360);
      const aspect = Math.max(camera.aspect || 1, 0.05);
      const distance = Math.max(radius / halfFov, radius / (halfFov * aspect)) * 1.16;
      camera.position.set(0, 0, distance);
      camera.lookAt(0, 0, 0);
    };

    /** Centres the object on the origin and frames it. */
    const fit = (target: THREE.Object3D) => {
      const box = new THREE.Box3().setFromObject(target);
      const size = box.getSize(new THREE.Vector3());
      target.position.sub(box.getCenter(new THREE.Vector3()));
      radius = Math.max(size.x, size.y, size.z) * 0.5 || 1;
      pullBack();
      return target;
    };

    const resize = () => {
      if (!renderer) return;
      const width = element.clientWidth;
      const height = element.clientHeight;
      if (width < 2 || height < 2) return;
      renderer.setSize(width, height, false);
      camera.aspect = width / height;
      camera.updateProjectionMatrix();
      pullBack();
    };

    // A canvas exists only while this preview is on screen. Ten idle cards
    // would otherwise hold ten WebGL contexts between them and the grid would
    // start losing the ones that are actually visible.
    const attach = () => {
      if (renderer || disposed) return;
      try {
        renderer = new THREE.WebGLRenderer({ alpha: true, antialias: true, powerPreference: 'low-power' });
      } catch {
        setFailed(true);
        return;
      }
      acquire(detach);
      renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
      renderer.setClearColor(0x000000, 0);
      renderer.outputColorSpace = THREE.SRGBColorSpace;
      element.appendChild(renderer.domElement);
      renderer.domElement.className = 'h-full w-full';
      resize();
    };
    const detach = () => {
      if (!renderer) return;
      release(detach);
      renderer.dispose();
      renderer.domElement.remove();
      renderer.forceContextLoss();
      renderer = null;
    };

    const observer = new ResizeObserver(resize);
    observer.observe(element);
    // The context goes as soon as the card leaves: a store page has dozens of
    // items and a browser hands out only so many WebGL contexts.
    const visibility = new IntersectionObserver(
      ([entry]) => {
        visible = entry.isIntersecting;
        window.clearTimeout(idle);
        if (visible) attach();
        else idle = window.setTimeout(detach, 150);
      },
      { rootMargin: '40px' },
    );
    visibility.observe(element);

    let capeTimer = 0;
    (async () => {
      try {
        if (item.type === 'wings' || item.type === 'hat') {
          object = fit(await loadCosmeticMesh(item.type, item.id));
        } else if (item.type === 'cape') {
          const urls = capeFrames(item.id);
          if (urls.length === 0) throw new Error('missing cape texture');
          const textures = urls.map(loadTexture);
          const mesh = buildCape(textures[0]);
          const material = mesh.material as THREE.MeshStandardMaterial;
          mesh.rotation.y = Math.PI;
          object = fit(mesh);
          // Animated capes play on their own clock, at the frames the game uses.
          if (textures.length > 1 && item.animated) {
            let current = 0;
            capeTimer = window.setInterval(() => {
              current = (current + 1) % textures.length;
              material.map = textures[current];
              material.needsUpdate = true;
            }, 1000 / item.animated.fps);
          }
        } else {
          const skin = item.skinUrl ? loadTexture(item.skinUrl) : placeholderSkin();
          object = fit(buildPlayer(skin, item.slim ?? false));
        }
        if (disposed) return;
        scene.add(object);
      } catch {
        if (!disposed) setFailed(true);
      }
    })();

    const clock = new THREE.Clock();
    const loop = () => {
      frame = requestAnimationFrame(loop);
      const delta = clock.getDelta();
      if (!visible || !object || !renderer) return;
      if (!drag.current.active) offset.current += delta * SPIN;
      object.rotation.y = offset.current;
      renderer.render(scene, camera);
    };
    loop();

    return () => {
      disposed = true;
      cancelAnimationFrame(frame);
      window.clearInterval(capeTimer);
      window.clearTimeout(idle);
      observer.disconnect();
      visibility.disconnect();
      scene.traverse((child) => {
        const mesh = child as THREE.Mesh;
        if (mesh.geometry) mesh.geometry.dispose();
        const material = mesh.material as THREE.Material | THREE.Material[] | undefined;
        if (Array.isArray(material)) material.forEach((m) => m.dispose());
        else material?.dispose();
      });
      detach();
    };
  }, [item]);

  const onPointerDown = (event: React.PointerEvent) => {
    if (!interactive) return;
    drag.current = { active: true, startX: event.clientX, base: offset.current };
    (event.target as HTMLElement).setPointerCapture?.(event.pointerId);
  };
  const onPointerMove = (event: React.PointerEvent) => {
    if (!drag.current.active) return;
    offset.current = drag.current.base + (event.clientX - drag.current.startX) * 0.012;
  };
  const onPointerUp = () => {
    drag.current.active = false;
  };

  return (
    <div
      ref={host}
      // Callers position this box (`absolute inset-0` in a card); the default
      // keeps it in flow when none is given.
      className={`${className || 'relative'} ${interactive ? 'cursor-grab active:cursor-grabbing' : ''}`}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onPointerLeave={onPointerUp}
    >
      {failed && fallback}
    </div>
  );
}
