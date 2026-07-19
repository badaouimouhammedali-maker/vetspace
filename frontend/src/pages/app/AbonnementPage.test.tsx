import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ToastProvider';
import { AbonnementPage } from './AbonnementPage';
import { useAuth } from '../../auth/AuthContext';
import * as endpoints from '../../lib/endpoints';

vi.mock('../../auth/AuthContext', () => ({ useAuth: vi.fn() }));
vi.mock('../../lib/endpoints');

const mockUseAuth = useAuth as unknown as ReturnType<typeof vi.fn>;

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <ToastProvider>
        <AbonnementPage />
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe('AbonnementPage — redeem form', () => {
  beforeEach(() => {
    mockUseAuth.mockReturnValue({
      user: { schoolId: 'sch-1', studyYear: 3, activeSubscriptions: [] },
      refreshUser: vi.fn().mockResolvedValue(undefined),
    });
    vi.mocked(endpoints.fetchPacks).mockResolvedValue([]);
  });

  it('renders the idle form with an input and submit button', () => {
    renderPage();
    expect(screen.getByRole('textbox', { name: 'Activer un code' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'VALIDER' })).toBeEnabled();
    expect(screen.queryByText(/Abonnement activé/)).not.toBeInTheDocument();
  });

  it('shows a success message after a valid code is redeemed', async () => {
    vi.mocked(endpoints.redeemCode).mockResolvedValue({
      subscriptionId: 'sub-1',
      packName: 'Pack 3e année',
      startsAt: '2026-01-01T00:00:00Z',
      endsAt: '2027-07-15T00:00:00Z',
    });
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByRole('textbox', { name: 'Activer un code' }), 'ABCD-EFGH');
    await user.click(screen.getByRole('button', { name: 'VALIDER' }));

    // Shown both as the inline banner and as a toast.
    const messages = await screen.findAllByText('Abonnement activé : Pack 3e année — merci !');
    expect(messages.length).toBeGreaterThanOrEqual(1);
    expect(endpoints.redeemCode).toHaveBeenCalledWith('ABCD-EFGH');
  });

  it('shows an error message when the code is rejected', async () => {
    vi.mocked(endpoints.redeemCode).mockRejectedValue(new Error('bad'));
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByRole('textbox', { name: 'Activer un code' }), 'WRONG');
    await user.click(screen.getByRole('button', { name: 'VALIDER' }));

    expect(await screen.findByText('Code invalide.')).toBeInTheDocument();
  });

  it('disables the submit button while the redemption is in flight', async () => {
    let resolve!: (v: {
      subscriptionId: string;
      packName: string;
      startsAt: string;
      endsAt: string;
    }) => void;
    vi.mocked(endpoints.redeemCode).mockReturnValue(new Promise((r) => (resolve = r)));
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByRole('textbox', { name: 'Activer un code' }), 'PENDING');
    await user.click(screen.getByRole('button', { name: 'VALIDER' }));

    await waitFor(() => expect(screen.getByRole('button', { name: 'VALIDER' })).toBeDisabled());

    resolve({ subscriptionId: 's', packName: 'P', startsAt: 'a', endsAt: 'b' });
    await waitFor(() => expect(screen.getByRole('button', { name: 'VALIDER' })).toBeEnabled());
  });
});
