import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { useDropdown } from './useDropdown';

function Harness() {
  const { open, setOpen, ref } = useDropdown();
  return (
    <div>
      <button type="button" onClick={() => setOpen(true)}>
        outside
      </button>
      <div ref={ref}>
        <button type="button" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
          menu
        </button>
        {open ? <div role="menu">contents</div> : null}
      </div>
    </div>
  );
}

describe('useDropdown', () => {
  it('opens and closes on the trigger', async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await user.click(screen.getByRole('button', { name: 'menu' }));
    expect(screen.getByRole('menu')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'menu' }));
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  });

  it('closes on click outside', async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await user.click(screen.getByRole('button', { name: 'menu' }));
    expect(screen.getByRole('menu')).toBeInTheDocument();

    await user.click(document.body);
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  });

  it('stays open when clicking inside the panel', async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await user.click(screen.getByRole('button', { name: 'menu' }));
    await user.click(screen.getByRole('menu'));
    expect(screen.getByRole('menu')).toBeInTheDocument();
  });

  it('closes on Escape — without it a keyboard user cannot dismiss without activating an item', async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await user.click(screen.getByRole('button', { name: 'menu' }));
    expect(screen.getByRole('menu')).toBeInTheDocument();

    await user.keyboard('{Escape}');
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  });

  it('removes its listeners on unmount', async () => {
    const user = userEvent.setup();
    const { unmount } = render(<Harness />);
    await user.click(screen.getByRole('button', { name: 'menu' }));
    unmount();
    // A leaked keydown handler would call setOpen on an unmounted tree and warn.
    await user.keyboard('{Escape}');
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  });
});
