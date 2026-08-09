/**
 * Regenerates the raster brand assets from the vector masters in `public/brand`.
 *
 * Run after any change to the logo artwork:
 *
 *   npm run brand:icons
 *
 * Deliberately NOT wired into `npm run build`: the outputs are static files that
 * belong in git, and making every deploy depend on sharp's native binaries buys
 * nothing. Regenerate, eyeball the result, commit it.
 *
 * Masters (hand-checked, do not edit by hand — see docs/brand.md):
 *   icon.svg        square viewBox, mark centred, tight to the artwork bounds
 *   logo-white.svg  the same mark in white, for navy backgrounds
 */

import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';

const BRAND = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'public', 'brand');

/** Brand navy — mirrors `--color-secondary` in index.css and the theme-color meta. */
const NAVY = '#12355B';

/**
 * Rasterize an SVG, constraining exactly one axis so the artwork keeps its aspect.
 *
 * sharp derives an SVG's intrinsic size from its viewBox and renders at `density`
 * DPI against a 72dpi baseline, so asking for the target size directly would
 * rasterize 1:1 and alias. Render at >=3x and let the resize downsample instead.
 *
 * Pass `width` OR `height`, never both — pinning both is exactly the mistake that
 * stretches a logo, and `fit: 'contain'` would only trade the stretch for silent
 * letterboxing, which undersizes the mark instead.
 */
async function render(file, { width, height }) {
  const svg = await fs.readFile(path.join(BRAND, file));
  const [, , vbWidth, vbHeight] = svg
    .toString()
    .match(/viewBox="([^"]+)"/)[1]
    .split(/\s+/)
    .map(Number);
  const scale = width ? width / vbWidth : height / vbHeight;
  return sharp(svg, { density: Math.min(2400, Math.ceil(72 * scale * 3)) })
    .resize(width ? { width } : { height })
    .png()
    .toBuffer();
}

/**
 * Pack PNGs into an ICO container.
 *
 * PNG-compressed ICO entries are understood by every browser in our support matrix
 * and by Windows Vista onward, and avoid hand-rolling a BMP + AND-mask encoder.
 */
function ico(images) {
  const header = Buffer.alloc(6);
  header.writeUInt16LE(1, 2); // type: icon
  header.writeUInt16LE(images.length, 4);

  let offset = 6 + images.length * 16;
  const entries = images.map(({ size, data }) => {
    const e = Buffer.alloc(16);
    e.writeUInt8(size >= 256 ? 0 : size, 0); // width  (0 encodes 256)
    e.writeUInt8(size >= 256 ? 0 : size, 1); // height
    e.writeUInt16LE(1, 4); // colour planes
    e.writeUInt16LE(32, 6); // bits per pixel
    e.writeUInt32LE(data.length, 8);
    e.writeUInt32LE(offset, 12);
    offset += data.length;
    return e;
  });

  return Buffer.concat([header, ...entries, ...images.map((i) => i.data)]);
}

/**
 * Composite `mark` onto an opaque `background` tile and return alpha-free PNG bytes.
 *
 * The second sharp pass is not redundant: sharp runs its operations in a fixed
 * internal order in which flatten/removeAlpha precede composite, so chaining them
 * onto the same pipeline strips alpha from the blank tile and then the composite
 * puts it straight back. Encoding first forces the ordering we actually want.
 *
 * Alpha has to go, not merely be opaque: iOS masks apple-touch-icon to a rounded
 * rect and renders any transparency it finds as black.
 */
async function onOpaque({ width, height, background, mark }) {
  const composited = await sharp({ create: { width, height, channels: 4, background } })
    .composite([{ input: mark, gravity: 'centre' }])
    .png()
    .toBuffer();
  return sharp(composited).removeAlpha().png({ compressionLevel: 9 }).toBuffer();
}

async function write(name, data) {
  await fs.writeFile(path.join(BRAND, name), data);
  console.log(`  ${name.padEnd(22)} ${String(data.length).padStart(7)} bytes`);
}

console.log('Generating raster brand assets from public/brand/*.svg');

// favicon.ico — 16/32/48, transparent, tight bounds so the mark gets every pixel
// it can at 16px.
const ICO_SIZES = [16, 32, 48];
await write(
  'favicon.ico',
  ico(
    await Promise.all(
      ICO_SIZES.map(async (size) => ({ size, data: await render('icon.svg', { width: size }) })),
    ),
  ),
);

// apple-touch-icon.png — 180x180, opaque. iOS composites this onto the home screen
// and masks it to a rounded rect: a transparent background renders black, and an
// edge-to-edge mark loses its corners to the mask. Hence white, and 12% padding.
const TOUCH = 180;
const inset = Math.round(TOUCH * 0.76);
await write(
  'apple-touch-icon.png',
  await onOpaque({
    width: TOUCH,
    height: TOUCH,
    background: '#ffffff',
    mark: await render('icon.svg', { width: inset }),
  }),
);

// og-image.png — 1200x630, the ratio Facebook/LinkedIn/X all crop against. Navy so
// the white mark carries, and no text: the title and description ride in their own
// meta tags, and baking them in would strand a French string inside a binary.
const OG = { w: 1200, h: 630 };
await write(
  'og-image.png',
  await onOpaque({
    width: OG.w,
    height: OG.h,
    background: NAVY,
    mark: await render('logo-white.svg', { height: Math.round(OG.h * 0.46) }),
  }),
);

console.log('Done.');
