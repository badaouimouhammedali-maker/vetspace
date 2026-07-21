import { useEffect, useRef, type ReactNode } from 'react';
import { t } from '../i18n/fr';

const FOCUSABLE =
  'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), ' +
  'select:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * Accessible modal: focus trap, body scroll lock, Escape and backdrop to close.
 *
 * <p>The trap matters more than it looks. Without it, Tab walks straight out of the
 * dialog and into the page behind — which a screen-reader or keyboard user cannot see
 * is still there, so they are typing into a form they think is closed.
 */
export function Modal({
  open,
  onClose,
  title,
  children,
  wide = false,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
  wide?: boolean;
}) {
  const panelRef = useRef<HTMLDivElement>(null);
  // The close button sits first in the DOM, so "first focusable in the panel" is the
  // ✕, not the first field. Scope initial focus to the content region instead.
  const contentRef = useRef<HTMLDivElement>(null);
  // Whatever had focus before we opened, so it can be handed back on close.
  const previouslyFocused = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (!open) {
      return;
    }
    previouslyFocused.current = document.activeElement as HTMLElement | null;

    // Scroll lock. Restoring the exact previous value rather than clearing it avoids
    // stomping on an overflow another component set.
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    // Initial focus on the first field, so a keyboard user starts on the work rather
    // than on the dismiss control.
    const fields = contentRef.current?.querySelectorAll<HTMLElement>(FOCUSABLE);
    (fields?.[0] ?? panelRef.current)?.focus();

    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose();
        return;
      }
      if (e.key !== 'Tab') {
        return;
      }
      // Re-query on every Tab: the dialog's contents can change while it is open.
      const items = panelRef.current?.querySelectorAll<HTMLElement>(FOCUSABLE);
      if (!items || items.length === 0) {
        return;
      }
      const first = items[0]!;
      const last = items[items.length - 1]!;
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    };

    window.addEventListener('keydown', onKey);
    return () => {
      window.removeEventListener('keydown', onKey);
      document.body.style.overflow = previousOverflow;
      previouslyFocused.current?.focus();
    };
  }, [open, onClose]);

  if (!open) {
    return null;
  }

  return (
    <div
      className="fixed inset-0 z-40 flex items-center justify-center bg-brand-navy/40 p-4 backdrop-blur-sm"
      onClick={onClose}
    >
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        tabIndex={-1}
        // Scale + fade on enter. 150ms is long enough to read as deliberate and short
        // enough not to be in the way of someone who opens dialogs all day.
        className={`max-h-[90vh] w-full ${wide ? 'max-w-2xl' : 'max-w-md'} animate-modal-in overflow-y-auto rounded-xl bg-surface p-6 shadow-pop`}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mb-5 flex items-start justify-between gap-4">
          <h2 className="text-h2 text-brand-navy">{title}</h2>
          <button
            type="button"
            onClick={onClose}
            aria-label={t('play.close')}
            className="-mr-1 -mt-1 rounded-md px-2 py-1 text-xl leading-none text-gray-400 transition-colors duration-150 hover:bg-gray-100 hover:text-brand-navy"
          >
            ×
          </button>
        </div>
        <div ref={contentRef}>{children}</div>
      </div>
    </div>
  );
}
