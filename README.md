<p align="center">
  <img src="assets/branding/novomc-logo.svg" width="104" alt="Novo Client logo" />
</p>

<h1 align="center">Novo Client</h1>

<p align="center">
  A Minecraft 1.8.9 PvP client and launcher, with Sodium's renderer ported to Forge.<br />
  Designed and built by <a href="https://github.com/foreski">foreski</a> (foreskin).<br />
  <a href="https://github.com/foreski/NovoMC/releases/latest">Download</a> ·
  <a href="#building">Building</a> ·
  <a href="https://github.com/foreski/NovoMC/issues">Issues</a>
</p>

<p align="center">
  <img src="docs/images/launcher-home.png" width="820" alt="The launcher home screen" />
</p>

<p align="center">
  <img src="docs/images/showcase.gif" width="720" alt="A 1.8.9 duel on Minemen" /><br />
  <sub>A real duel on 1.8.9 · <a href="docs/media/novomc-showcase.mp4">watch the full 50 seconds</a></sub>
</p>

## What this is

Novo Client is a Minecraft Java launcher plus its own in-game client mod, aimed at 1.8.9 PvP.

The mod part is roughly two things. First, a chunk renderer ported from
[Sodium](https://github.com/CaffeineMC/sodium) — terrain is baked into shared region buffers and drawn a region
at a time instead of a draw call per chunk, which is where most of the frame rate comes from. Second, a set of
modules: HUD elements, PvP visuals, quality-of-life features, and the OptiFine-style options people expect from
a client (connected textures, custom sky, better grass, fast math). Those are written here from scratch rather
than borrowed, so they run alongside the renderer without arguing with it.

The launcher downloads the game, Forge and Java, keeps instances separate, handles Microsoft sign-in and
starts everything with sensible JVM flags. It's a Tauri app, so it's a normal Windows desktop program with a
web UI on top of Rust doing the actual work.

There's also an early-access build for modern Minecraft (26.3 on Fabric) that shares the same settings format
and HUD layout as the 1.8.9 client.

## Download

Grab `NovoMC_x.y.z_x64-setup.exe` from
[releases](https://github.com/foreski/NovoMC/releases/latest) and run it.

Windows SmartScreen will probably complain, because the installer isn't code-signed yet — that's a certificate
costing a few hundred a year, not something suspicious. "More info" → "Run anyway" gets you past it. If that
makes you uncomfortable, the whole thing builds from source; see [Building](#building).

Windows 10 or 11, 64-bit. Java is downloaded for you.

## Around the game

- One Play button. Instances on Home, each with its own worlds, mods, packs, memory and Java, and a duplicate
  button when you want one.
- Every Minecraft version, pulled from Mojang and verified file by file.
- Microsoft accounts for online servers and Realms. Tokens go in Windows Credential Manager, not into a JSON
  file in `%APPDATA%`.
- Quick-join: pin your servers to Home with live player counts and ping, then land straight in them.
- Discord Rich Presence, a live console with crash reports, accent colours, and the UI in English, German,
  Spanish, French and Brazilian Portuguese.
- Signed self-updates, so you don't have to reinstall by hand.
- A resource pack browser in game: about 2,700 Modrinth packs that work on 1.8.x, searchable and sortable,
  installed with one click and switched on immediately.

<p align="center">
  <img src="docs/images/ingame-menu.png" width="49%" alt="The in-game main menu" />
  <img src="docs/images/ingame-packs.png" width="49%" alt="The in-game resource pack browser" />
</p>

## In game

About 80 modules, all toggleable, all with settings. Right Shift opens the HUD editor — drag things where you
want them, scroll to resize, right-click to reset — and the same key gets you the module list. Nothing overlaps
by default; untouched elements settle into a left and right column.

| Category | What's in it |
| :-- | :-- |
| HUD | FPS, CPS, keystrokes, armour status, potion effects, coordinates, direction, ping, clock, memory, combo counter, reach, waypoints |
| PvP visuals | 1.7 animations, hit colour, hurt cam, block outline, hitboxes, item physics, 2D items, motion blur, menu blur, dynamic lights |
| Quality of life | freelook, snaplook, zoom, toggle sprint/sneak, nick hider, custom scoreboard and tab list, chat timestamps, pack display, screenshot manager, Hypixel quickplay and requeue |
| Rendering | Sodium renderer, fast math, smart animations, lazy chunk loading, connected textures, custom sky, better grass and snow, entity and particle culling, uncapped frame rate |

A few things worth calling out, because they're less common in 1.8.9 clients:

**Mode profiles.** Novo reads the scoreboard and your kit to work out whether you're in Sword, Pot PvP, UHC,
Bed Wars, SkyWars or The Bridge, and keeps a separate setup per mode — HUD layout, enabled modules, their
settings and your keybinds. Change something mid-game and that mode remembers it. The modern client does the
same for Crystal, Mace, Spear, Nethpot, UHC, Axe and Sword.

**Trainers.** Four drills in a private flat world, with personal bests: click speed, aim and combo (30 seconds
against a strafing target), speedbridge (30 blocks over void) and pot refill. The modern client has crystal
speed, mace dive and spear charge.

**OptiFine options without OptiFine.** Connected textures generate 47 glass border tiles from whatever pack
you're using, plus `optifine/ctm` and `mcpatcher/ctm` properties if the pack ships them. Custom sky reads
`optifine/sky`. Fast math is a 4096-entry sine table. All of it is a module, so you can turn any of it off.

<p align="center">
  <img src="docs/images/ingame-video.png" width="49%" alt="Video settings with the Sodium renderer options" />
  <img src="docs/images/ingame-mods.png" width="49%" alt="The in-game mod list" />
</p>

## Performance

The repo has a benchmark that builds a lobby-shaped stress scene — 400 armour stands, ~1,900 block entities,
constant particles — and measures the same viewpoint three ways: performance modules off, on, and on with the
HUD hidden.

Last run on the dev machine: **126.5 FPS off, 259.1 FPS on**. The entire HUD costs about 3.5%. Those are one
machine's numbers on one scene, so treat them as a before-and-after rather than a promise about your PC. Every
optimisation is a module and can be switched off if you'd rather have vanilla.

## Fair play

Novo is a visual and quality-of-life client, in the same category as the well-known ones. Nothing automates
input and nothing reveals information the vanilla game hides.

The one thing to know: freelook really detaches the camera, and unlike some clients there's no hook for a
server to switch it off remotely, because Novo doesn't run a custom protocol on servers. So it's on you to
check whether the server you're playing allows freelook. Same goes for any HUD element a tournament ruleset
bans.

## Building

```bash
npm install
npm run mod          # builds the 1.8.9 and modern client jars
npm run tauri dev    # runs the launcher
```

`npm run tauri build` produces the NSIS installer. Building the mod needs JDK 8 for 1.8.9 and JDK 17+ for
Gradle; the launcher needs Rust with the MSVC toolchain, and the WebView2 runtime that ships with Windows 11.

## Notes

The Sodium renderer under `client-mod/src/main/java/com/novomc/client/sodium` is ported from Sodium 0.5 by
JellySquid and the CaffeineMC contributors, and stays under the
[GNU LGPL v3](client-mod/src/main/resources/com/novomc/client/sodium/LICENSE-SODIUM.txt). The OptiFine-style
features are inspired by [OptiFine](https://optifine.net) but contain none of its code. Novo Client isn't
affiliated with either project, or with Mojang or Microsoft.

Everything else here — the launcher, the 1.8.9 client, the modern client, the relay — is designed and written
by [foreski](https://github.com/foreski) (foreskin).

The wing meshes, their materials and their textures sit in
`client-mod/src/main/resources/assets/novomc/models/wings`.

## License

MIT — see [LICENSE](LICENSE). The one exception is the Sodium renderer port under
`client-mod/src/main/java/com/novomc/client/sodium` and its shaders, which stays under the GNU LGPL v3 and
carries its own license text next to the code.

---

<p align="center">
  <sub>Novo Client is not an official Minecraft product and is not approved by or associated with Mojang or
  Microsoft. Minecraft is a trademark of Mojang AB. Resource packs belong to their authors and come from
  Modrinth.</sub>
</p>
