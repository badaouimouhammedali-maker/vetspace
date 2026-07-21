import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { Field, PasswordInput, Select, SubmitButton, Textarea, TextInput } from './forms';

/**
 * These assert the *contract* of a field, not its class strings: that an invalid field
 * says so to a screen reader, that an error replaces the hint rather than stacking with
 * it, and that the password toggle actually changes the input type. Asserting on
 * Tailwind classes would pass while the field was unusable.
 */
describe('Field', () => {
  it('associates the label with its control', () => {
    render(
      <Field label="Adresse e-mail" htmlFor="email">
        <TextInput id="email" />
      </Field>,
    );
    expect(screen.getByLabelText('Adresse e-mail')).toBeInTheDocument();
  });

  it('shows the hint when there is no error', () => {
    render(
      <Field label="Mot de passe" htmlFor="pw" hint="12 caractères minimum">
        <TextInput id="pw" />
      </Field>,
    );
    expect(screen.getByText('12 caractères minimum')).toBeInTheDocument();
  });

  it('replaces the hint with the error rather than showing both', () => {
    render(
      <Field label="Mot de passe" htmlFor="pw" hint="12 caractères minimum" error="Trop court">
        <TextInput id="pw" />
      </Field>,
    );
    expect(screen.getByText('Trop court')).toBeInTheDocument();
    expect(screen.queryByText('12 caractères minimum')).not.toBeInTheDocument();
  });
});

describe('invalid state', () => {
  it.each([
    ['input', <TextInput key="i" aria-label="i" invalid />],
    ['textarea', <Textarea key="t" aria-label="t" invalid />],
    [
      'select',
      <Select key="s" aria-label="s" invalid>
        <option>a</option>
      </Select>,
    ],
  ])('%s exposes aria-invalid', (_name, element) => {
    const { container } = render(element);
    expect(container.firstChild).toHaveAttribute('aria-invalid', 'true');
  });

  it('omits aria-invalid entirely when valid, rather than setting it to "false"', () => {
    const { container } = render(<TextInput aria-label="i" />);
    expect(container.firstChild).not.toHaveAttribute('aria-invalid');
  });
});

describe('PasswordInput', () => {
  it('starts masked and toggles the input type', async () => {
    const user = userEvent.setup();
    const { container } = render(<PasswordInput aria-label="Mot de passe" />);
    const input = container.querySelector('input')!;

    expect(input).toHaveAttribute('type', 'password');
    await user.click(screen.getByRole('button', { name: 'Afficher le mot de passe' }));
    expect(input).toHaveAttribute('type', 'text');

    await user.click(screen.getByRole('button', { name: 'Masquer le mot de passe' }));
    expect(input).toHaveAttribute('type', 'password');
  });
});

describe('SubmitButton', () => {
  it('submits its form', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn((e: React.FormEvent) => e.preventDefault());
    render(
      <form onSubmit={onSubmit}>
        <SubmitButton>Envoyer</SubmitButton>
      </form>,
    );
    await user.click(screen.getByRole('button', { name: 'Envoyer' }));
    expect(onSubmit).toHaveBeenCalledOnce();
  });

  it('is disabled while loading, so a double click cannot double submit', () => {
    render(<SubmitButton loading>Envoyer</SubmitButton>);
    expect(screen.getByRole('button')).toBeDisabled();
  });
});
