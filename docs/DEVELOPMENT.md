# NovoMC: development guide

A Minecraft Java Edition launcher built with Tauri 2 (Rust) + React + TypeScript + Vite + Tailwind.
All game logic lives in Rust. The UI only calls Tauri commands and listens for events.

## Requirements (Windows)

- Node.js 20+
- Rust (stable, MSVC), installed with rustup
- Visual Studio Build Tools with "Desktop development with C++"
- WebView2, which is built into Windows 11
- JDK 17+ (runs Gradle) and JDK 8 (compiles the mod) to build the NovoMC Client mod, e.g. Eclipse Temurin

## Run

```bash
npm install
npm run mod
npm run tauri dev
```

`npm run mod` builds the NovoMC Client Forge mod (`client-mod/`) and copies it to
`src-tauri/resources/novomc-client.jar`, which the launcher bundles. Run it again after changing the mod.

Build the installer (NSIS, written to `src-tauri/target/release/bundle/nsis/`):

```bash
npm run tauri build
```

Regenerate the app icons after changing the logo:

```bash
npm run icons
```

## Configuration

Copy `novomc.config.example.json` to `novomc.config.json` (git-ignored). `msaClientId` is the OAuth client ID
used for Microsoft sign-in. It is set to the official Minecraft Launcher's ID (`00000000402b5328`), which
Microsoft and Mojang don't license to third-party launchers and could block at any time; swap in your own
Azure app ID once Mojang approves it. The file is bundled into the installer, and a copy in
`%APPDATA%\NovoMC` overrides it.

Launcher data is stored in `%APPDATA%\NovoMC`.

## Project structure

```text
NovoMC/
├─ assets/branding/          logo, icon, palette notes (BRANDING.md)
├─ client-mod/               NovoMC Client: Forge 1.8.9 mod (Java 8, Mixins, Gradle)
│  └─ src/main/resources/assets/novomc/catalog.json   module list, shared with the Mod Library
├─ scripts/build-mod.mjs     `npm run mod`
├─ src/                      React UI
│  ├─ components/            TitleBar, Sidebar, …
│  ├─ pages/                 Home, Instances, Mods, Settings, Accounts
│  └─ lib/api.ts             typed wrappers around the Tauri commands
├─ src-tauri/
│  ├─ tauri.conf.json        app id com.novomc.app, frameless window, NSIS bundle
│  ├─ capabilities/          window permissions for the UI
│  └─ src/
│     ├─ lib.rs              command registration
│     ├─ error.rs            AppError / AppResult
│     ├─ paths.rs            %APPDATA%\NovoMC layout
│     ├─ auth/               MS OAuth → XBL → XSTS → MC token, keyring
│     ├─ download/           manifest, jars, libraries, assets, SHA1, parallel
│     ├─ launch/             launch arguments, process, logs, crash detection
│     ├─ instances/          instance CRUD
│     └─ java/               Java detection and runtime download
└─ novomc.config.example.json
```

## Roadmap

1. **Scaffold** ✅: frameless window, title bar, sidebar, IPC smoke test, data folder
2. **Auth** ✅: offline accounts; Microsoft sign-in in its own window, then Xbox Live, XSTS, Minecraft token and profile (ownership check). Refresh tokens live in Windows Credential Manager; Minecraft tokens refresh automatically at launch
3. **Version download** ✅: piston-meta manifest, client/libraries/natives/assets, SHA1 checks, parallel downloads with retries, progress events
4. **Launch vanilla** ✅: Java detection and auto-download (8/17/21), argument builder (both formats), log streaming, crash reports, close/minimize on launch, tuned performance JVM flags
5. **Instances** ✅: per-instance folders (`instances/<id>/minecraft`), version, memory, Java and JVM args; create, duplicate, delete (to Recycle Bin), rename, open folder; versions download on first play
6. **Loaders**: Forge for 1.8.9 ✅ (legacy installer, installed on first Play, libraries verified); Fabric for modern versions later
7. **NovoMC Client mod + Mod Library** ✅ batches 1 and 2, plus the NovoMC main menu (see below)
8. **UI polish** ✅: game window size and fullscreen, game folder card, news cards on Home (NovoMC news from
   `src-tauri/src/news.json` plus the latest Minecraft release/snapshot, with generated artwork), theme accent
   (presets or custom, applied live), and Minecraft's game language (written to each instance's
   `options.txt` at launch).
9. **Translations** ✅: launcher UI in English, Deutsch, Español, Français and Português (Brasil), following
   the system language by default; pick one under Settings → Language (switches live). Strings live in
   `src/locales/*.ts`; `en.ts` is the source and every other locale is typed against it, so `npm run build`
   fails if a translation is missing a key. To add a language: copy `de.ts`, translate it, and register it in
   `LANGUAGES`/`DICTIONARIES` in `src/lib/i18n.tsx` and in the allow-list in `src-tauri/src/settings.rs`.
   Dates and "3 hours ago" use `Intl`, so they follow the chosen language too. Mod names and descriptions in
   the Mod Library stay English (they come from the shared `catalog.json`).

Phase 2: Modrinth mod browser, resource/shader packs, Tauri updater, Discord Rich Presence.

## NovoMC Client mods

NovoMC is a performance PvP client. Its mods are NovoMC's own code, with features modeled on
Lunar Client's mod list but none of its code or assets.

- **Target:** Minecraft 1.8.9 on Forge (Java 8, Mixin 0.7 via its tweaker), built with Gradle 8 and
  Essential's architectury-loom fork.
- **Shape:** one `novomc-client` jar containing every module. The launcher bundles it and copies it into
  Forge 1.8.9 instances before each launch (per-instance switch in the Mod Library).
- **Catalog:** `catalog.json` defines each module's name, defaults and settings (toggle, color, number,
  choice, text). The mod reads it for defaults; the Mod Library builds its UI from the same file. HUD
  entries can stack in a column (`stack`) and be positioned by their center (`center`).
- **Mixins** are strict (`defaultRequire: 1`): if a hook can't find its target in Minecraft, the game
  stops at startup instead of silently losing the feature.
- **Mod Library page:** enable or disable each module and change its settings. These are stored in the
  instance's `config/novomc/modules.json`, which the mod reloads within a second of a change.
- **Main menu:** the NovoMC title screen (blurred panorama, logo, glass buttons, quick icons, and a card
  that opens the mods list) replaces vanilla's; the "NovoMC Menu" module switches it off. Its icons are
  Lucide icons (ISC) rendered by `node scripts/mod-icons.mjs`.
- **In game:** Right Shift opens the HUD editor (drag to move, scroll to resize, right-click to reset) and,
  from there, an on/off list of all modules. Untouched HUD modules stack in a left or right column so they
  never overlap. Freelook is Left Alt and Snaplook is unbound by default (Controls > NovoMC).
- **Dev smoke test:** launching with `-Dnovomc.autotest=true` screenshots the main menu, creates a flat
  world, turns everything on, summons mobs and items, attacks once, then screenshots the HUD, an
  inventory (menu blur), the HUD editor and the mods list, and quits.
- **Fair play:** visual and quality-of-life only, like Lunar. Nothing automates input or reveals
  information vanilla hides. Lunar lets servers turn Freelook off through its own server protocol; NovoMC
  has no such protocol, so check a server's rules before using Freelook there.
- **No third-party performance mods.** FPS gains come from NovoMC's own 1.8.9 optimization modules
  plus the tuned JVM flags.
- **Resource Packs library:** in-game browser for every Modrinth resource pack that supports 1.8.x
  (about 2,700), with search, sorting (popular / relevance / newest / updated), category chips and an
  Installed tab. Install downloads the pack from Modrinth's CDN (SHA-1 checked) into `resourcepacks/`
  and turns it on at the top; packs can be switched on and off there too. Open it from the main menu's
  layers icon, the HUD editor's Packs button, or the Mods / Resource Packs tabs. Pack icons (mostly
  WebP) are decoded with the bundled TwelveMonkeys ImageIO WebP plugin (BSD-3).
- **FPS benchmark:** `-Dnovomc.benchmark=true` (or `NOVOMC_TEST_BENCH=1` with the `launch_real` test)
  builds a lobby-like stress scene (400 armor stands, ~1,900 block entities, constant particles) and
  measures the same view with the performance mods off, on, and on with the HUD hidden. Last run:
  **126.5 → 259.1 FPS (+105%)**; the whole HUD costs about 3.5%.

| Batch | Modules |
| --- | --- |
| 1: Core HUD, QoL, performance ✅ | FPS, CPS, Keystrokes, Coordinates, Direction HUD, Armor Status, Potion Effects, Ping, Memory Usage, Clock, Saturation, Item Counter, Toggle Sprint/Sneak, Zoom, Fullbright, Crosshair; performance: Entity Culling, Particle Limiter (Mixin), Unfocused FPS Limit, Performance Tweaks (VBOs, no entity shadows) |
| 1b: More performance ✅ | Entity Culling covers every entity (items, armor stands, mobs) and block entities (chests, signs, skulls, banners) via Mixins; Particle Limiter skips far particles; Performance Tweaks adds uncapped FPS / VSync off, no clouds and optional fast graphics |
| 2: PvP visuals ✅ | 1.7 Visuals (block-hit swing, red armor), Hit Color, Block Outline, Hurt Cam, FOV Changer, Particle Changer, Time Changer, Weather Changer, Fog Customizer, Glint Colorizer, Motion Blur, Menu Blur, 2D Items, Item Physics, Hitbox, Chunk Borders, Nametags, Freelook, Snaplook, Combo Counter, Reach Display. (Shiny Pots skipped: 1.8.9 potions already glint.) |
| 3: UI and info ✅ | Scoreboard (movable, hide red numbers), Chat (timestamps, stacking), Titles (scale/hide), Boss Bar (movable), Tab Editor (ping numbers, background, header/footer), Nick Hider (text setting), Pack Display, Server Address, Playtime, Stopwatch (unbound key; Shift resets), Day Counter, TNT Countdown, Scrollable Tooltips, Cooldowns (durations are settings; 1.8.9 itself has none) |
| Later | Waypoints, WAILA, Auto Text Hotkey, Hypixel mods (Quickplay, Bedwars, levels), UHC Overlay, Item Tracker, Pack Organizer |
| Out of scope for now | Replay Mod, 3D Skins, Screenshot Uploader, Mumble Link, WorldEdit CUI, Team View |
