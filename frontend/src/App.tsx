import { useEffect } from 'react';
import { Navigate, Route, Routes, useNavigate } from 'react-router-dom';
import { ProtectedRoute } from './auth/guards';
import { AppLayout } from './components/layout/AppLayout';
import { useToast } from './components/ToastProvider';
import { t } from './i18n/fr';
import { API_EVENTS } from './lib/api';
import { AbonnementPage } from './pages/app/AbonnementPage';
import { DashboardPage } from './pages/app/DashboardPage';
import { LabelsPage } from './pages/app/LabelsPage';
import { MindmapsPage } from './pages/app/MindmapsPage';
import { NotesPage } from './pages/app/NotesPage';
import { NotificationsPage } from './pages/app/NotificationsPage';
import { PlayPage } from './pages/app/PlayPage';
import { ProfilePage } from './pages/app/ProfilePage';
import { SessionsPage } from './pages/app/SessionsPage';
import { SignalsPage } from './pages/app/SignalsPage';
import { StatsPage } from './pages/app/StatsPage';
import { SupportPage } from './pages/app/SupportPage';
import { ForgotPasswordPage } from './pages/auth/ForgotPasswordPage';
import { LoginPage } from './pages/auth/LoginPage';
import { RegisterPage } from './pages/auth/RegisterPage';
import { ResetPasswordPage } from './pages/auth/ResetPasswordPage';

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
    window.addEventListener(API_EVENTS.subscriptionRequired, onSubscriptionRequired);
    window.addEventListener(API_EVENTS.sessionExpired, onSessionExpired);
    return () => {
      window.removeEventListener(API_EVENTS.subscriptionRequired, onSubscriptionRequired);
      window.removeEventListener(API_EVENTS.sessionExpired, onSessionExpired);
    };
  }, [navigate, toast]);

  return null;
}

export function App() {
  return (
    <>
      <ApiEventBridge />
      <Routes>
        <Route path="/" element={<Navigate to="/app" replace />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />

        {/* Écran de jeu plein écran, hors gabarit */}
        <Route
          path="/app/session/:id"
          element={
            <ProtectedRoute>
              <PlayPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/app"
          element={
            <ProtectedRoute>
              <AppLayout />
            </ProtectedRoute>
          }
        >
          <Route index element={<DashboardPage />} />
          <Route path="statistiques" element={<StatsPage />} />
          <Route path="mindmaps" element={<MindmapsPage />} />
          <Route path="sessions/entrainement" element={<SessionsPage type="ENTRAINEMENT" />} />
          <Route path="sessions/examens" element={<SessionsPage type="EXAMEN" />} />
          <Route path="labels" element={<LabelsPage />} />
          <Route path="notes" element={<NotesPage />} />
          <Route path="signals" element={<SignalsPage />} />
          <Route path="notifications" element={<NotificationsPage />} />
          <Route path="abonnement" element={<AbonnementPage />} />
          <Route path="support" element={<SupportPage />} />
          <Route path="profil" element={<ProfilePage />} />
        </Route>
        <Route path="*" element={<Navigate to="/app" replace />} />
      </Routes>
    </>
  );
}
