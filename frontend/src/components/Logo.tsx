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
 * would rather do. Nothing here compensates for the assets: `public/brand/*.svg` were
 * repaired at source (stray embedded raster removed, viewBox tightened to the real
 * vector bounds), so plain sizing is enough.
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

export interface LogoProps {
  /**
   * `full` is the lockup (mark + wordmark), `mark` is the icon alone.
   *
   * <p>NOTE: only one artwork exists today — the mark. There is no wordmark asset in
   * `public/brand`, so both variants currently resolve to the same file. The prop is
   * here so that adding `logo-full.svg` later is a change in this file and nowhere
   * else; it is not currently a lie the call sites can detect.
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
  void variant;
  const src = theme === 'dark' ? '/brand/logo-white.svg' : '/brand/logo.svg';

  return (
    <img
      src={src}
      alt="VetSpace"
      style={{ height, width: 'auto' }}
      className={`object-contain${className ? ` ${className}` : ''}`}
      // Intrinsic ratio from the repaired viewBox (1324.8 x 1136.2), so the browser
      // reserves the right box before the file loads and the header does not jump.
      width={Math.round(height * (1324.8 / 1136.2))}
      height={height}
    />
  );
}
