import { Component, type ErrorInfo, type ReactNode } from 'react';
import { t } from '../i18n/fr';

interface Props {
  children: ReactNode;
  /** Identifies which part of the tree failed, in the dev-only diagnostic block. */
  scope?: string;
}

interface State {
  error: Error | null;
}

/**
 * Catches render-time errors so a single broken component degrades to a branded card
 * instead of an empty page.
 *
 * <p>This exists because of a real incident: a bad vendor chunk threw during module
 * evaluation and the entire app rendered nothing at all — no message, no logo, nothing to
 * report. React unmounts the whole tree on an uncaught render error, so without a boundary
 * the failure mode is always a white page.
 *
 * <p>Error boundaries must be class components: there is no hook equivalent of
 * componentDidCatch.
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    // Keep it in the console for local debugging; no telemetry endpoint exists yet.
    console.error(`[ErrorBoundary${this.props.scope ? ` ${this.props.scope}` : ''}]`, error, info);
  }

  private handleReload = () => {
    window.location.reload();
  };

  render() {
    const { error } = this.state;
    if (!error) {
      return this.props.children;
    }

    return (
      <div
        role="alert"
        className="flex min-h-screen flex-col items-center justify-center bg-slate-50 px-6 text-center"
      >
        <div className="w-full max-w-md rounded-2xl bg-white p-8 shadow-sm">
          <p className="text-2xl font-extrabold text-brand-navy">{t('common.appName')}</p>

          <h1 className="mt-6 text-lg font-bold text-brand-navy">{t('error.title')}</h1>
          <p className="mt-2 text-sm text-brand-gray">{t('error.body')}</p>

          <button
            type="button"
            onClick={this.handleReload}
            className="mt-6 w-full rounded-lg bg-brand-green px-4 py-2.5 font-semibold text-white hover:bg-brand-green-hover"
          >
            {t('error.reload')}
          </button>

          {/*
            The message and stack are useful while developing and are noise — or an
            information leak — for a student in production, so they ship only in dev.
          */}
          {import.meta.env.DEV ? (
            <pre className="mt-6 max-h-48 overflow-auto rounded-lg bg-slate-100 p-3 text-left text-xs text-slate-700">
              {this.props.scope ? `[${this.props.scope}] ` : ''}
              {error.message}
              {error.stack ? `\n\n${error.stack}` : ''}
            </pre>
          ) : null}
        </div>
      </div>
    );
  }
}
