import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { t } from '../i18n/fr';
import { useAuth } from './AuthContext';

function BootSpinner() {
  return (
    <div className="flex min-h-screen items-center justify-center">
      <div
        className="h-10 w-10 animate-spin rounded-full border-4 border-accent border-t-transparent"
        role="status"
        aria-label={t('common.loading')}
      />
    </div>
  );
}

/** Connecté requis ; pendant le refresh silencieux on affiche un spinner, pas une redirection. */
export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const location = useLocation();
  if (user === undefined) {
    return <BootSpinner />;
  }
  if (user === null) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }
  return <>{children}</>;
}

/** ADMIN ou TEACHER uniquement. */
export function AdminRoute({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  if (user === undefined) {
    return <BootSpinner />;
  }
  if (user === null) {
    return <Navigate to="/login" replace />;
  }
  if (user.role !== 'ADMIN' && user.role !== 'TEACHER') {
    return <Navigate to="/app" replace />;
  }
  return <>{children}</>;
}

/**
 * Route nécessitant un abonnement actif. Le gros du travail est fait par
 * l'intercepteur global (toast + redirection sur 403 SUBSCRIPTION_REQUIRED) ;
 * ce garde évite simplement d'afficher une page qu'on sait condamnée.
 * ADMIN/TEACHER sont exemptés, comme côté serveur.
 */
export function SubscribedRoute({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  if (user === undefined) {
    return <BootSpinner />;
  }
  if (user === null) {
    return <Navigate to="/login" replace />;
  }
  const staff = user.role === 'ADMIN' || user.role === 'TEACHER';
  if (!staff && user.activeSubscriptions.length === 0) {
    return <Navigate to="/app/abonnement" replace />;
  }
  return <>{children}</>;
}
