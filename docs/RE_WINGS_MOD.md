# Wings Mod 2.0 — static reverse-engineering report

Target: `[1.8.9] Wings Mod-2.0.jar`, sha256 `2b0cbaa7d821a38f079d986939caa385e34ab4560d424efc3fe79586a4a8f185`,
36,222 bytes, 5 classes, 1 asset.

`mcmod.info`: modid `wingsmod`, name "Wings Mod(Modified)", version 2.0, MC 1.8.9, author
`canelex`, URL `youtube.com/canelex`, update URL `planetminecraft.com/member/gustavohe/`.

Method: the REA skill was read, but its MCP server is not registered in this session, so the
`open_binary` / `analyze_function` route was unavailable. This is the equivalent static pass:
unzip, then CFR 0.152 over the five classes, plus the MCP-mapped 1.8.9 classes the mod relies on
(`ModelBox`, `EntityLivingBase`, `RenderPlayer`) to resolve UV and matrix semantics. No runtime
observation: nothing here is claimed as observed execution.

| Class | Bytes | Role |
| --- | --- | --- |
| `WingsMod` | 2,342 | `@Mod` entry: config in pre-init, registers the renderer and command |
| `WingSettings` | 2,132 | Forge `Configuration` wrapper, colour maths |
| `RenderWings` | 4,814 | The whole renderer, `extends ModelBase` |
| `command/CommandEditWings` | 2,186 | Client command `/wingsmod` |
| `gui/GuiEditWings` | 3,723 | Five controls |

No mixins or ASM: `net/canelex/wingsmod/asm/` exists but is empty. Two Forge extension points
only, `RenderPlayerEvent.Post` and `ClientCommandHandler`.

## 1. Geometry — two quads and two spars

`RenderWings` builds one wing tree in its constructor: `setTextureSize(30, 30)`, rotation point
`(-2, 0, 0)`.

| Part | Offset | Box | Meaning |
| --- | --- | --- | --- |
| `wing.bone` | `(0, 0)` | `(-10, -1, -1)` × `10, 2, 2` | the spar, along −X |
| `wing.skin` | `(-10, 8)` | `(-10, 0, 0.5)` × `10, 0, 10` | flat 10×10 quad, **zero height** |
| `wingtip.bone` | `(0, 5)` | `(-10, -0.5, -0.5)` × `10, 1, 1` | tip spar |
| `wingtip.skin` | `(-10, 18)` | `(-10, 0, 0.5)` × `10, 0, 10` | second flat 10×10 quad |

`wingTip` hangs at `(-10, 0, 0)` — the far end of the spar — and is a child of `wing`, so the
whole wing is two hinges and four boxes. Span is 10 + 10 model units = **1.25 blocks per side**,
2.5 blocks tip to tip.

The zero-height boxes are the trick: a box with `h = 0` is legal in `ModelBox` and yields two
coincident quads (its +Y and −Y faces) lying in the XZ plane. The quad is authored horizontally
and only becomes a vertical wing once `rotateAngleX` tilts it (below).

## 2. Atlas and UVs

The image is 300×300 RGBA, greyscale art, painted as four 100 px wing panels in a 2×2 block plus
two grey bone strips across the top. Model texture size is 30×30, so one model unit is 10 px.

From `ModelBox` with `w=10, h=0, d=10, tx=-10, ty=8`:

- +Y face → u `0..10`, v `8..18` → px `(0,80)-(100,180)`: top-left panel
- −Y face → u `10..20`, v `8..18` → px `(100,80)-(200,180)`: top-right panel

and for the tip (`ty=18`): px `(0,180)-(100,280)` and `(100,180)-(200,280)`, the bottom two
panels. Bone strips land at v `0..4` and v `5..7`, matching the light bars in the atlas.

So each face of each quad reads its own column: the inner and outer surfaces get different
painted art, and no texture mirroring is needed anywhere. The art is white-ish and is tinted at
draw time (`glColor3f`), which is what makes the colour and chroma options possible from a
single image.

## 3. Anchor and matrix stack

Fires on `RenderPlayerEvent.Post`, which `RenderPlayer.doRender` posts **after**
`RendererLivingEntity.doRender` has pushed and popped its own matrix — so the body yaw is no
longer on the stack, only the entity's world translation. The mod therefore supplies its own
orientation.

```
s      = settings.scale / 100                      // 0.60 .. 1.40
rotate = lerp(prevRenderYawOffset, renderYawOffset, partialTicks)

push
scale(-s, -s, s)
rotate(180 + rotate, +Y)
translate(0, -(fullHeight ? 1.45 : 1.25) / s, 0)
translate(0, 0, 0.2 / s)
if (player.isSneaking()) translate(0, 0.125 / s, 0)
tint
bind wings.png
for j in 0..1: wing.render(1/16); scale(-1, 1, 1); cullFace switches to GL_FRONT after the first
```

The division by `s` keeps the anchor at a fixed **1.25 blocks up, 0.2 blocks behind** the feet
regardless of the scale slider; with a mod whose Forge id is `animations` loaded the anchor moves
to 1.45, because that mod's player model is taller. Sneaking drops the wings 0.125.

`scale(-s,-s,s)` then `rotate(180°)` composes to `diag(s, -s, -s)`, a proper 180° rotation about
X (determinant `s³`), so it is not a reflection — worth knowing because the loop's
`scale(-1,1,1)` **is** one. That mirror reverses triangle winding, which is why the mod flips
`cullFace` to `GL_FRONT` for the second copy instead of disabling culling: one geometry, two
draws, correct faces on both wings.

`rotate` is a plain `lerp` with `% 360` wrapped into `0..360`, using `prevRenderYawOffset` and
`renderYawOffset` (the vanilla pair, confirmed against the 1.8.9 mapping table).

## 4. Flap — a 1 Hz wall-clock cycle

```
phase = (System.currentTimeMillis() % 1000) / 1000 * 2π

wing.rotateAngleX    = -80° − cos(phase) · 0.2
wing.rotateAngleY    = +20° + sin(phase) · 0.4
wing.rotateAngleZ    =  20°                     (constant)
wingtip.rotateAngleZ = −(sin(phase + 2.0) + 0.5) · 0.75
```

- The −80° X is the fold: the quad is flat in XZ, so this is what stands it up, leaving a 10°
  backward lean.
- The tip is driven **only** by `rotateAngleZ` on its own hinge, lagging the shoulder by 2 rad
  (≈ 32 % of the cycle) — the mod's entire secondary motion, and the reason it reads as feathered
  rather than rigid.
- Wall clock, not tick or animation time: exactly 1 Hz, in sync on both wings, independent of
  frame rate or `/gamerule` tick speed. It also keeps advancing while the game is paused; nothing
  renders, so it simply resumes at a new phase.
- Angles are recomputed inside the two-iteration loop, so both copies share the same values.

## 5. Configuration and UI

`config/wingsmod.cfg`, category `All`: `Enabled` (true), `Colored` (false), `Chroma` (false),
`Hue` (100), `Scale` (100).

- Uncoloured → white tint, art as painted.
- Coloured → `Color.getHSBColor(hue / 100, 0.8, 1.0)`, saturation pinned at 0.8.
- Chroma → ignores `Hue` and feeds the same 1 s wall clock into hue, so the cycle matches the
  flap.

The GUI is five rows: wings on/off, a 60–140 % scale slider, colour on/off, a 0–100 hue slider,
chroma on/off. Hue and chroma are hidden until colour is on. Values are written back on screen
close.

`/wingsmod` is registered as a client command and does not open the GUI directly: it registers
itself as a `ClientTickEvent` handler, opens the screen on the next tick, and unregisters. That
one-tick deferral is the usual workaround for `setScreen` during command execution.

## 6. What it deliberately does not do

- Renders **only the local player** (`player.equals(mc.thePlayer)`), so nobody else ever sees the
  wings. It is a client-side cosmetic with no networking, packets or server component.
- Skips invisible players.
- No per-account or per-server state: the settings are one config file for the installation.
- No cape/skin interaction, no depth sorting beyond the cull-face switch, no LOD.

## 7. Provenance and licence

The jar ships no licence file and `mcmod.info` has no license field, which under default
copyright means all rights reserved. "Deprecated" or "no longer maintained" does not transfer
rights, so `wings.png` and the five class files remain the author's work.

Fair to reuse: the geometry parameters, the transform maths, the timing formula — reimplemented
in our own code. Not fair to reuse: shipping the texture, the class files, or a decompiled copy
of the source. NovoMC does not need the asset in any case; our wings are the OBJ meshes in
`assets/novomc/models/wings/`.

## 8. What is worth taking into NovoMC

| Idea | NovoMC today | Assessment |
| --- | --- | --- |
| Lagging tip hinge | Ported: each side is split at the elbow into two display lists, the outer one trailing the shoulder by 1.5 rad | The strongest idea here, and the one thing taken. Ours cannot copy the amplitude: the reference's tip is its own quad hanging off a spar, so a 2 rad swing tears nothing, while our membrane is one continuous sheet and a hard fold would open a wedge unless the halves overlap at the seam — which they now do |
| White art + tint | Palette baked into each `.mtl`; four separate wings | Would let one mesh be sold in several colours, chroma as a premium item |
| 1 Hz wall-clock phase | `System.nanoTime()`, rates brought down to 0.95–1.30 Hz when the motion above was ported | Wall clock is the part worth having and we already had it; the 1 s cycle is a useful anchor, but a single rate for every pair would cost the per-wing character |
| 60–140 % scale slider | Fixed per mesh | Cheap to add if the wardrobe gets a settings row |
| Anchor 1.25 up, 0.2 back | Hinge at `y 0.10, z 0.05` off the mesh origin, which its header puts between the two attachment points | Independent designs that agree on shoulder-and-behind; ours sits a little lower |
| Local-player-only rendering | Presence sync shows wings to everyone on Novo | Do not copy: ours is a server-side cosmetic and is meant to be visible |

## 9. Unknowns

- No runtime capture was made. Which painted column faces outward follows from the decompiled
  `ModelBox` UV layout and winding, not from watching the mod render.
- The `animations` mod was not inspected, so the 1.45 anchor is reported as authored, not
  verified against that mod's model height.
- The PNG was not checked for atlas padding or edge bleed, which would only matter if the asset
  were reused.
