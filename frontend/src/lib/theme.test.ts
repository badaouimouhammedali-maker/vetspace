import { beforeEach, describe, expect, it } from 'vitest';
import { applyTheme, DEFAULT_THEME } from './theme';

/**
 * The hex → "r g b" conversion is the whole point of this module: Tailwind's opacity
 * modifiers (`bg-brand-green/10`) only work against channel triplets, so a variable set
 * to "#0f766e" silently breaks every tinted surface in the app rather than erroring.
 */
const read = (name: string) => document.documentElement.style.getPropertyValue(name);

describe('applyTheme', () => {
  beforeEach(() => {
    document.documentElement.removeAttribute('style');
  });

  it('writes RGB channels, not hex', () => {
    applyTheme({ primary: '#0F766E', secondary: '#12355B', tertiary: '#6B7280' });
    expect(read('--color-primary')).toBe('15 118 110');
    expect(read('--color-secondary')).toBe('18 53 91');
    expect(read('--color-tertiary')).toBe('107 114 128');
  });

  it('accepts hex without the leading #', () => {
    applyTheme({ primary: '0F766E' });
    expect(read('--color-primary')).toBe('15 118 110');
  });

  it.each([
    ['null', null],
    ['empty', ''],
    ['too short', '#abc'],
    ['not hex', '#zzzzzz'],
    ['undefined', undefined],
  ])('falls back to the default when the value is %s', (_label, value) => {
    applyTheme({ primary: value as string | null });
    // The default is #0f766e — the fallback must be converted too, not passed through.
    expect(read('--color-primary')).toBe('15 118 110');
    expect(read('--color-primary')).not.toContain('#');
  });

  it('falls back on every channel when passed null', () => {
    applyTheme(null);
    expect(read('--color-primary')).toBe('15 118 110');
    expect(read('--color-secondary')).toBe('18 53 91');
    expect(read('--color-tertiary')).toBe('107 114 128');
  });

  it('applies a partial theme without clearing the others', () => {
    applyTheme({ secondary: '#000000' });
    expect(read('--color-secondary')).toBe('0 0 0');
    expect(read('--color-primary')).toBe('15 118 110');
  });

  it('exposes defaults as hex, since that is what the API and colour inputs use', () => {
    expect(DEFAULT_THEME.primary).toMatch(/^#[0-9a-f]{6}$/);
  });
});
