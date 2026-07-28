import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as endpoints from '../../lib/endpoints';
import { NotificationsDropdown } from './NotificationsDropdown';

vi.mock('../../lib/endpoints');

const NOTIFICATIONS = [
  {
    id: '11111111-1111-1111-1111-111111111111',
    kind: 'UPDATE' as const,
    title: 'Nouveau module',
    body: 'Le module Chirurgie est disponible.',
    createdAt: new Date().toISOString(),
    read: false,
  },
  {
    id: '22222222-2222-2222-2222-222222222222',
    kind: 'INFO' as const,
    title: 'Maintenance',
    body: 'Interruption prévue dimanche.',
    createdAt: new Date().toISOString(),
    read: true,
  },
];

function renderBell(unreadCount = 1) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>
        <NotificationsDropdown unreadCount={unreadCount} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('NotificationsDropdown', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(endpoints.fetchNotifications).mockResolvedValue(NOTIFICATIONS);
    vi.mocked(endpoints.markAllNotificationsRead).mockResolvedValue({} as never);
  });

  it('shows nothing until the bell is clicked, then the scrollable panel in place', async () => {
    const user = userEvent.setup();
    renderBell();

    // Closed: no panel, no fetch — the badge endpoint alone drives the count.
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(endpoints.fetchNotifications).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: 'Notification' }));

    // Open: the list renders inside the dropdown — no navigation involved.
    expect(await screen.findByText('Nouveau module')).toBeInTheDocument();
    expect(screen.getByText('Maintenance')).toBeInTheDocument();
    expect(screen.getByRole('dialog', { name: 'Notifications' })).toBeInTheDocument();
    // The full history stays one click away.
    expect(screen.getByRole('link', { name: "Voir tout l'historique" })).toHaveAttribute(
      'href',
      '/app/notifications',
    );
  });

  it('marks everything read once opened', async () => {
    const user = userEvent.setup();
    renderBell();

    await user.click(screen.getByRole('button', { name: 'Notification' }));
    await screen.findByText('Nouveau module');

    await waitFor(() => expect(endpoints.markAllNotificationsRead).toHaveBeenCalledTimes(1));
  });

  it('does not mark anything when every notification is already read', async () => {
    vi.mocked(endpoints.fetchNotifications).mockResolvedValue(
      NOTIFICATIONS.map((n) => ({ ...n, read: true })),
    );
    const user = userEvent.setup();
    renderBell(0);

    await user.click(screen.getByRole('button', { name: 'Notification' }));
    await screen.findByText('Nouveau module');

    expect(endpoints.markAllNotificationsRead).not.toHaveBeenCalled();
  });

  it('deletes a notification from the panel', async () => {
    vi.mocked(endpoints.deleteNotification).mockResolvedValue({} as never);
    const user = userEvent.setup();
    renderBell();

    await user.click(screen.getByRole('button', { name: 'Notification' }));
    await screen.findByText('Nouveau module');
    const [firstDelete] = screen.getAllByRole('button', { name: 'Supprimer' });
    expect(firstDelete).toBeDefined();
    await user.click(firstDelete as HTMLElement);

    // TanStack Query appends a context argument after the variables — only the
    // first argument is ours.
    expect(vi.mocked(endpoints.deleteNotification).mock.calls[0]?.[0]).toBe(
      '11111111-1111-1111-1111-111111111111',
    );
  });

  it('shows the empty state inside the panel', async () => {
    vi.mocked(endpoints.fetchNotifications).mockResolvedValue([]);
    const user = userEvent.setup();
    renderBell(0);

    await user.click(screen.getByRole('button', { name: 'Notification' }));

    expect(await screen.findByText('Aucune notification.')).toBeInTheDocument();
  });
});
