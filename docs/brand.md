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

## Open decisions

Two colour questions the token migration surfaced and deliberately did not answer. Both
need an owner rather than a patch.

### The mark's green is not `--accent`

`logo.svg`, `icon.svg` and `favicon.svg` are `#006b5a`. The interface accent is
`#0F766E`. They sit side by side in the sidebar, the auth panel and the landing header,
close enough to read as a mistake rather than a distinction — the mark looks like a
slightly-off version of the button next to it.

Pick one, and write the choice down here:

- **The mark adopts `--accent`.** Regenerate the vector masters at `#0F766E` and re-run
  `npm run brand:icons`. Changes the logo, so it is a brand decision, not a frontend one.
- **The gap is intentional.** Say why (a deeper green for print or embroidery, say), and
  note that the two greens are never meant to match. That closes the question for the
  next person who notices.

Until then the mark is left exactly as-is: the colour pass deliberately did not touch it.

### Quiz score colour should key off the pass threshold

The end-of-session score (`PlayPage`, the 44px figure) uses `--accent`. `--success`
would be wrong as a blanket rule — painting a 30% green reads as praise — and
`--accent` is wrong in the other direction, since emerald means "interact with this"
everywhere else and the score is not interactive.

The honest version keys the colour off the pass threshold: `--success-text` at or above
it, `--warning` or `--danger` below, with the threshold owned in one place the way
`PrecisionChip` already owns its bands. That is conditional logic and a pedagogical
decision about what counts as a pass, so it was out of scope for a colour pass.
