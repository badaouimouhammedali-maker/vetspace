/* eslint-disable react-refresh/only-export-components -- composants UI + aides exportés ensemble, perte de HMR acceptée */

/**
 * The UI kit's front door.
 *
 * <p>Components live in siblings (Button, Card, Badge, …) and are re-exported here, so
 * every existing `from '../../components/ui'` import keeps working and there is still
 * one obvious place to look for "what can I build with".
 */
export { Button, LinkButton, type ButtonSize, type ButtonVariant } from './Button';
export { Card, PageHeader, SectionHeader } from './Card';
export { Badge, PrecisionChip, type BadgeTone } from './Badge';
export { EmptyState } from './EmptyState';
export {
  Disclosure,
  MenuRow,
  PlainButton,
  SegmentToggle,
  SelectableRow,
  type ToggleTone,
} from './Toggle';
export { Modal } from './Modal';
export {
  CardGridSkeleton,
  DashboardSkeleton,
  ListSkeleton,
  Skeleton,
  TableSkeleton,
} from './Skeletons';

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
            className={`rounded text-lg transition-transform duration-150 ${
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

/** hh:mm:ss compact (mm:ss sous l'heure). */
export function formatSeconds(total: number): string {
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  const mm = String(m).padStart(2, '0');
  const ss = String(s).padStart(2, '0');
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`;
}
