import { useState, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes } from 'react';

const FIELD_CLASSES =
  'w-full rounded-lg border border-gray-300 bg-white px-3.5 py-2.5 text-sm text-gray-900 ' +
  'placeholder-gray-400 outline-none transition focus:border-brand-green focus:ring-2 ' +
  'focus:ring-brand-green/20 disabled:cursor-not-allowed disabled:bg-gray-100';

interface FieldProps {
  label: string;
  htmlFor: string;
  error?: string | undefined;
  children: ReactNode;
}

export function Field({ label, htmlFor, error, children }: FieldProps) {
  return (
    <div className="space-y-1.5">
      <label htmlFor={htmlFor} className="block text-sm font-semibold text-brand-navy">
        {label}
      </label>
      {children}
      {error ? <p className="text-xs font-medium text-red-600">{error}</p> : null}
    </div>
  );
}

export function TextInput(props: InputHTMLAttributes<HTMLInputElement>) {
  return <input {...props} className={FIELD_CLASSES} />;
}

/** Champ mot de passe avec bouton afficher/masquer. */
export function PasswordInput(props: InputHTMLAttributes<HTMLInputElement>) {
  const [visible, setVisible] = useState(false);
  return (
    <div className="relative">
      <input {...props} type={visible ? 'text' : 'password'} className={`${FIELD_CLASSES} pr-16`} />
      <button
        type="button"
        onClick={() => setVisible((v) => !v)}
        className="absolute inset-y-0 right-0 px-3 text-xs font-semibold text-brand-gray hover:text-brand-navy"
        aria-label={visible ? 'Masquer le mot de passe' : 'Afficher le mot de passe'}
      >
        {visible ? 'Masquer' : 'Afficher'}
      </button>
    </div>
  );
}

export function Select(props: SelectHTMLAttributes<HTMLSelectElement>) {
  return <select {...props} className={FIELD_CLASSES} />;
}

interface SubmitButtonProps {
  children: ReactNode;
  loading?: boolean;
}

export function SubmitButton({ children, loading = false }: SubmitButtonProps) {
  return (
    <button
      type="submit"
      disabled={loading}
      className="w-full rounded-lg bg-brand-green px-4 py-2.5 text-sm font-bold text-white transition
        hover:bg-brand-green-hover disabled:cursor-not-allowed disabled:opacity-60"
    >
      {loading ? '…' : children}
    </button>
  );
}
