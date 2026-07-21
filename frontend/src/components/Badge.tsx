import type { ReactNode } from 'react';

export type BadgeTone = 'neutral' | 'brand' | 'success' | 'warning' | 'danger';

/**
 * Small status pill. Every tone is a soft `/10` fill with the strong colour as text, so
 * badges never compete with buttons for attention — which is what the old solid
 * `bg-red-500` badges were doing.
 */
const TONES: Record<BadgeTone, string> = {
  neutral: 'bg-gray-100 text-gray-700',
  brand: 'bg-brand-green/10 text-brand-green',
  success: 'bg-success/10 text-success',
  warning: 'bg-warning/10 text-warning',
  danger: 'bg-danger/10 text-danger',
};

export function Badge({
  tone = 'neutral',
  children,
  className = '',
}: {
  tone?: BadgeTone;
  children: ReactNode;
  className?: string;
}) {
  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-caption font-semibold ${TONES[tone]} ${className}`}
    >
      {children}
    </span>
  );
}

/**
 * Accuracy percentage as a badge. Thresholds live here rather than at each call site so
 * "what counts as good" is answered once.
 */
export function PrecisionChip({ percent }: { percent: number }) {
  const tone: BadgeTone = percent >= 70 ? 'success' : percent >= 40 ? 'warning' : 'danger';
  return <Badge tone={tone}>{Math.round(percent)}%</Badge>;
}
