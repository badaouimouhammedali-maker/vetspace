import { useEffect, useRef, useState } from 'react';

/**
 * Closes on click-outside *and* Escape.
 *
 * <p>Escape matters: without it, a keyboard user who opens the menu has no way to
 * dismiss it without activating one of its items.
 *
 * <p>Lives in its own module rather than beside the layout components so React Fast
 * Refresh keeps working — a file that exports both components and plain functions
 * loses it.
 */
export function useDismissable(onDismiss: () => void) {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const onPointer = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        onDismiss();
      }
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onDismiss();
      }
    };
    document.addEventListener('mousedown', onPointer);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onPointer);
      document.removeEventListener('keydown', onKey);
    };
  }, [onDismiss]);

  return ref;
}

/** Open/close state for a dropdown, wired to the dismiss behaviour above. */
export function useDropdown() {
  const [open, setOpen] = useState(false);
  const ref = useDismissable(() => setOpen(false));
  return { open, setOpen, ref };
}
