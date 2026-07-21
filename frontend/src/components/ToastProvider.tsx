/* eslint-disable react-refresh/only-export-components -- fournisseur + hook/aides exportés ensemble, perte de HMR acceptée */
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { t } from '../i18n/fr';

type ToastKind = 'success' | 'error' | 'info';

interface Toast {
  id: number;
  kind: ToastKind;
  message: string;
  /** Correlation id of the failed request, when there was one. */
  reference?: string | undefined;
}

interface ToastContextValue {
  /**
   * `reference` is the X-Request-Id of the request that failed. Pass it for anything
   * that came back from the API; omit it for client-side validation, which never
   * reached the server and therefore has nothing for support to look up.
   */
  toast: (kind: ToastKind, message: string, reference?: string | null) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

let nextId = 1;

const DISMISS_MS = 5000;

/** Soft fill + strong text, matching Badge — a toast is a notification, not a banner ad. */
const KIND_CLASSES: Record<ToastKind, string> = {
  success: 'bg-success/10 text-success border-success/20',
  error: 'bg-danger/10 text-danger border-danger/20',
  info: 'bg-brand-navy/5 text-brand-navy border-brand-navy/15',
};

const KIND_ICONS: Record<ToastKind, string> = {
  success: '✓',
  error: '!',
  info: 'i',
};

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.filter((item) => item.id !== id));
  }, []);

  const toast = useCallback((kind: ToastKind, message: string, reference?: string | null) => {
    setToasts((current) => [
      ...current,
      { id: nextId++, kind, message, reference: reference ?? undefined },
    ]);
  }, []);

  const value = useMemo(() => ({ toast }), [toast]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div
        className="pointer-events-none fixed bottom-4 right-4 z-50 flex flex-col gap-2"
        role="region"
        aria-label="Notifications"
      >
        {toasts.map((item) => (
          <ToastItem key={item.id} toast={item} onDismiss={dismiss} />
        ))}
      </div>
    </ToastContext.Provider>
  );
}

/**
 * One toast, owning its own dismissal timer.
 *
 * <p>The timer pauses on hover: a toast that vanishes while you are reading it — or
 * reaching for the text in it — is worse than no toast.
 */
function ToastItem({ toast, onDismiss }: { toast: Toast; onDismiss: (id: number) => void }) {
  const [paused, setPaused] = useState(false);
  // Time already served, so hovering pauses rather than restarting the countdown.
  const elapsed = useRef(0);
  const startedAt = useRef(Date.now());

  useEffect(() => {
    if (paused) {
      elapsed.current += Date.now() - startedAt.current;
      return;
    }
    startedAt.current = Date.now();
    const remaining = Math.max(0, DISMISS_MS - elapsed.current);
    const timer = setTimeout(() => onDismiss(toast.id), remaining);
    return () => clearTimeout(timer);
  }, [paused, toast.id, onDismiss]);

  return (
    <div
      role="status"
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      className={`pointer-events-auto flex animate-toast-in items-start gap-2.5 rounded-md border px-4 py-3 text-body font-medium shadow-card ${KIND_CLASSES[toast.kind]}`}
    >
      <span
        aria-hidden="true"
        className="mt-0.5 flex h-4 w-4 shrink-0 items-center justify-center rounded-full bg-current text-[10px] font-bold text-white"
      >
        {KIND_ICONS[toast.kind]}
      </span>
      <span>
        {toast.message}
        {/* The reference is the whole point of the correlation id reaching the client:
            a student can copy these characters into a support message and the exact
            request is one grep away. `select-all` so one click takes the id and not the
            sentence around it; tabular-nums so it is legible character by character
            when read aloud or retyped. */}
        {toast.reference ? (
          <span className="mt-1 block select-all font-mono text-caption font-normal opacity-70 tabular-nums">
            {t('common.errorReference')} {toast.reference}
          </span>
        ) : null}
      </span>
    </div>
  );
}

export function useToast(): ToastContextValue {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used within ToastProvider');
  }
  return context;
}
