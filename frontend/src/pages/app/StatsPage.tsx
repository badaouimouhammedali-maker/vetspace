import { useQuery } from '@tanstack/react-query';
import { Fragment, useState } from 'react';
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
import { EmptyState, PrecisionChip, Skeleton, formatSeconds } from '../../components/ui';
import { t } from '../../i18n/fr';
import { fetchCourseStats, fetchSessionStats } from '../../lib/endpoints';
import type { SessionStats, SessionType } from '../../lib/schemas';

function CourseBreakdown({ sessionId }: { sessionId: string }) {
  const courses = useQuery({
    queryKey: ['course-stats', sessionId],
    queryFn: () => fetchCourseStats(sessionId),
  });
  if (courses.isLoading) {
    return <Skeleton className="m-3 h-20" />;
  }
  return (
    <div className="bg-gray-50 p-3">
      <p className="mb-2 text-xs font-bold uppercase tracking-wide text-brand-gray">
        {t('stats.byCourse')}
      </p>
      <table className="w-full text-sm">
        <thead>
          <tr className="text-left text-xs text-brand-gray">
            <th className="py-1">{t('stats.course')}</th>
            <th className="py-1 text-center">{t('stats.juste')}</th>
            <th className="py-1 text-center">{t('stats.fausse')}</th>
            <th className="py-1 text-center">{t('stats.consulte')}</th>
            <th className="py-1 text-center">{t('stats.time')}</th>
            <th className="py-1 text-right">{t('stats.precision')}</th>
          </tr>
        </thead>
        <tbody>
          {(courses.data ?? []).map((course) => (
            <tr key={course.courseId} className="border-t border-gray-200">
              <td className="py-1.5 font-medium text-brand-navy">{course.courseName}</td>
              <td className="py-1.5 text-center text-brand-green">{course.juste}</td>
              <td className="py-1.5 text-center text-red-600">{course.fausse}</td>
              <td className="py-1.5 text-center text-brand-gray">{course.consulte}</td>
              <td className="py-1.5 text-center">{formatSeconds(course.totalSeconds)}</td>
              <td className="py-1.5 text-right">
                <PrecisionChip percent={course.precisionPercent} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function StatsTable({ rows }: { rows: SessionStats[] }) {
  const [expanded, setExpanded] = useState<string | null>(null);
  return (
    <div className="overflow-x-auto rounded-2xl bg-white shadow-sm ring-1 ring-gray-100">
      <table className="w-full min-w-[720px] text-sm">
        <thead>
          <tr className="border-b border-gray-100 text-left text-xs font-bold uppercase tracking-wide text-brand-gray">
            <th className="px-4 py-3">{t('stats.session')}</th>
            <th className="px-4 py-3">{t('stats.time')}</th>
            <th className="px-4 py-3">{t('stats.avgPerQuestion')}</th>
            <th className="px-4 py-3 text-center">{t('stats.total')}</th>
            <th className="px-4 py-3 text-center">{t('stats.juste')}</th>
            <th className="px-4 py-3 text-center">{t('stats.fausse')}</th>
            <th className="px-4 py-3 text-center">{t('stats.consulte')}</th>
            <th className="px-4 py-3 text-right">{t('stats.precision')}</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => {
            const open = expanded === row.id;
            return (
              <Fragment key={row.id}>
                <tr
                  onClick={() => setExpanded(open ? null : row.id)}
                  className="cursor-pointer border-b border-gray-50 hover:bg-gray-50"
                >
                  <td className="px-4 py-3 font-semibold text-brand-navy">
                    <span className="mr-1.5 inline-block text-brand-gray">{open ? '▾' : '▸'}</span>
                    {row.title}
                    <span className="ml-2 block text-xs font-normal text-brand-gray">
                      {new Date(row.startedAt).toLocaleDateString('fr-FR')}
                    </span>
                  </td>
                  <td className="px-4 py-3">{formatSeconds(row.totalSeconds)}</td>
                  <td className="px-4 py-3">{formatSeconds(Math.round(row.avgSecondsPerQuestion))}</td>
                  <td className="px-4 py-3 text-center">{row.totalQuestions}</td>
                  <td className="px-4 py-3 text-center font-semibold text-brand-green">{row.juste}</td>
                  <td className="px-4 py-3 text-center font-semibold text-red-600">{row.fausse}</td>
                  <td className="px-4 py-3 text-center text-brand-gray">{row.consulte}</td>
                  <td className="px-4 py-3 text-right">
                    <PrecisionChip percent={row.precisionPercent} />
                  </td>
                </tr>
                {open ? (
                  <tr>
                    <td colSpan={8} className="p-0">
                      <CourseBreakdown sessionId={row.id} />
                    </td>
                  </tr>
                ) : null}
              </Fragment>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function StatsChart({ rows }: { rows: SessionStats[] }) {
  const data = rows
    .slice()
    .reverse()
    .map((row) => ({
      name: new Date(row.startedAt).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' }),
      [t('stats.juste')]: row.juste,
      [t('stats.fausse')]: row.fausse,
      [t('stats.consulte')]: row.consulte,
    }));
  return (
    <div className="h-80 rounded-2xl bg-white p-6 shadow-sm ring-1 ring-gray-100">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data}>
          <CartesianGrid strokeDasharray="3 3" stroke="#E5E7EB" />
          <XAxis dataKey="name" tick={{ fontSize: 12 }} />
          <YAxis allowDecimals={false} tick={{ fontSize: 12 }} />
          <Tooltip />
          <Legend />
          <Bar dataKey={t('stats.juste')} fill="#0F766E" radius={[4, 4, 0, 0]} />
          <Bar dataKey={t('stats.fausse')} fill="#DC2626" radius={[4, 4, 0, 0]} />
          <Bar dataKey={t('stats.consulte')} fill="#6B7280" radius={[4, 4, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

export function StatsPage() {
  const [type, setType] = useState<SessionType>('ENTRAINEMENT');
  const [view, setView] = useState<'table' | 'chart'>('table');
  const stats = useQuery({
    queryKey: ['session-stats', type],
    queryFn: () => fetchSessionStats(type),
  });
  const rows = stats.data ?? [];

  return (
    <div className="space-y-5">
      <h1 className="text-2xl font-extrabold text-brand-navy dark:text-white">{t('stats.title')}</h1>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <select
          value={type}
          onChange={(e) => setType(e.target.value as SessionType)}
          aria-label={t('stats.typeFilter')}
          className="rounded-lg border border-gray-300 bg-white px-3.5 py-2 text-sm font-semibold text-brand-navy outline-none focus:border-brand-green focus:ring-2 focus:ring-brand-green/20"
        >
          <option value="ENTRAINEMENT">{t('builder.typeEntrainement')}</option>
          <option value="EXAMEN">{t('builder.typeExamen')}</option>
        </select>
        <div className="inline-flex rounded-lg border border-gray-300 p-0.5">
          {(['table', 'chart'] as const).map((mode) => (
            <button
              key={mode}
              onClick={() => setView(mode)}
              className={`rounded-md px-3 py-1.5 text-sm font-semibold transition ${
                view === mode ? 'bg-brand-green text-white' : 'text-brand-gray'
              }`}
            >
              {mode === 'table' ? t('stats.viewTable') : t('stats.viewChart')}
            </button>
          ))}
        </div>
      </div>

      {stats.isLoading ? (
        <Skeleton className="h-64" />
      ) : rows.length === 0 ? (
        <EmptyState message={t('stats.empty')} />
      ) : view === 'table' ? (
        <StatsTable rows={rows} />
      ) : (
        <StatsChart rows={rows} />
      )}
    </div>
  );
}
