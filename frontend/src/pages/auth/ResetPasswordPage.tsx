import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { AuthLayout } from '../../components/AuthLayout';
import { Field, PasswordInput, SubmitButton } from '../../components/forms';
import { useToast } from '../../components/ToastProvider';
import { t } from '../../i18n/fr';
import { apiErrorStatus, apiPostVoid } from '../../lib/api';

export function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { toast } = useToast();
  const token = searchParams.get('token');

  const [password, setPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    if (password !== passwordConfirm) {
      setError(t('register.passwordMismatch'));
      return;
    }
    setLoading(true);
    try {
      await apiPostVoid('/api/auth/reset-password', { token, newPassword: password });
      toast('success', t('reset.success'));
      navigate('/login');
    } catch (err) {
      setError(apiErrorStatus(err) === 400 ? t('reset.invalidToken') : t('common.error'));
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthLayout>
      <h1 className="text-2xl font-extrabold text-brand-navy">{t('reset.title')}</h1>
      <p className="mt-1 text-sm text-brand-gray">{t('reset.subtitle')}</p>

      {!token ? (
        <p className="mt-6 rounded-lg bg-red-50 p-4 text-sm font-medium text-red-600">
          {t('reset.missingToken')}
        </p>
      ) : (
        <form onSubmit={onSubmit} className="mt-6 space-y-4" noValidate>
          <Field label={t('reset.password')} htmlFor="password">
            <PasswordInput
              id="password"
              autoComplete="new-password"
              required
              minLength={10}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </Field>
          <p className="-mt-2 text-xs text-brand-gray">{t('register.passwordHint')}</p>
          <Field label={t('reset.passwordConfirm')} htmlFor="passwordConfirm">
            <PasswordInput
              id="passwordConfirm"
              autoComplete="new-password"
              required
              value={passwordConfirm}
              onChange={(e) => setPasswordConfirm(e.target.value)}
            />
          </Field>
          {error ? <p className="text-sm font-medium text-red-600">{error}</p> : null}
          <SubmitButton loading={loading}>{t('reset.submit')}</SubmitButton>
        </form>
      )}

      <p className="mt-6 text-center text-sm">
        <Link to="/login" className="font-semibold text-brand-green hover:underline">
          {t('forgot.backToLogin')}
        </Link>
      </p>
    </AuthLayout>
  );
}
