import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useAuth } from '../../auth/AuthContext';
import { ToastProvider } from '../../components/ToastProvider';
import * as api from '../../lib/api';
import { RegisterPage } from './RegisterPage';

vi.mock('../../auth/AuthContext', () => ({ useAuth: vi.fn() }));
vi.mock('../../lib/api', async (importOriginal) => ({
  ...(await importOriginal<typeof api>()),
  apiGet: vi.fn(),
}));

const navigate = vi.fn();
vi.mock('react-router-dom', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-router-dom')>()),
  useNavigate: () => navigate,
}));

const mockUseAuth = useAuth as unknown as ReturnType<typeof vi.fn>;
const register = vi.fn();

const CREATED = {
  id: '11111111-1111-1111-1111-111111111111',
  email: 'jane@vetspace.dz',
  username: 'jane',
  role: 'STUDENT',
  status: 'ACTIVE',
};

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <ToastProvider>
        <MemoryRouter>
          <RegisterPage />
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

/** Fills every required field and submits. */
async function submitValidForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('Nom'), 'Doe');
  await user.type(screen.getByLabelText('Prénom'), 'Jane');
  await user.type(screen.getByLabelText("Nom d'utilisateur"), 'jane');
  await user.type(screen.getByLabelText('Adresse e-mail'), 'jane@vetspace.dz');
  await user.selectOptions(screen.getByLabelText('École'), 'sch-1');
  await user.selectOptions(screen.getByLabelText("Année d'étude"), '3');
  await user.type(screen.getByLabelText('Mot de passe'), 'correct-horse-battery');
  await user.type(screen.getByLabelText('Confirmer le mot de passe'), 'correct-horse-battery');
  await user.click(screen.getByRole('button', { name: "S'inscrire" }));
}

describe('RegisterPage — verification email outcome', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseAuth.mockReturnValue({ register });
    vi.mocked(api.apiGet).mockResolvedValue([{ id: 'sch-1', name: 'ENSV Alger', slug: 'ensv' }]);
  });

  it('sends the student to the login page when the email went out', async () => {
    register.mockResolvedValue({ ...CREATED, verificationEmailSent: true });
    const user = userEvent.setup();
    renderPage();
    await screen.findByRole('option', { name: 'ENSV Alger' });

    await submitValidForm(user);

    await waitFor(() => expect(navigate).toHaveBeenCalledWith('/login'));
    expect(
      screen.queryByText(/l'e-mail de vérification n'a pas pu être envoyé/),
    ).not.toBeInTheDocument();
  });

  it('says the email failed and routes to the resend screen, prefilled', async () => {
    // The account exists — only the send failed. The student must not be told to
    // wait for a message that will never arrive, nor to register again.
    register.mockResolvedValue({ ...CREATED, verificationEmailSent: false });
    const user = userEvent.setup();
    renderPage();
    await screen.findByRole('option', { name: 'ENSV Alger' });

    await submitValidForm(user);

    expect(
      await screen.findByText(
        "Compte créé — l'e-mail de vérification n'a pas pu être envoyé, utilisez « renvoyer ».",
      ),
    ).toBeInTheDocument();
    // /verify-email is where the "renvoyer" form lives; the address is carried over
    // so the student does not have to retype it.
    expect(navigate).toHaveBeenCalledWith('/verify-email?email=jane%40vetspace.dz');
    expect(navigate).not.toHaveBeenCalledWith('/login');
  });
});
