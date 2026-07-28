import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { Button, EmptyState, ListSkeleton } from '../../components/ui';
import { t, type TranslationKey } from '../../i18n/fr';
import {
  deleteNotification,
  fetchNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from '../../lib/endpoints';
import { NOTIFICATION_KIND_ICON as KIND_ICON, relativeDate } from '../../lib/notificationUi';

export function NotificationsPage() {
  const queryClient = useQueryClient();
  const notifications = useQuery({ queryKey: ['notifications'], queryFn: fetchNotifications });
  const markedRef = useRef(false);

  const invalidate = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ['notifications'] }),
      queryClient.invalidateQueries({ queryKey: ['unread-count'] }),
    ]);

  const markRead = useMutation({ mutationFn: markNotificationRead, onSuccess: invalidate });
  const markAll = useMutation({ mutationFn: markAllNotificationsRead, onSuccess: invalidate });
  const remove = useMutation({ mutationFn: deleteNotification, onSuccess: invalidate });

  // Ouverture de la page = tout marquer comme lu (une seule fois), comme la référence.
  useEffect(() => {
    const hasUnread = notifications.data?.some((n) => !n.read);
    if (hasUnread && !markedRef.current) {
      markedRef.current = true;
      markAll.mutate();
    }
  }, [notifications.data, markAll]);

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <h1 className="text-h1 text-brand-navy dark:text-white">
          {t('notifs.title')}
        </h1>
        {(notifications.data?.length ?? 0) > 0 ? (
          <Button variant="ghost" size="sm"
            onClick={() => markAll.mutate()}
            
          >
            {t('notifs.markAllRead')}
          </Button>
        ) : null}
      </div>

      {notifications.isLoading ? (
        <ListSkeleton rows={4} />
      ) : (notifications.data?.length ?? 0) === 0 ? (
        <EmptyState title={t('notifs.empty')} />
      ) : (
        <div className="space-y-2">
          {notifications.data?.map((notification) => (
            <div
              key={notification.id}
              onClick={() => !notification.read && markRead.mutate(notification.id)}
              className={`flex items-start gap-3 rounded-lg p-4 shadow-subtle ring-1 transition ${
                notification.read
                  ? 'bg-surface ring-subtle'
                  : 'bg-brand-green/5 ring-brand-green/30'
              }`}
            >
              <span className="text-xl" aria-hidden>
                {KIND_ICON[notification.kind]}
              </span>
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  {!notification.read ? (
                    <span className="h-2 w-2 shrink-0 rounded-full bg-brand-green" />
                  ) : null}
                  <p className="font-bold text-brand-navy">{notification.title}</p>
                  <span className="ml-auto shrink-0 rounded-full bg-gray-100 px-2 py-0.5 text-[10px] font-bold uppercase text-brand-gray">
                    {t(`notifs.kind${notification.kind}` as TranslationKey)}
                  </span>
                </div>
                <p className="mt-1 text-sm text-brand-gray">{notification.body}</p>
                <p className="mt-1 text-xs text-brand-gray">{relativeDate(notification.createdAt)}</p>
              </div>
              <Button
                variant="ghost"
                size="sm"
                onClick={(e) => {
                  e.stopPropagation();
                  remove.mutate(notification.id);
                }}
                aria-label={t('notifs.delete')}
                className="shrink-0 text-gray-500 hover:bg-danger/10 hover:text-danger"
              >
                🗑️
              </Button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
