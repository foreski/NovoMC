// Launcher accent color. Tailwind's `accent` utilities read the
// --color-accent variable, so changing it on :root recolors the app live.

export const DEFAULT_ACCENT = '#6AA2FF';

export const ACCENT_PRESETS = [
  { name: 'Novo blue', color: DEFAULT_ACCENT },
  { name: 'Violet', color: '#B45BFF' },
  { name: 'Cyan', color: '#22D3EE' },
  { name: 'Emerald', color: '#34D399' },
  { name: 'Rose', color: '#FB7185' },
  { name: 'Amber', color: '#FBBF24' },
];

export function applyAccent(color: string | null) {
  document.documentElement.style.setProperty('--color-accent', color ?? DEFAULT_ACCENT);
}
