import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { useAuth } from '../../auth/AuthContext';
import { Button, CardGridSkeleton, EmptyState } from '../../components/ui';
import { useToast } from '../../components/ToastProvider';
import { t } from '../../i18n/fr';
import { apiErrorMessage, apiErrorStatus } from '../../lib/api';
import { fetchPacks, redeemCode } from '../../lib/endpoints';

export function AbonnementPage() {
  const { user, refreshUser } = useAuth();
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const packs = useQuery({
    queryKey: ['packs', user?.schoolId, user?.studyYear],
    queryFn: () => fetchPacks(user?.schoolId ?? null, user?.studyYear ?? null),
  });

  const redeem = useMutation({
    mutationFn: () => redeemCode(code),
    onSuccess: async (response) => {
      setSuccess(t('abo.redeemSuccess').replace('{pack}', response.packName));
      setCode('');
      toast('success', t('abo.redeemSuccess').replace('{pack}', response.packName));
      // L'abonnement vient de changer : profil + toutes les données verrouillées.
      await Promise.all([
        refreshUser(),
        queryClient.invalidateQueries({ queryKey: ['overview'] }),
        queryClient.invalidateQueries({ queryKey: ['sessions'] }),
      ]);
    },
    onError: (err) => {
      setSuccess(null);
      setError(apiErrorStatus(err) === 429 ? t('api.rateLimited') : (apiErrorMessage(err) ?? t('abo.redeemError')));
    },
  });

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    redeem.mutate();
  }

  return (
    <div className="space-y-6">
      <h1 className="text-h1 text-brand-navy dark:text-white">
        {t('app.subscription')}
      </h1>

      <div className="grid gap-4 lg:grid-cols-2">
        {/* Activation d'un code */}
        <div className="rounded-lg bg-surface p-6 shadow-card">
          <h2 className="mb-4 text-base font-bold text-brand-navy">{t('abo.redeemTitle')}</h2>
          <form onSubmit={onSubmit} className="flex gap-2">
            <input
              value={code}
              onChange={(e) => setCode(e.target.value)}
              placeholder={t('abo.redeemPlaceholder')}
              required
              aria-label={t('abo.redeemTitle')}
              className="w-full rounded-lg border border-gray-300 px-3.5 py-2.5 font-mono text-sm uppercase tracking-widest outline-none focus:border-brand-green focus:ring-2 focus:ring-brand-green/20"
            />
            <Button variant="primary" size="md" className="shrink-0"
       type="submit"
       disabled={redeem.isPending}>
              {t('abo.redeemSubmit')}
            </Button>
          </form>
          {error ? <p className="mt-3 text-sm font-medium text-danger">{error}</p> : null}
          {success ? (
            <p className="mt-3 rounded-lg bg-brand-green/10 p-3 text-sm font-medium text-brand-green">
              {success}
            </p>
          ) : null}
        </div>

        {/* Historique */}
        <div className="rounded-lg bg-surface p-6 shadow-card">
          <h2 className="mb-4 text-base font-bold text-brand-navy">{t('abo.historyTitle')}</h2>
          {(user?.activeSubscriptions.length ?? 0) === 0 ? (
            <p className="text-sm text-brand-gray">{t('abo.historyEmpty')}</p>
          ) : (
            <ul className="space-y-2">
              {user?.activeSubscriptions.map((subscription) => (
                <li
                  key={`${subscription.packName}-${subscription.endsAt}`}
                  className="flex items-center justify-between rounded-lg border border-gray-100 px-4 py-3"
                >
                  <span className="text-sm font-bold text-brand-navy">{subscription.packName}</span>
                  <span className="text-xs text-brand-gray">
                    {t('abo.activeUntil')}{' '}
                    {new Date(subscription.endsAt).toLocaleDateString('fr-FR')}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>

      {/* Carrousel de packs */}
      <div>
        <h2 className="mb-3 text-base font-bold text-brand-navy dark:text-white">
          {t('abo.packsTitle')}
        </h2>
        {packs.isLoading ? (
          <div className="flex gap-4">
            <CardGridSkeleton count={2} />
          </div>
        ) : (packs.data?.length ?? 0) === 0 ? (
          <EmptyState title={t('abo.historyEmpty')} />
        ) : (
          <div className="flex snap-x gap-4 overflow-x-auto pb-2">
            {packs.data?.map((pack) => (
              <div
                key={pack.id}
                className="w-72 shrink-0 snap-start rounded-lg bg-surface p-6 shadow-card"
              >
                <p className="text-sm font-bold text-brand-navy">{pack.name}</p>
                <p className="mt-1 text-xs text-brand-gray">
                  {pack.studyYear != null
                    ? `${t('abo.year')} ${pack.studyYear}`
                    : t('abo.allYears')}{' '}
                  · {pack.academicYear}
                </p>
                <p className="mt-4 text-display text-brand-green">
                  {pack.priceDa.toLocaleString('fr-FR')} <span className="text-base">DA</span>
                </p>
                <p className="mt-1 text-xs text-brand-gray">
                  {t('home.expiresOn')} {new Date(pack.expiresAt).toLocaleDateString('fr-FR')}
                </p>
                <div className="mt-4 border-t border-gray-100 pt-3">
                  <p className="text-xs font-bold tracking-wide text-brand-navy">
                    {t('abo.buyTitle')}
                  </p>
                  <p className="mt-1 text-xs leading-relaxed text-brand-gray">
                    {t('abo.buyInstructions')}
                  </p>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
