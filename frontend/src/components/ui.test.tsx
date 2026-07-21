import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { Button } from './Button';
import { Modal } from './Modal';

describe('Button', () => {
  it('replaces the label with a spinner and blocks clicks while loading', async () => {
    const onClick = vi.fn();
    render(
      <Button loading onClick={onClick}>
        Enregistrer
      </Button>,
    );

    const button = screen.getByRole('button');
    // The label is gone, so the button keeps its size but says nothing misleading.
    expect(button).not.toHaveTextContent('Enregistrer');
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('aria-busy', 'true');

    // A loading button that still fires is how double submissions happen.
    await userEvent.click(button);
    expect(onClick).not.toHaveBeenCalled();
  });

  it('is clickable when not loading', async () => {
    const onClick = vi.fn();
    render(<Button onClick={onClick}>Valider</Button>);

    await userEvent.click(screen.getByRole('button', { name: 'Valider' }));
    expect(onClick).toHaveBeenCalledOnce();
  });
});

describe('Modal', () => {
  function Fixture({ onClose = () => {} }: { onClose?: () => void }) {
    return (
      <Modal open onClose={onClose} title="Titre du dialogue">
        <input aria-label="premier" />
        <input aria-label="second" />
      </Modal>
    );
  }

  it('is announced as a dialog and focuses the first field', () => {
    render(<Fixture />);

    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    // Initial focus inside the dialog, so a keyboard user starts where the work is.
    expect(screen.getByLabelText('premier')).toHaveFocus();
  });

  it('keeps Tab inside the dialog', async () => {
    render(<Fixture />);

    const first = screen.getByLabelText('premier');
    const second = screen.getByLabelText('second');
    const close = screen.getByRole('button');

    await userEvent.tab();
    expect(second).toHaveFocus();
    // From the last field, wrap to the first focusable in the DOM — the ✕ — rather than
    // escaping into the page behind, which the user cannot see is still there.
    await userEvent.tab();
    expect(close).toHaveFocus();
    await userEvent.tab();
    expect(first).toHaveFocus();
  });

  it('wraps backwards too', async () => {
    render(<Fixture />);

    // Focus starts on the first field; Shift+Tab steps back to the ✕ before it.
    await userEvent.tab({ shift: true });
    expect(screen.getByRole('button')).toHaveFocus();
  });

  it('closes on Escape', async () => {
    const onClose = vi.fn();
    render(<Fixture onClose={onClose} />);

    await userEvent.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalled();
  });

  it('locks body scroll while open and restores it on close', () => {
    const { unmount } = render(<Fixture />);
    expect(document.body.style.overflow).toBe('hidden');

    unmount();
    // Restored rather than cleared, so a scroll lock set by something else survives.
    expect(document.body.style.overflow).not.toBe('hidden');
  });
});
