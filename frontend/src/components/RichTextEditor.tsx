import { useEffect, useRef } from 'react';
import { t } from '../i18n/fr';
import { sanitizeHtml } from '../lib/sanitize';

const COLORS = ['#0F766E', '#12355B', '#DC2626', '#CA8A04'];

/**
 * Éditeur riche minimal basé sur contentEditable + document.execCommand.
 * La sortie passe TOUJOURS par sanitizeHtml — c'est le HTML nettoyé qui remonte,
 * jamais le contenu brut de l'éditeur.
 */
export function RichTextEditor({
  value,
  onChange,
}: {
  value: string;
  onChange: (html: string) => void;
}) {
  const ref = useRef<HTMLDivElement>(null);

  // Initialise le contenu une seule fois (évite de déplacer le curseur à chaque frappe).
  useEffect(() => {
    if (ref.current && ref.current.innerHTML !== value) {
      ref.current.innerHTML = value;
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- init au montage uniquement
  }, []);

  function emit() {
    if (ref.current) {
      onChange(sanitizeHtml(ref.current.innerHTML));
    }
  }

  function cmd(command: string, arg?: string) {
    document.execCommand(command, false, arg);
    ref.current?.focus();
    emit();
  }

  return (
    <div className="rounded-lg border border-gray-300 focus-within:border-brand-green focus-within:ring-2 focus-within:ring-brand-green/20">
      <div className="flex flex-wrap items-center gap-1 border-b border-gray-200 p-1.5">
        <ToolbarButton label={t('notes.bold')} onClick={() => cmd('bold')}>
          <b>G</b>
        </ToolbarButton>
        <ToolbarButton label={t('notes.italic')} onClick={() => cmd('italic')}>
          <i>I</i>
        </ToolbarButton>
        <ToolbarButton label={t('notes.bulletList')} onClick={() => cmd('insertUnorderedList')}>
          • —
        </ToolbarButton>
        <ToolbarButton label={t('notes.numberedList')} onClick={() => cmd('insertOrderedList')}>
          1.
        </ToolbarButton>
        <span className="mx-1 h-5 w-px bg-gray-200" />
        {COLORS.map((color) => (
          <button
            key={color}
            type="button"
            aria-label={`${t('notes.color')} ${color}`}
            onMouseDown={(e) => e.preventDefault()}
            onClick={() => cmd('foreColor', color)}
            className="h-5 w-5 rounded-full ring-1 ring-gray-200"
            style={{ backgroundColor: color }}
          />
        ))}
      </div>
      <div
        ref={ref}
        contentEditable
        role="textbox"
        aria-multiline="true"
        onInput={emit}
        onBlur={emit}
        className="min-h-[140px] px-3 py-2 text-sm text-gray-800 outline-none [&_ol]:list-decimal [&_ol]:pl-5 [&_ul]:list-disc [&_ul]:pl-5"
      />
    </div>
  );
}

function ToolbarButton({
  label,
  onClick,
  children,
}: {
  label: string;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      onMouseDown={(e) => e.preventDefault()}
      onClick={onClick}
      className="min-w-8 rounded px-2 py-1 text-sm font-semibold text-brand-navy transition hover:bg-gray-100"
    >
      {children}
    </button>
  );
}
