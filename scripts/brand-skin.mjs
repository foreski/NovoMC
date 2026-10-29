// Brands a Minecraft skin with the Novo client mark.
//
//   node scripts/brand-skin.mjs <input.png> <output-prefix> [--dump]
//
// Writes <prefix>_64.png (uploadable to Mojang) and <prefix>_hd.png (4x, for
// previews and clients that accept HD skins), plus <prefix>_preview.png.
//
// A 64x64 skin gives the torso exactly 8x12 pixels, so the full domain cannot
// be legible there; the 64 version carries a stacked NOVO mark instead. The HD
// version has room for "novoclient" / ".cc" in a 5x7 pixel font.
import { readFileSync, writeFileSync } from 'node:fs';
import { deflateSync, inflateSync } from 'node:zlib';

const SIG = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);

// --- PNG ------------------------------------------------------------------

const CRC_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    table[n] = c >>> 0;
  }
  return table;
})();

function crc32(buf) {
  let c = 0xffffffff;
  for (const byte of buf) c = CRC_TABLE[(c ^ byte) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const out = Buffer.alloc(12 + data.length);
  out.writeUInt32BE(data.length, 0);
  out.write(type, 4, 'ascii');
  data.copy(out, 8);
  out.writeUInt32BE(crc32(Buffer.concat([Buffer.from(type, 'ascii'), data])), 8 + data.length);
  return out;
}

function decodePng(buf) {
  let offset = 8;
  let width = 0;
  let height = 0;
  let colorType = 0;
  let palette = null;
  let transparency = null;
  const idat = [];
  while (offset < buf.length) {
    const length = buf.readUInt32BE(offset);
    const type = buf.toString('ascii', offset + 4, offset + 8);
    const data = buf.subarray(offset + 8, offset + 8 + length);
    if (type === 'IHDR') {
      width = data.readUInt32BE(0);
      height = data.readUInt32BE(4);
      if (data[8] !== 8) throw new Error('only 8-bit skins are supported');
      if (data[12] !== 0) throw new Error('interlaced PNGs are not supported');
      colorType = data[9];
    } else if (type === 'PLTE') palette = data;
    else if (type === 'tRNS') transparency = data;
    else if (type === 'IDAT') idat.push(data);
    else if (type === 'IEND') break;
    offset += 12 + length;
  }
  const bpp = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[colorType];
  if (!bpp) throw new Error(`unsupported colour type ${colorType}`);
  const raw = inflateSync(Buffer.concat(idat));
  const stride = width * bpp;
  const out = Buffer.alloc(width * height * 4);
  let prev = Buffer.alloc(stride);
  for (let y = 0; y < height; y++) {
    const filter = raw[y * (stride + 1)];
    const line = raw.subarray(y * (stride + 1) + 1, y * (stride + 1) + 1 + stride);
    const cur = Buffer.alloc(stride);
    for (let i = 0; i < stride; i++) {
      const a = i >= bpp ? cur[i - bpp] : 0;
      const b = prev[i];
      const c = i >= bpp ? prev[i - bpp] : 0;
      let v = line[i];
      if (filter === 1) v = (v + a) & 255;
      else if (filter === 2) v = (v + b) & 255;
      else if (filter === 3) v = (v + ((a + b) >> 1)) & 255;
      else if (filter === 4) {
        const p = a + b - c;
        const pa = Math.abs(p - a);
        const pb = Math.abs(p - b);
        const pc = Math.abs(p - c);
        v = (v + (pa <= pb && pa <= pc ? a : pb <= pc ? b : c)) & 255;
      } else if (filter !== 0) throw new Error(`bad filter ${filter}`);
      cur[i] = v;
    }
    for (let x = 0; x < width; x++) {
      const s = x * bpp;
      const d = (y * width + x) * 4;
      if (colorType === 6) {
        out[d] = cur[s];
        out[d + 1] = cur[s + 1];
        out[d + 2] = cur[s + 2];
        out[d + 3] = cur[s + 3];
      } else if (colorType === 2) {
        out[d] = cur[s];
        out[d + 1] = cur[s + 1];
        out[d + 2] = cur[s + 2];
        out[d + 3] = 255;
      } else if (colorType === 4) {
        out[d] = out[d + 1] = out[d + 2] = cur[s];
        out[d + 3] = cur[s + 1];
      } else {
        const p = cur[s] * 3;
        out[d] = palette[p];
        out[d + 1] = palette[p + 1];
        out[d + 2] = palette[p + 2];
        out[d + 3] = transparency && cur[s] < transparency.length ? transparency[cur[s]] : 255;
      }
    }
    prev = cur;
  }
  return { width, height, data: out };
}

function encodePng(width, height, data) {
  const stride = width * 4;
  const raw = Buffer.alloc((stride + 1) * height);
  for (let y = 0; y < height; y++) {
    raw[y * (stride + 1)] = 0;
    data.copy(raw, y * (stride + 1) + 1, y * stride, (y + 1) * stride);
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
  return Buffer.concat([
    SIG,
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

// --- pixels ---------------------------------------------------------------

const hex = (value) => [
  parseInt(value.slice(1, 3), 16),
  parseInt(value.slice(3, 5), 16),
  parseInt(value.slice(5, 7), 16),
];

function put(image, x, y, [r, g, b]) {
  if (x < 0 || y < 0 || x >= image.width || y >= image.height) return;
  const i = (y * image.width + x) * 4;
  image.data[i] = r;
  image.data[i + 1] = g;
  image.data[i + 2] = b;
  image.data[i + 3] = 255;
}

function get(image, x, y) {
  const i = (y * image.width + x) * 4;
  return [image.data[i], image.data[i + 1], image.data[i + 2], image.data[i + 3]];
}

function upscale(image, factor) {
  const out = {
    width: image.width * factor,
    height: image.height * factor,
    data: Buffer.alloc(image.width * factor * image.height * factor * 4),
  };
  for (let y = 0; y < out.height; y++) {
    for (let x = 0; x < out.width; x++) {
      const src = ((y / factor | 0) * image.width + (x / factor | 0)) * 4;
      const dst = (y * out.width + x) * 4;
      image.data.copy(out.data, dst, src, src + 4);
    }
  }
  return out;
}

// 5x7 glyphs, lowercase x-height rows 2..6.
const GLYPHS = {
  n: ['.....', '.....', '.#.#.', '.##.#', '.#..#', '.#..#', '.#..#'],
  o: ['.....', '.....', '.###.', '#...#', '#...#', '#...#', '.###.'],
  v: ['.....', '.....', '#...#', '#...#', '#...#', '.#.#.', '..#..'],
  c: ['.....', '.....', '.###.', '#...#', '#....', '#...#', '.###.'],
  l: ['.##..', '..#..', '..#..', '..#..', '..#..', '..#..', '.###.'],
  i: ['..#..', '.....', '..#..', '..#..', '..#..', '..#..', '..#..'],
  e: ['.....', '.....', '.###.', '#...#', '#####', '#....', '.###.'],
  t: ['..#..', '..#..', '#####', '..#..', '..#..', '..#..', '...#.'],
  '.': ['.....', '.....', '.....', '.....', '.....', '..#..', '..#..'],
};

// 3x5 caps, for the standard-resolution mark.
const CAPS = {
  N: ['#.#', '###', '#.#', '#.#', '#.#'],
  O: ['###', '#.#', '#.#', '#.#', '###'],
  V: ['#.#', '#.#', '#.#', '#.#', '.#.'],
};

function block(image, x, y, size, color) {
  for (let dy = 0; dy < size; dy++) {
    for (let dx = 0; dx < size; dx++) put(image, x + dx, y + dy, color);
  }
}

/** Draws text at `scale`, with a one-pixel outline behind the letters. */
function draw(image, text, x, y, color, outline, scale = 1) {
  const advance = 5 * scale;
  const glyphs = [...text].map((char) => GLYPHS[char] ?? GLYPHS['.']);
  if (outline) {
    let cursor = x;
    for (const glyph of glyphs) {
      for (let row = 0; row < glyph.length; row++) {
        for (let col = 0; col < glyph[row].length; col++) {
          if (glyph[row][col] === '#') block(image, cursor + col * scale - 1, y + row * scale - 1, scale + 2, outline);
        }
      }
      cursor += advance;
    }
  }
  let cursor = x;
  for (const glyph of glyphs) {
    for (let row = 0; row < glyph.length; row++) {
      for (let col = 0; col < glyph[row].length; col++) {
        if (glyph[row][col] === '#') block(image, cursor + col * scale, y + row * scale, scale, color);
      }
    }
    cursor += advance;
  }
}

const textWidth = (text, scale = 1) => [...text].length * 5 * scale - scale;

function drawCaps(image, text, x, y, color, outline) {
  let cursor = x;
  for (const char of text) {
    const glyph = CAPS[char];
    for (let row = 0; row < glyph.length; row++) {
      for (let col = 0; col < glyph[row].length; col++) {
        if (glyph[row][col] !== '#') continue;
        if (outline) {
          for (const [dx, dy] of [[-1, 0], [1, 0], [0, -1], [0, 1]]) put(image, cursor + col + dx, y + row + dy, outline);
        }
      }
    }
    for (let row = 0; row < glyph.length; row++) {
      for (let col = 0; col < glyph[row].length; col++) {
        if (glyph[row][col] === '#') put(image, cursor + col, y + row, color);
      }
    }
    cursor += 4;
  }
}

// --- branding -------------------------------------------------------------

const WHITE = hex('#E8ECF3');
const NAVY = hex('#0B0E14');

/** Torso front starts at (20,20) and is 8x12 skin pixels. */
const TORSO = { u: 20, v: 20, w: 8, h: 12 };

function brand64(source) {
  const image = { width: source.width, height: source.height, data: Buffer.from(source.data) };
  const left = TORSO.u + Math.floor((TORSO.w - 7) / 2);
  const top = TORSO.v + 1;
  // Three-pixel letters have no room for an outline: white on the blue base reads cleanest.
  drawCaps(image, 'NO', left, top, WHITE, null);
  drawCaps(image, 'VO', left, top + 6, WHITE, null);
  return image;
}

function brandHd(source) {
  const hd = upscale(source, 8);
  const x = TORSO.u * 8;
  const y = TORSO.v * 8;
  const width = TORSO.w * 8;
  const scale = 2;
  const lines = ['novo', 'client', '.cc'];
  const lineHeight = 7 * scale + 4;
  let cursorY = y + Math.round((TORSO.h * 8 - (lines.length * lineHeight - 4)) / 2);
  for (const line of lines) {
    const w = textWidth(line, scale);
    draw(hd, line, x + Math.round((width - w) / 2), cursorY, WHITE, NAVY, scale);
    cursorY += lineHeight;
  }
  return hd;
}

// --- main -----------------------------------------------------------------

const [input, prefix, flag] = process.argv.slice(2);
if (!input || !prefix) {
  console.error('usage: node scripts/brand-skin.mjs <input.png> <output-prefix> [--dump]');
  process.exit(2);
}
const source = decodePng(readFileSync(input));
if (source.width !== 64 || (source.height !== 64 && source.height !== 32)) {
  throw new Error(`expected a 64x64 or 64x32 skin, got ${source.width}x${source.height}`);
}

if (flag === '--dump') {
  const labels = [
    ['head front', 8, 8, 8, 8],
    ['torso front', 20, 20, 8, 12],
    ['torso back', 32, 20, 8, 12],
  ];
  for (const [label, u, v, w, h] of labels) {
    console.log(`\n${label} (${u},${v})`);
    for (let y = v; y < v + h; y++) {
      let row = '';
      for (let x = u; x < u + w; x++) {
        const [r, g, b, a] = get(source, x, y);
        row += a < 16 ? '.' : r > 200 && g > 200 && b > 200 ? 'W' : r > 150 && g > 150 && b < 120 ? 'y' : b > r + 40 ? 'B' : r > g + 40 ? 'R' : '#';
      }
      console.log('  ' + row);
    }
  }
  process.exit(0);
}

writeFileSync(`${prefix}_64.png`, encodePng(64, source.height, brand64(source).data));
const hd = brandHd(source);
writeFileSync(`${prefix}_hd.png`, encodePng(hd.width, hd.height, hd.data));
const preview = upscale(brand64(source), 8);
writeFileSync(`${prefix}_preview.png`, encodePng(preview.width, preview.height, preview.data));
console.log(`wrote ${prefix}_64.png (${64}x${source.height}), ${prefix}_hd.png (${hd.width}x${hd.height}), ${prefix}_preview.png`);
