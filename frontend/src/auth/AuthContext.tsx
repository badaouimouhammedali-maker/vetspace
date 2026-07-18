/* eslint-disable react-refresh/only-export-components -- fournisseur + hook/aides exportés ensemble, perte de HMR acceptée */
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { apiGet, apiPost, apiPostVoid, setAccessToken, singleFlightRefresh } from '../lib/api';
import {
  loginResponseSchema,
  meSchema,
  registerResponseSchema,
  type Me,
} from '../lib/schemas';

export interface RegisterInput {
  lastName: string;
  firstName: string;
  username: string;
  email: string;
  password: string;
  schoolId: string;
  studyYear: number;
  recaptchaToken: string;
}

interface AuthContextValue {
  /** undefined = démarrage en cours (refresh silencieux), null = non connecté. */
  user: Me | null | undefined;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  register: (input: RegisterInput) => Promise<void>;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/** Applique le thème personnalisé de l'utilisateur aux variables CSS globales. */
function applyTheme(user: Me | null): void {
  const root = document.documentElement;
  root.style.setProperty('--color-primary', user?.theme.primary ?? '#0f766e');
  root.style.setProperty('--color-secondary', user?.theme.secondary ?? '#12355b');
  root.style.setProperty('--color-tertiary', user?.theme.tertiary ?? '#6b7280');
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Me | null | undefined>(undefined);

  const loadMe = useCallback(async () => {
    const me = await apiGet('/api/users/me', meSchema);
    setUser(me);
    applyTheme(me);
  }, []);

  // Refresh silencieux au démarrage : le cookie httpOnly suffit pour ré-obtenir
  // un jeton d'accès, donc F5 ne déconnecte pas. Partagé (single-flight) car le
  // StrictMode de React monte l'effet deux fois en dev.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      const token = await singleFlightRefresh();
      if (cancelled) {
        return;
      }
      if (!token) {
        setUser(null);
        applyTheme(null);
        return;
      }
      try {
        await loadMe();
      } catch {
        if (!cancelled) {
          setUser(null);
          applyTheme(null);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [loadMe]);

  const login = useCallback(
    async (email: string, password: string) => {
      const response = await apiPost('/api/auth/login', { email, password }, loginResponseSchema);
      setAccessToken(response.accessToken);
      await loadMe();
    },
    [loadMe],
  );

  const logout = useCallback(async () => {
    try {
      await apiPostVoid('/api/auth/logout');
    } finally {
      setAccessToken(null);
      setUser(null);
      applyTheme(null);
    }
  }, []);

  const register = useCallback(async (input: RegisterInput) => {
    await apiPost('/api/auth/register', input, registerResponseSchema);
  }, []);

  const value = useMemo(
    () => ({ user, login, logout, register, refreshUser: loadMe }),
    [user, login, logout, register, loadMe],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return context;
}
