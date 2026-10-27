import { useEffect, useState } from 'react';
import { api } from '../lib/api';
// Pixel-art identicon generated from the player name: a mirrored 8x8 face-like
// pattern in a per-player hue. Original art, no Mojang skins in the UI.

function hash(s: string): number {
  let h = 2166136261;
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

/** Small seeded PRNG (mulberry32) so the same name always draws the same avatar. */
function rng(seed: number) {
  return () => {
    seed = (seed + 0x6d2b79f5) | 0;
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function cells(name: string) {
  const rand = rng(hash(name));
  const hue = Math.floor(rand() * 360);
  const colors = [`hsl(${hue} 85% 62%)`, `hsl(${(hue + 35) % 360} 80% 52%)`, `hsl(${(hue + 320) % 360} 80% 72%)`];
  const out: { x: number; y: number; fill: string }[] = [];
  for (let y = 0; y < 8; y++) {
    for (let x = 0; x < 4; x++) {
      if (rand() < 0.42) continue;
      const fill = colors[Math.floor(rand() * colors.length)];
      out.push({ x, y, fill }, { x: 7 - x, y, fill });
    }
  }
  return { hue, out };
}

/** Skin heads by player UUID, fetched once per launcher session. */
const heads = new Map<string, Promise<string | null>>();

function useSkinHead(uuid?: string): string | null {
  const [head, setHead] = useState<string | null>(null);
  useEffect(() => {
    setHead(null);
    if (!uuid) return;
    if (!heads.has(uuid)) heads.set(uuid, api.getSkinHead(uuid).catch(() => null));
    let live = true;
    heads.get(uuid)!.then((h) => live && setHead(h));
    return () => {
      live = false;
    };
  }, [uuid]);
  return head;
}

/** The player's real skin face when `uuid` is given (Microsoft accounts), else a generated pixel avatar. */
export function Avatar({ name, size = 40, uuid }: { name: string; size?: number; uuid?: string }) {
  const head = useSkinHead(uuid);
  if (head) {
    return (
      <img
        src={head}
        alt=""
        width={size}
        height={size}
        className="shrink-0 rounded-xl"
        style={{ imageRendering: 'pixelated' }}
      />
    );
  }
  if (!name) {
    return (
      <div
        className="grid shrink-0 place-items-center rounded-xl border border-dashed border-novo-border-strong text-novo-muted"
        style={{ width: size, height: size, fontSize: size * 0.4 }}
      >
        ?
      </div>
    );
  }
  const { hue, out } = cells(name);
  return (
    <svg
      viewBox="-1 -1 10 10"
      width={size}
      height={size}
      shapeRendering="crispEdges"
      className="shrink-0 rounded-xl"
      style={{ background: `linear-gradient(135deg, hsl(${hue} 45% 18%), hsl(${(hue + 40) % 360} 40% 10%))` }}
    >
      {out.map((c) => (
        <rect key={`${c.x}-${c.y}`} x={c.x} y={c.y} width={1} height={1} fill={c.fill} />
      ))}
    </svg>
  );
}

/** Avatar wrapped in a spinning brand-gradient ring (for the active player). */
export function RingAvatar({ name, size = 40, active = true, uuid }: { name: string; size?: number; active?: boolean; uuid?: string }) {
  return (
    <div className={`shrink-0 rounded-[14px] p-[2px] ${active ? 'gradient-border' : 'bg-novo-border'}`}>
      <div className="rounded-xl bg-novo-bg">
        <Avatar name={name} size={size} uuid={uuid} />
      </div>
    </div>
  );
}
