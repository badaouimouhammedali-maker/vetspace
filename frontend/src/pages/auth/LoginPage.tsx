import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { AuthLayout } from '../../components/AuthLayout';
import { Field, PasswordInput, SubmitButton, TextInput } from '../../components/forms';
import { t } from '../../i18n/fr';
import { SUPERSEDED_FLAG, apiErrorStatus } from '../../lib/api';

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
  /**
   * Set when the API answered SESSION_SUPERSEDED — the account was signed into elsewhere.
   * A distinct UI state, not a variant of the login error: it explains something that
   * already happened rather than something the user got wrong, and it survives on screen
   * instead of disappearing like a toast.
   */
  const superseded = Boolean((location.state as Record<string, unknown> | null)?.[SUPERSEDED_FLAG]);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await login(email, password);
      navigate(from, { replace: true });
    } catch (err) {
      const status = apiErrorStatus(err);
      if (status === 403) {
        // The address is not confirmed yet. Send the user somewhere they can act —
        // the verify screen with a resend — rather than a dead end on this form.
        navigate(`/verify-email?email=${encodeURIComponent(email)}`, { replace: true });
        return;
      }
      setError(status === 429 ? t('api.rateLimited') : t('login.failed'));
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthLayout>
      <h1 className="text-h1 text-brand-navy">{t('login.title')}</h1>
      <p className="mt-1.5 text-body text-gray-500">{t('login.subtitle')}</p>

      {superseded ? (
        <div
          role="alert"
          className="mt-5 rounded-lg border border-warning/40 bg-warning/10 p-4"
          data-testid="session-superseded"
        >
          <p className="text-sm font-bold text-brand-navy">{t('api.sessionSupersededTitle')}</p>
          <p className="mt-1 text-sm text-brand-navy">{t('api.sessionSuperseded')}</p>
          {/* The one case where the right next step may not be "sign in again". */}
          <p className="mt-2 text-xs text-brand-gray">{t('api.sessionSupersededHint')}</p>
        </div>
      ) : null}

      <form onSubmit={onSubmit} className="mt-6 space-y-5" noValidate>
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

        {error ? <p className="text-sm font-medium text-danger">{error}</p> : null}

        {/* Permanent, not conditional: warning people BEFORE the policy bites is the
            difference between a rule and an ambush. Right above the button, where the
            decision is actually being made. */}
        <p className="flex items-start gap-2 text-xs text-brand-gray">
          <span aria-hidden>ℹ️</span>
          <span>{t('auth.singleDeviceNotice')}</span>
        </p>

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
