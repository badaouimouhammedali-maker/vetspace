import type { ReactNode } from 'react';
import { NavLink } from 'react-router-dom';
import { t, type TranslationKey } from '../../i18n/fr';
import { Logo } from '../Logo';

export interface NavItem {
  to: string;
  label: TranslationKey;
  icon: string;
  end?: boolean;
}

export interface NavSection {
  label: TranslationKey;
  items: NavItem[];
}

/**
 * One sidebar row.
 *
 * <p>The active state is a green left bar plus a tinted fill, not a solid green block:
 * a fully filled row competes with the primary button for "the thing to click", and at
 * a glance the sidebar stops reading as navigation.
 */
export function SidebarLink({
  item,
  onNavigate,
}: {
  item: NavItem;
  onNavigate?: (() => void) | undefined;
}) {
  return (
    <NavLink
      to={item.to}
      end={item.end ?? false}
      onClick={onNavigate}
      className={({ isActive }) =>
        `relative flex h-10 items-center gap-3 rounded-md px-3 text-body font-semibold transition-colors duration-150 ${
          isActive
            ? 'bg-brand-green/15 text-white before:absolute before:left-0 before:h-5 before:w-1 before:rounded-r-sm before:bg-brand-green'
            : 'text-gray-300 hover:bg-white/5 hover:text-white'
        }`
      }
    >
      {/* Fixed-width icon cell so labels line up whatever the glyph's width. */}
      <span aria-hidden className="w-5 shrink-0 text-center">
        {item.icon}
      </span>
      <span className="truncate">{t(item.label)}</span>
    </NavLink>
  );
}

/** Sidebar body: brand, grouped nav, and the user block pinned to the bottom. */
export function Sidebar({
  sections,
  homeTo,
  footer,
  onNavigate,
}: {
  sections: NavSection[];
  homeTo: string;
  footer?: ReactNode | undefined;
  onNavigate?: (() => void) | undefined;
}) {
  return (
    <div className="flex h-full flex-col">
      <NavLink to={homeTo} className="flex shrink-0 justify-center px-6 py-6" onClick={onNavigate}>
        <Logo variant="full" theme="dark" size="sm" />
      </NavLink>

      <nav className="sidebar-scroll flex-1 overflow-y-auto px-3 pb-4">
        {sections.map((section, index) => (
          <div
            key={section.label}
            className={index > 0 ? 'mt-6 border-t border-white/10 pt-4' : ''}
          >
            <p className="px-3 pb-2 text-caption font-bold uppercase tracking-wide text-white/40">
              {t(section.label)}
            </p>
            <div className="space-y-1">
              {section.items.map((item) => (
                <SidebarLink key={item.to} item={item} onNavigate={onNavigate} />
              ))}
            </div>
          </div>
        ))}
      </nav>

      {footer ? <div className="shrink-0 border-t border-white/10 p-3">{footer}</div> : null}
    </div>
  );
}

/** Avatar circle: photo when there is one, initials otherwise. */
export function Avatar({
  photoUrl,
  initials,
  size = 'md',
}: {
  photoUrl?: string | null | undefined;
  initials: string;
  size?: 'sm' | 'md';
}) {
  const dimensions = size === 'sm' ? 'h-8 w-8 text-caption' : 'h-9 w-9 text-body';
  return (
    <span
      className={`flex ${dimensions} shrink-0 items-center justify-center overflow-hidden rounded-full bg-brand-green font-bold text-white`}
    >
      {photoUrl ? <img src={photoUrl} alt="" className="h-full w-full object-cover" /> : initials}
    </span>
  );
}

/** Unread count bubble on the bell. Scales in so a new notification is noticed. */
export function NotificationBadge({ count }: { count: number }) {
  if (count <= 0) {
    return null;
  }
  return (
    <span className="absolute -right-0.5 -top-0.5 flex h-5 min-w-5 animate-badge-in items-center justify-center rounded-full bg-danger px-1 text-[10px] font-bold text-white">
      {count > 99 ? '99+' : count}
    </span>
  );
}

/** Page container: one max width and one padding rhythm for every screen. */
export function PageContainer({ children }: { children: ReactNode }) {
  return <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">{children}</main>;
}

/** Topbar shell: h-16 with a subtle bottom border. */
export function Topbar({ left, right }: { left?: ReactNode; right: ReactNode }) {
  return (
    <header className="sticky top-0 z-20 flex h-16 items-center justify-between border-b border-white/10 bg-brand-navy px-4 lg:px-6">
      <div className="flex items-center gap-2">{left}</div>
      <div className="flex items-center gap-2">{right}</div>
    </header>
  );
}

/** Icon-only control in the navy topbar — one hit area and hover for all of them. */
export function TopbarIconButton({
  onClick,
  label,
  children,
  className = '',
}: {
  onClick?: () => void;
  label: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      className={`flex h-9 w-9 items-center justify-center rounded-md text-lg text-white/80 transition-colors duration-150 hover:bg-white/10 hover:text-white ${className}`}
    >
      {children}
    </button>
  );
}

/** Shared dropdown surface for the avatar menus. */
export function DropdownPanel({ children }: { children: ReactNode }) {
  return (
    <div
      role="menu"
      className="absolute right-0 mt-2 w-56 animate-toast-in rounded-lg bg-surface p-1.5 shadow-pop ring-1 ring-black/5"
    >
      {children}
    </div>
  );
}

export function DropdownItem({
  onClick,
  to,
  danger = false,
  children,
}: {
  onClick?: () => void;
  to?: string;
  danger?: boolean;
  children: ReactNode;
}) {
  const classes = `block w-full rounded-md px-3 py-2 text-left text-body font-medium transition-colors duration-150 ${
    danger ? 'text-danger hover:bg-danger/10' : 'text-gray-600 hover:bg-gray-100 hover:text-brand-navy'
  }`;
  if (to) {
    return (
      <NavLink to={to} onClick={onClick} className={classes} role="menuitem">
        {children}
      </NavLink>
    );
  }
  return (
    <button type="button" onClick={onClick} className={classes} role="menuitem">
      {children}
    </button>
  );
}
