import { useEffect, useRef } from 'react';
import { t } from '../i18n/fr';
import { sanitizeHtml } from '../lib/sanitize';

// Les seuls hex hors du fichier de jetons, et volontairement : ces valeurs sont
// sérialisées dans le HTML des explications stocké en base, pas appliquées via CSS.
// Une variable CSS ici produirait du contenu qui change de couleur avec le thème du
// lecteur. Elles reprennent --accent, --text-primary, --danger et --warning.
const COLORS = ['#0F766E', '#17324D', '#B42318', '#B54708'];

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

  /**
   * Couleur de texte : on force `styleWithCSS` pour produire `<span style="color:…">`
   * plutôt que `<font color>` — seul le span coloré passe l'allowlist de nettoyage
   * (client DOMPurify + serveur jsoup), donc c'est la seule forme qui « survit ».
   */
  function applyColor(value: string) {
    document.execCommand('styleWithCSS', false, 'true');
    document.execCommand('foreColor', false, value);
    document.execCommand('styleWithCSS', false, 'false');
    ref.current?.focus();
    emit();
  }

  return (
    <div className="rounded-lg border border-subtle focus-within:border-accent focus-within:ring-2 focus-within:ring-accent/20">
      <div className="flex flex-wrap items-center gap-1 border-b border-subtle p-1.5">
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
        <span className="mx-1 h-5 w-px bg-ink/10" />
        {COLORS.map((color) => (
          <button
            key={color}
            type="button"
            aria-label={`${t('notes.color')} ${color}`}
            onMouseDown={(e) => e.preventDefault()}
            onClick={() => applyColor(color)}
            className="h-5 w-5 rounded-full ring-1 ring-subtle"
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
        className="min-h-[140px] px-3 py-2 text-sm text-ink outline-none [&_ol]:list-decimal [&_ol]:pl-5 [&_ul]:list-disc [&_ul]:pl-5"
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
      className="min-w-8 rounded px-2 py-1 text-sm font-semibold text-ink transition hover:bg-accent/6"
    >
      {children}
    </button>
  );
}
