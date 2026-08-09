/**
 * The only thing in the app allowed to reference the brand files.
 *
 * <p>Before this existed every call site did its own `<img src="/brand/...">` with its
 * own height class, which is how two of them ended up rendering the artwork at sizes
 * the artboard could not support. Route brand rendering through here and a size or
 * asset change is one edit.
 *
 * <p>Height is set explicitly and width is left `auto`, with `object-contain`, so the
 * mark keeps its aspect ratio no matter what the surrounding flex or grid container
 * would rather do. Nothing here compensates for the assets: `public/brand/*.svg` are
 * generated tight to the real vector bounds, so plain sizing is enough. If the logo
 * ever looks wrong, fix the artboard, not this file — a CSS margin that corrects one
 * size is a CSS margin that breaks every other size.
 */

/** Named sizes, so call sites stay declarative and consistent across the app. */
const SIZES = {
  /** Sidebar / compact chrome. */
  sm: 32,
  /** Landing header, in-app headers. */
  md: 36,
  /** Auth pages — the logo is the page's anchor there. */
  lg: 48,
} as const;

/**
 * Artwork aspect, from the generated viewBox (503.01 x 437.42).
 *
 * <p>Used only to reserve the right box before the file loads, so headers do not
 * reflow. It must track the SVGs: a stale number here is a layout jump, not a
 * stretch, because the rendered size still comes from CSS height + `width: auto`.
 */
const ASPECT = 503.01 / 437.42;

export interface LogoProps {
  /**
   * `full` is the lockup (mark + wordmark), `mark` is the icon alone.
   *
   * <p>NOTE: only one artwork exists — the mark. There is no wordmark in `public/brand`
   * and none was supplied with the current artwork, so both variants deliberately
   * resolve to the same file rather than this component inventing typography. The prop
   * stays so that call sites already say which they mean: dropping in a real lockup
   * later is a change in this file and nowhere else.
   */
  variant?: 'full' | 'mark';
  /** `dark` = for dark backgrounds (white artwork); `light` = for light ones (green). */
  theme?: 'light' | 'dark';
  /** Named size, or an explicit height in px. */
  size?: keyof typeof SIZES | number;
  className?: string;
}

export function Logo({ variant = 'mark', theme = 'light', size = 'md', className }: LogoProps) {
  const height = typeof size === 'number' ? size : SIZES[size];
  // Only the colour varies today; `variant` selects the file once a wordmark exists.
  // Note it must NOT select `icon.svg`: that is the same mark on a squared-off canvas,
  // so at a fixed height it would draw the mark ~13% smaller than the sibling using
  // `full`, and the sidebar would stop matching the header. `icon.svg` is a source for
  // the favicon and app-icon rasters, not a render target.
  void variant;
  const src = theme === 'dark' ? '/brand/logo-white.svg' : '/brand/logo.svg';

  return (
    <img
      src={src}
      alt="VetSpace"
      style={{ height, width: 'auto' }}
      className={`object-contain${className ? ` ${className}` : ''}`}
      // Intrinsic ratio only — CSS above wins for actual layout. Present so the browser
      // reserves the right box before the file loads and the header does not jump.
      width={Math.round(height * ASPECT)}
      height={height}
    />
  );
}
