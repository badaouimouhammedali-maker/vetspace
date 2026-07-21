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
import {
  EmptyState,
  PrecisionChip,
  SegmentToggle,
  Skeleton,
  TableSkeleton,
  formatSeconds,
} from '../../components/ui';
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
    <div className="bg-canvas p-3">
      <p className="mb-2 text-xs font-bold uppercase tracking-wide text-brand-gray">
        {t('stats.byCourse')}
      </p>
      <table className="w-full text-sm">
        <thead>
          <tr className="text-left text-caption text-gray-500">
            <th className="py-1">{t('stats.course')}</th>
            <th className="py-1 text-right">{t('stats.juste')}</th>
            <th className="py-1 text-right">{t('stats.fausse')}</th>
            <th className="py-1 text-right">{t('stats.consulte')}</th>
            <th className="py-1 text-right">{t('stats.time')}</th>
            <th className="py-1 text-right">{t('stats.precision')}</th>
          </tr>
        </thead>
        <tbody>
          {(courses.data ?? []).map((course) => (
            <tr key={course.courseId} className="border-t border-subtle">
              <td className="py-1.5 font-medium text-brand-navy">{course.courseName}</td>
              <td className="py-1.5 text-right tabular-nums text-success">{course.juste}</td>
              <td className="py-1.5 text-right tabular-nums text-danger">{course.fausse}</td>
              <td className="py-1.5 text-right tabular-nums text-gray-500">{course.consulte}</td>
              <td className="py-1.5 text-right tabular-nums">{formatSeconds(course.totalSeconds)}</td>
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
    <div className="max-h-[70vh] overflow-auto rounded-lg bg-surface shadow-card">
      <table className="w-full min-w-[720px] text-body">
        {/*
          Sticky header: these tables get long, and a scrolled-away header turns a row
          of five numbers into a guessing game.
        */}
        <thead className="sticky top-0 z-10 bg-surface">
          <tr className="border-b border-subtle text-left text-caption font-bold uppercase tracking-wide text-gray-500">
            <th className="px-4 py-3">{t('stats.session')}</th>
            <th className="px-4 py-3 text-right">{t('stats.time')}</th>
            <th className="px-4 py-3 text-right">{t('stats.avgPerQuestion')}</th>
            <th className="px-4 py-3 text-right">{t('stats.total')}</th>
            <th className="px-4 py-3 text-right">{t('stats.juste')}</th>
            <th className="px-4 py-3 text-right">{t('stats.fausse')}</th>
            <th className="px-4 py-3 text-right">{t('stats.consulte')}</th>
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
                  // Zebra striping on the odd rows; hover wins over the stripe so the
                  // pointer target stays obvious.
                  className="cursor-pointer border-b border-subtle odd:bg-gray-50/50 hover:bg-brand-green/5"
                >
                  <td className="px-4 py-3 font-semibold text-brand-navy">
                    <span className="mr-1.5 inline-block text-brand-gray">{open ? '▾' : '▸'}</span>
                    {row.title}
                    <span className="ml-2 block text-xs font-normal text-brand-gray">
                      {new Date(row.startedAt).toLocaleDateString('fr-FR')}
                    </span>
                  </td>
                  {/* Numerics right-aligned with tabular-nums so digits line up in a column. */}
                  <td className="px-4 py-3 text-right tabular-nums">{formatSeconds(row.totalSeconds)}</td>
                  <td className="px-4 py-3 text-right tabular-nums">
                    {formatSeconds(Math.round(row.avgSecondsPerQuestion))}
                  </td>
                  <td className="px-4 py-3 text-right tabular-nums">{row.totalQuestions}</td>
                  <td className="px-4 py-3 text-right font-semibold tabular-nums text-success">
                    {row.juste}
                  </td>
                  <td className="px-4 py-3 text-right font-semibold tabular-nums text-danger">
                    {row.fausse}
                  </td>
                  <td className="px-4 py-3 text-right tabular-nums text-gray-500">{row.consulte}</td>
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
    <div className="h-80 rounded-lg bg-surface p-6 shadow-card">
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
      <h1 className="text-h1 text-brand-navy dark:text-white">{t('stats.title')}</h1>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <select
          value={type}
          onChange={(e) => setType(e.target.value as SessionType)}
          aria-label={t('stats.typeFilter')}
          className="rounded-lg border border-gray-300 bg-surface px-3.5 py-2 text-sm font-semibold text-brand-navy outline-none focus:border-brand-green focus:ring-2 focus:ring-brand-green/20"
        >
          <option value="ENTRAINEMENT">{t('builder.typeEntrainement')}</option>
          <option value="EXAMEN">{t('builder.typeExamen')}</option>
        </select>
        <div className="inline-flex rounded-lg border border-gray-300 p-0.5">
          {(['table', 'chart'] as const).map((mode) => (
            <SegmentToggle key={mode} selected={view === mode} onClick={() => setView(mode)}>
              {mode === 'table' ? t('stats.viewTable') : t('stats.viewChart')}
            </SegmentToggle>
          ))}
        </div>
      </div>

      {stats.isLoading ? (
        <TableSkeleton rows={5} columns={8} />
      ) : rows.length === 0 ? (
        <EmptyState title={t('stats.empty')} />
      ) : view === 'table' ? (
        <StatsTable rows={rows} />
      ) : (
        <StatsChart rows={rows} />
      )}
    </div>
  );
}
