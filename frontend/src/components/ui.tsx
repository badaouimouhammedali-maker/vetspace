/* eslint-disable react-refresh/only-export-components -- composants UI + aides exportés ensemble, perte de HMR acceptée */
import { useEffect, type ReactNode } from 'react';
import { t } from '../i18n/fr';

/** Anneau de progression SVG (donut) avec pourcentage au centre. */
export function Donut({
  percent,
  size = 72,
  stroke = 8,
  color = 'rgb(var(--color-primary))',
  label,
}: {
  percent: number;
  size?: number;
  stroke?: number;
  color?: string;
  label?: string;
}) {
  const radius = (size - stroke) / 2;
  const circumference = 2 * Math.PI * radius;
  const clamped = Math.max(0, Math.min(100, percent));
  return (
    <svg width={size} height={size} role="img" aria-label={`${Math.round(clamped)}%`}>
      <circle
        cx={size / 2}
        cy={size / 2}
        r={radius}
        fill="none"
        stroke="#E5E7EB"
        strokeWidth={stroke}
      />
      <circle
        cx={size / 2}
        cy={size / 2}
        r={radius}
        fill="none"
        stroke={color}
        strokeWidth={stroke}
        strokeLinecap="round"
        strokeDasharray={circumference}
        strokeDashoffset={circumference * (1 - clamped / 100)}
        transform={`rotate(-90 ${size / 2} ${size / 2})`}
      />
      <text
        x="50%"
        y="50%"
        dominantBaseline="central"
        textAnchor="middle"
        className="fill-brand-navy text-xs font-bold"
      >
        {label ?? `${Math.round(clamped)}%`}
      </text>
    </svg>
  );
}

/** Boîte de dialogue modale accessible au clavier (Échap pour fermer). */
export function Modal({
  open,
  onClose,
  title,
  children,
  wide = false,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
  wide?: boolean;
}) {
  useEffect(() => {
    if (!open) {
      return;
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  if (!open) {
    return null;
  }
  return (
    <div
      className="fixed inset-0 z-40 flex items-center justify-center bg-brand-navy/40 p-4"
      onClick={onClose}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className={`max-h-[90vh] w-full ${wide ? 'max-w-2xl' : 'max-w-md'} overflow-y-auto rounded-2xl bg-white p-6 shadow-xl`}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-extrabold text-brand-navy">{title}</h2>
          <button
            onClick={onClose}
            aria-label={t('play.close')}
            className="rounded-full px-2 text-xl font-bold text-brand-gray hover:text-brand-navy"
          >
            ×
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}

export function EmptyState({ message, action }: { message: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-4 rounded-2xl border-2 border-dashed border-gray-200 bg-white p-12 text-center">
      <span className="text-4xl">🐾</span>
      <p className="text-sm font-medium text-brand-gray">{message}</p>
      {action}
    </div>
  );
}

export function Skeleton({ className = '' }: { className?: string }) {
  return <div className={`animate-pulse rounded-lg bg-gray-200 ${className}`} />;
}

const EMOJIS = ['😞', '😕', '😐', '🙂', '🤩'] as const;

/** Notation 1–5 par émoji. */
export function EmojiRating({
  value,
  onChange,
}: {
  value: number | null;
  onChange: (rating: number) => void;
}) {
  return (
    <div className="flex gap-1">
      {EMOJIS.map((emoji, index) => {
        const rating = index + 1;
        return (
          <button
            key={rating}
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              onChange(rating);
            }}
            aria-label={`Note ${rating}/5`}
            className={`rounded text-lg transition ${
              value === rating ? 'scale-125' : 'opacity-40 hover:opacity-100'
            }`}
          >
            {emoji}
          </button>
        );
      })}
    </div>
  );
}

/** Chip de précision coloré : vert ≥70, orange 40–69, rouge <40. */
export function PrecisionChip({ percent }: { percent: number }) {
  const color =
    percent >= 70
      ? 'bg-brand-green/15 text-brand-green'
      : percent >= 40
        ? 'bg-orange-100 text-orange-700'
        : 'bg-red-100 text-red-700';
  return (
    <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-bold ${color}`}>
      {Math.round(percent)}%
    </span>
  );
}

/** hh:mm:ss compact (mm:ss sous l'heure). */
export function formatSeconds(total: number): string {
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  const mm = String(m).padStart(2, '0');
  const ss = String(s).padStart(2, '0');
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`;
}
