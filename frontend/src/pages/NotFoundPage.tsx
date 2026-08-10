import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { t } from '../i18n/fr';

/**
 * Real 404 for unknown routes.
 *
 * <p>Replaces a silent `<Navigate to="/" />`, which made every typo look like an
 * intentional landing on the marketing page — during debugging it also disguised a
 * mistyped URL as a routing bug, which cost real time to chase.
 *
 * <p>The "back" link follows who is asking: a signed-in student returns to the app,
 * everyone else to the public site.
 */
export function NotFoundPage() {
  const { user } = useAuth();
  const backTo = user ? '/app' : '/';

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-canvas px-6 text-center">
      <div className="w-full max-w-md rounded-lg bg-surface p-8 shadow-subtle">
        <p className="text-h1 text-ink">{t('common.appName')}</p>

        <p className="mt-6 text-5xl font-bold text-accent">404</p>
        <h1 className="mt-4 text-lg font-bold text-ink">{t('notFound.title')}</h1>
        <p className="mt-2 text-sm text-ink-muted">{t('notFound.body')}</p>

        <Link
          to={backTo}
          className="mt-6 inline-block w-full rounded-lg bg-accent px-4 py-2.5 font-semibold text-white hover:bg-accent-hover"
        >
          {user ? t('notFound.backToApp') : t('notFound.backToHome')}
        </Link>
      </div>
    </div>
  );
}
