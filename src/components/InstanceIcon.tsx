import { Box } from 'lucide-react';

function hue(name: string): number {
  let h = 0;
  for (let i = 0; i < name.length; i++) h = (h * 31 + name.charCodeAt(i)) >>> 0;
  return h % 360;
}

/** Glossy cube tile in a per-instance hue. */
export function InstanceIcon({ name, size = 48 }: { name: string; size?: number }) {
  const h = hue(name);
  return (
    <div
      className="relative grid shrink-0 place-items-center overflow-hidden rounded-2xl ring-1 ring-white/15"
      style={{
        width: size,
        height: size,
        background: `linear-gradient(135deg, hsl(${h} 80% 60%), hsl(${(h + 50) % 360} 75% 42%))`,
        boxShadow: `0 8px 24px -10px hsl(${h} 80% 55%)`,
      }}
    >
      <span className="absolute inset-x-0 top-0 h-1/2 bg-white/15" />
      <Box size={size * 0.5} className="relative text-white drop-shadow" strokeWidth={1.8} />
    </div>
  );
}
