import { useQuery } from '@tanstack/react-query';
import { useCallback, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { AuthLayout } from '../../components/AuthLayout';
import { Field, PasswordInput, Select, SubmitButton, TextInput } from '../../components/forms';
import { DEV_RECAPTCHA_TOKEN, Recaptcha, recaptchaEnabled } from '../../components/Recaptcha';
import { useToast } from '../../components/ToastProvider';
import { t } from '../../i18n/fr';
import { apiErrorMessage, apiGet } from '../../lib/api';
import { schoolListSchema } from '../../lib/schemas';

const STUDY_YEARS = [1, 2, 3, 4, 5, 6];

export function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const { toast } = useToast();

  const schools = useQuery({
    queryKey: ['schools'],
    queryFn: () => apiGet('/api/schools', schoolListSchema),
  });

  const [lastName, setLastName] = useState('');
  const [firstName, setFirstName] = useState('');
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [schoolId, setSchoolId] = useState('');
  const [studyYear, setStudyYear] = useState('');
  const [captchaToken, setCaptchaToken] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const onCaptchaToken = useCallback((token: string | null) => setCaptchaToken(token), []);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    if (password !== passwordConfirm) {
      setError(t('register.passwordMismatch'));
      return;
    }
    const recaptchaToken = recaptchaEnabled() ? captchaToken : DEV_RECAPTCHA_TOKEN;
    if (!recaptchaToken) {
      setError(t('register.captchaRequired'));
      return;
    }
    setLoading(true);
    try {
      const created = await register({
        lastName,
        firstName,
        username,
        email,
        password,
        schoolId,
        studyYear: Number(studyYear),
        recaptchaToken,
      });
      // Trois issues, du meilleur au pire. Les comparaisons strictes (`=== false`,
      // `=== true`) et non `!…` : un backend antérieur à ces champs n'envoie rien,
      // et l'absence doit garder l'ancien comportement (connexion) sans avertir à tort.
      if (created.verificationEmailSent === false) {
        // Le compte est créé : le serveur le commit avant même de composer l'e-mail.
        // On envoie donc l'utilisateur là où se trouve le bouton « renvoyer », adresse
        // pré-remplie, plutôt que vers une connexion qui refusera un compte non vérifié.
        toast('error', t('register.emailNotSent'));
        navigate(`/verify-email?email=${encodeURIComponent(email)}`);
      } else if (created.emailVerified === false) {
        // L'e-mail est parti et le compte attend sa confirmation : la connexion ne
        // ferait que répondre EMAIL_NOT_VERIFIED. Direction « vérifiez votre boîte »,
        // avec le renvoi à portée de main.
        toast('success', t('register.checkInbox'));
        navigate(`/verify-email?email=${encodeURIComponent(email)}&sent=1`);
      } else {
        // Auto-vérifié (dev/e2e) — ou backend d'avant ces champs : la connexion
        // fonctionne immédiatement.
        toast('success', t('register.success'));
        navigate('/login');
      }
    } catch (err) {
      setError(apiErrorMessage(err) ?? t('common.error'));
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthLayout>
      <h1 className="text-h1 text-ink">{t('register.title')}</h1>
      <p className="mt-1.5 text-body text-ink-muted">{t('register.subtitle')}</p>

      <form onSubmit={onSubmit} className="mt-6 space-y-5" noValidate>
        <div className="grid grid-cols-2 gap-3">
          <Field label={t('register.lastName')} htmlFor="lastName">
            <TextInput
              id="lastName"
              autoComplete="family-name"
              required
              value={lastName}
              onChange={(e) => setLastName(e.target.value)}
            />
          </Field>
          <Field label={t('register.firstName')} htmlFor="firstName">
            <TextInput
              id="firstName"
              autoComplete="given-name"
              required
              value={firstName}
              onChange={(e) => setFirstName(e.target.value)}
            />
          </Field>
        </div>

        <Field label={t('register.username')} htmlFor="username">
          <TextInput
            id="username"
            autoComplete="username"
            required
            minLength={3}
            value={username}
            onChange={(e) => setUsername(e.target.value)}
          />
        </Field>

        <Field label={t('register.email')} htmlFor="email">
          <TextInput
            id="email"
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </Field>

        <div className="grid grid-cols-2 gap-3">
          <Field label={t('register.school')} htmlFor="school">
            <Select
              id="school"
              required
              value={schoolId}
              onChange={(e) => setSchoolId(e.target.value)}
            >
              <option value="" disabled>
                {t('register.schoolPlaceholder')}
              </option>
              {(schools.data ?? []).map((school) => (
                <option key={school.id} value={school.id}>
                  {school.name}
                </option>
              ))}
            </Select>
          </Field>
          <Field label={t('register.studyYear')} htmlFor="studyYear">
            <Select
              id="studyYear"
              required
              value={studyYear}
              onChange={(e) => setStudyYear(e.target.value)}
            >
              <option value="" disabled>
                {t('register.studyYearPlaceholder')}
              </option>
              {STUDY_YEARS.map((year) => (
                <option key={year} value={year}>
                  {t('register.year')} {year}
                </option>
              ))}
            </Select>
          </Field>
        </div>

        <Field label={t('register.password')} htmlFor="password">
          <PasswordInput
            id="password"
            autoComplete="new-password"
            required
            minLength={10}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </Field>
        <p className="-mt-2 text-xs text-ink-muted">{t('register.passwordHint')}</p>

        <Field label={t('register.passwordConfirm')} htmlFor="passwordConfirm">
          <PasswordInput
            id="passwordConfirm"
            autoComplete="new-password"
            required
            value={passwordConfirm}
            onChange={(e) => setPasswordConfirm(e.target.value)}
          />
        </Field>

        <Recaptcha onToken={onCaptchaToken} />

        {error ? <p className="text-sm font-medium text-danger">{error}</p> : null}

        {/* Stated at sign-up, not discovered later: the one-device rule is part of what
            someone is agreeing to, and finding out by being logged out is not "told". */}
        <p className="flex items-start gap-2 text-xs text-ink-muted">
          <span aria-hidden>ℹ️</span>
          <span>{t('register.singleDeviceNotice')}</span>
        </p>

        <SubmitButton loading={loading}>{t('register.submit')}</SubmitButton>
      </form>

      <p className="mt-6 text-center text-sm text-ink-muted">
        {t('register.hasAccount')}{' '}
        <Link to="/login" className="font-semibold text-accent hover:underline">
          {t('register.loginLink')}
        </Link>
      </p>
    </AuthLayout>
  );
}
