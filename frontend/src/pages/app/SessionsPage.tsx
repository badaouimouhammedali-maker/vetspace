import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import {
  Button,
  Card,
  CardGridSkeleton,
  Donut,
  EmojiRating,
  EmptyState,
  LinkButton,
  Modal,
  PlainButton,
  formatSeconds,
} from '../../components/ui';
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
    <Card padding="sm" className="flex h-full flex-col">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="truncate font-semibold text-brand-navy">{session.title}</p>
          <p className="mt-0.5 text-caption text-gray-500">
            {new Date(session.startedAt).toLocaleDateString('fr-FR', {
              day: '2-digit',
              month: 'long',
              year: 'numeric',
            })}
          </p>
        </div>
        <PlainButton
          onClick={() => patch.mutate({ favorite: !session.favorite })}
          aria-label="Favori"
          aria-pressed={session.favorite}
          className={`text-xl transition-transform duration-150 hover:scale-110 active:scale-95 ${
            session.favorite ? 'text-star' : 'text-gray-300 hover:text-star'
          }`}
        >
          ★
        </PlainButton>
      </div>

      <div className="mt-4 flex items-center gap-4">
        <Donut percent={progress} size={72} />
        <div className="flex-1 space-y-0.5 text-caption text-gray-500">
          <p className="tabular-nums">
            {session.answeredCount}/{session.questionCount} {t('sessions.questions')}
          </p>
          <p className="tabular-nums">⏱ {formatSeconds(session.totalSeconds)}</p>
          {session.score != null ? (
            <p className="font-semibold tabular-nums text-brand-navy">
              {t('sessions.score')} : {session.score}%
            </p>
          ) : null}
        </div>
      </div>

      <div className="mt-auto flex items-center justify-between pt-4">
        <EmojiRating
          value={session.rating}
          onChange={(rating) => patch.mutate({ rating })}
        />
        <div className="flex items-center gap-1.5">
          <Button
            variant="secondary"
            size="sm"
            onClick={() => onRepeat(session)}
            aria-label={t('sessions.repeat')}
            title={t('sessions.repeat')}
          >
            🔁
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => onDelete(session)}
            aria-label={t('sessions.delete')}
            title={t('sessions.delete')}
            className="text-gray-500 hover:bg-danger/10 hover:text-danger"
          >
            🗑️
          </Button>
          <LinkButton
            to={`/app/session/${session.id}`}
            size="sm"
          >
            {started && session.status === 'ACTIVE' ? t('sessions.resume') : t('sessions.play')}
          </LinkButton>
        </div>
      </div>
    </Card>
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
        <h1 className="text-h1 text-brand-navy dark:text-white">{title}</h1>
        <Button variant="primary" size="md"
          onClick={() => setBuilderOpen(true)}
        >
          {t('sessions.create')}
        </Button>
      </div>

      {sessions.isLoading ? (
        <CardGridSkeleton count={3} />
      ) : filtered.length === 0 ? (
        <EmptyState
          title={t('sessions.empty')}
          action={
            <Button variant="primary" size="md"
              onClick={() => setBuilderOpen(true)}
            >
              {t('sessions.create')}
            </Button>
          }
        />
      ) : (
        <div className="grid items-stretch gap-4 md:grid-cols-2 xl:grid-cols-3">
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
