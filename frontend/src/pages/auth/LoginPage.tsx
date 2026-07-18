import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { AuthLayout } from '../../components/AuthLayout';
import { Field, PasswordInput, SubmitButton, TextInput } from '../../components/forms';
import { t } from '../../i18n/fr';
import { apiErrorStatus } from '../../lib/api';

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [rememberMe, setRememberMe] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const from = (location.state as { from?: string } | null)?.from ?? '/app';

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await login(email, password);
      navigate(from, { replace: true });
    } catch (err) {
      setError(
        apiErrorStatus(err) === 429 ? t('api.rateLimited') : t('login.failed'),
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthLayout>
      <h1 className="text-2xl font-extrabold text-brand-navy">{t('login.title')}</h1>
      <p className="mt-1 text-sm text-brand-gray">{t('login.subtitle')}</p>

      <form onSubmit={onSubmit} className="mt-6 space-y-4" noValidate>
        <Field label={t('login.email')} htmlFor="email">
          <TextInput
            id="email"
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </Field>
        <Field label={t('login.password')} htmlFor="password">
          <PasswordInput
            id="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </Field>

        <div className="flex items-center justify-between text-sm">
          <label className="flex items-center gap-2 text-brand-gray">
            <input
              type="checkbox"
              checked={rememberMe}
              onChange={(e) => setRememberMe(e.target.checked)}
              className="h-4 w-4 rounded border-gray-300 accent-brand-green"
            />
            {t('login.rememberMe')}
          </label>
          <Link to="/forgot-password" className="font-semibold text-brand-green hover:underline">
            {t('login.forgotPassword')}
          </Link>
        </div>

        {error ? <p className="text-sm font-medium text-red-600">{error}</p> : null}

        <SubmitButton loading={loading}>{t('login.submit')}</SubmitButton>
      </form>

      <p className="mt-6 text-center text-sm text-brand-gray">
        {t('login.noAccount')}{' '}
        <Link to="/register" className="font-semibold text-brand-green hover:underline">
          {t('login.registerLink')}
        </Link>
      </p>
    </AuthLayout>
  );
}
