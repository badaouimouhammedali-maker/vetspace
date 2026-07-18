import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { EmojiRating, Modal, Skeleton, formatSeconds } from '../../components/ui';
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
      <div className="flex min-h-screen items-center justify-center bg-gray-50">
        <Skeleton className="h-64 w-full max-w-2xl" />
      </div>
    );
  }
  if (play.isError || !play.data) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-gray-50">
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
      <div className="flex min-h-screen flex-col items-center justify-center gap-6 bg-gray-50 p-6">
        <img src="/brand/Logo.svg" alt="VetSpace" className="h-14" />
        <div className="w-full max-w-md rounded-2xl bg-white p-8 text-center shadow-sm ring-1 ring-gray-100">
          <h1 className="text-2xl font-extrabold text-brand-navy">{t('play.scoreTitle')}</h1>
          <p className="mt-6 text-sm font-medium text-brand-gray">{t('play.yourScore')}</p>
          <p className="text-5xl font-extrabold text-brand-green">
            {score != null ? `${score}%` : '—'}
          </p>
          <div className="mt-8">
            <p className="mb-2 text-sm font-medium text-brand-gray">{t('play.ratePrompt')}</p>
            <div className="flex justify-center">
              <EmojiRating value={result?.rating ?? null} onChange={(r) => rate.mutate(r)} />
            </div>
          </div>
          <button
            onClick={() =>
              navigate(
                play.data.sessionType === 'EXAMEN'
                  ? '/app/sessions/examens'
                  : '/app/sessions/entrainement',
              )
            }
            className="mt-8 w-full rounded-lg bg-brand-green px-4 py-2.5 text-sm font-bold text-white hover:bg-brand-green-hover"
          >
            {t('play.backToSessions')}
          </button>
        </div>
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
    <div className="flex min-h-screen bg-gray-50">
      {/* Rail gauche : questions + chronomètres */}
      <aside className="flex w-44 shrink-0 flex-col border-r border-gray-200 bg-white lg:w-56">
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
                  <span className="font-bold text-red-500">✗</span>
                )
              ) : st.state === 'CONSULTED' ? (
                <span className="font-bold text-gray-400">👁</span>
              ) : null;
            return (
              <button
                key={q.question.id}
                onClick={() => setIndex(i)}
                className={`flex w-full items-center justify-between rounded-lg px-3 py-2 text-sm font-semibold transition ${
                  i === index
                    ? 'bg-brand-green text-white'
                    : 'text-brand-gray hover:bg-gray-50'
                }`}
              >
                {t('play.question')} {i + 1}
                {mark}
              </button>
            );
          })}
        </nav>
        <div className="sticky bottom-0 space-y-1 border-t border-gray-100 bg-white p-4 text-xs font-semibold text-brand-navy">
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
                <button
                  onClick={() => setSignalOpen(true)}
                  aria-label={t('signals.report')}
                  title={t('signals.report')}
                  className="mt-1 shrink-0 rounded-lg border border-gray-200 p-2 text-base text-brand-gray transition hover:border-red-500 hover:text-red-500"
                >
                  🚩
                </button>
              </div>
              {current.question.statementImages.length > 0 ? (
                <div className="mt-4 flex flex-wrap gap-3">
                  {current.question.statementImages.map((src) => (
                    <button key={src} onClick={() => setLightbox(src)} className="group relative">
                      <img src={src} alt="" className="h-32 rounded-lg object-cover" />
                      <span className="absolute inset-0 flex items-center justify-center rounded-lg bg-brand-navy/50 text-xs font-bold text-white opacity-0 transition group-hover:opacity-100">
                        {t('play.enlargeImage')}
                      </span>
                    </button>
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
                  const rowClasses = showCorrection
                    ? correctionRow?.isTrue
                      ? 'border-brand-green bg-brand-green/5'
                      : 'border-red-300 bg-red-50'
                    : isSelected
                      ? 'border-brand-green bg-brand-green/10'
                      : 'border-gray-200 bg-white hover:border-brand-green/50';
                  return (
                    <div key={proposition.id} className={`rounded-xl border-2 transition ${rowClasses}`}>
                      <button
                        onClick={() => toggleProposition(proposition.id)}
                        disabled={currentState !== 'UNANSWERED'}
                        className="flex w-full items-start gap-3 p-4 text-left"
                      >
                        <span
                          className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-sm font-bold ${
                            isSelected ? 'bg-brand-green text-white' : 'bg-gray-100 text-brand-navy'
                          }`}
                        >
                          {proposition.letter}
                        </span>
                        <span className="flex-1 pt-0.5 text-sm text-gray-800">{proposition.text}</span>
                        {showCorrection && correctionRow ? (
                          <span
                            className={`text-sm font-extrabold ${correctionRow.isTrue ? 'text-brand-green' : 'text-red-600'}`}
                          >
                            {correctionRow.isTrue ? t('play.vrai') : t('play.faux')}
                          </span>
                        ) : null}
                      </button>
                      {showCorrection && correctionRow?.explanationHtml ? (
                        <div className="border-t border-gray-100 px-4 py-3">
                          <SafeHtml
                            html={correctionRow.explanationHtml}
                            className="prose-sm text-sm text-gray-700 [&_ul]:list-disc [&_ul]:pl-5 [&_ol]:list-decimal [&_ol]:pl-5"
                          />
                          {correctionRow.explanationImages.map((src) => (
                            <button key={src} onClick={() => setLightbox(src)} className="mt-2">
                              <img src={src} alt="" className="h-24 rounded-lg object-cover" />
                            </button>
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
                  <button
                    onClick={() => answer.mutate()}
                    disabled={answer.isPending}
                    className="rounded-lg bg-brand-green px-10 py-3 text-sm font-extrabold tracking-wide text-white transition hover:bg-brand-green-hover disabled:opacity-60"
                  >
                    {t('play.valider')}
                  </button>
                  <button
                    onClick={() => consult.mutate(current.question.id)}
                    disabled={consult.isPending}
                    className="text-xs font-semibold text-brand-gray underline hover:text-brand-navy"
                  >
                    {t('play.consulter')}
                  </button>
                </div>
              ) : null}

              {/* Navigation */}
              <div className="mt-8 flex items-center justify-between">
                <button
                  onClick={() => setIndex((i) => Math.max(0, i - 1))}
                  disabled={index === 0}
                  className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-semibold text-brand-gray transition hover:border-brand-navy hover:text-brand-navy disabled:opacity-40"
                >
                  ← {t('play.previous')}
                </button>
                {index === questions.length - 1 ? (
                  <button
                    onClick={() => setConfirmSubmit(true)}
                    className="rounded-lg bg-brand-navy px-6 py-2 text-sm font-bold text-white transition hover:bg-brand-navy/90"
                  >
                    {t('play.submit')}
                  </button>
                ) : (
                  <button
                    onClick={() => setIndex((i) => Math.min(questions.length - 1, i + 1))}
                    className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-semibold text-brand-gray transition hover:border-brand-navy hover:text-brand-navy"
                  >
                    {t('play.next')} →
                  </button>
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
          <button
            className="absolute right-6 top-6 text-3xl font-bold text-white"
            aria-label={t('play.close')}
          >
            ×
          </button>
        </div>
      ) : null}

      {/* Confirmation de fin */}
      <Modal open={confirmSubmit} onClose={() => setConfirmSubmit(false)} title={t('play.submit')}>
        <p className="text-sm text-brand-gray">{t('play.submitConfirm')}</p>
        <div className="mt-5 flex justify-end gap-2">
          <button
            onClick={() => setConfirmSubmit(false)}
            className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-semibold text-brand-gray"
          >
            {t('common.cancel')}
          </button>
          <button
            onClick={() => submit.mutate()}
            disabled={submit.isPending}
            className="rounded-lg bg-brand-green px-4 py-2 text-sm font-bold text-white hover:bg-brand-green-hover"
          >
            {t('common.confirm')}
          </button>
        </div>
      </Modal>
    </div>
  );
}
