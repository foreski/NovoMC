//! `novo-mc` — the command line for the parts of the rewrite that run today:
//! the server-list ping and a save inspector. It is also the quickest way to
//! check the crates against a real server and a real world without a renderer.

use std::io;
use std::process::ExitCode;
use std::time::Duration;

use novo_anvil::{Chunk, RegionFile, Save, region_file_name};

const USAGE: &str = "\
novo-mc — inspect Minecraft servers and saves

USAGE:
  novo-mc ping <host[:port]> [--json] [--timeout <seconds>]
  novo-mc world <save-dir> info
  novo-mc world <save-dir> find <block>
  novo-mc world <save-dir> top <x> <z>

EXAMPLES:
  novo-mc ping mc.hypixel.net
  novo-mc ping localhost:25565 --timeout 2
  novo-mc world ~/.minecraft/saves/MyWorld info
  novo-mc world ~/.minecraft/saves/MyWorld find minecraft:diamond_ore
  novo-mc world ~/.minecraft/saves/MyWorld top 0 0

`find` on a 1.8 world needs numeric ids: pass legacy:1 for stone.";

fn main() -> ExitCode {
    let args: Vec<String> = std::env::args().skip(1).collect();
    let Some(command) = args.first().map(String::as_str) else {
        println!("{USAGE}");
        return ExitCode::from(2);
    };

    match command {
        "-h" | "--help" | "help" => {
            println!("{USAGE}");
            ExitCode::SUCCESS
        }
        "ping" => match run_ping(&args[1..]) {
            Ok(()) => ExitCode::SUCCESS,
            Err(err) => fail(err),
        },
        "world" => match run_world(&args[1..]) {
            Ok(()) => ExitCode::SUCCESS,
            Err(err) => fail(err),
        },
        other => {
            eprintln!("novo-mc: unknown command `{other}`\n\n{USAGE}");
            ExitCode::from(2)
        }
    }
}

fn fail(err: io::Error) -> ExitCode {
    eprintln!("novo-mc: {err}");
    ExitCode::FAILURE
}

fn flag_value<'a>(args: &'a [String], name: &str) -> Option<&'a str> {
    args.iter()
        .position(|arg| arg == name)
        .and_then(|at| args.get(at + 1))
        .map(String::as_str)
}

fn run_ping(args: &[String]) -> io::Result<()> {
    let positional: Vec<&String> = args.iter().filter(|arg| !arg.starts_with("--")).collect();
    let target = positional
        .first()
        .map(|arg| arg.as_str())
        .ok_or_else(|| io::Error::new(io::ErrorKind::InvalidInput, "ping needs a host"))?;
    let json = args.iter().any(|arg| arg == "--json");
    let timeout = flag_value(args, "--timeout")
        .and_then(|value| value.parse::<f64>().ok())
        .map(Duration::from_secs_f64)
        .unwrap_or(Duration::from_secs(5));

    let (host, port) = novo_proto::status::split_address(target);
    let status = novo_proto::ping(&host, port, timeout)?;

    if json {
        let out = serde_json::json!({
            "host": host,
            "port": port,
            "version": status.version_name,
            "protocol": status.protocol,
            "online": status.players_online,
            "max": status.players_max,
            "sample": status.sample,
            "motd": status.motd,
            "latency_ms": status.latency.map(|d| d.as_secs_f64() * 1000.0),
        });
        println!("{out}");
        return Ok(());
    }

    println!("{host}:{port}");
    println!(
        "  version   {}",
        status.version_name.as_deref().unwrap_or("unknown")
    );
    match status.protocol {
        Some(protocol) => println!("  protocol  {protocol}"),
        None => println!("  protocol  unknown"),
    }
    match (status.players_online, status.players_max) {
        (Some(online), Some(max)) => println!("  players   {online}/{max}"),
        _ => println!("  players   unavailable"),
    }
    if let Some(latency) = status.latency {
        println!("  latency   {:.0} ms", latency.as_secs_f64() * 1000.0);
    }
    if !status.motd.is_empty() {
        println!(
            "  motd      {}",
            status.motd.replace('\n', "\n            ")
        );
    }
    if !status.sample.is_empty() {
        println!("  online    {}", status.sample.join(", "));
    }
    Ok(())
}

fn run_world(args: &[String]) -> io::Result<()> {
    let Some(dir) = args.first() else {
        return Err(io::Error::new(
            io::ErrorKind::InvalidInput,
            "world needs a save directory",
        ));
    };
    let action = args.get(1).map(String::as_str).unwrap_or("info");
    let save = Save::open(dir)?;

    match action {
        "info" => world_info(&save),
        "find" => {
            let block = args.get(2).map(String::as_str).ok_or_else(|| {
                io::Error::new(io::ErrorKind::InvalidInput, "find needs a block name")
            })?;
            world_find(&save, block)
        }
        "top" => {
            let x = parse_coord(args.get(2), "x")?;
            let z = parse_coord(args.get(3), "z")?;
            world_top(&save, x, z)
        }
        other => Err(io::Error::new(
            io::ErrorKind::InvalidInput,
            format!("unknown world action `{other}`"),
        )),
    }
}

fn parse_coord(value: Option<&String>, name: &str) -> io::Result<i32> {
    value
        .ok_or_else(|| io::Error::new(io::ErrorKind::InvalidInput, format!("top needs {name}")))?
        .parse()
        .map_err(|_| {
            io::Error::new(
                io::ErrorKind::InvalidInput,
                format!("{name} must be a number"),
            )
        })
}

fn world_info(save: &Save) -> io::Result<()> {
    let level = &save.level;
    println!("{}", save.dir().display());
    println!(
        "  world      {}",
        if level.level_name.is_empty() {
            "(unnamed)"
        } else {
            &level.level_name
        }
    );
    println!(
        "  version    {}",
        level.version_name.as_deref().unwrap_or("unknown")
    );
    if let Some(data_version) = level.data_version {
        println!("  data ver   {data_version}");
    }
    println!("  mode       {}", level.game_mode());
    println!(
        "  spawn      {}, {}, {}",
        level.spawn.0, level.spawn.1, level.spawn.2
    );
    let regions = save.region_coords()?;
    println!("  regions    {}", regions.len());
    println!("  chunks     {}", save.chunk_count()?);
    Ok(())
}

fn world_find(save: &Save, block: &str) -> io::Result<()> {
    let name = if block.contains(':') {
        block.to_string()
    } else {
        format!("minecraft:{block}")
    };

    let mut total = 0usize;
    let mut shown = 0usize;
    let mut skipped = 0usize;
    let mut saw_legacy = false;

    walk_chunks(save, |chunk| {
        if chunk.format == novo_anvil::ChunkFormat::Legacy {
            saw_legacy = true;
        }
        let hits = chunk.find(&name);
        total += hits.len();
        for (x, y, z) in hits {
            if shown < 20 {
                println!("  {x} {y} {z}");
                shown += 1;
            }
        }
        Ok(true)
    })
    .or_else(|err| {
        // A partial scan is more useful than nothing: report what could not be
        // read and let the caller decide.
        if err.kind() == io::ErrorKind::Unsupported {
            skipped += 1;
            Ok(())
        } else {
            Err(err)
        }
    })?;

    if total == 0 && saw_legacy {
        println!(
            "no {name} found; this world stores numeric block ids (1.8), try legacy:1 for stone"
        );
    } else {
        println!("{total} × {name}");
    }
    if skipped > 0 {
        println!("({skipped} region(s) skipped: unsupported compression)");
    }
    Ok(())
}

fn world_top(save: &Save, x: i32, z: i32) -> io::Result<()> {
    let (chunk_x, chunk_z) = (x >> 4, z >> 4);
    let Some(chunk) = save.chunk(chunk_x, chunk_z)? else {
        println!("no chunk at {x} {z} (chunk {chunk_x} {chunk_z} is not generated)");
        return Ok(());
    };
    let highest = chunk.highest_y().unwrap_or(255);
    for y in (chunk.lowest_y().unwrap_or(0)..=highest).rev() {
        match chunk.block_at(x, y, z) {
            Some("minecraft:air") | Some("minecraft:cave_air") | Some("minecraft:void_air") => {}
            Some(name) => {
                println!("{x} {y} {z} {name}");
                return Ok(());
            }
            None => {}
        }
    }
    println!("no blocks at {x} {z} in the loaded sections");
    Ok(())
}

fn walk_chunks(save: &Save, mut visit: impl FnMut(&Chunk) -> io::Result<bool>) -> io::Result<()> {
    for (region_x, region_z) in save.region_coords()? {
        let path = save
            .dir()
            .join("region")
            .join(region_file_name(region_x << 5, region_z << 5));
        let region = RegionFile::open(&path)?;
        for (local_x, local_z) in region.occupied_slots() {
            let Some(bytes) = region.chunk_nbt_bytes(local_x, local_z)? else {
                continue;
            };
            let (_, tag) = novo_nbt::from_bytes(&bytes)?;
            let chunk_x = region_x * 32 + local_x as i32;
            let chunk_z = region_z * 32 + local_z as i32;
            let chunk = Chunk::parse(&tag, chunk_x, chunk_z)?;
            if !visit(&chunk)? {
                return Ok(());
            }
        }
    }
    Ok(())
}
