import type { NotificationKind } from './schemas';

/** Shared between the topbar dropdown and the full-history page. */
export const NOTIFICATION_KIND_ICON: Record<NotificationKind, string> = {
  UPDATE: '🆕',
  QUESTIONS: '❓',
  INFO: 'ℹ️',
};

export function relativeDate(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return "à l'instant";
  if (mins < 60) return `il y a ${mins} min`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `il y a ${hours} h`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `il y a ${days} j`;
  return new Date(iso).toLocaleDateString('fr-FR');
}
