import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { Link } from 'react-router-dom';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';
export type ButtonSize = 'sm' | 'md';

/**
 * The only button in the app.
 *
 * <p>Every raw `<button className=…>` was a chance for the radius, weight, transition
 * or focus ring to drift a little; 84 of them had. The focus ring is inherited from the
 * global `:focus-visible` rule in index.css rather than restated here, so it cannot be
 * forgotten or overridden per-variant.
 */
const BASE =
  'inline-flex items-center justify-center gap-2 rounded-md font-semibold ' +
  'transition-colors duration-150 disabled:opacity-50 disabled:cursor-not-allowed';

const VARIANTS: Record<ButtonVariant, string> = {
  primary: 'bg-accent text-white hover:bg-accent-hover active:bg-accent-pressed',
  // Outlined: the "second choice" next to an accent primary, still clearly a button.
  // The border is the same hairline as every card and input, so the button sits in the
  // same line-weight family as the rest of the surface.
  secondary: 'border border-subtle bg-surface text-ink hover:border-strong hover:bg-accent/6',
  ghost: 'text-ink hover:bg-accent/6',
  danger: 'bg-danger text-white hover:bg-danger/90',
};

const SIZES: Record<ButtonSize, string> = {
  sm: 'h-8 px-3 text-caption',
  md: 'h-10 px-4 text-body',
};

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
  children: ReactNode;
}

export function Button({
  variant = 'primary',
  size = 'md',
  loading = false,
  disabled,
  className = '',
  children,
  ...rest
}: ButtonProps) {
  return (
    <button
      {...rest}
      // A loading button must also be un-clickable, or a double submit slips through
      // while the spinner is showing.
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={`${BASE} ${VARIANTS[variant]} ${SIZES[size]} ${className}`}
    >
      {loading ? <Spinner /> : children}
    </button>
  );
}

/**
 * Replaces the label rather than sitting beside it, so the button keeps its width and
 * the row does not reflow mid-click.
 */
function Spinner() {
  return (
    <svg
      className="h-4 w-4 animate-spin"
      viewBox="0 0 24 24"
      fill="none"
      role="status"
      aria-label="Chargement"
    >
      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="3" />
      <path
        className="opacity-90"
        fill="currentColor"
        d="M12 2a10 10 0 0 1 10 10h-3a7 7 0 0 0-7-7V2z"
      />
    </svg>
  );
}

/**
 * A router Link wearing Button's clothes.
 *
 * <p>Several "buttons" in the app are navigations, and were hand-styled to look like
 * the real thing — which is how they drifted. This keeps the anchor semantics (middle
 * click, open in new tab, copy link) while sharing one visual definition.
 */
export function LinkButton({
  to,
  variant = 'primary',
  size = 'md',
  className = '',
  children,
  ...rest
}: {
  to: string;
  variant?: ButtonVariant;
  size?: ButtonSize;
  className?: string;
  children: ReactNode;
  'aria-label'?: string;
}) {
  return (
    <Link to={to} className={`${BASE} ${VARIANTS[variant]} ${SIZES[size]} ${className}`} {...rest}>
      {children}
    </Link>
  );
}
