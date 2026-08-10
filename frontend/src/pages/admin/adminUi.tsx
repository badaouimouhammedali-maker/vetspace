import type { ReactNode } from 'react';
import { Button, Card as KitCard, PageHeader } from '../../components/ui';

/**
 * Admin page title row. Delegates to the kit's PageHeader so admin and student pages
 * open identically — this used to be a second, slightly different implementation.
 */
export function AdminHeader({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle?: string;
  children?: ReactNode;
}) {
  return (
    <PageHeader
      title={title}
      {...(subtitle ? { caption: subtitle } : {})}
      {...(children
        ? { action: <div className="flex flex-wrap items-center gap-2">{children}</div> }
        : {})}
    />
  );
}

/** Kept as a name so admin pages need no edit; the kit's Card does the work. */
export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <KitCard padding="sm" className={className}>
      {children}
    </KitCard>
  );
}

/**
 * One toolbar shape for every admin table: search on the left, filters in the middle,
 * the primary action pinned right. Each table having its own arrangement is why they
 * never felt like the same console.
 */
export function AdminToolbar({
  search,
  filters,
  action,
}: {
  search?: ReactNode;
  filters?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center">
      {search ? <div className="sm:w-72">{search}</div> : null}
      {filters ? <div className="flex flex-wrap items-center gap-2">{filters}</div> : null}
      {action ? <div className="sm:ml-auto">{action}</div> : null}
    </div>
  );
}

export function PrimaryButton({
  children,
  onClick,
  type = 'button',
  disabled = false,
}: {
  children: ReactNode;
  onClick?: () => void;
  type?: 'button' | 'submit';
  disabled?: boolean;
}) {
  return (
    <Button variant="primary" size="md" type={type} onClick={onClick} disabled={disabled}>
      {children}
    </Button>
  );
}

export function GhostButton({
  children,
  onClick,
  tone = 'gray',
  type = 'button',
}: {
  children: ReactNode;
  onClick?: () => void;
  tone?: 'gray' | 'red';
  type?: 'button' | 'submit';
}) {
  // Delegates rather than re-implements: this was the last place in the admin area with
  // its own idea of what a button's radius, weight and transition are.
  //
  // `type` defaults to "button", not the HTML default of "submit". Every GhostButton is a
  // secondary action — Annuler, Fermer, Télécharger — and inside a <form> the HTML default
  // made all of them submit it: clicking "Annuler" in the JSON import dialog closed the
  // modal AND fired the import it was cancelling. Pass type="submit" explicitly if a ghost
  // button is ever genuinely the form's submit.
  return (
    <Button
      variant={tone === 'red' ? 'danger' : 'secondary'}
      size="sm"
      type={type}
      onClick={onClick}
      {...(tone === 'red'
        ? { className: 'bg-transparent border border-danger text-danger hover:bg-danger/10' }
        : {})}
    >
      {children}
    </Button>
  );
}

export function StatusBadge({
  tone,
  children,
}: {
  tone: 'green' | 'gray' | 'red' | 'amber' | 'navy';
  children: ReactNode;
}) {
  const tones: Record<string, string> = {
    green: 'bg-success/10 text-success-text',
    gray: 'bg-ink/10 text-ink',
    red: 'bg-danger/10 text-danger',
    amber: 'bg-warning/10 text-warning',
    navy: 'bg-brand-navy/10 text-brand-navy',
  };
  return (
    <span className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-bold ${tones[tone]}`}>
      {children}
    </span>
  );
}

export function Pager({
  page,
  totalPages,
  onPage,
}: {
  page: number;
  totalPages: number;
  onPage: (p: number) => void;
}) {
  if (totalPages <= 1) {
    return null;
  }
  return (
    <div className="mt-4 flex items-center justify-center gap-3 text-sm">
      <Button variant="secondary" size="sm" onClick={() => onPage(page - 1)} disabled={page <= 0}>
        ← Précédent
      </Button>
      <span className="text-ink-muted">
        Page {page + 1} / {totalPages}
      </span>
      <Button
        variant="secondary"
        size="sm"
        onClick={() => onPage(page + 1)}
        disabled={page >= totalPages - 1}
      >
        Suivant →
      </Button>
    </div>
  );
}
