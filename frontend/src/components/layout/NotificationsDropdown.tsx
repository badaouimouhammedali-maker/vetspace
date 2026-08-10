import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import { t, type TranslationKey } from '../../i18n/fr';
import {
  deleteNotification,
  fetchNotifications,
  markAllNotificationsRead,
} from '../../lib/endpoints';
import { NOTIFICATION_KIND_ICON, relativeDate } from '../../lib/notificationUi';
import { NotificationBadge } from './Shell';
import { useDropdown } from './useDropdown';

/**
 * The bell opens the notifications right here — a scrollable panel under the topbar —
 * instead of navigating away from whatever the student was doing. Reading a
 * notification is a two-second interruption; it should not cost the current page.
 *
 * <p>Behaviour mirrors the old full page: opening marks everything read (the badge is
 * the "something new" signal, and it has served its purpose once the panel is open).
 * The unread highlight stays visible while the panel is open — the notifications list
 * is only refetched on close — so what was new remains distinguishable while reading.
 *
 * <p>The full-history page at /app/notifications still exists behind the "voir tout"
 * link for anyone who wants more than the recent scroll.
 */
export function NotificationsDropdown({ unreadCount }: { unreadCount: number }) {
  const dropdown = useDropdown();
  const queryClient = useQueryClient();
  // Fetched only while open: the badge count has its own cheaper endpoint.
  const notifications = useQuery({
    queryKey: ['notifications'],
    queryFn: fetchNotifications,
    enabled: dropdown.open,
  });

  const markAll = useMutation({
    mutationFn: markAllNotificationsRead,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['unread-count'] }),
  });
  const remove = useMutation({
    mutationFn: deleteNotification,
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: ['notifications'] }),
        queryClient.invalidateQueries({ queryKey: ['unread-count'] }),
      ]),
  });

  // Once per opening, after the list has loaded: clear the badge server-side.
  const markedThisOpen = useRef(false);
  useEffect(() => {
    if (!dropdown.open) {
      markedThisOpen.current = false;
      return;
    }
    if (!markedThisOpen.current && notifications.data?.some((n) => !n.read)) {
      markedThisOpen.current = true;
      markAll.mutate();
    }
  }, [dropdown.open, notifications.data, markAll]);

  // On close, refetch so the next opening shows everything as read.
  const wasOpen = useRef(false);
  useEffect(() => {
    if (wasOpen.current && !dropdown.open) {
      void queryClient.invalidateQueries({ queryKey: ['notifications'] });
    }
    wasOpen.current = dropdown.open;
  }, [dropdown.open, queryClient]);

  return (
    <div className="relative" ref={dropdown.ref}>
      <button
        type="button"
        onClick={() => dropdown.setOpen((o) => !o)}
        aria-label={t('nav.notifications')}
        aria-expanded={dropdown.open}
        aria-haspopup="dialog"
        className="relative flex h-9 w-9 items-center justify-center rounded-md text-lg text-on-navy-muted transition-colors duration-150 hover:bg-on-navy/6 hover:text-white"
      >
        🔔
        <NotificationBadge count={unreadCount} />
      </button>

      {dropdown.open ? (
        <div
          role="dialog"
          aria-label={t('notifs.title')}
          className="absolute right-0 mt-2 w-[min(24rem,calc(100vw-1.5rem))] animate-toast-in rounded-lg bg-surface shadow-pop ring-1 ring-ink/5"
        >
          <p className="border-b border-subtle px-4 py-3 text-sm font-bold text-ink">
            {t('notifs.title')}
          </p>

          <div className="max-h-[min(24rem,60vh)] overflow-y-auto overscroll-contain">
            {notifications.isLoading ? (
              <div className="space-y-2 p-3" aria-hidden>
                {[0, 1, 2].map((i) => (
                  <div key={i} className="h-14 animate-pulse rounded-md bg-ink/10" />
                ))}
              </div>
            ) : (notifications.data?.length ?? 0) === 0 ? (
              <p className="px-4 py-8 text-center text-sm text-ink-muted">
                {t('notifs.empty')}
              </p>
            ) : (
              <ul className="divide-y divide-subtle">
                {notifications.data?.map((notification) => (
                  <li
                    key={notification.id}
                    className={`group flex items-start gap-3 px-4 py-3 ${
                      notification.read ? '' : 'bg-accent/5'
                    }`}
                  >
                    <span className="mt-0.5 text-lg" aria-hidden>
                      {NOTIFICATION_KIND_ICON[notification.kind]}
                    </span>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2">
                        {!notification.read ? (
                          <span className="h-2 w-2 shrink-0 rounded-full bg-accent" />
                        ) : null}
                        <p className="truncate text-sm font-bold text-ink">
                          {notification.title}
                        </p>
                        <span className="ml-auto shrink-0 text-xs text-ink-muted">
                          {relativeDate(notification.createdAt)}
                        </span>
                      </div>
                      <p className="mt-0.5 line-clamp-3 text-sm text-ink-muted">
                        {notification.body}
                      </p>
                      <p className="mt-1 text-[10px] font-bold uppercase text-ink-muted/70">
                        {t(`notifs.kind${notification.kind}` as TranslationKey)}
                      </p>
                    </div>
                    <button
                      type="button"
                      onClick={() => remove.mutate(notification.id)}
                      aria-label={t('notifs.delete')}
                      className="shrink-0 rounded-md p-1 text-sm text-ink-muted opacity-0 transition hover:bg-danger/10 hover:text-danger focus-visible:opacity-100 group-hover:opacity-100"
                    >
                      🗑️
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <Link
            to="/app/notifications"
            onClick={() => dropdown.setOpen(false)}
            className="block border-t border-subtle px-4 py-2.5 text-center text-sm font-semibold text-accent hover:bg-accent/6"
          >
            {t('notifs.viewAll')}
          </Link>
        </div>
      ) : null}
    </div>
  );
}
