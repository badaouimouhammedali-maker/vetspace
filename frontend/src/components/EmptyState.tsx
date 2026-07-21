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
}: {
  icon?: ReactNode;
  title: string;
  caption?: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-lg border border-dashed border-subtle bg-surface px-6 py-14 text-center">
      <span className="text-3xl" aria-hidden="true">
        {icon}
      </span>
      <p className="text-h2 text-brand-navy">{title}</p>
      {caption ? <p className="max-w-sm text-body text-gray-500">{caption}</p> : null}
      {action ? <div className="mt-2">{action}</div> : null}
    </div>
  );
}
