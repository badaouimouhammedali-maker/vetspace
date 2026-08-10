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
import { PrecisionChip } from '../../components/Badge';
import { t } from '../../i18n/fr';
import { fetchCourseCoverage, fetchOverview, fetchWeekly } from '../../lib/endpoints';
import type { CourseCoverage, ModuleCoverage } from '../../lib/schemas';

function StatCard({ label, value, icon }: { label: string; value: number; icon: string }) {
  return (
    <Card padding="sm" className="h-full transition-shadow duration-150 hover:shadow-pop">
      <div className="flex items-center gap-4">
        <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-md bg-accent/10 text-xl">
          {icon}
        </span>
        <div className="min-w-0">
          {/* tabular-nums so a row of stat cards does not jitter as values change. */}
          <p className="text-h1 tabular-nums text-ink">{value}</p>
          <p className="text-caption text-ink-muted">{label}</p>
        </div>
      </div>
    </Card>
  );
}

/**
 * Answered-question floor for the revision card. Below it a percentage is noise — one
 * wrong answer out of two is 50% and would outrank a course the student has genuinely
 * struggled through. Kept in step with the copy in `home.toReviseEmpty`.
 */
const MIN_ANSWERED_FOR_ADVICE = 5;

/** Worst précision first, among courses with enough answers to mean anything. */
function coursesToRevise(modules: ModuleCoverage[]): CourseCoverage[] {
  return modules
    .flatMap((m) => m.courses)
    .filter((c) => c.answeredQuestions >= MIN_ANSWERED_FOR_ADVICE)
    .sort((a, b) => a.precisionPercent - b.precisionPercent)
    .slice(0, 3);
}

export function DashboardPage() {
  const { user } = useAuth();
  const overview = useQuery({ queryKey: ['overview'], queryFn: fetchOverview });
  const weekly = useQuery({ queryKey: ['weekly'], queryFn: fetchWeekly });
  /**
   * The coverage endpoint is subscription-gated, and a 403 SUBSCRIPTION_REQUIRED is
   * globally intercepted into a toast plus a redirect to /app/abonnement. Firing this
   * query unconditionally would therefore bounce every unsubscribed student straight off
   * their own dashboard — a page that is deliberately reachable without a subscription.
   * So we ask only when the answer can be a 200, and hide the card otherwise; the
   * "Aucun abonnement actif" card directly above already explains why.
   *
   * <p>Shares its cache key with /app/suivi, so arriving there from this card is instant.
   */
  const staff = user?.role === 'ADMIN' || user?.role === 'TEACHER';
  const mayFetchCoverage = staff || (user?.activeSubscriptions.length ?? 0) > 0;
  const coverage = useQuery({
    queryKey: ['course-coverage'],
    queryFn: fetchCourseCoverage,
    enabled: mayFetchCoverage,
  });
  const toRevise = coursesToRevise(coverage.data ?? []);

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
                <p className="truncate font-bold text-ink">{last.title}</p>
                <p className="mt-1 text-sm text-ink-muted">
                  {last.juste + last.fausse + last.consulte}/{last.totalQuestions}{' '}
                  {t('sessions.questions')} · ⏱ {formatSeconds(last.totalSeconds)}
                </p>
                <p className="text-sm text-ink-muted">
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
                <p className="text-lg font-bold text-accent">{subscription.packName}</p>
                <p className="mt-1 text-sm text-ink-muted">
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

      {/* Cours à réviser — la porte d'entrée vers /app/suivi. Absent sans abonnement :
          la donnée est derrière le paywall, et /app/suivi le serait aussi. */}
      {mayFetchCoverage ? (
      <Card>
        <SectionHeader title={t('home.toRevise')} />
        {coverage.isLoading ? (
          <Skeleton className="h-24" />
        ) : toRevise.length === 0 ? (
          <p className="text-sm text-ink-muted">{t('home.toReviseEmpty')}</p>
        ) : (
          <ul className="divide-y divide-subtle">
            {toRevise.map((course) => (
              <li key={course.courseId} className="flex items-center gap-3 py-2.5">
                <span className="min-w-0 flex-1 truncate text-sm font-semibold text-ink">
                  {course.courseName}
                </span>
                <span className="shrink-0 text-xs tabular-nums text-ink-muted">
                  {course.correctQuestions}/{course.answeredQuestions}
                </span>
                <PrecisionChip percent={course.precisionPercent} />
              </li>
            ))}
          </ul>
        )}
        <LinkButton to="/app/suivi" variant="secondary" className="mt-4 self-start">
          {t('home.toReviseAll')}
        </LinkButton>
      </Card>
      ) : null}

      {/* Performance hebdomadaire */}
      <Card>
        <SectionHeader title={t('home.weeklyPerformance')} />
        {weekly.isLoading ? (
          <Skeleton className="h-64" />
        ) : (
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={chartData}>
                <CartesianGrid strokeDasharray="3 3" className="stroke-ink/10" />
                <XAxis dataKey="date" tick={{ fontSize: 12 }} />
                <YAxis allowDecimals={false} tick={{ fontSize: 12 }} />
                <Tooltip />
                <Legend />
                <Bar dataKey={t('home.juste')} className="fill-success" radius={[4, 4, 0, 0]} />
                <Bar dataKey={t('home.fausse')} className="fill-danger" radius={[4, 4, 0, 0]} />
                <Bar dataKey={t('home.consultees')} className="fill-ink-muted" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}
      </Card>
    </div>
  );
}
