import { useQuery } from '@tanstack/react-query';
import { EmptyState, Skeleton } from '../../components/ui';
import { t, type TranslationKey } from '../../i18n/fr';
import { fetchSignals } from '../../lib/endpoints';
import type { SignalStatus } from '../../lib/schemas';

const STATUS_STYLES: Record<SignalStatus, string> = {
  OPEN: 'bg-orange-100 text-orange-700',
  RESOLVED: 'bg-brand-green/15 text-brand-green',
  REJECTED: 'bg-red-100 text-red-700',
};

export function SignalsPage() {
  const signals = useQuery({ queryKey: ['signals'], queryFn: fetchSignals });

  return (
    <div className="space-y-5">
      <h1 className="text-2xl font-extrabold text-brand-navy dark:text-white">
        {t('signals.title')}
      </h1>

      {signals.isLoading ? (
        <Skeleton className="h-40" />
      ) : (signals.data?.length ?? 0) === 0 ? (
        <EmptyState message={t('signals.empty')} />
      ) : (
        <div className="space-y-3">
          {signals.data?.map((signal) => (
            <div key={signal.id} className="rounded-2xl bg-white p-5 shadow-sm ring-1 ring-gray-100">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <p className="min-w-0 flex-1 font-semibold text-brand-navy">
                  {signal.questionStatement}
                </p>
                <span
                  className={`shrink-0 rounded-full px-3 py-1 text-xs font-bold ${STATUS_STYLES[signal.status]}`}
                >
                  {t(`signals.status${signal.status}` as TranslationKey)}
                </span>
              </div>
              <p className="mt-2 text-sm text-brand-gray">{signal.message}</p>
              {signal.adminReply ? (
                <div className="mt-3 rounded-lg bg-brand-green/5 p-3">
                  <p className="text-xs font-bold uppercase tracking-wide text-brand-green">
                    {t('signals.adminReply')}
                  </p>
                  <p className="mt-1 text-sm text-brand-navy">{signal.adminReply}</p>
                </div>
              ) : null}
              <p className="mt-3 text-xs text-brand-gray">
                {new Date(signal.createdAt).toLocaleDateString('fr-FR')}
              </p>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
