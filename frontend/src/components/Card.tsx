import type { ReactNode } from 'react';

/**
 * The standard surface: white on the grey canvas, card shadow, lg radius.
 *
 * <p>`padding="none"` exists for cards whose content goes edge to edge — a table, a
 * full-bleed image — so those stop hand-rolling their own container.
 */
export function Card({
  children,
  padding = 'md',
  hoverable = false,
  className = '',
}: {
  children: ReactNode;
  padding?: 'none' | 'sm' | 'md';
  hoverable?: boolean;
  className?: string;
}) {
  const pad = padding === 'none' ? '' : padding === 'sm' ? 'p-5' : 'p-6';
  // Lift on hover only where the card is a link or opens something; a static card that
  // reacts to the cursor promises an interaction it does not have.
  const hover = hoverable
    ? 'transition-shadow duration-150 hover:shadow-pop cursor-pointer'
    : '';
  return (
    // `min-w-0`: a grid/flex item defaults to `min-width: auto`, so a card whose content
    // has a wide min-content (a donut beside a label beside a button) refuses to shrink
    // and pushes the whole page into horizontal scroll at 375px. Fixing it here means no
    // page has to remember.
    <div className={`min-w-0 rounded-lg bg-surface shadow-card ${pad} ${hover} ${className}`}>
      {children}
    </div>
  );
}

/**
 * `h2` plus an optional right-hand action. One component so the gap between a section
 * title and its button is the same everywhere.
 */
export function SectionHeader({
  title,
  caption,
  action,
  className = '',
}: {
  title: string;
  caption?: string;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div className={`mb-4 flex items-start justify-between gap-4 ${className}`}>
      <div>
        <h2 className="text-h2 text-brand-navy">{title}</h2>
        {caption ? <p className="mt-0.5 text-caption text-gray-500">{caption}</p> : null}
      </div>
      {action ? <div className="shrink-0">{action}</div> : null}
    </div>
  );
}

/**
 * The page-level header every screen opens with: title, caption, action on the right.
 * Distinct from SectionHeader by scale — `display` vs `h2`.
 */
export function PageHeader({
  title,
  caption,
  action,
}: {
  title: string;
  caption?: string;
  action?: ReactNode;
}) {
  return (
    <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
      <div>
        <h1 className="text-display text-brand-navy">{title}</h1>
        {caption ? <p className="mt-1 text-body text-gray-500">{caption}</p> : null}
      </div>
      {action ? <div className="shrink-0">{action}</div> : null}
    </div>
  );
}
