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
import { Link } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { Donut, EmptyState, Skeleton, formatSeconds } from '../../components/ui';
import { t } from '../../i18n/fr';
import { fetchOverview, fetchWeekly } from '../../lib/endpoints';

function StatCard({ label, value, icon }: { label: string; value: number; icon: string }) {
  return (
    <div className="flex items-center gap-4 rounded-2xl bg-white p-5 shadow-sm ring-1 ring-gray-100">
      <span className="flex h-12 w-12 items-center justify-center rounded-xl bg-brand-green/10 text-2xl">
        {icon}
      </span>
      <div>
        <p className="text-2xl font-extrabold text-brand-navy">{value}</p>
        <p className="text-sm font-medium text-brand-gray">{label}</p>
      </div>
    </div>
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
      <h1 className="text-2xl font-extrabold text-brand-navy dark:text-white">
        {t('home.welcome')} {user?.username} 👋
      </h1>

      {overview.isLoading ? (
        <div className="grid gap-4 sm:grid-cols-3">
          <Skeleton className="h-24" />
          <Skeleton className="h-24" />
          <Skeleton className="h-24" />
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-3">
          <StatCard label={t('home.bankQuestions')} value={overview.data?.bank.questions ?? 0} icon="❓" />
          <StatCard label={t('home.bankExams')} value={overview.data?.bank.sourceExams ?? 0} icon="🎓" />
          <StatCard label={t('home.bankMindmaps')} value={overview.data?.bank.mindmaps ?? 0} icon="🧠" />
        </div>
      )}

      <div className="grid gap-4 lg:grid-cols-2">
        {/* Dernière session */}
        <div className="rounded-2xl bg-white p-6 shadow-sm ring-1 ring-gray-100">
          <h2 className="mb-4 text-base font-extrabold text-brand-navy">{t('home.lastSession')}</h2>
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
              <Link
                to={`/app/session/${last.id}`}
                aria-label={t('sessions.play')}
                className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-brand-green text-xl text-white transition hover:bg-brand-green-hover"
              >
                ▶
              </Link>
            </div>
          ) : (
            <EmptyState
              message={t('home.noSessionYet')}
              action={
                <Link
                  to="/app/sessions/entrainement"
                  className="rounded-lg bg-brand-green px-4 py-2 text-sm font-bold text-white hover:bg-brand-green-hover"
                >
                  {t('home.createFirstSession')}
                </Link>
              }
            />
          )}
        </div>

        {/* Abonnement */}
        <div className="rounded-2xl bg-white p-6 shadow-sm ring-1 ring-gray-100">
          <h2 className="mb-4 text-base font-extrabold text-brand-navy">
            {t('home.subscriptionCard')}
          </h2>
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
              <Link
                to="/app/abonnement"
                className="self-start rounded-lg border-2 border-brand-green px-4 py-2 text-sm font-bold text-brand-green transition hover:bg-brand-green hover:text-white"
              >
                {t('home.seeDetails')}
              </Link>
            </div>
          ) : (
            <EmptyState
              message={t('home.noSubscription')}
              action={
                <Link
                  to="/app/abonnement"
                  className="rounded-lg bg-brand-green px-4 py-2 text-sm font-bold text-white hover:bg-brand-green-hover"
                >
                  {t('home.seeDetails')}
                </Link>
              }
            />
          )}
        </div>
      </div>

      {/* Performance hebdomadaire */}
      <div className="rounded-2xl bg-white p-6 shadow-sm ring-1 ring-gray-100">
        <h2 className="mb-4 text-base font-extrabold text-brand-navy">
          {t('home.weeklyPerformance')}
        </h2>
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
      </div>
    </div>
  );
}
