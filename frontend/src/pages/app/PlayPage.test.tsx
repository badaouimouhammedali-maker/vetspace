import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ToastProvider';
import { PlayPage } from './PlayPage';
import * as endpoints from '../../lib/endpoints';

vi.mock('../../lib/endpoints');

const PROP_A = '11111111-1111-1111-1111-111111111111';
const PROP_B = '22222222-2222-2222-2222-222222222222';
const QID = '33333333-3333-3333-3333-333333333333';

const session = {
  id: 'S-1',
  title: 'Session test',
  sessionType: 'ENTRAINEMENT' as const,
  status: 'ACTIVE' as const,
  totalSeconds: 0,
  questions: [
    {
      question: {
        id: QID,
        courseId: '44444444-4444-4444-4444-444444444444',
        statement: 'Quelle est la couleur du sang oxygéné ?',
        statementImages: [],
        difficulty: 'EASY' as const,
        propositions: [
          { id: PROP_A, letter: 'A', text: 'Rouge vif', position: 1 },
          { id: PROP_B, letter: 'B', text: 'Bleu foncé', position: 2 },
        ],
      },
      state: 'UNANSWERED' as const,
      isCorrect: null,
      secondsSpent: 0,
      selectedPropositionIds: [],
      position: 1,
    },
  ],
};

const correction = {
  questionId: QID,
  state: 'ANSWERED' as const,
  isCorrect: true,
  selectedPropositionIds: [PROP_A],
  propositions: [
    {
      id: PROP_A,
      letter: 'A',
      text: 'Rouge vif',
      isTrue: true,
      // Intentional XSS payload — must be stripped on render.
      explanationHtml: '<p>Bonne réponse&nbsp;: le sang oxygéné est rouge.</p><script>window.__pwned=1</script>',
      explanationImages: [],
      position: 1,
    },
    {
      id: PROP_B,
      letter: 'B',
      text: 'Bleu foncé',
      isTrue: false,
      explanationHtml: null,
      explanationImages: [],
      position: 2,
    },
  ],
};

function renderPlayer() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <ToastProvider>
        <MemoryRouter initialEntries={['/app/session/S-1']}>
          <Routes>
            <Route path="/app/session/:id" element={<PlayPage />} />
          </Routes>
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe('PlayPage', () => {
  beforeEach(() => {
    vi.mocked(endpoints.fetchPlay).mockResolvedValue(session);
    vi.mocked(endpoints.answerQuestion).mockResolvedValue(correction);
    // @ts-expect-error window global scratch for the XSS assertion
    delete window.__pwned;
  });

  it('lets the student select a proposition, validate, and see the sanitized correction', async () => {
    const user = userEvent.setup();
    renderPlayer();

    // Question loaded.
    await screen.findByText('Quelle est la couleur du sang oxygéné ?');

    // Select proposition A, then validate.
    await user.click(screen.getByText('Rouge vif'));
    await user.click(screen.getByRole('button', { name: 'VALIDER' }));

    // answerQuestion was called for this question.
    expect(endpoints.answerQuestion).toHaveBeenCalledWith(
      'S-1',
      QID,
      [PROP_A],
      expect.any(Number),
    );

    // Correction rendered: explanation text present, VRAI badge shown.
    await screen.findByText(/Bonne réponse/);
    expect(screen.getByText('VRAI')).toBeInTheDocument();

    // Sanitized: the <script> never made it into the DOM and did not execute.
    expect(document.querySelector('script')).toBeNull();
    // @ts-expect-error scratch global
    expect(window.__pwned).toBeUndefined();
    expect(document.body.innerHTML).not.toContain('window.__pwned');
  });

  /**
   * Opening a session that is ALREADY submitted — a refresh, or a click from the
   * session list. There is no submit response in this render, so the score has to come
   * off the play payload. It did not, and the student saw "—" over work that had been
   * scored and stored.
   */
  it('shows the stored score when reopening a submitted session', async () => {
    vi.mocked(endpoints.fetchPlay).mockResolvedValue({
      ...session,
      status: 'SUBMITTED' as const,
      score: 33.33,
    });
    renderPlayer();

    expect(await screen.findByText('Session terminée !')).toBeInTheDocument();
    expect(screen.getByText('33.33%')).toBeInTheDocument();
    expect(screen.queryByText('—')).not.toBeInTheDocument();
  });

  it('shows a dash only when the score genuinely is unknown', async () => {
    // Defensive: a submitted session with no score at all (older row) should still
    // render rather than crash — but it must not invent a number.
    vi.mocked(endpoints.fetchPlay).mockResolvedValue({
      ...session,
      status: 'SUBMITTED' as const,
      score: null,
    });
    renderPlayer();

    expect(await screen.findByText('Session terminée !')).toBeInTheDocument();
    expect(screen.getByText('—')).toBeInTheDocument();
  });
});
