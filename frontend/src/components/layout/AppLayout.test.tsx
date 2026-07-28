import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useAuth } from '../../auth/AuthContext';
import type { Me, Role } from '../../lib/schemas';
import { AdminLayout } from './AdminLayout';
import { AppLayout } from './AppLayout';

vi.mock('../../auth/AuthContext', () => ({ useAuth: vi.fn() }));
vi.mock('../../lib/endpoints', () => ({
  fetchUnreadCount: vi.fn().mockResolvedValue({ count: 0 }),
  // Pulled in by the bell dropdown the layout now renders.
  fetchNotifications: vi.fn().mockResolvedValue([]),
  markAllNotificationsRead: vi.fn().mockResolvedValue(undefined),
  deleteNotification: vi.fn().mockResolvedValue(undefined),
}));

const mockUseAuth = useAuth as unknown as ReturnType<typeof vi.fn>;

function userWithRole(role: Role): Me {
  return {
    id: '11111111-1111-1111-1111-111111111111',
    email: 'x@example.com',
    username: 'x',
    lastName: 'Nom',
    firstName: 'Prénom',
    role,
    status: 'ACTIVE',
    schoolId: null,
    studyYear: 3,
    photoUrl: null,
    theme: { primary: null, secondary: null, tertiary: null },
    activeSubscriptions: [],
  };
}

function renderLayout(node: React.ReactNode, at = '/app') {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={[at]}>
        <Routes>
          <Route path="/app" element={node} />
          <Route path="/admin" element={node} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

/** The console link, wherever it is rendered. */
const adminLinks = () => screen.queryAllByRole('link', { name: /Administration/i });

beforeEach(() => {
  mockUseAuth.mockReset();
  mockUseAuth.mockReturnValue({ user: userWithRole('STUDENT'), logout: vi.fn() });
});

describe('AppLayout — admin console entry', () => {
  /**
   * The bug this covers: staff logged in and saw only the student app, because the
   * sidebar had no way into /admin. The console was reachable only by typing the URL.
   */
  it.each<Role>(['ADMIN', 'TEACHER'])('shows the link to a %s', (role) => {
    mockUseAuth.mockReturnValue({ user: userWithRole(role), logout: vi.fn() });
    renderLayout(<AppLayout />);

    const links = adminLinks();
    expect(links.length).toBeGreaterThan(0);
    expect(links[0]).toHaveAttribute('href', '/admin');
  });

  it('hides it from a STUDENT', () => {
    renderLayout(<AppLayout />);
    expect(adminLinks()).toHaveLength(0);
    // Nothing anywhere in the shell points at the console.
    expect(screen.queryByRole('link', { name: /admin/i })).not.toBeInTheDocument();
  });

  it('hides it while auth is still booting, rather than flashing it', () => {
    // user === undefined is the silent-refresh window. Rendering the link here and
    // removing it a moment later would be a visible flicker for every student.
    mockUseAuth.mockReturnValue({ user: undefined, logout: vi.fn() });
    renderLayout(<AppLayout />);
    expect(adminLinks()).toHaveLength(0);
  });

  it('also offers it in the avatar menu, where people look for "switch context"', async () => {
    const user = userEvent.setup();
    mockUseAuth.mockReturnValue({ user: userWithRole('ADMIN'), logout: vi.fn() });
    renderLayout(<AppLayout />);

    await user.click(screen.getByRole('button', { name: 'Menu du compte' }));
    const inMenu = screen.getAllByRole('menuitem', { name: /Administration/i });
    expect(inMenu[0]).toHaveAttribute('href', '/admin');
  });

  it('keeps the avatar menu free of it for a STUDENT', async () => {
    const user = userEvent.setup();
    renderLayout(<AppLayout />);

    await user.click(screen.getByRole('button', { name: 'Menu du compte' }));
    expect(screen.queryByRole('menuitem', { name: /Administration/i })).not.toBeInTheDocument();
  });
});

describe('AdminLayout — way back', () => {
  it('links back to the student app, so switching is not a URL edit', () => {
    mockUseAuth.mockReturnValue({ user: userWithRole('ADMIN'), logout: vi.fn() });
    renderLayout(<AdminLayout />, '/admin');

    const back = screen.getByRole('link', { name: /Retour à l'application/i });
    expect(back).toHaveAttribute('href', '/app');
  });
});
