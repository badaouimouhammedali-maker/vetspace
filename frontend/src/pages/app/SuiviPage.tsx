import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { PrecisionChip } from '../../components/Badge';
import { Field, Select } from '../../components/forms';
import { Button, EmptyState, ListSkeleton } from '../../components/ui';
import { t } from '../../i18n/fr';
import { fetchCourseCoverage } from '../../lib/endpoints';
import type { CourseCoverage, ModuleCoverage } from '../../lib/schemas';
import { SessionBuilderDialog } from './SessionBuilderDialog';
import { compare, type SortMode } from './suiviSort';

function ProgressBar({ seen, total }: { seen: number; total: number }) {
  const percent = total === 0 ? 0 : Math.min(100, Math.round((seen / total) * 100));
  return (
    <div
      className="h-2 w-full overflow-hidden rounded-full bg-gray-200"
      role="progressbar"
      aria-valuenow={percent}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-label={`${seen}/${total} ${t('suivi.progress')}`}
    >
      <div className="h-full rounded-full bg-brand-green transition-all" style={{ width: `${percent}%` }} />
    </div>
  );
}

function CourseRow({
  course,
  moduleId,
  onRevise,
}: {
  course: CourseCoverage;
  moduleId: string;
  onRevise: (moduleId: string, course: CourseCoverage) => void;
}) {
  const empty = course.totalQuestions === 0;
  return (
    <li className="px-4 py-3">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
        {/* Its own line below `sm`: sharing one row with the status text and the button
            left "Arthrologie" rendering as "Art…" on a 375px screen. Above `sm` there is
            room for all three and the original single-line layout is kept. */}
        <span className="min-w-0 basis-full truncate text-sm font-semibold text-brand-navy sm:flex-1 sm:basis-auto">
          {course.courseName}
        </span>
        {empty ? (
          <span className="shrink-0 text-xs text-brand-gray">{t('suivi.noQuestions')}</span>
        ) : course.answeredQuestions === 0 ? (
          <span className="shrink-0 text-xs text-brand-gray">{t('suivi.notStarted')}</span>
        ) : (
          <PrecisionChip percent={course.precisionPercent} />
        )}
        <span className="flex-1 sm:hidden" />
        <Button
          variant="secondary"
          size="sm"
          disabled={empty}
          onClick={() => onRevise(moduleId, course)}
        >
          {t('suivi.revise')}
        </Button>
      </div>

      {empty ? null : (
        <div className="mt-2">
          <ProgressBar seen={course.seenQuestions} total={course.totalQuestions} />
          <p className="mt-1 text-xs tabular-nums text-brand-gray">
            {course.seenQuestions}/{course.totalQuestions} {t('suivi.progress')} ·{' '}
            {course.answeredQuestions} {t('suivi.answered')} · {course.correctQuestions}{' '}
            {t('suivi.correct')} · {course.neverSeenQuestions} {t('suivi.neverSeen')}
          </p>
        </div>
      )}
    </li>
  );
}

export function SuiviPage() {
  const coverage = useQuery({ queryKey: ['course-coverage'], queryFn: fetchCourseCoverage });
  const [sort, setSort] = useState<SortMode>('default');
  const [closedModules, setClosedModules] = useState<Record<string, boolean>>({});
  const [revise, setRevise] = useState<{ moduleId: string; courseId: string } | null>(null);

  const modules: ModuleCoverage[] = useMemo(() => {
    const data = coverage.data ?? [];
    if (sort === 'default') return data;
    // Sorting inside each module, not across modules: the accordion is the navigation, and
    // reordering the modules themselves would move the headings under the reader.
    return data.map((m) => ({ ...m, courses: [...m.courses].sort((a, b) => compare(sort, a, b)) }));
  }, [coverage.data, sort]);

  // Open by default — a student with four modules should see everything without clicking.
  const isOpen = (moduleId: string) => !(closedModules[moduleId] ?? false);

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-h1 text-brand-navy dark:text-white">{t('suivi.title')}</h1>
        <p className="mt-1 text-body text-gray-500">{t('suivi.subtitle')}</p>
      </div>

      <div className="grid gap-3 sm:max-w-xs">
        <Field label={t('suivi.sort')} htmlFor="suivi-sort">
          <Select
            id="suivi-sort"
            value={sort}
            onChange={(e) => setSort(e.target.value as SortMode)}
          >
            <option value="default">{t('suivi.sortDefault')}</option>
            <option value="weakest">{t('suivi.sortWeakest')}</option>
            <option value="leastCovered">{t('suivi.sortLeastCovered')}</option>
          </Select>
        </Field>
      </div>

      <p className="text-xs text-brand-gray">{t('suivi.precisionHelp')}</p>

      {coverage.isLoading ? (
        <ListSkeleton rows={4} />
      ) : modules.length === 0 ? (
        <EmptyState icon="✓" title={t('suivi.empty')} />
      ) : (
        <div className="space-y-3">
          {modules.map((module) => (
            <div
              key={module.moduleId}
              className="overflow-hidden rounded-lg bg-surface shadow-subtle ring-1 ring-subtle"
            >
              <button
                type="button"
                onClick={() =>
                  setClosedModules((c) => ({ ...c, [module.moduleId]: isOpen(module.moduleId) }))
                }
                aria-expanded={isOpen(module.moduleId)}
                className="flex w-full items-center gap-3 px-4 py-3 text-left transition hover:bg-gray-50"
              >
                <span className="text-brand-gray" aria-hidden>
                  {isOpen(module.moduleId) ? '▾' : '▸'}
                </span>
                <span className="flex-1 font-bold text-brand-navy">{module.moduleName}</span>
                <span className="text-xs font-semibold tabular-nums text-brand-gray">
                  {module.seenQuestions}/{module.totalQuestions} {t('suivi.moduleProgress')}
                </span>
              </button>

              {isOpen(module.moduleId) ? (
                <ul className="divide-y divide-subtle border-t border-subtle">
                  {module.courses.map((course) => (
                    <CourseRow
                      key={course.courseId}
                      course={course}
                      moduleId={module.moduleId}
                      onRevise={(moduleId, c) => setRevise({ moduleId, courseId: c.courseId })}
                    />
                  ))}
                </ul>
              ) : null}
            </div>
          ))}
        </div>
      )}

      {/* Keyed on the course so each "Réviser" remounts the dialog and its defaults take
          effect — the builder seeds its state at mount, not on every open. */}
      {revise ? (
        <SessionBuilderDialog
          key={revise.courseId}
          open
          onClose={() => setRevise(null)}
          defaultType="ENTRAINEMENT"
          defaultModuleId={revise.moduleId}
          defaultCourseIds={[revise.courseId]}
        />
      ) : null}
    </div>
  );
}
