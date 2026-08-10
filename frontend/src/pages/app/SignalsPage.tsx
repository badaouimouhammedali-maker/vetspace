import { useQuery } from '@tanstack/react-query';
import { EmptyState, ListSkeleton } from '../../components/ui';
import { t, type TranslationKey } from '../../i18n/fr';
import { fetchSignals } from '../../lib/endpoints';
import type { SignalStatus } from '../../lib/schemas';

const STATUS_STYLES: Record<SignalStatus, string> = {
  OPEN: 'bg-warning/10 text-warning',
  RESOLVED: 'bg-success/10 text-success-text',
  REJECTED: 'bg-danger/10 text-danger',
};

export function SignalsPage() {
  const signals = useQuery({ queryKey: ['signals'], queryFn: fetchSignals });

  return (
    <div className="space-y-5">
      <h1 className="text-h1 text-ink dark:text-white">
        {t('signals.title')}
      </h1>

      {signals.isLoading ? (
        <ListSkeleton rows={4} />
      ) : (signals.data?.length ?? 0) === 0 ? (
        <EmptyState title={t('signals.empty')} />
      ) : (
        <div className="space-y-3">
          {signals.data?.map((signal) => (
            <div key={signal.id} className="rounded-lg bg-surface p-5 shadow-card">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <p className="min-w-0 flex-1 font-semibold text-ink">
                  {signal.questionStatement}
                </p>
                <span
                  className={`shrink-0 rounded-full px-3 py-1 text-xs font-bold ${STATUS_STYLES[signal.status]}`}
                >
                  {t(`signals.status${signal.status}` as TranslationKey)}
                </span>
              </div>
              <p className="mt-2 text-sm text-ink-muted">{signal.message}</p>
              {signal.adminReply ? (
                <div className="mt-3 rounded-lg bg-accent/5 p-3">
                  <p className="text-xs font-bold uppercase tracking-wide text-accent">
                    {t('signals.adminReply')}
                  </p>
                  <p className="mt-1 text-sm text-ink">{signal.adminReply}</p>
                </div>
              ) : null}
              <p className="mt-3 text-xs text-ink-muted">
                {new Date(signal.createdAt).toLocaleDateString('fr-FR')}
              </p>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
