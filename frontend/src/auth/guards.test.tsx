import { render, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AdminRoute, ProtectedRoute } from './guards';
import { useAuth } from './AuthContext';

vi.mock('./AuthContext', () => ({ useAuth: vi.fn() }));
const mockUseAuth = useAuth as unknown as ReturnType<typeof vi.fn>;

function renderAt(node: ReactNode) {
  return render(
    <MemoryRouter initialEntries={['/secret']}>
      <Routes>
        <Route path="/secret" element={node} />
        <Route path="/login" element={<div>login page</div>} />
        <Route path="/app" element={<div>app home</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => mockUseAuth.mockReset());

describe('ProtectedRoute', () => {
  it('shows a loading spinner while auth is booting (user === undefined)', () => {
    mockUseAuth.mockReturnValue({ user: undefined });
    renderAt(
      <ProtectedRoute>
        <div>secret content</div>
      </ProtectedRoute>,
    );
    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(screen.queryByText('secret content')).not.toBeInTheDocument();
  });

  it('redirects to /login when logged out (user === null)', () => {
    mockUseAuth.mockReturnValue({ user: null });
    renderAt(
      <ProtectedRoute>
        <div>secret content</div>
      </ProtectedRoute>,
    );
    expect(screen.getByText('login page')).toBeInTheDocument();
  });

  it('renders children when authenticated', () => {
    mockUseAuth.mockReturnValue({ user: { id: '1', role: 'STUDENT' } });
    renderAt(
      <ProtectedRoute>
        <div>secret content</div>
      </ProtectedRoute>,
    );
    expect(screen.getByText('secret content')).toBeInTheDocument();
  });
});

describe('AdminRoute', () => {
  it('redirects a STUDENT to /app', () => {
    mockUseAuth.mockReturnValue({ user: { id: '1', role: 'STUDENT' } });
    renderAt(
      <AdminRoute>
        <div>admin console</div>
      </AdminRoute>,
    );
    expect(screen.getByText('app home')).toBeInTheDocument();
    expect(screen.queryByText('admin console')).not.toBeInTheDocument();
  });

  it('renders children for an ADMIN', () => {
    mockUseAuth.mockReturnValue({ user: { id: '1', role: 'ADMIN' } });
    renderAt(
      <AdminRoute>
        <div>admin console</div>
      </AdminRoute>,
    );
    expect(screen.getByText('admin console')).toBeInTheDocument();
  });

  it('redirects to /login when logged out', () => {
    mockUseAuth.mockReturnValue({ user: null });
    renderAt(
      <AdminRoute>
        <div>admin console</div>
      </AdminRoute>,
    );
    expect(screen.getByText('login page')).toBeInTheDocument();
  });
});
