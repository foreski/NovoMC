# NovoMC branding

Taken from the old NovoLauncher project (branding only, no code).

## Files

| File | Source | Use |
| --- | --- | --- |
| `novo.ico` | `NovoLauncher/core/resources/win/novo.ico` | Original multi-size Windows icon (16–256 px) |
| `novomc-logo.svg` | Redrawn from the NovoLauncher brand mark / icon design | Master logo; `npm run icons` builds every app icon from it |

## Logo

A circular orb with a conic gradient `#5b8cff → #b45bff → #22d3ee → #5b8cff`,
a soft top-left highlight, and a bold white "N" with a subtle shadow.

## Palette

| Token | Value | Role |
| --- | --- | --- |
| `novo-bg` | `#0b0e14` | App background |
| `novo-elev` | `rgba(255,255,255,0.035)` | Raised surface |
| `novo-elev-2` | `rgba(255,255,255,0.06)` | Raised surface, stronger |
| `novo-hover` | `rgba(255,255,255,0.08)` | Hover |
| `novo-selected` | `rgba(120,170,255,0.16)` | Selected item |
| `novo-border` | `rgba(255,255,255,0.08)` | Border |
| `novo-border-strong` | `rgba(255,255,255,0.16)` | Border, strong |
| `novo-text` | `#e8ecf3` | Text |
| `novo-muted` | `#8a94a6` | Secondary text |
| `brand-a` | `#5b8cff` | Brand blue |
| `brand-b` | `#b45bff` | Brand purple |
| `brand-c` | `#22d3ee` | Brand cyan |
| `accent` | `#6aa2ff` | Default accent |
| `play` / `play-dark` | `#4ade80` / `#16a34a` | Play button |
| `danger` | `#f87171` | Errors |
| Close-button hover | `#c42b1c` | Window close |

Radius: 10px (6px small).

## Fonts

NovoLauncher shipped no font files. It used Windows system fonts, and NovoMC does the same:

- UI: `Segoe UI Variable Text`, `Segoe UI`, `system-ui`
- Monospace (logs): `Cascadia Mono`, `Consolas`
