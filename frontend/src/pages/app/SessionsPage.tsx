import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Donut, EmojiRating, EmptyState, Modal, Skeleton, formatSeconds } from '../../components/ui';
import { useToast } from '../../components/ToastProvider';
import { t } from '../../i18n/fr';
import { apiErrorMessage } from '../../lib/api';
import {
  deleteSession,
  fetchSessions,
  patchSession,
  repeatSession,
  resetSession,
  type RepeatMode,
} from '../../lib/endpoints';
import type { SessionSummary, SessionType } from '../../lib/schemas';
import { SessionBuilderDialog } from './SessionBuilderDialog';

function SessionCard({
  session,
  onRepeat,
  onDelete,
}: {
  session: SessionSummary;
  onRepeat: (session: SessionSummary) => void;
  onDelete: (session: SessionSummary) => void;
}) {
  const queryClient = useQueryClient();
  const patch = useMutation({
    mutationFn: (input: { favorite?: boolean; rating?: number }) => patchSession(session.id, input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['sessions'] }),
  });

  const progress =
    session.questionCount > 0 ? (session.answeredCount / session.questionCount) * 100 : 0;
  const started = session.answeredCount > 0 || session.status === 'SUBMITTED';

  return (
    <div className="rounded-2xl bg-white p-5 shadow-sm ring-1 ring-gray-100">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="truncate font-bold text-brand-navy">{session.title}</p>
          <p className="mt-0.5 text-xs text-brand-gray">
            {new Date(session.startedAt).toLocaleDateString('fr-FR', {
              day: '2-digit',
              month: 'long',
              year: 'numeric',
            })}
          </p>
        </div>
        <button
          onClick={() => patch.mutate({ favorite: !session.favorite })}
          aria-label="Favori"
          className={`text-xl transition ${session.favorite ? 'text-yellow-400' : 'text-gray-300 hover:text-yellow-400'}`}
        >
          ★
        </button>
      </div>

      <div className="mt-4 flex items-center gap-4">
        <Donut percent={progress} size={72} />
        <div className="flex-1 text-sm text-brand-gray">
          <p>
            {session.answeredCount}/{session.questionCount} {t('sessions.questions')}
          </p>
          <p>⏱ {formatSeconds(session.totalSeconds)}</p>
          {session.score != null ? (
            <p className="font-semibold text-brand-navy">
              {t('sessions.score')} : {session.score}%
            </p>
          ) : null}
        </div>
      </div>

      <div className="mt-4 flex items-center justify-between">
        <EmojiRating
          value={session.rating}
          onChange={(rating) => patch.mutate({ rating })}
        />
        <div className="flex items-center gap-1.5">
          <button
            onClick={() => onRepeat(session)}
            aria-label={t('sessions.repeat')}
            title={t('sessions.repeat')}
            className="rounded-lg border border-gray-200 p-2 text-sm text-brand-gray transition hover:border-brand-green hover:text-brand-green"
          >
            🔁
          </button>
          <button
            onClick={() => onDelete(session)}
            aria-label={t('sessions.delete')}
            title={t('sessions.delete')}
            className="rounded-lg border border-gray-200 p-2 text-sm text-brand-gray transition hover:border-red-500 hover:text-red-500"
          >
            🗑️
          </button>
          <Link
            to={`/app/session/${session.id}`}
            className="rounded-lg bg-brand-green px-4 py-2 text-sm font-bold text-white transition hover:bg-brand-green-hover"
          >
            {started && session.status === 'ACTIVE' ? t('sessions.resume') : t('sessions.play')}
          </Link>
        </div>
      </div>
    </div>
  );
}

export function SessionsPage({ type }: { type: SessionType }) {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const [builderOpen, setBuilderOpen] = useState(false);
  const [repeatTarget, setRepeatTarget] = useState<SessionSummary | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<SessionSummary | null>(null);

  const sessions = useQuery({ queryKey: ['sessions'], queryFn: fetchSessions });
  const filtered = (sessions.data?.content ?? []).filter((s) => s.sessionType === type);

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['sessions'] });

  const repeat = useMutation({
    mutationFn: (mode: RepeatMode) => repeatSession(repeatTarget!.id, mode),
    onSuccess: async () => {
      await invalidate();
      setRepeatTarget(null);
    },
    onError: (err) => toast('error', apiErrorMessage(err) ?? t('repeat.error')),
  });

  const reset = useMutation({
    mutationFn: () => resetSession(repeatTarget!.id),
    onSuccess: async () => {
      await invalidate();
      setRepeatTarget(null);
    },
  });

  const remove = useMutation({
    mutationFn: () => deleteSession(deleteTarget!.id),
    onSuccess: async () => {
      await invalidate();
      setDeleteTarget(null);
    },
  });

  const title = type === 'ENTRAINEMENT' ? t('sessions.entrainementTitle') : t('sessions.examensTitle');

  const repeatOptions: { mode: RepeatMode; label: string }[] = [
    { mode: 'WRONG_ONLY', label: t('repeat.wrong') },
    { mode: 'UNANSWERED_ONLY', label: t('repeat.unanswered') },
    { mode: 'ALL', label: t('repeat.all') },
    { mode: 'SAME_FILTERS', label: t('repeat.sameFilters') },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-extrabold text-brand-navy dark:text-white">{title}</h1>
        <button
          onClick={() => setBuilderOpen(true)}
          className="rounded-lg bg-brand-green px-4 py-2.5 text-sm font-bold text-white transition hover:bg-brand-green-hover"
        >
          {t('sessions.create')}
        </button>
      </div>

      {sessions.isLoading ? (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          <Skeleton className="h-52" />
          <Skeleton className="h-52" />
          <Skeleton className="h-52" />
        </div>
      ) : filtered.length === 0 ? (
        <EmptyState
          message={t('sessions.empty')}
          action={
            <button
              onClick={() => setBuilderOpen(true)}
              className="rounded-lg bg-brand-green px-4 py-2 text-sm font-bold text-white hover:bg-brand-green-hover"
            >
              {t('sessions.create')}
            </button>
          }
        />
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {filtered.map((session) => (
            <SessionCard
              key={session.id}
              session={session}
              onRepeat={setRepeatTarget}
              onDelete={setDeleteTarget}
            />
          ))}
        </div>
      )}

      <SessionBuilderDialog
        open={builderOpen}
        onClose={() => setBuilderOpen(false)}
        defaultType={type}
      />

      {/* Dialogue répéter */}
      <Modal
        open={repeatTarget !== null}
        onClose={() => setRepeatTarget(null)}
        title={t('repeat.title')}
      >
        <div className="space-y-2">
          <button
            onClick={() => reset.mutate()}
            disabled={reset.isPending}
            className="w-full rounded-lg border border-gray-200 px-4 py-3 text-left text-sm font-semibold text-brand-navy transition hover:border-brand-green"
          >
            {t('repeat.reset')}
            <span className="mt-0.5 block text-xs font-normal text-brand-gray">
              {t('repeat.resetHint')}
            </span>
          </button>
          {repeatOptions.map((option) => (
            <button
              key={option.mode}
              onClick={() => repeat.mutate(option.mode)}
              disabled={repeat.isPending}
              className="w-full rounded-lg border border-gray-200 px-4 py-3 text-left text-sm font-semibold text-brand-navy transition hover:border-brand-green"
            >
              {option.label}
            </button>
          ))}
        </div>
      </Modal>

      {/* Confirmation de suppression */}
      <Modal
        open={deleteTarget !== null}
        onClose={() => setDeleteTarget(null)}
        title={t('sessions.delete')}
      >
        <p className="text-sm text-brand-gray">{t('sessions.deleteConfirm')}</p>
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
