import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Field, SubmitButton, TextInput } from '../../components/forms';
import { Button, Disclosure, EmptyState, ListSkeleton, Modal, Skeleton } from '../../components/ui';
import { useToast } from '../../components/ToastProvider';
import { t } from '../../i18n/fr';
import { DEFAULT_THEME } from '../../lib/theme';
import { apiErrorMessage, apiErrorReference } from '../../lib/api';
import {
  createLabel,
  createSession,
  deleteLabel,
  fetchLabelQuestions,
  fetchLabels,
  updateLabel,
} from '../../lib/endpoints';
import type { Label } from '../../lib/schemas';

function LabelQuestions({ labelId }: { labelId: string }) {
  const questions = useQuery({
    queryKey: ['label-questions', labelId],
    queryFn: () => fetchLabelQuestions(labelId),
  });
  if (questions.isLoading) {
    return <Skeleton className="mt-2 h-16" />;
  }
  if ((questions.data?.length ?? 0) === 0) {
    return <p className="mt-2 text-sm text-ink-muted">{t('labels.noQuestions')}</p>;
  }
  return (
    <ul className="mt-2 space-y-1">
      {questions.data?.map((question) => (
        <li key={question.id} className="truncate rounded-lg bg-canvas px-3 py-2 text-sm text-ink">
          {question.statement}
        </li>
      ))}
    </ul>
  );
}

export function LabelsPage() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { toast } = useToast();
  const labels = useQuery({ queryKey: ['labels'], queryFn: fetchLabels });

  const [dialog, setDialog] = useState<{ mode: 'create' | 'edit'; label?: Label } | null>(null);
  const [name, setName] = useState('');
  const [color, setColor] = useState<string>(DEFAULT_THEME.primary);
  const [error, setError] = useState<string | null>(null);
  const [expanded, setExpanded] = useState<string | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<Label | null>(null);

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['labels'] });

  function openCreate() {
    setName('');
    setColor(DEFAULT_THEME.primary);
    setError(null);
    setDialog({ mode: 'create' });
  }
  function openEdit(label: Label) {
    setName(label.name);
    setColor(label.color);
    setError(null);
    setDialog({ mode: 'edit', label });
  }

  const save = useMutation({
    mutationFn: () =>
      dialog?.mode === 'edit'
        ? updateLabel(dialog.label!.id, name, color)
        : createLabel(name, color),
    onSuccess: async () => {
      await invalidate();
      setDialog(null);
    },
    onError: (err) => setError(apiErrorMessage(err) ?? t('common.error')),
  });

  const remove = useMutation({
    mutationFn: () => deleteLabel(deleteTarget!.id),
    onSuccess: async () => {
      await invalidate();
      setDeleteTarget(null);
    },
  });

  const startSession = useMutation({
    mutationFn: (labelId: string) =>
      createSession({ sessionType: 'ENTRAINEMENT', filters: { labelIds: [labelId] }, questionCount: 50 }),
    onSuccess: (session) => navigate(`/app/session/${session.id}`),
    onError: (err) => toast('error', apiErrorMessage(err) ?? t('common.error'), apiErrorReference(err)),
  });

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <h1 className="text-h1 text-ink dark:text-white">
          {t('labels.title')}
        </h1>
        <Button variant="primary" size="md"
     onClick={openCreate}>
          {t('labels.create')}
        </Button>
      </div>

      {labels.isLoading ? (
        <ListSkeleton rows={4} />
      ) : (labels.data?.length ?? 0) === 0 ? (
        <EmptyState
          title={t('labels.empty')}
          action={
            <Button variant="primary" size="md"
       onClick={openCreate}>
              {t('labels.create')}
            </Button>
          }
        />
      ) : (
        <div className="space-y-3">
          {labels.data?.map((label) => (
            <div key={label.id} className="rounded-lg bg-surface p-4 shadow-card">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <Disclosure
                  open={expanded === label.id}
                  onClick={() => setExpanded(expanded === label.id ? null : label.id)}
                  className="w-auto"
                >
                  <span
                    className="inline-block h-4 w-4 shrink-0 rounded-full"
                    style={{ backgroundColor: label.color }}
                  />
                  {label.name}
                  <span className="text-caption font-normal text-ink-muted">
                    {label.questionCount} {t('labels.questionCount')}
                  </span>
                </Disclosure>
                <div className="flex items-center gap-1.5">
                  <Button variant="primary" size="sm"
                    onClick={() => startSession.mutate(label.id)}
                    disabled={label.questionCount === 0 || startSession.isPending}
                  >
                    ▶ {t('labels.startSession')}
                  </Button>
                  <Button variant="secondary" size="md"
                    onClick={() => openEdit(label)}
                    aria-label={t('labels.edit')}
                  >
                    ✎
                  </Button>
                  <Button variant="ghost" size="sm" className="text-ink-muted hover:bg-danger/10 hover:text-danger"
                    onClick={() => setDeleteTarget(label)}
                    aria-label={t('sessions.delete')}
                    
                  >
                    🗑️
                  </Button>
                </div>
              </div>
              {expanded === label.id ? <LabelQuestions labelId={label.id} /> : null}
            </div>
          ))}
        </div>
      )}

      {/* Créer / modifier */}
      <Modal
        open={dialog !== null}
        onClose={() => setDialog(null)}
        title={dialog?.mode === 'edit' ? t('labels.edit') : t('labels.create')}
      >
        <form
          onSubmit={(e) => {
            e.preventDefault();
            setError(null);
            save.mutate();
          }}
          className="space-y-4"
        >
          <Field label={t('labels.name')} htmlFor="label-name">
            <TextInput
              id="label-name"
              required
              maxLength={100}
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </Field>
          <Field label={t('labels.color')} htmlFor="label-color">
            <input
              id="label-color"
              type="color"
              value={color}
              onChange={(e) => setColor(e.target.value)}
              className="h-11 w-full cursor-pointer rounded-lg border border-subtle"
            />
          </Field>
          {error ? <p className="text-sm font-medium text-danger">{error}</p> : null}
          <SubmitButton loading={save.isPending}>{t('common.save')}</SubmitButton>
        </form>
      </Modal>

      {/* Confirmation suppression */}
      <Modal
        open={deleteTarget !== null}
        onClose={() => setDeleteTarget(null)}
        title={t('sessions.delete')}
      >
        <p className="text-sm text-ink-muted">{t('labels.deleteConfirm')}</p>
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="secondary" size="md"
            onClick={() => setDeleteTarget(null)}
          >
            {t('common.cancel')}
          </Button>
          <Button variant="danger" size="md"
            onClick={() => remove.mutate()}
            disabled={remove.isPending}
          >
            {t('common.confirm')}
          </Button>
        </div>
      </Modal>
    </div>
  );
}
