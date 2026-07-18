import { useQuery } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { t, type TranslationKey } from '../../i18n/fr';
import { fetchUnreadCount } from '../../lib/endpoints';

const NAV_ITEMS: { to: string; label: TranslationKey; icon: string; end?: boolean }[] = [
  { to: '/app', label: 'nav.accueil', icon: '🏠', end: true },
  { to: '/app/statistiques', label: 'nav.statistiques', icon: '📊' },
  { to: '/app/mindmaps', label: 'nav.mindmaps', icon: '🧠' },
  { to: '/app/sessions/entrainement', label: 'nav.sessionsEntrainement', icon: '📝' },
  { to: '/app/sessions/examens', label: 'nav.sessionsExamens', icon: '🎓' },
  { to: '/app/labels', label: 'nav.labels', icon: '🏷️' },
  { to: '/app/notes', label: 'nav.notes', icon: '🗒️' },
  { to: '/app/signals', label: 'nav.signals', icon: '🚩' },
  { to: '/app/notifications', label: 'nav.notifications', icon: '🔔' },
  { to: '/app/abonnement', label: 'nav.abonnement', icon: '💳' },
  { to: '/app/support', label: 'nav.support', icon: '💬' },
  { to: '/app/profil', label: 'nav.profile', icon: '👤' },
];

function SidebarContent({ onNavigate }: { onNavigate?: () => void }) {
  return (
    <div className="flex h-full flex-col">
      <NavLink to="/app" className="flex justify-center px-6 py-6" onClick={onNavigate}>
        <img src="/brand/Logo white.svg" alt="VetSpace" className="h-12" />
      </NavLink>
      <nav className="flex-1 space-y-0.5 overflow-y-auto px-3 pb-6">
        {NAV_ITEMS.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end ?? false}
            onClick={onNavigate}
            className={({ isActive }) =>
              `flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-semibold transition ${
                isActive
                  ? 'bg-brand-green text-white'
                  : 'text-white/70 hover:bg-white/10 hover:text-white'
              }`
            }
          >
            <span aria-hidden>{item.icon}</span>
            {t(item.label)}
          </NavLink>
        ))}
      </nav>
    </div>
  );
}

export function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const [dark, setDark] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  const unread = useQuery({
    queryKey: ['unread-count'],
    queryFn: fetchUnreadCount,
    refetchInterval: 60_000,
  });

  useEffect(() => {
    document.documentElement.classList.toggle('dark', dark);
  }, [dark]);

  useEffect(() => {
    const close = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setMenuOpen(false);
      }
    };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, []);

  async function onLogout() {
    await logout();
    navigate('/login');
  }

  const initials = `${user?.firstName?.[0] ?? ''}${user?.lastName?.[0] ?? ''}`.toUpperCase();

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
      {/* Barre latérale fixe (desktop) */}
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 bg-brand-navy lg:block">
        <SidebarContent />
      </aside>

      {/* Tiroir mobile */}
      {drawerOpen ? (
        <div className="fixed inset-0 z-40 lg:hidden" onClick={() => setDrawerOpen(false)}>
          <div className="absolute inset-0 bg-brand-navy/50" />
          <aside
            className="absolute inset-y-0 left-0 w-64 bg-brand-navy shadow-xl"
            onClick={(e) => e.stopPropagation()}
          >
            <SidebarContent onNavigate={() => setDrawerOpen(false)} />
          </aside>
        </div>
      ) : null}

      <div className="lg:pl-64">
        {/* Barre supérieure */}
        <header className="sticky top-0 z-20 flex h-14 items-center justify-between bg-brand-navy px-4 lg:px-6">
          <button
            className="rounded-lg p-2 text-white lg:hidden"
            onClick={() => setDrawerOpen(true)}
            aria-label="Ouvrir le menu"
          >
            ☰
          </button>
          <div className="hidden lg:block" />
          <div className="flex items-center gap-3">
            <button
              onClick={() => setDark((d) => !d)}
              aria-label="Changer de thème"
              className="rounded-lg p-2 text-lg text-white/80 transition hover:text-white"
            >
              {dark ? '☀️' : '🌙'}
            </button>
            <NavLink
              to="/app/notifications"
              aria-label={t('nav.notifications')}
              className="relative rounded-lg p-2 text-lg text-white/80 transition hover:text-white"
            >
              🔔
              {(unread.data?.count ?? 0) > 0 ? (
                <span className="absolute -right-0.5 -top-0.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-red-500 px-1 text-[10px] font-bold text-white">
                  {unread.data?.count}
                </span>
              ) : null}
            </NavLink>
            <div className="relative" ref={menuRef}>
              <button
                onClick={() => setMenuOpen((o) => !o)}
                aria-label="Menu du compte"
                className="flex h-9 w-9 items-center justify-center overflow-hidden rounded-full bg-brand-green text-sm font-bold text-white"
              >
                {user?.photoUrl ? (
                  <img src={user.photoUrl} alt="" className="h-full w-full object-cover" />
                ) : (
                  initials
                )}
              </button>
              {menuOpen ? (
                <div className="absolute right-0 mt-2 w-48 rounded-xl bg-white p-2 shadow-lg ring-1 ring-gray-100">
                  <p className="px-3 py-2 text-sm font-bold text-brand-navy">
                    {user?.firstName} {user?.lastName}
                  </p>
                  <NavLink
                    to="/app/profil"
                    onClick={() => setMenuOpen(false)}
                    className="block rounded-lg px-3 py-2 text-sm font-medium text-brand-gray hover:bg-gray-50"
                  >
                    {t('nav.profile')}
                  </NavLink>
                  <button
                    onClick={onLogout}
                    className="block w-full rounded-lg px-3 py-2 text-left text-sm font-medium text-red-600 hover:bg-red-50"
                  >
                    {t('app.logout')}
                  </button>
                </div>
              ) : null}
            </div>
          </div>
        </header>

        <main className="mx-auto max-w-6xl px-4 py-6 lg:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

/** Page « à venir » pour les entrées de menu livrées aux prochaines étapes. */
export function ComingSoonPage({ titleKey }: { titleKey: TranslationKey }) {
  return (
    <div>
      <h1 className="text-2xl font-extrabold text-brand-navy">{t(titleKey)}</h1>
      <p className="mt-2 text-sm text-brand-gray">{t('nav.comingSoon')}</p>
    </div>
  );
}
