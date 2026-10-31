# NovoMC in Rust

The start of a native client for Minecraft: Java Edition — read the format
your launcher already manages, then build a client on top of it instead of
patching the Java one.

Read this before expecting a game: **nothing here renders, simulates or plays
anything yet.** What runs today is the layer a client has to stand on: the wire
format, the save format, and the packet framing, each with tests that run
against fixtures and, for the ping, against real servers.

For scale: [Horizon](https://github.com/horizon-rust/horizon) is the reference
project for this idea, a 26.3 client in Rust. Its own ledger still lists 833
player-visible gaps. This repository is nowhere near that — it is the first
four crates, written to be extended rather than demoed.

## What works

```console
$ cargo run -q -p novo-mc -- ping mc.hypixel.net
mc.hypixel.net:25565
  version   Requires MC 1.8 / 1.21
  protocol  47
  players   21836/200000
  latency   51 ms
  motd      §f                 §aHypixel Network §c[1.8/26.3]
```

That is a real status exchange: handshake with next-state 1, status request,
then a ping/pong that gives the latency. The same code talks to 1.8.9 and to
26.3, because that exchange has not changed since 1.7.

```console
$ cargo run -q -p novo-mc -- world ~/.minecraft/saves/World info
$ cargo run -q -p novo-mc -- world ~/.minecraft/saves/World find minecraft:diamond_ore
$ cargo run -q -p novo-mc -- world ~/.minecraft/saves/World top 0 0
```

`world` reads `level.dat` and the region files the Java game wrote: name,
version, data version, game mode, spawn, how many chunks exist, where a block
is, and the highest block in a column.

## Crates

| Crate | What it does | Tests |
| --- | --- | --- |
| `novo-core` | VarInt/VarLong, length-prefixed strings, 26+26+12 position packing, angles | 10 |
| `novo-nbt` | NBT read/write, all twelve tag types, gzip and zlib, named and unnamed roots | 7 |
| `novo-anvil` | `level.dat`, `.mca` region files, chunk sections, the three chunk layouts | 15 |
| `novo-proto` | Packet framing and the server-list ping | 10 |
| `novo-mc` | The command line over the four above | — |

42 tests in total, under a second.

```console
$ cargo test --workspace
$ cargo clippy --workspace --all-targets
```

No warnings, no clippy lints, no unsafe code in any crate.

## The chunk layouts it accepts

A save written years ago and played forward contains all of these at once, so
the reader takes them per section:

- **1.8** — `Level.Sections[].Blocks`: one byte per block, a numeric id. Reads
  as `legacy:<id>`, so `find legacy:1` is stone. The `Add` nibble array (ids
  above 255 from mods) is not applied, which is why ids are reported rather
  than guessed names.
- **1.13 – 1.17** — `Palette` + `BlockStates`, where a value can straddle two
  longs. That packing is implemented, not assumed away.
- **1.18+** — `block_states.palette` + `block_states.data`, padded so a value
  never crosses a long, including the one-entry palette with no data array.

Chunks compressed with LZ4 (1.20.5+) are detected and reported as unsupported
rather than misread.

## What is deliberately not here

- **Rendering.** A window needs `wgpu`, a camera, entity and block models, and
  the resource pack pipeline. That is the largest single piece and none of it
  exists.
- **Login.** Encryption and the Microsoft account flow are skipped; only the
  status state of the protocol is implemented. Play-state packets are next, and
  they are a lot of them.
- **Writing worlds.** Reading only. No region writer, no chunk encoder.
- **Simulation.** Ticks, entities, physics, lighting — none.
- **1.8-specific behaviour.** The target is the current format (26.3), with the
  old formats read for compatibility. A 1.8.9 protocol client would be a
  different, smaller job: 1.8 has no palettes and a much shorter packet list.

## Next, in the order that keeps it honest

1. **A play-session skeleton** — connection, compression threshold, login
   start, offline-mode join, then chunk data packets into the same `Chunk`
   type the save reader produces. At that point `world` and a live server feed
   one representation, which is the thing worth getting right early.
2. **A region writer** — finish the round trip: read a chunk, write it back
   byte-identically. That is also the cheapest regression test for the chunk
   code.
3. **A window** — `winit` + `wgpu`, one chunk rendered as a wireframe box, then
   real block models from a resource pack.
4. **Direction-independent physics** — player movement against the collision
   shapes the save reader already exposes.

Roadmap for the parity work, if it ever gets far enough to need one: the honest
model is Horizon's — a ledger of vanilla behaviours with a harness per row, so
"does it match" is a test result and not an opinion.
