# Brand assets

## Files

All in `frontend/public/brand/`.

| File | What | Used by |
|---|---|---|
| `logo.svg` | Mark, `#006b5a`, for light backgrounds | `<Logo theme="light">` |
| `logo-white.svg` | Mark, white, for navy backgrounds | `<Logo theme="dark">` |
| `icon.svg` | Same mark on a square canvas | source for the rasters below |
| `favicon.svg` | Copy of `icon.svg` | `<link rel="icon">` |
| `favicon.ico` | 16/32/48, PNG-in-ICO, transparent | `<link rel="alternate icon">` |
| `apple-touch-icon.png` | 180×180, **opaque white**, 12% padding | `<link rel="apple-touch-icon">` |
| `og-image.png` | 1200×630, white mark on navy | `og:image` |

The SVGs share one artwork and one scale. The wide pair is `viewBox="0 0 503.01 437.42"`,
tight to the vector bounds with zero padding; the square pair is
`viewBox="0 0 503.01 503.01"`, the same artwork with the canvas squared off and the
mark centred. Nothing is stretched between them — only the canvas changes.

**There is no wordmark.** The artwork is the mark alone, so `<Logo variant="full">` and
`<Logo variant="mark">` resolve to the same file on purpose. The prop exists so call
sites already say which they mean if a real lockup ever arrives.

## Regenerating the rasters

```bash
cd frontend && npm run brand:icons
```

Reads the SVG masters, writes `favicon.ico`, `apple-touch-icon.png` and `og-image.png`.
Not part of `npm run build`: the outputs belong in git, and making every deploy depend
on sharp's native binaries buys nothing. Run it, look at the result, commit it.

## Rules that keep breaking

Two production incidents came from the same place, so these are worth stating plainly.

1. **Fix the artboard, never the CSS.** Both past bugs — a mark that rendered oversized
   and cropped, and a second tiny duplicate beside it — were in the SVG files: a stray
   embedded raster in the corner had inflated the canvas to 2.64:1 for a 1.17:1 mark, so
   every height class was sizing mostly whitespace, and 23% of the mark hung outside the
   viewBox. A margin that corrects one size breaks every other size.
2. **Height only, width auto.** Pinning both axes is what stretches a logo. `<Logo>`
   sets `height` with `width: auto` and `object-contain`, and `Logo.test.tsx` asserts
   it. The `width`/`height` HTML attributes are there only to reserve the box before
   load; CSS wins for layout. If the artwork's aspect changes, update `ASPECT` in
   `Logo.tsx` to match, or headers will reflow on load.
3. **One logo per screen.** `AuthLayout` renders the navy-panel logo at `lg:flex` and the
   card logo at `lg:hidden` — exactly mutually exclusive, never both.
4. **Nothing outside `<Logo>` may reference `/brand/*.svg`.** The exceptions are the
   `<link>` tags in `index.html` and the `og:image` meta, which are not rendered logos.

## Known limitation

`og:image` is set client-side by react-helmet-async and is a root-relative path. Crawlers
that do not execute JavaScript will not see it, and some require an absolute URL. Fixing
that properly needs SSR or prerendering, which the SPA does not have today.
