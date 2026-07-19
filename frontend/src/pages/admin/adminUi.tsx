import type { ReactNode } from 'react';

/** Page title row with optional action buttons on the right. */
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
    <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
      <div>
        <h1 className="text-2xl font-extrabold text-brand-navy">{title}</h1>
        {subtitle ? <p className="mt-1 text-sm text-brand-gray">{subtitle}</p> : null}
      </div>
      {children ? <div className="flex flex-wrap items-center gap-2">{children}</div> : null}
    </div>
  );
}

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <div className={`rounded-2xl bg-white p-5 shadow-sm ring-1 ring-gray-100 ${className}`}>
      {children}
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
    <button
      type={type}
      onClick={onClick}
      disabled={disabled}
      className="rounded-lg bg-brand-green px-4 py-2 text-sm font-bold text-white transition hover:bg-brand-green-hover disabled:opacity-50"
    >
      {children}
    </button>
  );
}

export function GhostButton({
  children,
  onClick,
  tone = 'gray',
}: {
  children: ReactNode;
  onClick?: () => void;
  tone?: 'gray' | 'red';
}) {
  const cls =
    tone === 'red'
      ? 'border-red-200 text-red-600 hover:bg-red-50'
      : 'border-gray-300 text-brand-gray hover:bg-gray-50';
  return (
    <button
      type="button"
      onClick={onClick}
      className={`rounded-lg border px-3 py-1.5 text-sm font-semibold transition ${cls}`}
    >
      {children}
    </button>
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
    green: 'bg-green-100 text-green-800',
    gray: 'bg-gray-100 text-gray-700',
    red: 'bg-red-100 text-red-700',
    amber: 'bg-amber-100 text-amber-800',
    navy: 'bg-blue-100 text-blue-800',
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
      <button
        onClick={() => onPage(page - 1)}
        disabled={page <= 0}
        className="rounded-lg border border-gray-300 px-3 py-1.5 font-semibold text-brand-gray disabled:opacity-40"
      >
        ← Précédent
      </button>
      <span className="text-brand-gray">
        Page {page + 1} / {totalPages}
      </span>
      <button
        onClick={() => onPage(page + 1)}
        disabled={page >= totalPages - 1}
        className="rounded-lg border border-gray-300 px-3 py-1.5 font-semibold text-brand-gray disabled:opacity-40"
      >
        Suivant →
      </button>
    </div>
  );
}
