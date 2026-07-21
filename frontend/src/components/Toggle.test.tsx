import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { Disclosure, MenuRow, PlainButton, SegmentToggle, SelectableRow } from './Toggle';

/**
 * The reason these primitives exist at all is that a toggle, an accordion header and a
 * menu row announce three different things. That distinction is invisible on screen, so
 * it is only ever protected by tests like these — a regression here is silent for
 * sighted users and total for anyone on a screen reader.
 */
describe('SegmentToggle', () => {
  it('announces pressed state', () => {
    const { rerender } = render(<SegmentToggle selected={false}>Tableau</SegmentToggle>);
    expect(screen.getByRole('button')).toHaveAttribute('aria-pressed', 'false');
    rerender(<SegmentToggle selected>Tableau</SegmentToggle>);
    expect(screen.getByRole('button')).toHaveAttribute('aria-pressed', 'true');
  });

  it('carries the tone through to the selected fill', () => {
    render(
      <>
        <SegmentToggle selected tone="success">
          VRAI
        </SegmentToggle>
        <SegmentToggle selected tone="danger">
          FAUX
        </SegmentToggle>
      </>,
    );
    expect(screen.getByRole('button', { name: 'VRAI' }).className).toContain('bg-success');
    expect(screen.getByRole('button', { name: 'FAUX' }).className).toContain('bg-danger');
  });

  it('does not carry a tone fill when unselected', () => {
    render(
      <SegmentToggle selected={false} tone="danger">
        FAUX
      </SegmentToggle>,
    );
    expect(screen.getByRole('button').className).not.toContain('bg-danger');
  });

  it('defaults to type=button so it never submits a surrounding form', () => {
    render(<SegmentToggle selected={false}>x</SegmentToggle>);
    expect(screen.getByRole('button')).toHaveAttribute('type', 'button');
  });
});

describe('SelectableRow', () => {
  it('announces pressed state and fires onClick', async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(
      <SelectableRow selected onClick={onClick}>
        Question 1
      </SelectableRow>,
    );
    const row = screen.getByRole('button');
    expect(row).toHaveAttribute('aria-pressed', 'true');
    await user.click(row);
    expect(onClick).toHaveBeenCalledOnce();
  });
});

describe('Disclosure', () => {
  it('announces expanded — NOT pressed; expanding a section is not selecting it', () => {
    const { rerender } = render(<Disclosure open={false}>Une question</Disclosure>);
    const button = screen.getByRole('button');
    expect(button).toHaveAttribute('aria-expanded', 'false');
    expect(button).not.toHaveAttribute('aria-pressed');

    rerender(<Disclosure open>Une question</Disclosure>);
    expect(screen.getByRole('button')).toHaveAttribute('aria-expanded', 'true');
  });

  it('rotates its caret with the open state', () => {
    const { rerender, container } = render(<Disclosure open={false}>x</Disclosure>);
    expect(container.querySelector('span')?.className).not.toContain('rotate-90');
    rerender(<Disclosure open>x</Disclosure>);
    expect(container.querySelector('span')?.className).toContain('rotate-90');
  });

  it('hides the caret from assistive tech — it is decoration, the state is in aria-expanded', () => {
    const { container } = render(<Disclosure open>x</Disclosure>);
    expect(container.querySelector('span')).toHaveAttribute('aria-hidden');
  });
});

describe('MenuRow', () => {
  it('renders title and hint, and stays free of aria-pressed — a menu item is not a toggle', () => {
    render(<MenuRow title="Refaire" hint="Repart de zéro" />);
    const button = screen.getByRole('button');
    expect(button).toHaveTextContent('Refaire');
    expect(button).toHaveTextContent('Repart de zéro');
    expect(button).not.toHaveAttribute('aria-pressed');
  });

  it('omits the hint line when there is none', () => {
    render(<MenuRow title="Refaire" />);
    expect(screen.getByRole('button').textContent).toBe('Refaire');
  });

  it('honours disabled', async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(<MenuRow title="Refaire" disabled onClick={onClick} />);
    await user.click(screen.getByRole('button'));
    expect(onClick).not.toHaveBeenCalled();
  });
});

describe('PlainButton', () => {
  it('is a real button with type=button, which is the whole point', () => {
    render(<PlainButton aria-label="Agrandir">🖼</PlainButton>);
    expect(screen.getByRole('button', { name: 'Agrandir' })).toHaveAttribute('type', 'button');
  });

  it('does not submit a surrounding form', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn((e: React.FormEvent) => e.preventDefault());
    render(
      <form onSubmit={onSubmit}>
        <PlainButton>★</PlainButton>
      </form>,
    );
    await user.click(screen.getByRole('button'));
    expect(onSubmit).not.toHaveBeenCalled();
  });
});
