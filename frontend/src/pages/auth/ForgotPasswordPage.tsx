import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { AuthLayout } from '../../components/AuthLayout';
import { Field, SubmitButton, TextInput } from '../../components/forms';
import { t } from '../../i18n/fr';
import { apiErrorStatus, apiPostVoid } from '../../lib/api';

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await apiPostVoid('/api/auth/forgot-password', { email });
      setSent(true);
    } catch (err) {
      setError(apiErrorStatus(err) === 429 ? t('api.rateLimited') : t('common.error'));
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthLayout>
      <h1 className="text-h1 text-ink">{t('forgot.title')}</h1>
      <p className="mt-1.5 text-body text-ink-muted">{t('forgot.subtitle')}</p>

      {sent ? (
        <p className="mt-6 rounded-lg bg-success/10 p-4 text-sm font-medium text-success-text">
          {t('forgot.sent')}
        </p>
      ) : (
        <form onSubmit={onSubmit} className="mt-6 space-y-5" noValidate>
          <Field label={t('forgot.email')} htmlFor="email">
            <TextInput
              id="email"
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </Field>
          {error ? <p className="text-sm font-medium text-danger">{error}</p> : null}
          <SubmitButton loading={loading}>{t('forgot.submit')}</SubmitButton>
        </form>
      )}

      <p className="mt-6 text-center text-sm">
        <Link to="/login" className="font-semibold text-accent hover:underline">
          {t('forgot.backToLogin')}
        </Link>
      </p>
    </AuthLayout>
  );
}
