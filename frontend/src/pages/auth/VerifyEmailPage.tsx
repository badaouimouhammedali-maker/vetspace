import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { AuthLayout } from '../../components/AuthLayout';
import { Field, SubmitButton, TextInput } from '../../components/forms';
import { t } from '../../i18n/fr';
import { apiErrorStatus, apiPostVoid } from '../../lib/api';

type Status = 'verifying' | 'verified' | 'invalid' | 'needsVerification';

/**
 * Serves two arrivals:
 *  - from the emailed link (`?token=…`), which verifies immediately;
 *  - from a login refused with EMAIL_NOT_VERIFIED, which offers a resend instead of
 *    leaving the user at a dead end on the login screen.
 */
export function VerifyEmailPage() {
  const [params] = useSearchParams();
  const token = params.get('token');
  const prefilledEmail = params.get('email') ?? '';
  // Set by RegisterPage right after a successful send: the intro then says "check your
  // inbox" rather than implying the user has to ask for a (re)send first.
  const justSent = params.get('sent') === '1';

  const [status, setStatus] = useState<Status>(token ? 'verifying' : 'needsVerification');
  const [email, setEmail] = useState(prefilledEmail);
  const [resent, setResent] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  // React 18 StrictMode double-invokes effects in dev; the token is single-use, so a
  // second call would report "invalid" for a link that actually worked.
  const verifyStarted = useRef(false);

  useEffect(() => {
    if (!token || verifyStarted.current) {
      return;
    }
    verifyStarted.current = true;
    apiPostVoid('/api/auth/verify-email', { token })
      .then(() => setStatus('verified'))
      .catch(() => setStatus('invalid'));
  }, [token]);

  async function onResend(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await apiPostVoid('/api/auth/resend-verification', { email });
      setResent(true);
    } catch (err) {
      setError(apiErrorStatus(err) === 429 ? t('verify.resendLimited') : t('common.error'));
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthLayout>
      <h1 className="text-h1 text-ink">{t('verify.title')}</h1>

      {status === 'verifying' ? (
        <p className="mt-6 text-body text-ink-muted">{t('verify.checking')}</p>
      ) : null}

      {status === 'verified' ? (
        <>
          <p className="mt-6 rounded-lg bg-success/10 p-4 text-sm font-medium text-success-text">
            {t('verify.success')}
          </p>
          <Link
            to="/login"
            className="mt-6 inline-block font-semibold text-accent hover:underline"
          >
            {t('verify.goToLogin')}
          </Link>
        </>
      ) : null}

      {status === 'invalid' ? (
        <p className="mt-6 rounded-lg bg-danger/10 p-4 text-sm font-medium text-danger">
          {t('verify.invalid')}
        </p>
      ) : null}

      {status === 'needsVerification' || status === 'invalid' ? (
        <>
          {justSent && status === 'needsVerification' ? (
            <p className="mt-6 rounded-lg bg-success/10 p-4 text-sm font-medium text-success-text">
              {t('verify.sentIntro')}
            </p>
          ) : (
            <p className="mt-6 text-sm text-ink-muted">{t('verify.resendIntro')}</p>
          )}

          {resent ? (
            <p className="mt-4 rounded-lg bg-success/10 p-4 text-sm font-medium text-success-text">
              {t('verify.resent')}
            </p>
          ) : (
            <form onSubmit={onResend} className="mt-4 space-y-4" noValidate>
              <Field label={t('verify.email')} htmlFor="email">
                <TextInput
                  id="email"
                  type="email"
                  autoComplete="email"
                  required
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                />
              </Field>
              {error ? <p className="text-sm font-medium text-danger">{error}</p> : null}
              <SubmitButton loading={loading}>{t('verify.resend')}</SubmitButton>
            </form>
          )}

          <Link
            to="/login"
            className="mt-6 inline-block text-sm font-semibold text-accent hover:underline"
          >
            {t('verify.backToLogin')}
          </Link>
        </>
      ) : null}
    </AuthLayout>
  );
}
