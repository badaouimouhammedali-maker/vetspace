import { useQuery } from '@tanstack/react-query';
import { fetchAdminOverview } from '../../lib/adminEndpoints';
import { Skeleton } from '../../components/ui';
import { AdminHeader, Card } from './adminUi';

const STAT_CARDS: { key: keyof StatMap; label: string; icon: string }[] = [
  { key: 'students', label: 'Étudiants', icon: '👥' },
  { key: 'questions', label: 'Questions', icon: '❓' },
  { key: 'sessionsToday', label: "Sessions aujourd'hui", icon: '📝' },
  { key: 'activeSubscriptions', label: 'Abonnements actifs', icon: '🎟️' },
  { key: 'openSignals', label: 'Signalements ouverts', icon: '🚩' },
];

interface StatMap {
  students: number;
  questions: number;
  sessionsToday: number;
  activeSubscriptions: number;
  openSignals: number;
}

export function OverviewPage() {
  const overview = useQuery({ queryKey: ['admin', 'overview'], queryFn: fetchAdminOverview });

  return (
    <div>
      <AdminHeader title="Vue d'ensemble" subtitle="Un aperçu de l'activité de la plateforme." />

      {overview.isLoading ? (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
          {STAT_CARDS.map((c) => (
            <Skeleton key={c.key} className="h-24 rounded-lg" />
          ))}
        </div>
      ) : overview.data ? (
        <>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
            {STAT_CARDS.map((c) => (
              <Card key={c.key} className="flex flex-col gap-1">
                <span className="text-2xl" aria-hidden>
                  {c.icon}
                </span>
                <span className="text-display text-brand-navy">
                  {overview.data[c.key]}
                </span>
                <span className="text-xs font-semibold text-brand-gray">{c.label}</span>
              </Card>
            ))}
          </div>

          <h2 className="mb-3 mt-8 text-lg font-bold text-brand-navy">
            Dernières inscriptions
          </h2>
          <Card className="overflow-x-auto p-0">
            {overview.data.latestRegistrations.length === 0 ? (
              <p className="p-5 text-sm text-brand-gray">Aucune inscription pour le moment.</p>
            ) : (
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-gray-100 text-left text-xs uppercase text-brand-gray">
                    <th className="px-4 py-3 font-semibold">Nom</th>
                    <th className="px-4 py-3 font-semibold">E-mail</th>
                    <th className="px-4 py-3 font-semibold">École</th>
                    <th className="px-4 py-3 font-semibold">Année</th>
                    <th className="px-4 py-3 font-semibold">Inscrit le</th>
                  </tr>
                </thead>
                <tbody>
                  {overview.data.latestRegistrations.map((r) => (
                    <tr key={r.id} className="border-b border-gray-50 last:border-0">
                      <td className="px-4 py-3 font-semibold text-brand-navy">{r.fullName}</td>
                      <td className="px-4 py-3 text-brand-gray">{r.email}</td>
                      <td className="px-4 py-3 text-brand-gray">{r.schoolName ?? '—'}</td>
                      <td className="px-4 py-3 text-brand-gray">{r.studyYear ?? '—'}</td>
                      <td className="px-4 py-3 text-brand-gray">
                        {new Date(r.createdAt).toLocaleDateString('fr-FR')}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </Card>
        </>
      ) : (
        <p className="text-sm text-danger">Impossible de charger les données.</p>
      )}
    </div>
  );
}
