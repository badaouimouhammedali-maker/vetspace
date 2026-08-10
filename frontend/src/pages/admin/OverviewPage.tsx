import { useQuery } from '@tanstack/react-query';
import { fetchAdminOverview } from '../../lib/adminEndpoints';
import { EmptyState, Skeleton } from '../../components/ui';
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
                <span className="text-display text-ink">{overview.data[c.key]}</span>
                <span className="text-xs font-semibold text-ink-muted">{c.label}</span>
              </Card>
            ))}
          </div>

          {/* École no longer scopes any content, so this is where the field earns its
              keep: it tells the team where their students actually are. */}
          <h2 className="mb-3 mt-8 text-lg font-bold text-ink">Répartition par école</h2>
          <Card className="p-0">
            {(overview.data.studentsBySchool ?? []).length === 0 ? (
              <div className="p-5">
                <EmptyState icon="🏫" title="Aucun étudiant inscrit" compact />
              </div>
            ) : (
              <ul className="divide-y divide-subtle">
                {(overview.data.studentsBySchool ?? []).map((row) => {
                  const share =
                    overview.data.students > 0
                      ? Math.round((row.students / overview.data.students) * 100)
                      : 0;
                  return (
                    <li
                      key={row.schoolId ?? 'unassigned'}
                      className="flex items-center gap-4 px-4 py-3"
                    >
                      <span className="w-48 shrink-0 truncate text-sm font-semibold text-ink">
                        {row.schoolName ?? 'École non renseignée'}
                      </span>
                      {/* A bar makes the distribution readable at a glance; the number
                          stays because a bar alone cannot be quoted in a meeting. */}
                      <span className="h-2 min-w-0 flex-1 overflow-hidden rounded-full bg-ink/10">
                        <span
                          className="block h-full rounded-full bg-accent"
                          style={{ width: `${share}%` }}
                        />
                      </span>
                      <span className="w-24 shrink-0 text-right text-sm text-ink-muted">
                        {row.students} <span className="text-xs">({share}%)</span>
                      </span>
                    </li>
                  );
                })}
              </ul>
            )}
          </Card>

          <h2 className="mb-3 mt-8 text-lg font-bold text-ink">Dernières inscriptions</h2>
          <Card className="overflow-x-auto p-0">
            {overview.data.latestRegistrations.length === 0 ? (
              <div className="p-5">
                <EmptyState icon="👤" title="Aucune inscription pour le moment" compact />
              </div>
            ) : (
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-subtle text-left text-xs uppercase text-ink-muted">
                    <th className="px-4 py-3 font-semibold">Nom</th>
                    <th className="px-4 py-3 font-semibold">E-mail</th>
                    <th className="px-4 py-3 font-semibold">École</th>
                    <th className="px-4 py-3 font-semibold">Année</th>
                    <th className="px-4 py-3 font-semibold">Inscrit le</th>
                  </tr>
                </thead>
                <tbody>
                  {overview.data.latestRegistrations.map((r) => (
                    <tr key={r.id} className="border-b border-subtle last:border-0">
                      <td className="px-4 py-3 font-semibold text-ink">{r.fullName}</td>
                      <td className="px-4 py-3 text-ink-muted">{r.email}</td>
                      <td className="px-4 py-3 text-ink-muted">{r.schoolName ?? '—'}</td>
                      <td className="px-4 py-3 text-ink-muted">{r.studyYear ?? '—'}</td>
                      <td className="px-4 py-3 text-ink-muted">
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
