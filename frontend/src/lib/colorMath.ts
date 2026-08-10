/**
 * Maths couleur pour la dérivation du thème runtime.
 *
 * <p>Les jetons dérivés (survol, pressé, accent-sur-navy) sont des *relations* et non
 * des valeurs : « le même accent, plus sombre » ou « le même accent, assez clair pour
 * rester lisible sur le navy ». Un utilisateur peut choisir n'importe laquelle des 16,7
 * millions de couleurs — figer ces dérivés en dur casserait la relation dès qu'il
 * s'éloigne du teal par défaut.
 *
 * <p>On passe par OKLCH plutôt que HSL : la clarté y est perceptuelle, donc « −15 % de
 * clarté » donne le même écart visuel sur un jaune et sur un bleu. En HSL, assombrir un
 * jaune et un bleu du même montant donne deux résultats très différents.
 *
 * <p>Rien ici ne touche au DOM — c'est volontaire, pour que les dérivations soient
 * testables sans navigateur.
 */

/** sRGB (0–1) → linéaire. */
const toLinear = (v: number): number => (v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4);

/** Linéaire → sRGB (0–1). */
const toSrgb = (v: number): number =>
  v <= 0.0031308 ? 12.92 * v : 1.055 * v ** (1 / 2.4) - 0.055;

const clamp = (v: number, min: number, max: number): number => Math.min(max, Math.max(min, v));

export type Rgb = readonly [number, number, number];

interface Oklch {
  /** Clarté perceptuelle, 0–1. */
  l: number;
  /** Chroma (saturation), 0 ≈ gris. */
  c: number;
  /** Teinte, en radians. */
  h: number;
}

/** "#0F766E" ou "0F766E" → [15, 118, 110] ; null si l'entrée n'est pas un hex 6 chiffres. */
export function hexToRgb(hex: string): Rgb | null {
  const match = /^#?([0-9a-fA-F]{6})$/.exec(hex.trim());
  const value = match?.[1];
  if (!value) {
    return null;
  }
  return [
    parseInt(value.slice(0, 2), 16),
    parseInt(value.slice(2, 4), 16),
    parseInt(value.slice(4, 6), 16),
  ] as const;
}

/** [15, 118, 110] → "15 118 110", la forme attendue par les variables CSS. */
export function toChannels([r, g, b]: Rgb): string {
  return `${r} ${g} ${b}`;
}

function rgbToOklch([r, g, b]: Rgb): Oklch {
  const lr = toLinear(r / 255);
  const lg = toLinear(g / 255);
  const lb = toLinear(b / 255);

  const l = Math.cbrt(0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb);
  const m = Math.cbrt(0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb);
  const s = Math.cbrt(0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb);

  const okL = 0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s;
  const okA = 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s;
  const okB = 0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s;

  return { l: okL, c: Math.hypot(okA, okB), h: Math.atan2(okB, okA) };
}

function oklchToRgb({ l, c, h }: Oklch): Rgb {
  const okA = c * Math.cos(h);
  const okB = c * Math.sin(h);

  const lc = (l + 0.3963377774 * okA + 0.2158037573 * okB) ** 3;
  const mc = (l - 0.1055613458 * okA - 0.0638541728 * okB) ** 3;
  const sc = (l - 0.0894841775 * okA - 1.291485548 * okB) ** 3;

  const lr = 4.0767416621 * lc - 3.3077115913 * mc + 0.2309699292 * sc;
  const lg = -1.2684380046 * lc + 2.6097574011 * mc - 0.3413193965 * sc;
  const lb = -0.0041960863 * lc - 0.7034186147 * mc + 1.707614701 * sc;

  // Le clamp par canal sort du gamut sRGB « à la dure ». Une réduction de chroma serait
  // plus fidèle, mais les seules entrées possibles ici sont déjà des couleurs sRGB
  // légèrement décalées — l'écart est invisible et le code reste lisible.
  return [lr, lg, lb].map((v) => Math.round(clamp(toSrgb(v), 0, 1) * 255)) as unknown as Rgb;
}

/** Luminance relative WCAG. */
function luminance([r, g, b]: Rgb): number {
  const [lr, lg, lb] = [r, g, b].map((v) => toLinear(v / 255)) as unknown as Rgb;
  return 0.2126 * lr + 0.7152 * lg + 0.0722 * lb;
}

/** Rapport de contraste WCAG entre deux couleurs, de 1 (identiques) à 21 (noir/blanc). */
export function contrastRatio(a: Rgb, b: Rgb): number {
  const la = luminance(a);
  const lb = luminance(b);
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
}

/**
 * Même teinte, clarté et chroma mis à l'échelle.
 *
 * <p>Les facteurs sont calibrés sur les jetons de référence : appliqués au teal par
 * défaut ils reproduisent les hex de la charte à 1–3 unités par canal près. Ils ne
 * servent qu'aux thèmes personnalisés — le thème par défaut lit les hex exacts depuis
 * `:root`, donc cet écart n'atteint jamais l'écran d'un utilisateur non personnalisé.
 */
export function shade(rgb: Rgb, lightnessFactor: number, chromaFactor: number): Rgb {
  const { l, c, h } = rgbToOklch(rgb);
  return oklchToRgb({ l: clamp(l * lightnessFactor, 0, 1), c: c * chromaFactor, h });
}

const WHITE: Rgb = [255, 255, 255] as const;
const BLACK: Rgb = [0, 0, 0] as const;

/**
 * Décale `color` en clarté jusqu'à atteindre `target` de contraste sur `background`.
 *
 * <p>C'est la dérivation qui compte vraiment pour l'accessibilité : `--accent-on-dark`
 * existe parce que l'accent lui-même est illisible sur le navy (2,3:1). Viser un ratio
 * plutôt qu'un éclaircissement fixe garantit la règle quel que soit l'accent choisi.
 *
 * <p>La *direction* se déduit du fond, pas de l'intention : éclaircir sur un fond
 * sombre, assombrir sur un fond clair. Toujours éclaircir serait un bug dès qu'un
 * utilisateur choisit un « navy » pâle — on obtiendrait du blanc sur du rose pâle
 * (1,4:1) au lieu de foncer.
 *
 * <p>Quand aucune clarté ne satisfait la cible — un fond de luminance moyenne n'a
 * parfois aucune solution, quelle que soit la teinte — on ne renvoie *pas* la dernière
 * itération de la recherche : on renvoie explicitement le blanc ou le noir, celui des
 * deux qui contraste le plus avec ce fond, et on le signale en développement.
 */
export function contrastingVariant(
  color: Rgb,
  background: Rgb,
  target: number,
  chromaFactor = 1.316,
): Rgb {
  const { l, c, h } = rgbToOklch(color);
  const chroma = c * chromaFactor;
  // Le seuil 0,18 est le point où le blanc et le noir contrastent également (≈4,6:1
  // chacun) : au-dessus, foncer donne plus de marge ; en dessous, éclaircir.
  const lighten = luminance(background) < 0.18;

  const extreme = oklchToRgb({ l: lighten ? 1 : 0, c: chroma, h });
  if (contrastRatio(extreme, background) < target) {
    const fallback =
      contrastRatio(WHITE, background) >= contrastRatio(BLACK, background) ? WHITE : BLACK;
    if (import.meta.env.DEV) {
      console.warn(
        `[theme] Aucune clarté ne donne ${target}:1 sur rgb(${background.join(' ')}) ` +
          `pour rgb(${color.join(' ')}). Repli sur rgb(${fallback.join(' ')}) ` +
          `(${contrastRatio(fallback, background).toFixed(2)}:1).`,
      );
    }
    return fallback;
  }

  // Dichotomie vers la plus petite variation qui tient la cible, pour rester aussi
  // proche que possible de la couleur choisie par l'utilisateur.
  let near = l;
  let far = lighten ? 1 : 0;
  let best = extreme;
  for (let i = 0; i < 30; i += 1) {
    const mid = (near + far) / 2;
    const candidate = oklchToRgb({ l: mid, c: chroma, h });
    if (contrastRatio(candidate, background) >= target) {
      far = mid;
      best = candidate;
    } else {
      near = mid;
    }
  }
  return best;
}

/**
 * Assombrit `color` jusqu'à ce qu'elle atteigne `target` de contraste sur `background`.
 *
 * <p>Le pendant à sens unique de `contrastingVariant`, pour l'encre sur le fond
 * d'application — qui n'est pas thémable et donc toujours quasi blanc. Aujourd'hui un
 * utilisateur qui choisit un secondaire pâle obtient des titres quasi invisibles ; ce
 * garde-fou l'empêche. Le noir atteint toujours la cible sur un fond clair, donc le
 * repli initial ci-dessous ne peut pas être atteint sans l'avoir vérifié.
 */
export function darkenToContrast(color: Rgb, background: Rgb, target: number): Rgb {
  const { l, c, h } = rgbToOklch(color);

  if (contrastRatio(color, background) >= target) {
    return color;
  }

  let lo = 0;
  let hi = l;
  let best: Rgb = BLACK;
  for (let i = 0; i < 30; i += 1) {
    const mid = (lo + hi) / 2;
    const candidate = oklchToRgb({ l: mid, c, h });
    if (contrastRatio(candidate, background) >= target) {
      lo = mid;
      best = candidate;
    } else {
      hi = mid;
    }
  }
  return best;
}
