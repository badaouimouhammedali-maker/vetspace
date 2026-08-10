import type { ReactNode } from 'react';

/**
 * "Nothing here yet" with a way out.
 *
 * <p>The CTA is the point: an empty list that only says it is empty leaves the user to
 * work out what to do next. Every empty state in the app should offer the action that
 * fills it.
 */
export function EmptyState({
  icon = '🐾',
  title,
  caption,
  action,
  compact = false,
}: {
  icon?: ReactNode;
  title: string;
  caption?: string;
  action?: ReactNode;
  /**
   * For an empty *region* inside an already-framed area — the courses under an expanded
   * module, a sub-table in a drawer. The full-height panel is right for a page, but
   * nested inside a card it shouts; this keeps the same visual language at a whisper.
   */
  compact?: boolean;
}) {
  return (
    <div
      className={`flex flex-col items-center gap-3 rounded-lg border border-dashed border-subtle bg-surface text-center ${
        compact ? 'gap-1 px-4 py-6' : 'px-6 py-14'
      }`}
    >
      <span className={compact ? 'text-body' : 'text-3xl'} aria-hidden="true">
        {icon}
      </span>
      <p className={compact ? 'text-caption font-semibold text-ink-muted' : 'text-h2 text-ink'}>
        {title}
      </p>
      {caption ? <p className="max-w-sm text-body text-ink-muted">{caption}</p> : null}
      {action ? <div className="mt-2">{action}</div> : null}
    </div>
  );
}
