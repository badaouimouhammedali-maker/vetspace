import { useEffect, useRef, useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { t, type TranslationKey } from '../../i18n/fr';

const NAV_ITEMS: { to: string; label: TranslationKey; icon: string; end?: boolean }[] = [
  { to: '/admin', label: 'nav.admin.overview', icon: '📈', end: true },
  { to: '/admin/ecoles', label: 'nav.admin.schools', icon: '🏫' },
  { to: '/admin/questions', label: 'nav.admin.questions', icon: '❓' },
  { to: '/admin/sources', label: 'nav.admin.sourceExams', icon: '📄' },
  { to: '/admin/mindmaps', label: 'nav.admin.mindmaps', icon: '🧠' },
  { to: '/admin/packs', label: 'nav.admin.packs', icon: '🎟️' },
  { to: '/admin/abonnes', label: 'nav.admin.users', icon: '👥' },
  { to: '/admin/signalements', label: 'nav.admin.signals', icon: '🚩' },
  { to: '/admin/notifications', label: 'nav.admin.notifications', icon: '🔔' },
  { to: '/admin/support', label: 'nav.admin.support', icon: '💬' },
];

function SidebarContent({ onNavigate }: { onNavigate?: () => void }) {
  return (
    <div className="flex h-full flex-col">
      <NavLink to="/admin" className="flex flex-col items-center gap-1 px-6 py-6" onClick={onNavigate}>
        <img src="/brand/Logo white.svg" alt="VetSpace" className="h-10" />
        <span className="text-xs font-semibold uppercase tracking-wide text-white/50">
          {t('admin.console')}
        </span>
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

export function AdminLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

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
    <div className="min-h-screen bg-gray-50">
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 bg-brand-navy lg:block">
        <SidebarContent />
      </aside>

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
            <NavLink
              to="/app"
              className="rounded-lg px-3 py-1.5 text-sm font-semibold text-white/80 transition hover:text-white"
            >
              {t('admin.backToApp')}
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
