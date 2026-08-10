import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import {
  Avatar,
  DropdownItem,
  DropdownPanel,
  NotificationBadge,
  PageContainer,
  Sidebar,
  SidebarLink,
  Topbar,
  TopbarIconButton,
  type NavSection,
} from './Shell';
import { tokenFor } from '../../test/tokens';

const wrap = (ui: React.ReactNode, route = '/app') =>
  render(<MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>);

const SECTIONS: NavSection[] = [
  { label: 'nav.sectionMain', items: [{ to: '/app', label: 'nav.accueil', icon: '🏠', end: true }] },
  { label: 'nav.sectionStudy', items: [{ to: '/app/mindmaps', label: 'nav.mindmaps', icon: '🧠' }] },
];

describe('SidebarLink', () => {
  it('marks the active route with the green bar, not a solid fill', () => {
    wrap(<SidebarLink item={{ to: '/app', label: 'nav.accueil', icon: '🏠', end: true }} />, '/app');
    const link = screen.getByRole('link');
    // A filled row would compete with the primary button for "the thing to click", so
    // the fill is the accent at 18% and the indicator carries the "you are here" green.
    const indicator = link.className.split(/\s+/).filter((c) => c.startsWith('before:'));
    expect(tokenFor(link, 'bg')).toEqual({ token: '--accent', alpha: 18 });
    expect(tokenFor(indicator.join(' '), 'bg')).toEqual({ token: '--success', alpha: null });
  });

  it('leaves an inactive route unhighlighted', () => {
    wrap(
      <SidebarLink item={{ to: '/app/mindmaps', label: 'nav.mindmaps', icon: '🧠' }} />,
      '/app',
    );
    // Au repos, aucun fond : seulement une teinte au survol. On filtre donc les
    // variantes, sinon `hover:bg-on-navy/6` ferait passer la ligne pour remplie.
    const resting = screen
      .getByRole('link')
      .className.split(/\s+/)
      .filter((c) => !c.includes(':'));
    expect(tokenFor(resting.join(' '), 'bg')).toBeNull();
  });

  it('calls onNavigate — the mobile drawer relies on this to close itself', async () => {
    const user = userEvent.setup();
    const onNavigate = vi.fn();
    wrap(
      <SidebarLink item={{ to: '/app', label: 'nav.accueil', icon: '🏠' }} onNavigate={onNavigate} />,
    );
    await user.click(screen.getByRole('link'));
    expect(onNavigate).toHaveBeenCalledOnce();
  });

  it('hides the icon glyph from assistive tech', () => {
    const { container } = wrap(
      <SidebarLink item={{ to: '/app', label: 'nav.accueil', icon: '🏠' }} />,
    );
    expect(container.querySelector('[aria-hidden]')).toHaveTextContent('🏠');
  });
});

describe('Sidebar', () => {
  it('renders every section and item', () => {
    wrap(<Sidebar sections={SECTIONS} homeTo="/app" />);
    expect(screen.getAllByRole('link')).toHaveLength(3); // brand + 2 nav items
  });

  it('renders the footer slot when given one', () => {
    wrap(<Sidebar sections={SECTIONS} homeTo="/app" footer={<p>user block</p>} />);
    expect(screen.getByText('user block')).toBeInTheDocument();
  });

  it('omits the footer container entirely when there is none', () => {
    wrap(<Sidebar sections={SECTIONS} homeTo="/app" />);
    expect(screen.queryByText('user block')).not.toBeInTheDocument();
  });
});

describe('Avatar', () => {
  it('falls back to initials when there is no photo', () => {
    render(<Avatar initials="RD" />);
    expect(screen.getByText('RD')).toBeInTheDocument();
  });

  it('renders the photo with an empty alt — the name is already beside it', () => {
    const { container } = render(<Avatar photoUrl="https://x/y.png" initials="RD" />);
    const img = container.querySelector('img');
    expect(img).toHaveAttribute('src', 'https://x/y.png');
    expect(img).toHaveAttribute('alt', '');
  });

  it('treats a null photoUrl as no photo', () => {
    const { container } = render(<Avatar photoUrl={null} initials="RD" />);
    expect(container.querySelector('img')).toBeNull();
  });
});

describe('NotificationBadge', () => {
  it('renders nothing at zero, rather than an empty bubble', () => {
    const { container } = render(<NotificationBadge count={0} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('renders nothing for a negative count', () => {
    const { container } = render(<NotificationBadge count={-1} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('shows the count', () => {
    render(<NotificationBadge count={7} />);
    expect(screen.getByText('7')).toBeInTheDocument();
  });

  it('caps at 99+ so the bubble cannot stretch the topbar', () => {
    render(<NotificationBadge count={1200} />);
    expect(screen.getByText('99+')).toBeInTheDocument();
  });
});

describe('Topbar and controls', () => {
  it('renders left and right slots', () => {
    render(<Topbar left={<span>L</span>} right={<span>R</span>} />);
    expect(screen.getByText('L')).toBeInTheDocument();
    expect(screen.getByText('R')).toBeInTheDocument();
  });

  it('TopbarIconButton is labelled and clickable', async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(
      <TopbarIconButton label="Notifications" onClick={onClick}>
        🔔
      </TopbarIconButton>,
    );
    await user.click(screen.getByRole('button', { name: 'Notifications' }));
    expect(onClick).toHaveBeenCalledOnce();
  });
});

describe('Dropdown', () => {
  it('panel exposes the menu role', () => {
    render(<DropdownPanel>x</DropdownPanel>);
    expect(screen.getByRole('menu')).toBeInTheDocument();
  });

  it('renders an item as a link when given a target', () => {
    wrap(<DropdownItem to="/app/profil">Profil</DropdownItem>);
    expect(screen.getByRole('menuitem')).toHaveAttribute('href', '/app/profil');
  });

  it('renders an item as a button when there is no target', async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(<DropdownItem onClick={onClick}>Déconnexion</DropdownItem>);
    const item = screen.getByRole('menuitem');
    expect(item.tagName).toBe('BUTTON');
    await user.click(item);
    expect(onClick).toHaveBeenCalledOnce();
  });

  it('tints a destructive item', () => {
    render(<DropdownItem danger>Supprimer</DropdownItem>);
    expect(tokenFor(screen.getByRole('menuitem'), 'text')).toEqual({ token: '--danger', alpha: null });
  });
});

describe('PageContainer', () => {
  it('is the one place the page max width and padding are decided', () => {
    const { container } = render(<PageContainer>content</PageContainer>);
    const main = container.querySelector('main')!;
    expect(main.className).toContain('max-w-7xl');
    expect(main).toHaveTextContent('content');
  });
});
