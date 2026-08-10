/**
 * Thème runtime.
 *
 * <p>Trois couleurs sont choisies par l'utilisateur (profil → Thème) : l'accent, le
 * navy, et le gris de texte. Les autres jetons de la charte sont fixes et vivent dans
 * `index.css`.
 *
 * <p>Mais quatre jetons sont des *dérivés* de ces trois : le survol et l'état pressé de
 * l'accent, l'accent éclairci pour rester lisible sur le navy, et le navy assombri. Les
 * figer en dur ne marche que pour le teal par défaut — dès que l'utilisateur choisit
 * autre chose, un « accent-sur-navy » teal sur un navy violet est incohérent, et
 * surtout potentiellement illisible. On les recalcule donc à partir des couleurs
 * choisies, en visant un rapport de contraste plutôt qu'un éclaircissement fixe.
 *
 * <p>Quand une couleur vaut le défaut — le cas de l'immense majorité des comptes — on
 * *efface* la variable inline au lieu d'écrire une valeur calculée. La cascade retombe
 * alors sur `:root`, donc un utilisateur non personnalisé voit les hex exacts de la
 * charte, au canal près, et `index.css` reste la source de vérité.
 *
 * <p>Les valeurs côté API/formulaire restent en hexadécimal (#rrggbb) ; on ne convertit
 * qu'à l'écriture.
 */
import {
  contrastRatio,
  darkenToContrast,
  hexToRgb,
  contrastingVariant,
  shade,
  toChannels,
  type Rgb,
} from './colorMath';

export const DEFAULT_THEME = {
  primary: '#0f766e',
  secondary: '#12355b',
  tertiary: '#64748b',
} as const;

export interface Theme {
  primary: string | null;
  secondary: string | null;
  tertiary: string | null;
}

/** Le fond d'application, en dur : ce n'est pas un jeton thémable. */
const BG_APP: Rgb = [248, 250, 249];

/**
 * Facteurs calibrés sur les jetons de la charte : appliqués au teal par défaut ils
 * reproduisent #115E59 / #134E4A / #0D2A47 à 1–3 unités par canal près. Ils ne servent
 * qu'aux thèmes personnalisés, le défaut étant lu depuis `:root`.
 */
const HOVER = { lightness: 0.855, chroma: 0.82 };
const PRESSED = { lightness: 0.756, chroma: 0.685 };
const NAVY_DEEP = { lightness: 0.861, chroma: 0.81 };
const INK = { lightness: 0.954, chroma: 0.75 };

/** Cibles de contraste (WCAG AA pour le texte normal, AAA pour l'encre principale). */
const ON_DARK_TARGET = 5.5;
const INK_TARGET = 7;

/** Compare deux hex sans tenir compte de la casse ni du `#`. */
function isSame(hex: string, other: string): boolean {
  return hex.replace('#', '').toLowerCase() === other.replace('#', '').toLowerCase();
}

/** La couleur choisie si elle est un hex valide, sinon le défaut. */
function resolve(value: string | null | undefined, fallback: string): string {
  return hexToRgb(value ?? '') ? (value as string) : fallback;
}

export function applyTheme(theme: Partial<Theme> | null): void {
  const root = document.documentElement;

  /** Écrit la variable, ou l'efface pour laisser `:root` — et donc la charte — gagner. */
  const set = (name: string, rgb: Rgb | null) => {
    if (rgb) {
      root.style.setProperty(name, toChannels(rgb));
    } else {
      root.style.removeProperty(name);
    }
  };

  const accentHex = resolve(theme?.primary, DEFAULT_THEME.primary);
  const navyHex = resolve(theme?.secondary, DEFAULT_THEME.secondary);
  const mutedHex = resolve(theme?.tertiary, DEFAULT_THEME.tertiary);

  const accent = hexToRgb(accentHex)!;
  const navy = hexToRgb(navyHex)!;
  const muted = hexToRgb(mutedHex)!;

  const accentIsDefault = isSame(accentHex, DEFAULT_THEME.primary);
  const navyIsDefault = isSame(navyHex, DEFAULT_THEME.secondary);
  const mutedIsDefault = isSame(mutedHex, DEFAULT_THEME.tertiary);

  set('--accent', accentIsDefault ? null : accent);
  set('--accent-hover', accentIsDefault ? null : shade(accent, HOVER.lightness, HOVER.chroma));
  set(
    '--accent-pressed',
    accentIsDefault ? null : shade(accent, PRESSED.lightness, PRESSED.chroma),
  );

  // Dépend des deux : un accent par défaut sur un navy personnalisé doit être revérifié.
  set(
    '--accent-on-dark',
    accentIsDefault && navyIsDefault ? null : contrastingVariant(accent, navy, ON_DARK_TARGET),
  );

  set('--brand-navy', navyIsDefault ? null : navy);
  set(
    '--brand-navy-deep',
    navyIsDefault ? null : shade(navy, NAVY_DEEP.lightness, NAVY_DEEP.chroma),
  );

  // L'encre suit le navy — c'est le comportement actuel, où les titres portent la
  // couleur secondaire — mais on la garde lisible. Aujourd'hui un secondaire pâle rend
  // les titres quasi invisibles ; le garde-fou de contraste l'empêche.
  set(
    '--text-primary',
    navyIsDefault
      ? null
      : darkenToContrast(shade(navy, INK.lightness, INK.chroma), BG_APP, INK_TARGET),
  );

  // Le gris de texte est déjà du texte : on le vérifie directement plutôt que de le
  // dériver, et on l'assombrit s'il ne tient pas AA sur le fond d'application.
  set(
    '--text-secondary',
    mutedIsDefault ? null : darkenToContrast(muted, BG_APP, 4.5),
  );
}

/*
  Cache local du thème.

  `applyTheme` est appelé depuis AuthContext, donc après le rafraîchissement du jeton
  *puis* l'appel à /users/me : deux allers-retours réseau pendant lesquels un compte
  personnalisé afficherait la palette par défaut, puis basculerait. On mémorise donc le
  dernier thème connu et on le rejoue de façon synchrone au démarrage, avant que React
  ne monte — voir `main.tsx`. Le réseau, quand il répond, ne fait que confirmer.

  Trois couleurs hexadécimales : rien de sensible, rien qui justifie un cookie.
*/
const CACHE_KEY = 'vetspace.theme';

/** Mémorise le thème appliqué, ou l'oublie à la déconnexion (`null`). */
export function cacheTheme(theme: Partial<Theme> | null): void {
  try {
    if (theme) {
      localStorage.setItem(CACHE_KEY, JSON.stringify(theme));
    } else {
      localStorage.removeItem(CACHE_KEY);
    }
  } catch {
    // Mode privé ou quota plein : le cache est une optimisation, pas une exigence.
  }
}

/**
 * Rejoue le thème mémorisé. À appeler avant le premier rendu.
 *
 * <p>Les valeurs viennent du stockage local, donc potentiellement modifiées à la main :
 * `applyTheme` les revalide (hex valide ou repli sur le défaut) avant d'écrire quoi que
 * ce soit, et ne peut donc pas produire autre chose qu'une couleur de la charte ou une
 * dérivation contrôlée.
 */
export function applyCachedTheme(): void {
  try {
    const raw = localStorage.getItem(CACHE_KEY);
    if (raw) {
      applyTheme(JSON.parse(raw) as Partial<Theme>);
    }
  } catch {
    // JSON illisible : on reste sur la charte par défaut.
  }
}

/** Exposé pour les tests et l'aperçu : le contraste réellement obtenu pour un thème. */
export function themeContrast(theme: Partial<Theme> | null): {
  accentOnNavy: number;
  inkOnApp: number;
} {
  const accent = hexToRgb(resolve(theme?.primary, DEFAULT_THEME.primary))!;
  const navy = hexToRgb(resolve(theme?.secondary, DEFAULT_THEME.secondary))!;
  return {
    accentOnNavy: contrastRatio(contrastingVariant(accent, navy, ON_DARK_TARGET), navy),
    inkOnApp: contrastRatio(
      darkenToContrast(shade(navy, INK.lightness, INK.chroma), BG_APP, INK_TARGET),
      BG_APP,
    ),
  };
}
