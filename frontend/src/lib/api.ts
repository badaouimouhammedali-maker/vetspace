import axios, { AxiosError, type AxiosRequestConfig } from 'axios';
import type { z } from 'zod';
import { apiErrorSchema } from './schemas';

/**
 * Client HTTP unique de l'application.
 * - Le jeton d'accès vit UNIQUEMENT en mémoire (jamais dans localStorage).
 * - Le cookie httpOnly de refresh part automatiquement (withCredentials).
 * - Un 401 déclenche UN refresh partagé (single-flight) puis rejoue la requête.
 * - Un 403 SUBSCRIPTION_REQUIRED est diffusé pour que l'app affiche un toast et redirige.
 */
export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '',
  withCredentials: true,
});

let accessToken: string | null = null;

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export function getAccessToken(): string | null {
  return accessToken;
}

api.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});

/** Événements globaux consommés par l'app (toast + navigation), sans couplage direct. */
export const API_EVENTS = {
  subscriptionRequired: 'vetspace:subscription-required',
  sessionExpired: 'vetspace:session-expired',
} as const;

let refreshInFlight: Promise<string | null> | null = null;

/**
 * Un seul refresh à la fois : tous les appels concurrents (401 simultanés,
 * double montage StrictMode au démarrage) partagent la même promesse — le
 * jeton de refresh est à rotation unique, deux POST parallèles déclencheraient
 * la détection de réutilisation.
 */
export async function singleFlightRefresh(): Promise<string | null> {
  refreshInFlight ??= axios
    .post<{ accessToken: string }>(
      `${import.meta.env.VITE_API_URL || ''}/api/auth/refresh`,
      undefined,
      { withCredentials: true },
    )
    .then((response) => {
      setAccessToken(response.data.accessToken);
      return response.data.accessToken;
    })
    .catch(() => {
      setAccessToken(null);
      return null;
    })
    .finally(() => {
      refreshInFlight = null;
    });
  return refreshInFlight;
}

interface RetriableConfig extends AxiosRequestConfig {
  _retried?: boolean;
}

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const config = error.config as RetriableConfig | undefined;
    const status = error.response?.status;
    const url = config?.url ?? '';

    const parsedBody = apiErrorSchema.safeParse(error.response?.data);
    if (status === 403 && parsedBody.success && parsedBody.data.error === 'SUBSCRIPTION_REQUIRED') {
      window.dispatchEvent(new CustomEvent(API_EVENTS.subscriptionRequired));
      return Promise.reject(error);
    }

    // 401 hors endpoints d'auth : tenter un refresh puis rejouer une seule fois.
    const isAuthEndpoint = url.includes('/api/auth/');
    if (status === 401 && config && !config._retried && !isAuthEndpoint) {
      const token = await singleFlightRefresh();
      if (token) {
        config._retried = true;
        return api.request(config);
      }
      window.dispatchEvent(new CustomEvent(API_EVENTS.sessionExpired));
    }
    return Promise.reject(error);
  },
);

/** Toute réponse passe par zod : une API qui dérive du contrat échoue ici, pas en profondeur dans l'UI. */
export async function apiGet<T>(url: string, schema: z.ZodType<T>): Promise<T> {
  const response = await api.get(url);
  return schema.parse(response.data);
}

export async function apiPost<T>(url: string, body: unknown, schema: z.ZodType<T>): Promise<T> {
  const response = await api.post(url, body);
  return schema.parse(response.data);
}

export async function apiPostVoid(url: string, body?: unknown): Promise<void> {
  await api.post(url, body);
}

/** Message d'erreur lisible extrait du corps d'erreur standard du backend. */
export function apiErrorMessage(error: unknown): string | null {
  if (axios.isAxiosError(error)) {
    const parsed = apiErrorSchema.safeParse(error.response?.data);
    if (parsed.success) {
      return parsed.data.message;
    }
  }
  return null;
}

export function apiErrorStatus(error: unknown): number | null {
  return axios.isAxiosError(error) ? (error.response?.status ?? null) : null;
}

/**
 * Correlation id for a failed request — the "référence" a student can quote in a
 * support message so the exact request can be found in the log.
 *
 * <p>Reads the header first and the body second. The header is set by a filter ahead of
 * the whole chain, so it survives responses that never reach a controller (401 from the
 * security chain, 429 from the rate limiter, 413 from the size filter) and bodies that
 * are not our JSON at all — a proxy's own 502 page, for instance.
 */
export function apiErrorReference(error: unknown): string | null {
  if (!axios.isAxiosError(error)) {
    return null;
  }
  const header = error.response?.headers?.['x-request-id'];
  if (typeof header === 'string' && header.length > 0) {
    return header;
  }
  const parsed = apiErrorSchema.safeParse(error.response?.data);
  return parsed.success ? (parsed.data.requestId ?? null) : null;
}
