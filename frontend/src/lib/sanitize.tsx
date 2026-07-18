/* eslint-disable react-refresh/only-export-components -- fournisseur + hook/aides exportés ensemble, perte de HMR acceptée */
import DOMPurify from 'dompurify';

/**
 * LE seul chemin par lequel du HTML atteint le DOM.
 * Même esprit d'allowlist que le sanitizer serveur (jsoup) : mise en forme
 * simple, couleurs de span, images de notre domaine média, liens https.
 */
const ALLOWED_TAGS = [
  'p',
  'br',
  'b',
  'strong',
  'i',
  'em',
  'u',
  'span',
  'ul',
  'ol',
  'li',
  'h3',
  'h4',
  'img',
  'a',
];

const ALLOWED_ATTR = ['style', 'src', 'alt', 'href', 'rel'];

DOMPurify.addHook('afterSanitizeAttributes', (node) => {
  if (node.tagName === 'A') {
    node.setAttribute('rel', 'noopener');
    const href = node.getAttribute('href') ?? '';
    if (!href.startsWith('https://')) {
      node.removeAttribute('href');
    }
  }
});

export function sanitizeHtml(html: string): string {
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS,
    ALLOWED_ATTR,
    ALLOWED_URI_REGEXP: /^https?:/i,
  });
}

/** Rendu sûr : à utiliser à la place de dangerouslySetInnerHTML brut. */
export function SafeHtml({ html, className }: { html: string; className?: string }) {
  return <div className={className} dangerouslySetInnerHTML={{ __html: sanitizeHtml(html) }} />;
}
