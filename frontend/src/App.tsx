import { Suspense, lazy, useEffect } from 'react';
import { Route, Routes, useNavigate } from 'react-router-dom';
import { AdminRoute, ProtectedRoute } from './auth/guards';
import { ErrorBoundary } from './components/ErrorBoundary';
import { NotFoundPage } from './pages/NotFoundPage';
import { useToast } from './components/ToastProvider';
import { t } from './i18n/fr';
import { API_EVENTS, SUPERSEDED_FLAG } from './lib/api';

// Route-level code splitting: each page group is a lazy chunk, so the initial
// download is just the shell + landing. Named exports are adapted to default.
const named = <T,>(p: Promise<Record<string, T>>, key: string) => p.then((m) => ({ default: m[key]! }));

// Landing (public)
const LandingPage = lazy(() => named(import('./pages/landing/LandingPage'), 'LandingPage'));

// Auth
const LoginPage = lazy(() => named(import('./pages/auth/LoginPage'), 'LoginPage'));
const RegisterPage = lazy(() => named(import('./pages/auth/RegisterPage'), 'RegisterPage'));
const ForgotPasswordPage = lazy(() => named(import('./pages/auth/ForgotPasswordPage'), 'ForgotPasswordPage'));
const ResetPasswordPage = lazy(() => named(import('./pages/auth/ResetPasswordPage'), 'ResetPasswordPage'));
const VerifyEmailPage = lazy(() => named(import('./pages/auth/VerifyEmailPage'), 'VerifyEmailPage'));

// Student app
const AppLayout = lazy(() => named(import('./components/layout/AppLayout'), 'AppLayout'));
const DashboardPage = lazy(() => named(import('./pages/app/DashboardPage'), 'DashboardPage'));
const StatsPage = lazy(() => named(import('./pages/app/StatsPage'), 'StatsPage'));
const MindmapsPage = lazy(() => named(import('./pages/app/MindmapsPage'), 'MindmapsPage'));
const CoursPage = lazy(() => named(import('./pages/app/CoursPage'), 'CoursPage'));
const SuiviPage = lazy(() => named(import('./pages/app/SuiviPage'), 'SuiviPage'));
const SessionsPage = lazy(() => named(import('./pages/app/SessionsPage'), 'SessionsPage'));
const LabelsPage = lazy(() => named(import('./pages/app/LabelsPage'), 'LabelsPage'));
const NotesPage = lazy(() => named(import('./pages/app/NotesPage'), 'NotesPage'));
const SignalsPage = lazy(() => named(import('./pages/app/SignalsPage'), 'SignalsPage'));
const NotificationsPage = lazy(() => named(import('./pages/app/NotificationsPage'), 'NotificationsPage'));
const AbonnementPage = lazy(() => named(import('./pages/app/AbonnementPage'), 'AbonnementPage'));
const SupportPage = lazy(() => named(import('./pages/app/SupportPage'), 'SupportPage'));
const ProfilePage = lazy(() => named(import('./pages/app/ProfilePage'), 'ProfilePage'));
const PlayPage = lazy(() => named(import('./pages/app/PlayPage'), 'PlayPage'));

// Admin console
const AdminLayout = lazy(() => named(import('./components/layout/AdminLayout'), 'AdminLayout'));
const OverviewPage = lazy(() => named(import('./pages/admin/OverviewPage'), 'OverviewPage'));
const EcolesPage = lazy(() => named(import('./pages/admin/EcolesPage'), 'EcolesPage'));
const QuestionsPage = lazy(() => named(import('./pages/admin/QuestionsPage'), 'QuestionsPage'));
const SourcesPage = lazy(() => named(import('./pages/admin/SourcesPage'), 'SourcesPage'));
const AdminMindmapsPage = lazy(() => named(import('./pages/admin/AdminMindmapsPage'), 'AdminMindmapsPage'));
const RessourcesPage = lazy(() => named(import('./pages/admin/RessourcesPage'), 'RessourcesPage'));
const PacksPage = lazy(() => named(import('./pages/admin/PacksPage'), 'PacksPage'));
const AbonnesPage = lazy(() => named(import('./pages/admin/AbonnesPage'), 'AbonnesPage'));
const SignalementsPage = lazy(() => named(import('./pages/admin/SignalementsPage'), 'SignalementsPage'));
const AdminNotificationsPage = lazy(() =>
  named(import('./pages/admin/AdminNotificationsPage'), 'AdminNotificationsPage'),
);
const AdminSupportPage = lazy(() => named(import('./pages/admin/AdminSupportPage'), 'AdminSupportPage'));

/** Réactions globales aux événements de l'intercepteur API. */
function ApiEventBridge() {
  const navigate = useNavigate();
  const { toast } = useToast();

  useEffect(() => {
    const onSubscriptionRequired = () => {
      toast('info', t('app.subscriptionRequired'));
      navigate('/app/abonnement');
    };
    const onSessionExpired = () => {
      toast('error', t('api.sessionExpired'));
      navigate('/login');
    };
    /**
     * Deliberately NOT a toast: a toast disappears, and the student needs to still be
     * reading this when they decide whether to sign in again or change their password.
     * The flag is carried in router state so the login screen renders it in place.
     */
    const onSessionSuperseded = () => {
      navigate('/login', { state: { [SUPERSEDED_FLAG]: true } });
    };
    window.addEventListener(API_EVENTS.subscriptionRequired, onSubscriptionRequired);
    window.addEventListener(API_EVENTS.sessionExpired, onSessionExpired);
    window.addEventListener(API_EVENTS.sessionSuperseded, onSessionSuperseded);
    return () => {
      window.removeEventListener(API_EVENTS.subscriptionRequired, onSubscriptionRequired);
      window.removeEventListener(API_EVENTS.sessionExpired, onSessionExpired);
      window.removeEventListener(API_EVENTS.sessionSuperseded, onSessionSuperseded);
    };
  }, [navigate, toast]);

  return null;
}

function PageLoader() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-canvas">
      <div
        className="h-10 w-10 animate-spin rounded-full border-4 border-accent border-t-transparent"
        role="status"
        aria-label={t('common.loading')}
      />
    </div>
  );
}

/** Dev-only helper for /__boundary-check. */
function ThrowsOnRender(): JSX.Element {
  throw new Error('Deliberate error from /__boundary-check');
}

export function App() {
  return (
    <>
      <ApiEventBridge />
      {/*
        Outermost boundary: catches anything the per-group boundaries miss — a failure
        in the router itself, or in a chunk that fails while Suspense is resolving.
      */}
      <ErrorBoundary scope="router">
        <Suspense fallback={<PageLoader />}>
          <Routes>
          <Route
            path="/"
            element={
              <ErrorBoundary scope="landing">
                <LandingPage />
              </ErrorBoundary>
            }
          />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />
          <Route path="/verify-email" element={<VerifyEmailPage />} />

          {/*
            Dev-only: renders a component that throws, so the ErrorBoundary can be seen
            and checked by hand. import.meta.env.DEV is statically false in a production
            build, so Rollup drops this branch entirely — the route does not exist in the
            shipped bundle.
          */}
          {import.meta.env.DEV ? (
            <Route
              path="/__boundary-check"
              element={
                <ErrorBoundary scope="boundary-check">
                  <ThrowsOnRender />
                </ErrorBoundary>
              }
            />
          ) : null}

          {/* Écran de jeu plein écran, hors gabarit */}
          <Route
            path="/app/session/:id"
            element={
              <ProtectedRoute>
                <ErrorBoundary scope="player">
                  <PlayPage />
                </ErrorBoundary>
              </ProtectedRoute>
            }
          />

          <Route
            path="/app"
            element={
              <ProtectedRoute>
                <ErrorBoundary scope="app">
                  <AppLayout />
                </ErrorBoundary>
              </ProtectedRoute>
            }
          >
            <Route index element={<DashboardPage />} />
            <Route path="statistiques" element={<StatsPage />} />
            <Route path="mindmaps" element={<MindmapsPage />} />
            <Route path="cours" element={<CoursPage />} />
            <Route path="suivi" element={<SuiviPage />} />
            <Route path="sessions/entrainement" element={<SessionsPage type="ENTRAINEMENT" />} />
            <Route path="sessions/examens" element={<SessionsPage type="EXAMEN" />} />
            <Route path="labels" element={<LabelsPage />} />
            <Route path="notes" element={<NotesPage />} />
            <Route path="signals" element={<SignalsPage />} />
            <Route path="notifications" element={<NotificationsPage />} />
            <Route path="abonnement" element={<AbonnementPage />} />
            <Route path="support" element={<SupportPage />} />
            <Route path="profil" element={<ProfilePage />} />
            {/* Unknown /app/* path: a real 404 inside the shell, not a bounce to "/". */}
            <Route path="*" element={<NotFoundPage />} />
          </Route>

          <Route
            path="/admin"
            element={
              <AdminRoute>
                <ErrorBoundary scope="admin">
                  <AdminLayout />
                </ErrorBoundary>
              </AdminRoute>
            }
          >
            <Route index element={<OverviewPage />} />
            <Route path="ecoles" element={<EcolesPage />} />
            <Route path="questions" element={<QuestionsPage />} />
            <Route path="sources" element={<SourcesPage />} />
            <Route path="mindmaps" element={<AdminMindmapsPage />} />
            <Route path="ressources" element={<RessourcesPage />} />
            <Route path="packs" element={<PacksPage />} />
            <Route path="abonnes" element={<AbonnesPage />} />
            <Route path="signalements" element={<SignalementsPage />} />
            <Route path="notifications" element={<AdminNotificationsPage />} />
            <Route path="support" element={<AdminSupportPage />} />
            <Route path="*" element={<NotFoundPage />} />
          </Route>

          {/*
            A silent redirect to "/" made every typo look like an intentional landing on
            the marketing page — and once disguised a mistyped URL as a routing bug.
          */}
          <Route path="*" element={<NotFoundPage />} />
          </Routes>
        </Suspense>
      </ErrorBoundary>
    </>
  );
}
