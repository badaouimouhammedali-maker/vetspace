import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Field, SubmitButton, TextInput } from '../../components/forms';
import { EmptyState, Modal, Skeleton } from '../../components/ui';
import { useToast } from '../../components/ToastProvider';
import { t } from '../../i18n/fr';
import { apiErrorMessage } from '../../lib/api';
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
    return <p className="mt-2 text-sm text-brand-gray">{t('labels.noQuestions')}</p>;
  }
  return (
    <ul className="mt-2 space-y-1">
      {questions.data?.map((question) => (
        <li key={question.id} className="truncate rounded-lg bg-gray-50 px-3 py-2 text-sm text-brand-navy">
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
  const [color, setColor] = useState('#0F766E');
  const [error, setError] = useState<string | null>(null);
  const [expanded, setExpanded] = useState<string | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<Label | null>(null);

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['labels'] });

  function openCreate() {
    setName('');
    setColor('#0F766E');
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
    onError: (err) => toast('error', apiErrorMessage(err) ?? t('common.error')),
  });

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-extrabold text-brand-navy dark:text-white">
          {t('labels.title')}
        </h1>
        <button
          onClick={openCreate}
          className="rounded-lg bg-brand-green px-4 py-2.5 text-sm font-bold text-white transition hover:bg-brand-green-hover"
        >
          {t('labels.create')}
        </button>
      </div>

      {labels.isLoading ? (
        <Skeleton className="h-40" />
      ) : (labels.data?.length ?? 0) === 0 ? (
        <EmptyState
          message={t('labels.empty')}
          action={
            <button
              onClick={openCreate}
              className="rounded-lg bg-brand-green px-4 py-2 text-sm font-bold text-white hover:bg-brand-green-hover"
            >
              {t('labels.create')}
            </button>
          }
        />
      ) : (
        <div className="space-y-3">
          {labels.data?.map((label) => (
            <div key={label.id} className="rounded-2xl bg-white p-4 shadow-sm ring-1 ring-gray-100">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <button
                  onClick={() => setExpanded(expanded === label.id ? null : label.id)}
                  className="flex items-center gap-2 font-bold text-brand-navy"
                >
                  <span
                    className="inline-block h-4 w-4 rounded-full"
                    style={{ backgroundColor: label.color }}
                  />
                  {label.name}
                  <span className="text-xs font-normal text-brand-gray">
                    {label.questionCount} {t('labels.questionCount')}
                  </span>
                </button>
                <div className="flex items-center gap-1.5">
                  <button
                    onClick={() => startSession.mutate(label.id)}
                    disabled={label.questionCount === 0 || startSession.isPending}
                    className="rounded-lg bg-brand-green/10 px-3 py-1.5 text-xs font-bold text-brand-green transition hover:bg-brand-green hover:text-white disabled:opacity-40"
                  >
                    ▶ {t('labels.startSession')}
                  </button>
                  <button
                    onClick={() => openEdit(label)}
                    aria-label={t('labels.edit')}
                    className="rounded-lg border border-gray-200 p-2 text-sm text-brand-gray hover:border-brand-green hover:text-brand-green"
                  >
                    ✎
                  </button>
                  <button
                    onClick={() => setDeleteTarget(label)}
                    aria-label={t('sessions.delete')}
                    className="rounded-lg border border-gray-200 p-2 text-sm text-brand-gray hover:border-red-500 hover:text-red-500"
                  >
                    🗑️
                  </button>
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
              className="h-11 w-full cursor-pointer rounded-lg border border-gray-300"
            />
          </Field>
          {error ? <p className="text-sm font-medium text-red-600">{error}</p> : null}
          <SubmitButton loading={save.isPending}>{t('common.save')}</SubmitButton>
        </form>
      </Modal>

      {/* Confirmation suppression */}
      <Modal
        open={deleteTarget !== null}
        onClose={() => setDeleteTarget(null)}
        title={t('sessions.delete')}
      >
        <p className="text-sm text-brand-gray">{t('labels.deleteConfirm')}</p>
        <div className="mt-5 flex justify-end gap-2">
          <button
            onClick={() => setDeleteTarget(null)}
            className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-semibold text-brand-gray"
          >
            {t('common.cancel')}
          </button>
          <button
            onClick={() => remove.mutate()}
            disabled={remove.isPending}
            className="rounded-lg bg-red-600 px-4 py-2 text-sm font-bold text-white hover:bg-red-700"
          >
            {t('common.confirm')}
          </button>
        </div>
      </Modal>
    </div>
  );
}
