import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest';
import { ErrorBoundary } from './ErrorBoundary';

/** A component that throws during render — the exact failure a boundary exists for. */
function Exploding(): JSX.Element {
  throw new Error('kaboom from a child component');
}

describe('ErrorBoundary', () => {
  // React logs the caught error to console.error by design; silence it so a passing
  // run is not full of red noise, and restore afterwards.
  const consoleError = console.error;
  beforeAll(() => {
    console.error = vi.fn();
  });
  afterAll(() => {
    console.error = consoleError;
  });

  it('renders children unchanged when nothing throws', () => {
    render(
      <ErrorBoundary>
        <p>contenu normal</p>
      </ErrorBoundary>,
    );

    expect(screen.getByText('contenu normal')).toBeInTheDocument();
  });

  it('renders the branded card instead of a white page when a child throws', () => {
    const { container } = render(
      <ErrorBoundary scope="test">
        <Exploding />
      </ErrorBoundary>,
    );

    // The whole point: something is on screen. React unmounts the tree on an uncaught
    // render error, so without the boundary this container would be empty.
    expect(container).not.toBeEmptyDOMElement();
    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByText('Une erreur est survenue')).toBeInTheDocument();
    // Branded, so the user knows they are still in VetSpace and not on a browser page.
    expect(screen.getByText('VetSpace')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Recharger la page' })).toBeInTheDocument();
  });

  it('reloads the page when the button is clicked', async () => {
    const reload = vi.fn();
    Object.defineProperty(window, 'location', {
      value: { ...window.location, reload },
      writable: true,
    });

    render(
      <ErrorBoundary>
        <Exploding />
      </ErrorBoundary>,
    );
    await userEvent.click(screen.getByRole('button', { name: 'Recharger la page' }));

    expect(reload).toHaveBeenCalledOnce();
  });

  it('shows the diagnostic block in dev only', () => {
    render(
      <ErrorBoundary scope="test">
        <Exploding />
      </ErrorBoundary>,
    );

    // Vitest runs with DEV true, so the message is expected here. The production
    // build sets DEV false and the block is omitted — a student should never be shown
    // a stack trace.
    expect(import.meta.env.DEV).toBe(true);
    expect(screen.getByText(/kaboom from a child component/)).toBeInTheDocument();
  });
});
