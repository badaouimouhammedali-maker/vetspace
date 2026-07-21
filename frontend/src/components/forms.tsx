import {
  useState,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
} from 'react';
import { Button } from './Button';

/**
 * One field skin for input, select and textarea.
 *
 * <p>No `focus:ring` here on purpose — the global `:focus-visible` rule in index.css
 * owns the focus treatment, so it is identical on a field, a button and a link, and it
 * shows for keyboard users only rather than on every click.
 */
const FIELD_BASE =
  'w-full rounded-md border bg-surface px-3.5 py-2.5 text-body text-gray-900 ' +
  'placeholder-gray-400 outline-none transition-colors duration-150 ' +
  'disabled:cursor-not-allowed disabled:bg-gray-100';

const FIELD_DEFAULT = 'border-gray-300 focus:border-brand-green';
/** Invalid fields carry the danger colour, so the error is visible before it is read. */
const FIELD_ERROR = 'border-danger focus:border-danger';

function fieldClasses(invalid: boolean | undefined): string {
  return `${FIELD_BASE} ${invalid ? FIELD_ERROR : FIELD_DEFAULT}`;
}

interface FieldProps {
  label: string;
  htmlFor: string;
  error?: string | undefined;
  hint?: string | undefined;
  children: ReactNode;
}

export function Field({ label, htmlFor, error, hint, children }: FieldProps) {
  return (
    <div className="space-y-1.5">
      <label htmlFor={htmlFor} className="block text-caption font-semibold text-brand-navy">
        {label}
      </label>
      {children}
      {/* Hint gives way to the error rather than stacking: two lines of guidance under
          one field is noise at the moment the user most needs a single answer. */}
      {error ? (
        <p className="text-caption font-medium text-danger">{error}</p>
      ) : hint ? (
        <p className="text-caption text-gray-500">{hint}</p>
      ) : null}
    </div>
  );
}

type WithInvalid<T> = T & { invalid?: boolean };

export function TextInput({
  invalid,
  ...props
}: WithInvalid<InputHTMLAttributes<HTMLInputElement>>) {
  return <input {...props} aria-invalid={invalid || undefined} className={fieldClasses(invalid)} />;
}

export function Textarea({
  invalid,
  ...props
}: WithInvalid<TextareaHTMLAttributes<HTMLTextAreaElement>>) {
  return (
    <textarea {...props} aria-invalid={invalid || undefined} className={fieldClasses(invalid)} />
  );
}

export function Select({ invalid, ...props }: WithInvalid<SelectHTMLAttributes<HTMLSelectElement>>) {
  return <select {...props} aria-invalid={invalid || undefined} className={fieldClasses(invalid)} />;
}

/** Password field with a show/hide toggle. */
export function PasswordInput({
  invalid,
  ...props
}: WithInvalid<InputHTMLAttributes<HTMLInputElement>>) {
  const [visible, setVisible] = useState(false);
  return (
    <div className="relative">
      <input
        {...props}
        type={visible ? 'text' : 'password'}
        aria-invalid={invalid || undefined}
        className={`${fieldClasses(invalid)} pr-20`}
      />
      <button
        type="button"
        onClick={() => setVisible((v) => !v)}
        className="absolute inset-y-0 right-0 rounded-md px-3 text-caption font-semibold text-gray-500 transition-colors duration-150 hover:text-brand-navy"
        aria-label={visible ? 'Masquer le mot de passe' : 'Afficher le mot de passe'}
      >
        {visible ? 'Masquer' : 'Afficher'}
      </button>
    </div>
  );
}

/** Full-width submit, delegating every visual decision to Button. */
export function SubmitButton({
  children,
  loading = false,
}: {
  children: ReactNode;
  loading?: boolean;
}) {
  return (
    <Button type="submit" loading={loading} className="w-full">
      {children}
    </Button>
  );
}
