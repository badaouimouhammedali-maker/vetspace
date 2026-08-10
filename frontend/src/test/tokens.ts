/**
 * Résolution « classe utilitaire → jeton de la charte » pour les tests.
 *
 * <p>Assertion naïve : `expect(className).toContain('bg-accent/18')`. Elle casse au
 * moindre renommage et, pire, elle ne vérifie rien d'intéressant — `bg-taupe/18`
 * passerait le même test tant que la chaîne correspond.
 *
 * <p>On passe donc la classe par la *vraie* configuration Tailwind pour obtenir la
 * variable CSS derrière elle. Un test dit alors « le fond de l'onglet actif est
 * --accent à 18 % », ce qui reste vrai après un renommage de classe et devient faux si
 * quelqu'un repointe la classe vers une autre couleur.
 *
 * <p>jsdom n'applique pas Tailwind, donc `getComputedStyle` ne peut pas servir ici :
 * la config est la source de vérité disponible en test.
 */
import resolveConfig from 'tailwindcss/resolveConfig';
// @ts-expect-error — config JS sans types, importée pour sa valeur runtime.
import tailwindConfig from '../../tailwind.config.js';

const theme = resolveConfig(tailwindConfig as never).theme as unknown as {
  colors: Record<string, unknown>;
  borderColor: Record<string, unknown>;
  ringColor: Record<string, unknown>;
};

/** Les préfixes ne lisent pas tous la même palette. */
const PALETTE: Record<string, 'colors' | 'borderColor' | 'ringColor'> = {
  bg: 'colors',
  text: 'colors',
  fill: 'colors',
  stroke: 'colors',
  accent: 'colors',
  placeholder: 'colors',
  from: 'colors',
  to: 'colors',
  border: 'borderColor',
  divide: 'borderColor',
  ring: 'ringColor',
};

export interface ResolvedToken {
  /** Nom de la variable CSS, par ex. `--accent`. */
  token: string;
  /** Opacité du modificateur `/n`, en pourcentage ; `null` s'il n'y en a pas. */
  alpha: number | null;
}

function lookup(palette: Record<string, unknown>, name: string): string | null {
  // `brand-navy` peut vivre soit à plat, soit sous `brand.navy`.
  const direct = palette[name];
  if (typeof direct === 'string') {
    return direct;
  }
  const cut = name.indexOf('-');
  if (cut > 0) {
    const group = palette[name.slice(0, cut)];
    if (group && typeof group === 'object') {
      const nested = (group as Record<string, unknown>)[name.slice(cut + 1)];
      if (typeof nested === 'string') {
        return nested;
      }
    }
  }
  return null;
}

/**
 * `"before:bg-accent/18"` → `{ token: '--accent', alpha: 18 }`.
 *
 * <p>Renvoie `null` si la classe n'existe pas dans la config — ce qui est en soi le
 * bug à attraper : une classe couleur inventée ne produit aucun style.
 */
export function resolveColorClass(className: string): ResolvedToken | null {
  // On ignore les variantes (`hover:`, `before:`, `dark:`…) : elles ne changent pas
  // la couleur, seulement quand elle s'applique.
  const bare = className.slice(className.lastIndexOf(':') + 1);
  const slash = bare.lastIndexOf('/');
  const alpha = slash > 0 ? Number(bare.slice(slash + 1)) : null;
  const withoutAlpha = slash > 0 ? bare.slice(0, slash) : bare;

  const dash = withoutAlpha.indexOf('-');
  if (dash < 0) {
    return null;
  }
  const prefix = withoutAlpha.slice(0, dash);
  const name = withoutAlpha.slice(dash + 1);
  const paletteName = PALETTE[prefix];
  if (!paletteName) {
    return null;
  }

  const value = lookup(theme[paletteName] as Record<string, unknown>, name);
  const variable = value ? /var\((--[a-z-]+)\)/.exec(value)?.[1] : null;
  return variable ? { token: variable, alpha: Number.isNaN(alpha) ? null : alpha } : null;
}

/**
 * Cherche, parmi les classes d'un élément, celle qui porte `prefix` et renvoie le jeton
 * qu'elle désigne. Assertion type : `expect(tokenFor(el, 'bg')).toEqual({token:'--accent', alpha:18})`.
 */
export function tokenFor(element: Element | string, prefix: string): ResolvedToken | null {
  const classes = (typeof element === 'string' ? element : element.className).split(/\s+/);
  for (const cls of classes) {
    const bare = cls.slice(cls.lastIndexOf(':') + 1);
    if (!bare.startsWith(`${prefix}-`)) {
      continue;
    }
    const resolved = resolveColorClass(cls);
    if (resolved) {
      return resolved;
    }
  }
  return null;
}
