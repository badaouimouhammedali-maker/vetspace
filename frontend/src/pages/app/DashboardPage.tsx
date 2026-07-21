import { useQuery } from '@tanstack/react-query';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { useAuth } from '../../auth/AuthContext';
import {
  Card,
  DashboardSkeleton,
  Donut,
  EmptyState,
  LinkButton,
  PageHeader,
  SectionHeader,
  Skeleton,
  formatSeconds,
} from '../../components/ui';
import { t } from '../../i18n/fr';
import { fetchOverview, fetchWeekly } from '../../lib/endpoints';

function StatCard({ label, value, icon }: { label: string; value: number; icon: string }) {
  return (
    <Card padding="sm" className="h-full transition-shadow duration-150 hover:shadow-pop">
      <div className="flex items-center gap-4">
        <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-md bg-brand-green/10 text-xl">
          {icon}
        </span>
        <div className="min-w-0">
          {/* tabular-nums so a row of stat cards does not jitter as values change. */}
          <p className="text-h1 tabular-nums text-brand-navy">{value}</p>
          <p className="text-caption text-gray-500">{label}</p>
        </div>
      </div>
    </Card>
  );
}

export function DashboardPage() {
  const { user } = useAuth();
  const overview = useQuery({ queryKey: ['overview'], queryFn: fetchOverview });
  const weekly = useQuery({ queryKey: ['weekly'], queryFn: fetchWeekly });

  const last = overview.data?.lastSession ?? null;
  const subscription = overview.data?.activeSubscriptions[0] ?? null;
  const lastProgress =
    last && last.totalQuestions > 0
      ? ((last.juste + last.fausse + last.consulte) / last.totalQuestions) * 100
      : 0;

  const chartData = (weekly.data ?? []).map((d) => ({
    date: d.date.slice(5),
    [t('home.juste')]: d.juste,
    [t('home.fausse')]: d.fausse,
    [t('home.consultees')]: d.consultees,
  }));

  return (
    <div className="space-y-6">
      <PageHeader title={`${t('home.welcome')} ${user?.username ?? ''} 👋`} />

      {overview.isLoading ? (
        <DashboardSkeleton />
      ) : (
        <div className="grid gap-4 sm:grid-cols-3">
          <StatCard label={t('home.bankQuestions')} value={overview.data?.bank.questions ?? 0} icon="❓" />
          <StatCard label={t('home.bankExams')} value={overview.data?.bank.sourceExams ?? 0} icon="🎓" />
          <StatCard label={t('home.bankMindmaps')} value={overview.data?.bank.mindmaps ?? 0} icon="🧠" />
        </div>
      )}

      <div className="grid items-stretch gap-6 lg:grid-cols-2">
        {/* Dernière session */}
        <Card className="flex h-full flex-col">
          <SectionHeader title={t('home.lastSession')} />
          {overview.isLoading ? (
            <Skeleton className="h-28" />
          ) : last ? (
            <div className="flex items-center gap-5">
              <Donut percent={lastProgress} size={84} />
              <div className="min-w-0 flex-1">
                <p className="truncate font-bold text-brand-navy">{last.title}</p>
                <p className="mt-1 text-sm text-brand-gray">
                  {last.juste + last.fausse + last.consulte}/{last.totalQuestions}{' '}
                  {t('sessions.questions')} · ⏱ {formatSeconds(last.totalSeconds)}
                </p>
                <p className="text-sm text-brand-gray">
                  {t('sessions.score')} : {last.precisionPercent}%
                </p>
              </div>
              <LinkButton
                to={`/app/session/${last.id}`}
                aria-label={t('sessions.play')}
                className="h-12 w-12 shrink-0 rounded-full p-0 text-xl"
              >
                ▶
              </LinkButton>
            </div>
          ) : (
            <EmptyState
              title={t('home.noSessionYet')}
              action={
                <LinkButton to="/app/sessions/entrainement">
                  {t('home.createFirstSession')}
                </LinkButton>
              }
            />
          )}
        </Card>

        {/* Abonnement */}
        <Card className="flex h-full flex-col">
          <SectionHeader title={t('home.subscriptionCard')} />
          {overview.isLoading ? (
            <Skeleton className="h-28" />
          ) : subscription ? (
            <div className="flex h-full flex-col justify-between gap-4">
              <div>
                <p className="text-lg font-bold text-brand-green">{subscription.packName}</p>
                <p className="mt-1 text-sm text-brand-gray">
                  {t('home.expiresOn')}{' '}
                  {new Date(subscription.endsAt).toLocaleDateString('fr-FR')}
                </p>
              </div>
              <LinkButton to="/app/abonnement" variant="secondary" className="self-start">
                {t('home.seeDetails')}
              </LinkButton>
            </div>
          ) : (
            <EmptyState
              title={t('home.noSubscription')}
              action={
                <LinkButton to="/app/abonnement">{t('home.seeDetails')}</LinkButton>
              }
            />
          )}
        </Card>
      </div>

      {/* Performance hebdomadaire */}
      <Card>
        <SectionHeader title={t('home.weeklyPerformance')} />
        {weekly.isLoading ? (
          <Skeleton className="h-64" />
        ) : (
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={chartData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#E5E7EB" />
                <XAxis dataKey="date" tick={{ fontSize: 12 }} />
                <YAxis allowDecimals={false} tick={{ fontSize: 12 }} />
                <Tooltip />
                <Legend />
                <Bar dataKey={t('home.juste')} fill="#0F766E" radius={[4, 4, 0, 0]} />
                <Bar dataKey={t('home.fausse')} fill="#DC2626" radius={[4, 4, 0, 0]} />
                <Bar dataKey={t('home.consultees')} fill="#6B7280" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}
      </Card>
    </div>
  );
}
