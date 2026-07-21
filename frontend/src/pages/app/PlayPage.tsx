import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  Badge,
  Button,
  Card,
  EmojiRating,
  Modal,
  PlainButton,
  SelectableRow,
  Skeleton,
  formatSeconds,
} from '../../components/ui';
import { SignalDialog } from '../../components/SignalDialog';
import { useToast } from '../../components/ToastProvider';
import { t } from '../../i18n/fr';
import { apiErrorMessage } from '../../lib/api';
import {
  answerQuestion,
  consultQuestion,
  fetchPlay,
  patchSession,
  submitSession,
} from '../../lib/endpoints';
import { SafeHtml } from '../../lib/sanitize';
import type { Correction, SessionSummary } from '../../lib/schemas';

type LocalState = { state: 'ANSWERED' | 'CONSULTED'; isCorrect: boolean | null };

export function PlayPage() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { toast } = useToast();

  const play = useQuery({ queryKey: ['play', id], queryFn: () => fetchPlay(id), retry: false });

  const [index, setIndex] = useState(0);
  const [selected, setSelected] = useState<Record<string, string[]>>({});
  const [corrections, setCorrections] = useState<Record<string, Correction>>({});
  const [overrides, setOverrides] = useState<Record<string, LocalState>>({});
  const [lightbox, setLightbox] = useState<string | null>(null);
  const [signalOpen, setSignalOpen] = useState(false);
  const [confirmSubmit, setConfirmSubmit] = useState(false);
  const [result, setResult] = useState<SessionSummary | null>(null);

  // Chronomètres : secondes non synchronisées par question, envoyées avec la réponse.
  const unsyncedRef = useRef<Record<string, number>>({});
  const [tick, setTick] = useState(0);
  const [paused, setPaused] = useState(false);

  const questions = useMemo(() => play.data?.questions ?? [], [play.data]);
  const current = questions[index];
  const currentQuestionId = current?.question.id ?? null;
  const finished = result !== null || play.data?.status === 'SUBMITTED';

  // Pré-remplir la sélection déjà enregistrée côté serveur.
  useEffect(() => {
    if (!play.data) {
      return;
    }
    const initial: Record<string, string[]> = {};
    play.data.questions.forEach((q) => {
      if (q.selectedPropositionIds.length > 0) {
        initial[q.question.id] = q.selectedPropositionIds;
      }
    });
    setSelected((prev) => ({ ...initial, ...prev }));
  }, [play.data]);

  // Pause quand l'onglet perd le focus.
  useEffect(() => {
    const onVisibility = () => setPaused(document.hidden);
    document.addEventListener('visibilitychange', onVisibility);
    window.addEventListener('blur', () => setPaused(true));
    window.addEventListener('focus', () => setPaused(false));
    return () => document.removeEventListener('visibilitychange', onVisibility);
  }, []);

  // Tic à la seconde tant que la session est active et l'onglet visible.
  useEffect(() => {
    if (paused || finished || !currentQuestionId) {
      return;
    }
    const interval = setInterval(() => {
      unsyncedRef.current[currentQuestionId] =
        (unsyncedRef.current[currentQuestionId] ?? 0) + 1;
      setTick((v) => v + 1);
    }, 1000);
    return () => clearInterval(interval);
  }, [paused, finished, currentQuestionId]);

  const unsyncedTotal = Object.values(unsyncedRef.current).reduce((a, b) => a + b, 0);
  const sessionSeconds = (play.data?.totalSeconds ?? 0) + unsyncedTotal;
  const questionSeconds = current
    ? current.secondsSpent + (unsyncedRef.current[current.question.id] ?? 0)
    : 0;
  void tick; // le tic force uniquement le re-rendu des chronomètres

  const stateOf = useCallback(
    (questionId: string): { state: string; isCorrect: boolean | null } => {
      const override = overrides[questionId];
      if (override) {
        return override;
      }
      const row = questions.find((q) => q.question.id === questionId);
      return { state: row?.state ?? 'UNANSWERED', isCorrect: row?.isCorrect ?? null };
    },
    [overrides, questions],
  );

  const answeredCount = questions.filter((q) => stateOf(q.question.id).state !== 'UNANSWERED').length;
  const progress = questions.length > 0 ? (answeredCount / questions.length) * 100 : 0;

  const answer = useMutation({
    mutationFn: () => {
      const questionId = current!.question.id;
      const seconds = unsyncedRef.current[questionId] ?? 0;
      unsyncedRef.current[questionId] = 0;
      return answerQuestion(id, questionId, selected[questionId] ?? [], seconds);
    },
    onSuccess: (correction) => {
      setCorrections((c) => ({ ...c, [correction.questionId]: correction }));
      setOverrides((o) => ({
        ...o,
        [correction.questionId]: { state: 'ANSWERED', isCorrect: correction.isCorrect },
      }));
    },
    onError: (err) => toast('error', apiErrorMessage(err) ?? t('common.error')),
  });

  const consult = useMutation({
    mutationFn: (questionId: string) => consultQuestion(id, questionId),
    onSuccess: (correction) => {
      setCorrections((c) => ({ ...c, [correction.questionId]: correction }));
      setOverrides((o) => {
        const previous = stateOf(correction.questionId);
        return {
          ...o,
          [correction.questionId]: {
            state: previous.state === 'ANSWERED' ? 'ANSWERED' : 'CONSULTED',
            isCorrect: correction.isCorrect,
          },
        };
      });
    },
  });

  const submit = useMutation({
    mutationFn: () => submitSession(id),
    onSuccess: async (summary) => {
      setConfirmSubmit(false);
      setResult(summary);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['sessions'] }),
        queryClient.invalidateQueries({ queryKey: ['overview'] }),
        queryClient.invalidateQueries({ queryKey: ['weekly'] }),
      ]);
    },
    onError: (err) => toast('error', apiErrorMessage(err) ?? t('common.error')),
  });

  const rate = useMutation({
    mutationFn: (rating: number) => patchSession(id, { rating }),
    onSuccess: (summary) => setResult(summary),
  });

  // Correction déjà connue côté serveur mais pas encore chargée localement
  // (question répondue lors d'une visite précédente) : on la récupère via consult.
  const currentState = current ? stateOf(current.question.id).state : 'UNANSWERED';
  const currentCorrection = current ? corrections[current.question.id] : undefined;
  useEffect(() => {
    if (
      current &&
      currentState !== 'UNANSWERED' &&
      !currentCorrection &&
      !consult.isPending &&
      !finished
    ) {
      consult.mutate(current.question.id);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- déclenché par le changement de question uniquement
  }, [currentQuestionId, currentState, currentCorrection === undefined, finished]);

  if (play.isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-canvas">
        <Skeleton className="h-64 w-full max-w-2xl" />
      </div>
    );
  }
  if (play.isError || !play.data) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-canvas">
        <p className="text-brand-gray">{t('play.notFound')}</p>
        <Link to="/app" className="font-semibold text-brand-green hover:underline">
          {t('common.back')}
        </Link>
      </div>
    );
  }

  // Écran de score
  if (finished) {
    const score = result?.score ?? null;
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-6 bg-canvas p-6">
        <img src="/brand/Logo.svg" alt="VetSpace" className="h-14" />
        <Card className="w-full max-w-md text-center">
          <h1 className="text-h1 text-brand-navy">{t('play.scoreTitle')}</h1>
          <p className="mt-6 text-caption font-medium text-brand-gray">{t('play.yourScore')}</p>
          <p className="text-[44px] font-bold leading-tight tabular-nums text-brand-green">
            {score != null ? `${score}%` : '—'}
          </p>
          <div className="mt-8">
            <p className="mb-2 text-caption font-medium text-brand-gray">{t('play.ratePrompt')}</p>
            <div className="flex justify-center">
              <EmojiRating value={result?.rating ?? null} onChange={(r) => rate.mutate(r)} />
            </div>
          </div>
          <Button variant="primary" size="md" className="mt-8 w-full"
            onClick={() =>
              navigate(
                play.data.sessionType === 'EXAMEN'
                  ? '/app/sessions/examens'
                  : '/app/sessions/entrainement',
              )
            }
          >
            {t('play.backToSessions')}
          </Button>
        </Card>
      </div>
    );
  }

  const selectedIds = current ? (selected[current.question.id] ?? []) : [];
  const showCorrection = currentState !== 'UNANSWERED' && currentCorrection;

  function toggleProposition(propositionId: string) {
    if (!current || currentState !== 'UNANSWERED') {
      return;
    }
    const questionId = current.question.id;
    setSelected((s) => {
      const list = s[questionId] ?? [];
      return {
        ...s,
        [questionId]: list.includes(propositionId)
          ? list.filter((x) => x !== propositionId)
          : [...list, propositionId],
      };
    });
  }

  return (
    <div className="flex min-h-screen bg-canvas">
      {/* Rail gauche : questions + chronomètres */}
      <aside className="flex w-44 shrink-0 flex-col border-r border-gray-200 bg-surface lg:w-56">
        <Link to="/app" className="flex justify-center border-b border-gray-100 py-4">
          <img src="/brand/Logo.svg" alt="VetSpace" className="h-9" />
        </Link>
        <nav className="flex-1 space-y-1 overflow-y-auto p-3">
          {questions.map((q, i) => {
            const st = stateOf(q.question.id);
            const mark =
              st.state === 'ANSWERED' ? (
                st.isCorrect ? (
                  <span className="font-bold text-brand-green">✓</span>
                ) : (
                  <span className="font-bold text-danger">✗</span>
                )
              ) : st.state === 'CONSULTED' ? (
                <span className="font-bold text-gray-400">👁</span>
              ) : null;
            return (
              <SelectableRow
                key={q.question.id}
                selected={i === index}
                onClick={() => setIndex(i)}
                className="justify-between"
              >
                <span>
                  {t('play.question')} {i + 1}
                </span>
                {mark}
              </SelectableRow>
            );
          })}
        </nav>
        <div className="sticky bottom-0 space-y-1 border-t border-gray-100 bg-surface p-4 text-xs font-semibold text-brand-navy">
          <p>
            {t('play.sessionTimer')} : ⏱ {formatSeconds(sessionSeconds)}
          </p>
          <p>
            {t('play.questionTimer')} : ⏱ {formatSeconds(questionSeconds)}
          </p>
        </div>
      </aside>

      {/* Volet principal */}
      <main className="flex-1 overflow-y-auto">
        <div className="mx-auto max-w-3xl px-6 py-8">
          {/* Progression */}
          <div className="mb-6 flex items-center gap-3">
            <div className="h-2 flex-1 overflow-hidden rounded-full bg-gray-200">
              <div
                className="h-full rounded-full bg-brand-green transition-all"
                style={{ width: `${progress}%` }}
              />
            </div>
            <span className="text-sm font-bold text-brand-navy">{Math.round(progress)}%</span>
          </div>

          {current ? (
            <>
              <div className="flex items-start justify-between gap-3">
                <h1 className="text-lg font-bold leading-relaxed text-brand-navy">
                  {current.question.statement}
                </h1>
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => setSignalOpen(true)}
                  aria-label={t('signals.report')}
                  title={t('signals.report')}
                  className="mt-1 shrink-0 text-gray-500 hover:border-danger hover:text-danger"
                >
                  🚩
                </Button>
              </div>
              {current.question.statementImages.length > 0 ? (
                <div className="mt-4 flex flex-wrap gap-3">
                  {current.question.statementImages.map((src) => (
                    <PlainButton key={src} onClick={() => setLightbox(src)} className="group relative">
                      <img src={src} alt="" className="h-32 rounded-lg object-cover" />
                      <span className="absolute inset-0 flex items-center justify-center rounded-lg bg-brand-navy/50 text-xs font-bold text-white opacity-0 transition group-hover:opacity-100">
                        {t('play.enlargeImage')}
                      </span>
                    </PlainButton>
                  ))}
                </div>
              ) : null}

              {/* Propositions */}
              <div className="mt-6 space-y-3">
                {current.question.propositions.map((proposition) => {
                  const correctionRow = currentCorrection?.propositions.find(
                    (p) => p.id === proposition.id,
                  );
                  const isSelected = selectedIds.includes(proposition.id);
                  // Correction reads as a soft status band with a 4px left border, not
                  // as coloured text — the verdict should be legible from the shape of
                  // the row before anyone reads the word.
                  const rowClasses = showCorrection
                    ? correctionRow?.isTrue
                      ? 'border-subtle border-l-4 border-l-success bg-success/10'
                      : 'border-subtle border-l-4 border-l-danger bg-danger/10'
                    : isSelected
                      ? 'border-brand-green bg-brand-green/10'
                      : 'border-subtle bg-surface hover:bg-gray-50';
                  return (
                    <div
                      key={proposition.id}
                      className={`overflow-hidden rounded-md border transition-colors duration-150 ${rowClasses}`}
                    >
                      {/* The selected/correction fill lives on the wrapper above, so this
                          is a bare button that only carries the semantics. */}
                      <PlainButton
                        onClick={() => toggleProposition(proposition.id)}
                        disabled={currentState !== 'UNANSWERED'}
                        aria-pressed={isSelected}
                        className="flex w-full items-start gap-3 p-4 text-left"
                      >
                        <span
                          className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-sm font-bold ${
                            isSelected ? 'bg-brand-green text-white' : 'bg-gray-100 text-brand-navy'
                          }`}
                        >
                          {proposition.letter}
                        </span>
                        <span className="flex-1 pt-0.5 text-body text-gray-800">
                          {proposition.text}
                        </span>
                        {showCorrection && correctionRow ? (
                          <Badge tone={correctionRow.isTrue ? 'success' : 'danger'}>
                            {correctionRow.isTrue ? t('play.vrai') : t('play.faux')}
                          </Badge>
                        ) : null}
                      </PlainButton>
                      {showCorrection && correctionRow?.explanationHtml ? (
                        <div className="border-t border-subtle px-4 py-3">
                          <SafeHtml
                            html={correctionRow.explanationHtml}
                            className="prose-sm text-sm text-gray-700 [&_ul]:list-disc [&_ul]:pl-5 [&_ol]:list-decimal [&_ol]:pl-5"
                          />
                          {correctionRow.explanationImages.map((src) => (
                            <PlainButton key={src} onClick={() => setLightbox(src)} className="mt-2">
                              <img src={src} alt="" className="h-24 rounded-lg object-cover" />
                            </PlainButton>
                          ))}
                        </div>
                      ) : null}
                    </div>
                  );
                })}
              </div>

              {/* Actions */}
              {currentState === 'UNANSWERED' ? (
                <div className="mt-6 flex flex-col items-center gap-3">
                  <Button variant="primary" size="md" className="tracking-wide"
                    onClick={() => answer.mutate()}
                    disabled={answer.isPending}
                  >
                    {t('play.valider')}
                  </Button>
                  <Button variant="ghost" size="sm" className="underline"
                    onClick={() => consult.mutate(current.question.id)}
                    disabled={consult.isPending}
                    
                  >
                    {t('play.consulter')}
                  </Button>
                </div>
              ) : null}

              {/* Navigation */}
              <div className="mt-8 flex items-center justify-between">
                <Button variant="secondary" size="md"
                  onClick={() => setIndex((i) => Math.max(0, i - 1))}
                  disabled={index === 0}
                >
                  ← {t('play.previous')}
                </Button>
                {index === questions.length - 1 ? (
                  <Button variant="secondary" size="md"
                    onClick={() => setConfirmSubmit(true)}
                    
                  >
                    {t('play.submit')}
                  </Button>
                ) : (
                  <Button variant="secondary" size="md"
                    onClick={() => setIndex((i) => Math.min(questions.length - 1, i + 1))}
                  >
                    {t('play.next')} →
                  </Button>
                )}
              </div>
            </>
          ) : null}
        </div>
      </main>

      {/* Signalement de la question courante */}
      <SignalDialog
        open={signalOpen}
        questionId={current?.question.id ?? null}
        onClose={() => setSignalOpen(false)}
      />

      {/* Lightbox */}
      {lightbox ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-brand-navy/80 p-6"
          onClick={() => setLightbox(null)}
        >
          <img src={lightbox} alt="" className="max-h-full max-w-full rounded-xl" />
          <PlainButton
            onClick={() => setLightbox(null)}
            className="absolute right-6 top-6 text-display text-white"
            aria-label={t('play.close')}
          >
            ×
          </PlainButton>
        </div>
      ) : null}

      {/* Confirmation de fin */}
      <Modal open={confirmSubmit} onClose={() => setConfirmSubmit(false)} title={t('play.submit')}>
        <p className="text-sm text-brand-gray">{t('play.submitConfirm')}</p>
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="secondary" size="md"
            onClick={() => setConfirmSubmit(false)}
          >
            {t('common.cancel')}
          </Button>
          <Button variant="primary" size="md"
            onClick={() => submit.mutate()}
            disabled={submit.isPending}
          >
            {t('common.confirm')}
          </Button>
        </div>
      </Modal>
    </div>
  );
}
