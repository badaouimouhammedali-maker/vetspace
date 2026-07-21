import type { ButtonHTMLAttributes, ReactNode } from 'react';

/**
 * The controls `Button` deliberately does not cover.
 *
 * <p>These are the ones that stayed raw `<button>` through the Button migration, because
 * forcing them into a primary/secondary/ghost variant would have been a worse lie than
 * leaving them alone: they are not "the action on this screen", they are stateful. What
 * they still need is one definition of what *selected* looks like, and the semantics of
 * a real button (keyboard, focus ring, `aria-pressed`) rather than a clickable div.
 */

interface ToggleProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children'> {
  selected: boolean;
  children: ReactNode;
  className?: string;
}

/**
 * A few toggles are not "pick a view", they are "assert a fact" — the VRAI/FAUX pair on
 * a proposition being the case that forced this. There the selected fill has to carry
 * the meaning, so the tone is part of the control rather than a per-call-site override.
 */
export type ToggleTone = 'brand' | 'success' | 'danger';

const SELECTED_FILL: Record<ToggleTone, string> = {
  brand: 'bg-brand-green text-white',
  success: 'bg-success text-white',
  danger: 'bg-danger text-white',
};

/**
 * One segment of a segmented control, or a filter chip: a small pill that is either on
 * or off. Selected is a solid green fill because these sit in dense toolbars where a
 * tint alone does not survive next to surrounding text.
 */
export function SegmentToggle({
  selected,
  tone = 'brand',
  children,
  className = '',
  ...rest
}: ToggleProps & { tone?: ToggleTone }) {
  return (
    <button
      type="button"
      // `aria-pressed` is what makes a screen reader announce "pressed"/"not pressed";
      // without it a toggle is indistinguishable from an action button.
      aria-pressed={selected}
      {...rest}
      className={`rounded-md px-3 py-1.5 text-caption font-semibold transition-colors duration-150 ${
        selected ? SELECTED_FILL[tone] : 'text-brand-navy hover:bg-brand-navy/5'
      } ${className}`}
    >
      {children}
    </button>
  );
}

/**
 * The header of a collapsible section — an FAQ entry, a module that expands to its
 * courses.
 *
 * <p>Deliberately *not* `SelectableRow`: expanding a section is not selecting it, and
 * `aria-pressed` would tell a screen reader the wrong thing. This announces
 * `aria-expanded` instead, and owns the caret so it can never point the wrong way.
 */
export function Disclosure({
  open,
  children,
  className = '',
  ...rest
}: Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children'> & {
  open: boolean;
  children: ReactNode;
  className?: string;
}) {
  return (
    <button
      type="button"
      aria-expanded={open}
      {...rest}
      className={`flex w-full items-center gap-2 text-left text-body font-semibold text-brand-navy transition-colors duration-150 hover:text-brand-green ${className}`}
    >
      <span
        aria-hidden
        className={`shrink-0 text-caption text-brand-green transition-transform duration-150 ${
          open ? 'rotate-90' : ''
        }`}
      >
        ▸
      </span>
      {children}
    </button>
  );
}

/**
 * A row in a list of actions — the choices in the "repeat this session" dialog.
 *
 * <p>These are neither the screen's primary action nor a toggle: they are a menu. So
 * they get no `aria-pressed`, and a title/hint pair rather than a single label, which is
 * what kept them hand-rolled.
 */
export function MenuRow({
  title,
  hint,
  className = '',
  ...rest
}: Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children' | 'title'> & {
  title: ReactNode;
  hint?: ReactNode;
  className?: string;
}) {
  return (
    <button
      type="button"
      {...rest}
      className={`w-full rounded-md border border-subtle px-4 py-3 text-left transition-colors duration-150 hover:border-brand-green hover:bg-brand-green/5 disabled:cursor-not-allowed disabled:opacity-50 ${className}`}
    >
      <span className="block text-body font-semibold text-brand-navy">{title}</span>
      {hint ? <span className="mt-0.5 block text-caption text-gray-500">{hint}</span> : null}
    </button>
  );
}

/**
 * A full-width row that can be picked: a question in the player's navigator, a saved
 * filter. Selected is a tint plus a green edge — the same language the sidebar uses for
 * the active link, so "you are here" reads the same app-wide.
 */
export function SelectableRow({ selected, children, className = '', ...rest }: ToggleProps) {
  return (
    <button
      type="button"
      aria-pressed={selected}
      {...rest}
      className={`flex w-full items-center gap-3 rounded-md px-3 py-2 text-left text-body font-semibold transition-colors duration-150 ${
        selected
          ? 'bg-brand-green/15 text-brand-navy ring-1 ring-brand-green/40'
          : 'text-brand-navy hover:bg-brand-navy/5'
      } ${className}`}
    >
      {children}
    </button>
  );
}

/**
 * A button with no chrome of its own — an image that opens a lightbox, a star that
 * toggles a favourite, a dialog's ✕.
 *
 * <p>It exists so those stop being bare `<button className="group relative">`: the
 * element carries `type="button"` (so it never submits a surrounding form) and picks up
 * the global `:focus-visible` ring, which hand-rolled wrappers kept losing.
 */
export function PlainButton({
  children,
  className = '',
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button type="button" {...rest} className={`rounded-md ${className}`}>
      {children}
    </button>
  );
}
