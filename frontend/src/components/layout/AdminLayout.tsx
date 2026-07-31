import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { t } from '../../i18n/fr';
import {
  Avatar,
  DropdownItem,
  DropdownPanel,
  PageContainer,
  Sidebar,
  Topbar,
  TopbarIconButton,
  type NavSection,
} from './Shell';
import { useDropdown } from './useDropdown';

const NAV_SECTIONS: NavSection[] = [
  {
    label: 'nav.admin.sectionOverview',
    items: [{ to: '/admin', label: 'nav.admin.overview', icon: '📈', end: true }],
  },
  {
    label: 'nav.admin.sectionContent',
    items: [
      { to: '/admin/ecoles', label: 'nav.admin.schools', icon: '🏫' },
      { to: '/admin/questions', label: 'nav.admin.questions', icon: '❓' },
      { to: '/admin/sources', label: 'nav.admin.sourceExams', icon: '📄' },
      { to: '/admin/mindmaps', label: 'nav.admin.mindmaps', icon: '🧠' },
      { to: '/admin/ressources', label: 'nav.admin.resources', icon: '📚' },
    ],
  },
  {
    label: 'nav.admin.sectionCommerce',
    items: [
      { to: '/admin/packs', label: 'nav.admin.packs', icon: '🎟️' },
      { to: '/admin/abonnes', label: 'nav.admin.users', icon: '👥' },
    ],
  },
  {
    label: 'nav.admin.sectionModeration',
    items: [
      { to: '/admin/signalements', label: 'nav.admin.signals', icon: '🚩' },
      { to: '/admin/notifications', label: 'nav.admin.notifications', icon: '🔔' },
      { to: '/admin/support', label: 'nav.admin.support', icon: '💬' },
    ],
  },
];

export function AdminLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const menu = useDropdown();
  const drawer = useDropdown();

  async function onLogout() {
    await logout();
    navigate('/login');
  }

  const initials = `${user?.firstName?.[0] ?? ''}${user?.lastName?.[0] ?? ''}`.toUpperCase();

  const userBlock = (
    <div className="flex items-center gap-3 rounded-md px-2 py-2">
      <Avatar photoUrl={user?.photoUrl} initials={initials} size="sm" />
      <span className="min-w-0">
        <span className="block truncate text-caption font-semibold text-white">
          {user?.firstName} {user?.lastName}
        </span>
        <span className="block truncate text-caption text-white/50">{t('admin.console')}</span>
      </span>
    </div>
  );

  return (
    <div className="min-h-screen bg-canvas">
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 bg-brand-navy lg:block">
        <Sidebar sections={NAV_SECTIONS} homeTo="/admin" footer={userBlock} />
      </aside>

      {drawer.open ? (
        <div className="fixed inset-0 z-40 lg:hidden" onClick={() => drawer.setOpen(false)}>
          <div className="absolute inset-0 bg-brand-navy/50 backdrop-blur-sm" />
          <aside
            className="absolute inset-y-0 left-0 w-64 bg-brand-navy shadow-pop"
            onClick={(e) => e.stopPropagation()}
          >
            <Sidebar
              sections={NAV_SECTIONS}
              homeTo="/admin"
              footer={userBlock}
              onNavigate={() => drawer.setOpen(false)}
            />
          </aside>
        </div>
      ) : null}

      <div className="lg:pl-64">
        <Topbar
          left={
            <TopbarIconButton
              onClick={() => drawer.setOpen(true)}
              label="Ouvrir le menu"
              className="lg:hidden"
            >
              ☰
            </TopbarIconButton>
          }
          right={
            <>
              <NavLink
                to="/app"
                className="rounded-md px-3 py-1.5 text-body font-semibold text-white/80 transition-colors duration-150 hover:bg-white/10 hover:text-white"
              >
                {t('admin.backToApp')}
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
