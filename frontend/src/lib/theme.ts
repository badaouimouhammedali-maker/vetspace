/**
 * Thème runtime. Les variables CSS --color-primary/secondary/tertiary sont
 * stockées en canaux RGB (« r g b ») pour que les modificateurs d'opacité
 * Tailwind (bg-brand-green/10) fonctionnent. Les valeurs côté API/formulaire
 * restent en hexadécimal (#rrggbb) ; on convertit uniquement à l'écriture.
 */
export const DEFAULT_THEME = {
  primary: '#0f766e',
  secondary: '#12355b',
  tertiary: '#6b7280',
} as const;

export interface Theme {
  primary: string | null;
  secondary: string | null;
  tertiary: string | null;
}

/** "#0F766E" -> "15 118 110" ; renvoie null si l'entrée n'est pas un hex valide. */
function hexToChannels(hex: string): string | null {
  const match = /^#?([0-9a-fA-F]{6})$/.exec(hex.trim());
  const value = match?.[1];
  if (!value) {
    return null;
  }
  const r = parseInt(value.slice(0, 2), 16);
  const g = parseInt(value.slice(2, 4), 16);
  const b = parseInt(value.slice(4, 6), 16);
  return `${r} ${g} ${b}`;
}

/** Applique le thème (hex nullable) aux variables CSS globales, en repli sur le défaut. */
export function applyTheme(theme: Partial<Theme> | null): void {
  const root = document.documentElement;
  const set = (name: string, hex: string | null | undefined, fallback: string) => {
    root.style.setProperty(name, hexToChannels(hex ?? '') ?? hexToChannels(fallback)!);
  };
  set('--color-primary', theme?.primary, DEFAULT_THEME.primary);
  set('--color-secondary', theme?.secondary, DEFAULT_THEME.secondary);
  set('--color-tertiary', theme?.tertiary, DEFAULT_THEME.tertiary);
}
