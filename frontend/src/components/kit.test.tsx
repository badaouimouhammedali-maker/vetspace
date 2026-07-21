import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { Badge, PrecisionChip } from './Badge';
import { Card, PageHeader, SectionHeader } from './Card';
import { EmptyState } from './EmptyState';
import {
  CardGridSkeleton,
  DashboardSkeleton,
  ListSkeleton,
  Skeleton,
  TableSkeleton,
} from './Skeletons';
import { Donut, EmojiRating, formatSeconds } from './ui';

describe('Card', () => {
  it('carries min-w-0 so a grid track cannot be blown out by its content', () => {
    // Regression: without this the dashboard scrolled horizontally at 375px, because a
    // grid item defaults to min-width:auto and refuses to shrink below min-content.
    const { container } = render(<Card>x</Card>);
    expect((container.firstChild as HTMLElement).className).toContain('min-w-0');
  });

  it.each([
    ['md', 'p-6'],
    ['sm', 'p-5'],
  ])('applies %s padding', (padding, expected) => {
    const { container } = render(<Card padding={padding as 'sm' | 'md'}>x</Card>);
    expect((container.firstChild as HTMLElement).className).toContain(expected);
  });

  it('applies no padding class when padding="none"', () => {
    const { container } = render(<Card padding="none">x</Card>);
    expect((container.firstChild as HTMLElement).className).not.toMatch(/\bp-[56]\b/);
  });

  it('only reacts to the cursor when it is actually interactive', () => {
    const { container: plain } = render(<Card>x</Card>);
    expect((plain.firstChild as HTMLElement).className).not.toContain('cursor-pointer');

    const { container: hoverable } = render(<Card hoverable>x</Card>);
    expect((hoverable.firstChild as HTMLElement).className).toContain('cursor-pointer');
  });
});

describe('headers', () => {
  it('SectionHeader renders an h2, with optional caption and action', () => {
    render(<SectionHeader title="Sessions" caption="Vos révisions" action={<button>+</button>} />);
    expect(screen.getByRole('heading', { level: 2, name: 'Sessions' })).toBeInTheDocument();
    expect(screen.getByText('Vos révisions')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '+' })).toBeInTheDocument();
  });

  it('PageHeader renders an h1 — the scale is what separates it from SectionHeader', () => {
    render(<PageHeader title="Statistiques" />);
    const heading = screen.getByRole('heading', { level: 1, name: 'Statistiques' });
    expect(heading.className).toContain('text-display');
  });

  it('omits caption and action nodes when not supplied', () => {
    const { container } = render(<SectionHeader title="Sessions" />);
    expect(container.querySelectorAll('p')).toHaveLength(0);
  });
});

describe('Badge', () => {
  it.each([
    ['success', 'bg-success/10'],
    ['warning', 'bg-warning/10'],
    ['danger', 'bg-danger/10'],
    ['brand', 'bg-brand-green/10'],
    ['neutral', 'bg-gray-100'],
  ])('tone %s is a soft fill, so badges never compete with buttons', (tone, expected) => {
    render(<Badge tone={tone as 'success'}>x</Badge>);
    expect(screen.getByText('x').className).toContain(expected);
  });
});

describe('PrecisionChip', () => {
  // The thresholds live in one place so "what counts as good" is answered once.
  it.each([
    [100, 'text-success'],
    [70, 'text-success'],
    [69, 'text-warning'],
    [40, 'text-warning'],
    [39, 'text-danger'],
    [0, 'text-danger'],
  ])('%i%% uses %s', (percent, expected) => {
    render(<PrecisionChip percent={percent} />);
    expect(screen.getByText(`${percent}%`).className).toContain(expected);
  });

  it('rounds rather than showing a long decimal', () => {
    render(<PrecisionChip percent={66.666} />);
    expect(screen.getByText('67%')).toBeInTheDocument();
  });
});

describe('EmptyState', () => {
  it('renders title, caption and the action that fills it', () => {
    render(
      <EmptyState title="Aucune session" caption="Créez-en une" action={<button>Créer</button>} />,
    );
    expect(screen.getByText('Aucune session')).toBeInTheDocument();
    expect(screen.getByText('Créez-en une')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Créer' })).toBeInTheDocument();
  });

  it('hides its icon from assistive tech', () => {
    const { container } = render(<EmptyState title="x" />);
    expect(container.querySelector('[aria-hidden="true"]')).toBeInTheDocument();
  });

  it('compact drops the full-height padding for a region nested inside a card', () => {
    const { container: full } = render(<EmptyState title="x" />);
    const { container: compact } = render(<EmptyState title="x" compact />);
    expect((full.firstChild as HTMLElement).className).toContain('py-14');
    expect((compact.firstChild as HTMLElement).className).not.toContain('py-14');
  });
});

describe('Skeletons', () => {
  it('Skeleton pulses', () => {
    const { container } = render(<Skeleton className="h-4" />);
    expect((container.firstChild as HTMLElement).className).toContain('animate-pulse');
  });

  it.each([
    ['dashboard', <DashboardSkeleton key="d" />],
    ['table', <TableSkeleton key="t" />],
    ['card grid', <CardGridSkeleton key="c" />],
    ['list', <ListSkeleton key="l" />],
  ])('%s skeleton announces itself as busy rather than as empty content', (_n, element) => {
    render(element);
    expect(screen.getAllByLabelText('Chargement').length).toBeGreaterThan(0);
    expect(screen.getAllByLabelText('Chargement')[0]).toHaveAttribute('aria-busy', 'true');
  });

  it('TableSkeleton honours its row and column counts', () => {
    const { container } = render(<TableSkeleton rows={3} columns={2} />);
    // header row + 3 body rows, 2 cells each = 8 placeholder blocks
    expect(container.querySelectorAll('.animate-pulse')).toHaveLength(8);
  });

  it('CardGridSkeleton honours its count', () => {
    const { container } = render(<CardGridSkeleton count={2} />);
    expect(container.querySelectorAll('.shadow-card')).toHaveLength(2);
  });
});

describe('Donut', () => {
  it('labels itself with the rounded percentage for screen readers', () => {
    render(<Donut percent={66.6} />);
    expect(screen.getByRole('img', { name: '67%' })).toBeInTheDocument();
  });

  it.each([
    [-20, '0%'],
    [140, '100%'],
  ])('clamps %i to %s rather than drawing an impossible arc', (percent, label) => {
    render(<Donut percent={percent} />);
    expect(screen.getByRole('img', { name: label })).toBeInTheDocument();
  });

  it('accepts an override label', () => {
    render(<Donut percent={50} label="3/6" />);
    expect(screen.getByText('3/6')).toBeInTheDocument();
  });
});

describe('EmojiRating', () => {
  it('exposes each rating as a labelled control', () => {
    render(<EmojiRating value={null} onChange={() => {}} />);
    expect(screen.getAllByRole('button')).toHaveLength(5);
    expect(screen.getByRole('button', { name: 'Note 4/5' })).toBeInTheDocument();
  });

  it('reports the chosen rating', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<EmojiRating value={null} onChange={onChange} />);
    await user.click(screen.getByRole('button', { name: 'Note 4/5' }));
    expect(onChange).toHaveBeenCalledWith(4);
  });

  it('stops the click reaching an enclosing card link', async () => {
    const user = userEvent.setup();
    const onCardClick = vi.fn();
    render(
      <div onClick={onCardClick}>
        <EmojiRating value={null} onChange={() => {}} />
      </div>,
    );
    await user.click(screen.getByRole('button', { name: 'Note 2/5' }));
    expect(onCardClick).not.toHaveBeenCalled();
  });

  it('emphasises only the selected emoji', () => {
    render(<EmojiRating value={3} onChange={() => {}} />);
    expect(screen.getByRole('button', { name: 'Note 3/5' }).className).toContain('scale-125');
    expect(screen.getByRole('button', { name: 'Note 1/5' }).className).toContain('opacity-40');
  });
});

describe('formatSeconds', () => {
  it.each([
    [0, '00:00'],
    [5, '00:05'],
    [65, '01:05'],
    [599, '09:59'],
    [3600, '1:00:00'],
    [3725, '1:02:05'],
    [86399, '23:59:59'],
  ])('%i seconds -> %s', (seconds, expected) => {
    expect(formatSeconds(seconds)).toBe(expected);
  });
});
