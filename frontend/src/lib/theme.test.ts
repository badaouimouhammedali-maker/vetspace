import { beforeEach, describe, expect, it } from 'vitest';
import {
  applyCachedTheme,
  applyTheme,
  cacheTheme,
  DEFAULT_THEME,
  themeContrast,
} from './theme';
import { contrastRatio, contrastingVariant, hexToRgb } from './colorMath';

/**
 * Deux invariants ici.
 *
 * <p>1. La conversion hex → "r g b" : les modificateurs d'opacité Tailwind
 * (`bg-accent/10`) ne fonctionnent que contre des triplets de canaux, donc une variable
 * réglée sur "#0f766e" casse silencieusement toutes les surfaces teintées au lieu
 * d'échouer.
 *
 * <p>2. Le thème par défaut n'écrit *rien* : il efface les variables inline pour que la
 * charte de `index.css` gagne. C'est ce qui garantit qu'un compte non personnalisé voit
 * les hex exacts de la charte et non des valeurs recalculées.
 */
const read = (name: string) => document.documentElement.style.getPropertyValue(name);

describe('applyTheme', () => {
  beforeEach(() => {
    document.documentElement.removeAttribute('style');
  });

  it('writes RGB channels, not hex', () => {
    applyTheme({ primary: '#6D28D9' });
    expect(read('--accent')).toBe('109 40 217');
    expect(read('--accent')).not.toContain('#');
  });

  it('accepts hex without the leading #', () => {
    applyTheme({ primary: '6D28D9' });
    expect(read('--accent')).toBe('109 40 217');
  });

  it('leaves the default palette to the stylesheet instead of recomputing it', () => {
    applyTheme(DEFAULT_THEME);
    for (const token of [
      '--accent',
      '--accent-hover',
      '--accent-pressed',
      '--accent-on-dark',
      '--brand-navy',
      '--brand-navy-deep',
      '--text-primary',
      '--text-secondary',
    ]) {
      expect(read(token)).toBe('');
    }
  });

  it('derives hover, pressed and on-dark from a custom accent', () => {
    applyTheme({ primary: '#6D28D9' });
    expect(read('--accent-hover')).not.toBe('');
    expect(read('--accent-pressed')).not.toBe('');
    expect(read('--accent-on-dark')).not.toBe('');
  });

  it.each([
    ['null', null],
    ['empty', ''],
    ['too short', '#abc'],
    ['not hex', '#zzzzzz'],
    ['undefined', undefined],
  ])('falls back to the default when the value is %s', (_label, value) => {
    applyTheme({ primary: value as string | null });
    // Le défaut est effacé, pas écrit : la charte de :root reprend la main.
    expect(read('--accent')).toBe('');
  });

  it('applies a partial theme without clearing the others', () => {
    applyTheme({ secondary: '#000000' });
    expect(read('--brand-navy')).toBe('0 0 0');
    expect(read('--accent')).toBe('');
  });

  it('re-derives accent-on-dark when only the navy is customised', () => {
    // L'accent par défaut sur un navy choisi n'est plus le couple validé de la charte :
    // il doit être revérifié, pas laissé au #3DBFB0 figé.
    applyTheme({ secondary: '#3A0A0A' });
    expect(read('--accent-on-dark')).not.toBe('');
  });

  it('exposes defaults as hex, since that is what the API and colour inputs use', () => {
    expect(DEFAULT_THEME.primary).toMatch(/^#[0-9a-f]{6}$/);
    expect(DEFAULT_THEME.secondary).toMatch(/^#[0-9a-f]{6}$/);
    expect(DEFAULT_THEME.tertiary).toMatch(/^#[0-9a-f]{6}$/);
  });
});

/**
 * Le cache existe pour une seule raison : `applyTheme` est appelé depuis AuthContext,
 * donc après le rafraîchissement du jeton *puis* /users/me. Sans cache, un compte
 * personnalisé afficherait la palette par défaut pendant deux allers-retours réseau.
 */
describe('theme cache', () => {
  beforeEach(() => {
    document.documentElement.removeAttribute('style');
    localStorage.clear();
  });

  it('replays a cached custom theme without waiting for the network', () => {
    cacheTheme({ primary: '#6D28D9', secondary: '#3A0A0A', tertiary: '#64748b' });
    document.documentElement.removeAttribute('style'); // simule un rechargement
    applyCachedTheme();
    expect(read('--accent')).toBe('109 40 217');
    expect(read('--brand-navy')).toBe('58 10 10');
    expect(read('--accent-on-dark')).not.toBe('');
  });

  it('forgets the theme on logout, so the next visitor gets the charter', () => {
    cacheTheme({ primary: '#6D28D9' });
    cacheTheme(null);
    document.documentElement.removeAttribute('style');
    applyCachedTheme();
    expect(read('--accent')).toBe('');
  });

  it('does nothing when there is no cache', () => {
    applyCachedTheme();
    expect(read('--accent')).toBe('');
  });

  it('revalidates cached values rather than trusting local storage', () => {
    // Le stockage local est modifiable à la main : une valeur bidon ne doit pas pouvoir
    // écrire n'importe quoi dans une variable CSS.
    localStorage.setItem('vetspace.theme', JSON.stringify({ primary: 'javascript:alert(1)' }));
    applyCachedTheme();
    expect(read('--accent')).toBe('');
  });

  it('survives unreadable JSON', () => {
    localStorage.setItem('vetspace.theme', '{not json');
    expect(() => applyCachedTheme()).not.toThrow();
    expect(read('--accent')).toBe('');
  });
});

/**
 * Le vrai intérêt de la dérivation : la règle de contraste doit tenir pour n'importe
 * quel choix de l'utilisateur, pas seulement pour le teal de la charte.
 */
describe('theme contrast guarantees', () => {
  it.each([
    ['default teal', DEFAULT_THEME.primary, DEFAULT_THEME.secondary],
    ['violet on navy', '#6D28D9', '#12355b'],
    ['near-black accent', '#111111', '#12355b'],
    ['pale pink accent', '#FBCFE8', '#12355b'],
    ['crimson on maroon', '#B91C1C', '#2A0E0E'],
    ['accent equal to navy', '#12355b', '#12355b'],
    // Les cas qui imposent d'assombrir plutôt que d'éclaircir : un « navy » pâle. Une
    // dérivation qui éclaircirait toujours renverrait du blanc sur du rose (1,4:1).
    ['saturated accent on a pale navy', '#FACC15', '#FBCFE8'],
    ['accent on a white navy', '#0F766E', '#FFFFFF'],
    ['accent on a mid-luminance navy', '#0F766E', '#808080'],
    ['pale accent on a pale navy', '#FBCFE8', '#F8FAF9'],
  ])('keeps accent-on-dark readable: %s', (_label, primary, secondary) => {
    const { accentOnNavy } = themeContrast({ primary, secondary });
    expect(accentOnNavy).toBeGreaterThanOrEqual(4.5);
  });

  it.each([
    ['default navy', DEFAULT_THEME.secondary],
    ['pale secondary', '#FBCFE8'],
    ['mid grey secondary', '#9CA3AF'],
  ])('keeps the heading ink readable on the app background: %s', (_label, secondary) => {
    const { inkOnApp } = themeContrast({ secondary });
    expect(inkOnApp).toBeGreaterThanOrEqual(7);
  });

  it('falls back to the better of white/black when no lightness reaches the target', () => {
    // Un fond de luminance moyenne n'a pas de solution à 5,5:1 : ni le blanc (3,9:1) ni
    // le noir (5,3:1) n'y arrivent. La dérivation ne doit pas renvoyer la dernière
    // itération de sa recherche — elle doit choisir le meilleur des deux extrêmes.
    const grey = hexToRgb('#808080')!;
    const derived = contrastingVariant(hexToRgb('#0F766E')!, grey, 5.5);
    const best = Math.max(
      contrastRatio(hexToRgb('#FFFFFF')!, grey),
      contrastRatio(hexToRgb('#000000')!, grey),
    );
    expect(contrastRatio(derived, grey)).toBeCloseTo(best, 2);
    expect(derived).toEqual([0, 0, 0]);
  });

  it('darkens rather than lightens when the navy is pale', () => {
    const pale = hexToRgb('#FBCFE8')!;
    const derived = contrastingVariant(hexToRgb('#FACC15')!, pale, 4.5);
    expect(contrastRatio(derived, pale)).toBeGreaterThanOrEqual(4.5);
    // Éclaircir aurait donné du blanc sur du rose pâle : 1,38:1.
    expect(contrastRatio(derived, pale)).toBeGreaterThan(
      contrastRatio(hexToRgb('#FFFFFF')!, pale),
    );
  });

  it('ships the charter pairing the spec measured', () => {
    // #3DBFB0 sur #12355B = 5.5:1. La valeur de :root, pas une dérivation.
    expect(contrastRatio(hexToRgb('#3DBFB0')!, hexToRgb('#12355B')!)).toBeCloseTo(5.5, 1);
    // …et l'accent lui-même y est bien illisible, ce qui justifie le jeton séparé.
    expect(contrastRatio(hexToRgb('#0F766E')!, hexToRgb('#12355B')!)).toBeLessThan(3);
  });
});
