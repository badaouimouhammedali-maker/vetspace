import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { GhostButton, PrimaryButton } from './adminUi';

/**
 * Regression: GhostButton set no `type`, so HTML's default of "submit" applied. Inside a
 * <form> that made every secondary action submit it — clicking "Annuler" in the JSON
 * import dialog both closed the modal and fired the import it was meant to cancel.
 */
describe('GhostButton inside a form', () => {
  it('does not submit the form it sits in', async () => {
    const onSubmit = vi.fn((e: React.FormEvent) => e.preventDefault());
    const onClick = vi.fn();
    render(
      <form onSubmit={onSubmit}>
        <GhostButton onClick={onClick}>Annuler</GhostButton>
      </form>,
    );

    await userEvent.click(screen.getByRole('button', { name: 'Annuler' }));

    expect(onClick).toHaveBeenCalledOnce();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('still submits when a ghost button is explicitly the submit', async () => {
    const onSubmit = vi.fn((e: React.FormEvent) => e.preventDefault());
    render(
      <form onSubmit={onSubmit}>
        <GhostButton type="submit">Envoyer</GhostButton>
      </form>,
    );

    await userEvent.click(screen.getByRole('button', { name: 'Envoyer' }));

    expect(onSubmit).toHaveBeenCalledOnce();
  });

  it('PrimaryButton keeps the same contract', async () => {
    const onSubmit = vi.fn((e: React.FormEvent) => e.preventDefault());
    render(
      <form onSubmit={onSubmit}>
        <PrimaryButton>Action</PrimaryButton>
        <PrimaryButton type="submit">Valider</PrimaryButton>
      </form>,
    );

    await userEvent.click(screen.getByRole('button', { name: 'Action' }));
    expect(onSubmit).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole('button', { name: 'Valider' }));
    expect(onSubmit).toHaveBeenCalledOnce();
  });
});
