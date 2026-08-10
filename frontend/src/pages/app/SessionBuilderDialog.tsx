import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { Field, Select, SubmitButton } from '../../components/forms';
import { Modal } from '../../components/ui';
import { useToast } from '../../components/ToastProvider';
import { t } from '../../i18n/fr';
import {
  createSession,
  fetchCourses,
  fetchLabels,
  fetchModules,
  fetchQuestionCount,
  fetchSourceExams,
  type SessionFilters,
} from '../../lib/endpoints';
import { apiErrorMessage, apiErrorReference } from '../../lib/api';
import type { SessionType } from '../../lib/schemas';

export function SessionBuilderDialog({
  open,
  onClose,
  defaultType,
  defaultModuleId = '',
  defaultCourseIds,
}: {
  open: boolean;
  onClose: () => void;
  defaultType: SessionType;
  /**
   * Opens the dialog with the module/course pickers already set — how "Réviser ce cours"
   * gets from a coverage row into a session without making the student re-find the course
   * they just clicked.
   *
   * <p>These are initial values only: the fields stay editable, and because they seed
   * `useState` they are read once at mount. A caller that changes them between opens must
   * remount the dialog (`key={courseId}`), which is what /app/suivi does.
   */
  defaultModuleId?: string;
  defaultCourseIds?: string[];
}) {
  const { user } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { toast } = useToast();

  const [type, setType] = useState<SessionType>(defaultType);
  const [moduleId, setModuleId] = useState(defaultModuleId);
  const [courseIds, setCourseIds] = useState<string[]>(defaultCourseIds ?? []);
  const [sourceExamId, setSourceExamId] = useState('');
  const [difficulty, setDifficulty] = useState('');
  const [onlyUnseen, setOnlyUnseen] = useState(false);
  const [labelIds, setLabelIds] = useState<string[]>([]);
  const [questionCount, setQuestionCount] = useState(10);
  const [error, setError] = useState<string | null>(null);

  const studyYear = user?.studyYear ?? 1;
  const modules = useQuery({
    queryKey: ['modules', studyYear],
    queryFn: () => fetchModules(studyYear),
    enabled: open,
  });
  const courses = useQuery({
    queryKey: ['courses', moduleId],
    queryFn: () => fetchCourses(moduleId),
    enabled: open && moduleId !== '',
  });
  const sourceExams = useQuery({
    queryKey: ['source-exams'],
    queryFn: fetchSourceExams,
    enabled: open,
  });
  const labels = useQuery({ queryKey: ['labels'], queryFn: fetchLabels, enabled: open });

  const filters: SessionFilters = useMemo(
    () => ({
      ...(moduleId ? { moduleIds: [moduleId] } : {}),
      ...(courseIds.length > 0 ? { courseIds } : {}),
      ...(sourceExamId ? { sourceExamIds: [sourceExamId] } : {}),
      ...(difficulty ? { difficulty: difficulty as 'EASY' | 'MEDIUM' | 'HARD' } : {}),
      ...(onlyUnseen ? { onlyUnseen: true } : {}),
      ...(labelIds.length > 0 ? { labelIds } : {}),
    }),
    [moduleId, courseIds, sourceExamId, difficulty, onlyUnseen, labelIds],
  );

  // Compteur en direct : rejoué à chaque changement de filtre.
  const count = useQuery({
    queryKey: ['question-count', filters],
    queryFn: () => fetchQuestionCount(filters),
    enabled: open,
  });
  const available = count.data?.count ?? 0;
  const maxCount = Math.max(1, Math.min(available, 100));

  const create = useMutation({
    mutationFn: () =>
      createSession({
        sessionType: type,
        filters,
        questionCount: Math.min(questionCount, maxCount),
      }),
    onSuccess: async (session) => {
      await queryClient.invalidateQueries({ queryKey: ['sessions'] });
      onClose();
      navigate(`/app/session/${session.id}`);
    },
    onError: (err) => {
      setError(apiErrorMessage(err) ?? t('common.error'));
      toast('error', apiErrorMessage(err) ?? t('common.error'), apiErrorReference(err));
    },
  });

  function toggle(list: string[], id: string, set: (v: string[]) => void) {
    set(list.includes(id) ? list.filter((x) => x !== id) : [...list, id]);
  }

  return (
    <Modal open={open} onClose={onClose} title={t('builder.title')} wide>
      <div className="space-y-4">
        <Field label={t('builder.type')} htmlFor="builder-type">
          <Select id="builder-type" value={type} onChange={(e) => setType(e.target.value as SessionType)}>
            <option value="ENTRAINEMENT">{t('builder.typeEntrainement')}</option>
            <option value="EXAMEN">{t('builder.typeExamen')}</option>
          </Select>
        </Field>

        <Field label={t('builder.module')} htmlFor="builder-module">
          <Select
            id="builder-module"
            value={moduleId}
            onChange={(e) => {
              setModuleId(e.target.value);
              setCourseIds([]);
            }}
          >
            <option value="">{t('builder.modulePlaceholder')}</option>
            {(modules.data ?? []).map((m) => (
              <option key={m.id} value={m.id}>
                {m.name}
              </option>
            ))}
          </Select>
        </Field>

        {moduleId && (courses.data?.length ?? 0) > 0 ? (
          <fieldset>
            <legend className="mb-1.5 block text-sm font-semibold text-ink">
              {t('builder.courses')}
            </legend>
            <div className="flex flex-wrap gap-2">
              {(courses.data ?? []).map((course) => (
                <label
                  key={course.id}
                  className={`cursor-pointer rounded-lg border px-3 py-1.5 text-sm font-medium transition ${
                    courseIds.includes(course.id)
                      ? 'border-accent bg-accent/10 text-accent'
                      : 'border-subtle text-ink-muted hover:border-accent'
                  }`}
                >
                  <input
                    type="checkbox"
                    className="sr-only"
                    checked={courseIds.includes(course.id)}
                    onChange={() => toggle(courseIds, course.id, setCourseIds)}
                  />
                  {course.name}
                </label>
              ))}
            </div>
          </fieldset>
        ) : null}

        <div className="grid grid-cols-2 gap-3">
          <Field label={t('builder.sourceExam')} htmlFor="builder-source">
            <Select
              id="builder-source"
              value={sourceExamId}
              onChange={(e) => setSourceExamId(e.target.value)}
            >
              <option value="">{t('builder.sourceExamAll')}</option>
              {(sourceExams.data ?? []).map((exam) => (
                <option key={exam.id} value={exam.id}>
                  {exam.label}
                </option>
              ))}
            </Select>
          </Field>
          <Field label={t('builder.difficulty')} htmlFor="builder-difficulty">
            <Select
              id="builder-difficulty"
              value={difficulty}
              onChange={(e) => setDifficulty(e.target.value)}
            >
              <option value="">{t('builder.difficultyAll')}</option>
              <option value="EASY">{t('builder.difficultyEasy')}</option>
              <option value="MEDIUM">{t('builder.difficultyMedium')}</option>
              <option value="HARD">{t('builder.difficultyHard')}</option>
            </Select>
          </Field>
        </div>

        <label className="flex items-center gap-2 text-sm font-medium text-ink-muted">
          <input
            type="checkbox"
            checked={onlyUnseen}
            onChange={(e) => setOnlyUnseen(e.target.checked)}
            className="h-4 w-4 rounded border-subtle accent-accent"
          />
          {t('builder.onlyUnseen')}
        </label>

        {(labels.data?.length ?? 0) > 0 ? (
          <fieldset>
            <legend className="mb-1.5 block text-sm font-semibold text-ink">
              {t('builder.labels')}
            </legend>
            <div className="flex flex-wrap gap-2">
              {(labels.data ?? []).map((label) => (
                <label
                  key={label.id}
                  className={`cursor-pointer rounded-lg border px-3 py-1.5 text-sm font-medium transition ${
                    labelIds.includes(label.id)
                      ? 'border-accent bg-accent/10 text-accent'
                      : 'border-subtle text-ink-muted hover:border-accent'
                  }`}
                >
                  <input
                    type="checkbox"
                    className="sr-only"
                    checked={labelIds.includes(label.id)}
                    onChange={() => toggle(labelIds, label.id, setLabelIds)}
                  />
                  <span
                    className="mr-1 inline-block h-2 w-2 rounded-full"
                    style={{ backgroundColor: label.color }}
                  />
                  {label.name}
                </label>
              ))}
            </div>
          </fieldset>
        ) : null}

        <div className="rounded-xl bg-canvas p-4">
          <p className="text-sm font-bold text-ink">
            {count.isFetching ? '…' : available} {t('builder.available')}
          </p>
          {available > 0 ? (
            <div className="mt-3">
              <label htmlFor="builder-count" className="text-sm font-semibold text-ink">
                {t('builder.questionCount')} : {Math.min(questionCount, maxCount)}
              </label>
              <input
                id="builder-count"
                type="range"
                min={1}
                max={maxCount}
                value={Math.min(questionCount, maxCount)}
                onChange={(e) => setQuestionCount(Number(e.target.value))}
                className="mt-1 w-full accent-accent"
              />
            </div>
          ) : (
            <p className="mt-1 text-sm text-ink-muted">{t('builder.noQuestions')}</p>
          )}
        </div>

        {error ? <p className="text-sm font-medium text-danger">{error}</p> : null}

        <form
          onSubmit={(e) => {
            e.preventDefault();
            setError(null);
            create.mutate();
          }}
        >
          <SubmitButton loading={create.isPending}>{t('builder.submit')}</SubmitButton>
        </form>
      </div>
    </Modal>
  );
}
