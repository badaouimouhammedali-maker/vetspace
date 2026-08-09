import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Logo } from './Logo';

/**
 * These assertions exist because each one has already broken in production once:
 * the mark rendered stretched, oversized and cropped because call sites pinned both
 * axes and the artboard carried padding. They are about sizing mechanics, not looks.
 */
describe('Logo', () => {
  it('sets height only and leaves width auto, so nothing can stretch it', () => {
    render(<Logo size="md" />);
    const img = screen.getByAltText('VetSpace');
    expect(img.style.height).toBe('36px');
    expect(img.style.width).toBe('auto');
  });

  it('keeps object-contain so a constrained parent cannot crop the artwork', () => {
    render(<Logo className="mb-8" />);
    const img = screen.getByAltText('VetSpace');
    expect(img).toHaveClass('object-contain');
    expect(img).toHaveClass('mb-8');
  });

  it('reserves the box using the real artwork ratio, so headers do not reflow', () => {
    render(<Logo size={48} />);
    const img = screen.getByAltText('VetSpace');
    // 48 * (503.01 / 437.42) = 55.2
    expect(img.getAttribute('height')).toBe('48');
    expect(img.getAttribute('width')).toBe('55');
  });

  it('picks the white artwork for dark backgrounds and the green one for light', () => {
    const { rerender } = render(<Logo theme="dark" />);
    expect(screen.getByAltText('VetSpace')).toHaveAttribute('src', '/brand/logo-white.svg');
    rerender(<Logo theme="light" />);
    expect(screen.getByAltText('VetSpace')).toHaveAttribute('src', '/brand/logo.svg');
  });

  it('renders exactly one element, whichever variant is asked for', () => {
    // There is no wordmark asset, so `full` and `mark` deliberately resolve to the
    // same file. What must never come back is a variant rendering a lockup AND a
    // separate mark — the "duplicated logo" bug.
    for (const variant of ['full', 'mark'] as const) {
      const { unmount } = render(<Logo variant={variant} />);
      expect(screen.getAllByAltText('VetSpace')).toHaveLength(1);
      unmount();
    }
  });

  it('never emits a transform, which would scale the mark off its pixel grid', () => {
    render(<Logo size="lg" />);
    expect(screen.getByAltText('VetSpace').style.transform).toBe('');
  });
});
