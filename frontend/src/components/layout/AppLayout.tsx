import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { t, type TranslationKey } from '../../i18n/fr';
import { fetchUnreadCount } from '../../lib/endpoints';
import {
  Avatar,
  DropdownItem,
  DropdownPanel,
  NotificationBadge,
  PageContainer,
  Sidebar,
  Topbar,
  TopbarIconButton,
  type NavSection,
} from './Shell';
import { useDropdown } from './useDropdown';

/**
 * Grouped rather than one flat list of twelve. A student looking for "mes notes" scans
 * a three-item group instead of the whole menu.
 */
const NAV_SECTIONS: NavSection[] = [
  {
    label: 'nav.sectionMain',
    items: [
      { to: '/app', label: 'nav.accueil', icon: '🏠', end: true },
      { to: '/app/statistiques', label: 'nav.statistiques', icon: '📊' },
    ],
  },
  {
    label: 'nav.sectionStudy',
    items: [
      { to: '/app/sessions/entrainement', label: 'nav.sessionsEntrainement', icon: '📝' },
      { to: '/app/sessions/examens', label: 'nav.sessionsExamens', icon: '🎓' },
      { to: '/app/mindmaps', label: 'nav.mindmaps', icon: '🧠' },
    ],
  },
  {
    label: 'nav.sectionOrganise',
    items: [
      { to: '/app/labels', label: 'nav.labels', icon: '🏷️' },
      { to: '/app/notes', label: 'nav.notes', icon: '🗒️' },
      { to: '/app/signals', label: 'nav.signals', icon: '🚩' },
    ],
  },
  {
    label: 'nav.sectionAccount',
    items: [
      { to: '/app/notifications', label: 'nav.notifications', icon: '🔔' },
      { to: '/app/abonnement', label: 'nav.abonnement', icon: '💳' },
      { to: '/app/support', label: 'nav.support', icon: '💬' },
      { to: '/app/profil', label: 'nav.profile', icon: '👤' },
    ],
  },
];

export function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [dark, setDark] = useState(false);
  const menu = useDropdown();

  const unread = useQuery({
    queryKey: ['unread-count'],
    queryFn: fetchUnreadCount,
    refetchInterval: 60_000,
  });

  useEffect(() => {
    document.documentElement.classList.toggle('dark', dark);
  }, [dark]);

  async function onLogout() {
    await logout();
    navigate('/login');
  }

  const initials = `${user?.firstName?.[0] ?? ''}${user?.lastName?.[0] ?? ''}`.toUpperCase();

  /** Pinned to the bottom of the sidebar: who is signed in, always in view. */
  const userBlock = (
    <NavLink
      to="/app/profil"
      onClick={() => setDrawerOpen(false)}
      className="flex items-center gap-3 rounded-md px-2 py-2 transition-colors duration-150 hover:bg-white/5"
    >
      <Avatar photoUrl={user?.photoUrl} initials={initials} size="sm" />
      <span className="min-w-0">
        <span className="block truncate text-caption font-semibold text-white">
          {user?.firstName} {user?.lastName}
        </span>
        <span className="block truncate text-caption text-white/50">{user?.email}</span>
      </span>
    </NavLink>
  );

  return (
    <div className="min-h-screen bg-canvas dark:bg-gray-900">
      {/* Barre latérale fixe (desktop) */}
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 bg-brand-navy lg:block">
        <Sidebar sections={NAV_SECTIONS} homeTo="/app" footer={userBlock} />
      </aside>

      {/* Tiroir mobile */}
      {drawerOpen ? (
        <div className="fixed inset-0 z-40 lg:hidden" onClick={() => setDrawerOpen(false)}>
          <div className="absolute inset-0 bg-brand-navy/50 backdrop-blur-sm" />
          <aside
            className="absolute inset-y-0 left-0 w-64 bg-brand-navy shadow-pop"
            onClick={(e) => e.stopPropagation()}
          >
            <Sidebar
              sections={NAV_SECTIONS}
              homeTo="/app"
              footer={userBlock}
              onNavigate={() => setDrawerOpen(false)}
            />
          </aside>
        </div>
      ) : null}

      <div className="lg:pl-64">
        <Topbar
          left={
            <TopbarIconButton
              onClick={() => setDrawerOpen(true)}
              label="Ouvrir le menu"
              className="lg:hidden"
            >
              ☰
            </TopbarIconButton>
          }
          right={
            <>
              <TopbarIconButton onClick={() => setDark((d) => !d)} label="Changer de thème">
                {dark ? '☀️' : '🌙'}
              </TopbarIconButton>

              <NavLink
                to="/app/notifications"
                aria-label={t('nav.notifications')}
                className="relative flex h-9 w-9 items-center justify-center rounded-md text-lg text-white/80 transition-colors duration-150 hover:bg-white/10 hover:text-white"
              >
                🔔
                <NotificationBadge count={unread.data?.count ?? 0} />
              </NavLink>

              <div className="relative" ref={menu.ref}>
                <button
                  type="button"
                  onClick={() => menu.setOpen((o) => !o)}
                  aria-label="Menu du compte"
                  aria-expanded={menu.open}
                  aria-haspopup="menu"
                >
                  <Avatar photoUrl={user?.photoUrl} initials={initials} />
                </button>
                {menu.open ? (
                  <DropdownPanel>
                    <p className="truncate px-3 py-2 text-caption font-bold text-brand-navy">
                      {user?.firstName} {user?.lastName}
                    </p>
                    <DropdownItem to="/app/profil" onClick={() => menu.setOpen(false)}>
                      {t('nav.profile')}
                    </DropdownItem>
                    <DropdownItem onClick={onLogout} danger>
                      {t('app.logout')}
                    </DropdownItem>
                  </DropdownPanel>
                ) : null}
              </div>
            </>
          }
        />

        <PageContainer>
          <Outlet />
        </PageContainer>
      </div>
    </div>
  );
}

/** Page « à venir » pour les entrées de menu livrées aux prochaines étapes. */
export function ComingSoonPage({ titleKey }: { titleKey: TranslationKey }) {
  return (
    <div>
      <h1 className="text-display text-brand-navy">{t(titleKey)}</h1>
      <p className="mt-2 text-body text-gray-500">{t('nav.comingSoon')}</p>
    </div>
  );
}
