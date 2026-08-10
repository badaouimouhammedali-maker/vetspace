#!/usr/bin/env node
/**
 * Garde-fou palette.
 *
 * <p>Après la migration, toute couleur de l'interface doit passer par un jeton de
 * `src/index.css`. Rien n'empêche mécaniquement quelqu'un d'écrire `#3ba17f` ou
 * `text-gray-500` dans un composant : ça compile, ça a l'air correct en revue, et la
 * charte se délite un fichier à la fois.
 *
 * <p>Ce script échoue sur deux choses sous `src/` :
 *   1. un hexadécimal littéral ;
 *   2. une classe couleur Tailwind issue de la palette par défaut (gray, slate, teal…).
 *
 * <p>On ne supprime pas la palette par défaut de Tailwind pour autant : la garder évite
 * de casser un composant tiers qui la suppose, et évite qu'un besoin ponctuel devienne
 * une modification de configuration. On interdit son *usage* dans notre code, ce qui
 * est la même contrainte avec un rayon d'action plus petit.
 *
 * <p>Les exceptions sont nommées une par une ci-dessous, avec leur raison. Une
 * exception non utilisée fait aussi échouer le script, pour que la liste ne devienne
 * pas un cimetière.
 */
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(fileURLToPath(new URL('.', import.meta.url)), '..');
const SRC = join(ROOT, 'src');

/** Chaque entrée : le fichier autorisé à contenir un hex, et la raison. */
const ALLOWED_HEX = [
  {
    file: 'lib/theme.ts',
    reason: "DEFAULT_THEME : l'API et <input type=color> parlent hexadécimal",
  },
  {
    file: 'components/RichTextEditor.tsx',
    reason:
      "pastilles sérialisées dans le HTML des explications stocké en base : une var CSS y ferait changer le contenu enregistré avec le thème du lecteur",
  },
  {
    file: 'pages/landing/LandingPage.tsx',
    reason: '<meta name="theme-color"> — la chrome du navigateur ne sait pas lire var()',
  },
];

/** Les tests décrivent volontairement des couleurs hors charte pour éprouver la dérivation. */
const HEX_EXEMPT_SUFFIXES = ['.test.ts', '.test.tsx'];

const TAILWIND_PALETTES = [
  'slate', 'gray', 'zinc', 'neutral', 'stone', 'red', 'orange', 'amber', 'yellow',
  'lime', 'green', 'emerald', 'teal', 'cyan', 'sky', 'blue', 'indigo', 'violet',
  'purple', 'fuchsia', 'pink', 'rose',
];
const UTILITY_PREFIXES = [
  'bg', 'text', 'border', 'ring', 'from', 'to', 'via', 'divide', 'fill', 'stroke',
  'outline', 'accent', 'caret', 'placeholder', 'shadow', 'decoration',
];

const HEX = /#[0-9a-fA-F]{3,8}\b/g;
const PALETTE_CLASS = new RegExp(
  `\\b(?:${UTILITY_PREFIXES.join('|')})-(?:${TAILWIND_PALETTES.join('|')})-\\d{2,3}\\b`,
  'g',
);
// `#accueil`, `#0` dans un gradient, les refs d'issue… ne sont pas des couleurs.
const LOOKS_LIKE_COLOR = (hex) => /^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$/.test(hex);

function walk(dir) {
  return readdirSync(dir).flatMap((entry) => {
    const full = join(dir, entry);
    return statSync(full).isDirectory() ? walk(full) : [full];
  });
}

const files = walk(SRC).filter((f) => /\.(ts|tsx|css)$/.test(f));
const violations = [];
const usedExceptions = new Set();

for (const file of files) {
  const rel = relative(SRC, file).split('\\').join('/');
  // Les commentaires de bloc — JSDoc compris — citent volontiers une couleur pour
  // expliquer un jeton ou raconter ce qui a été remplacé. On les neutralise d'abord, en
  // préservant les sauts de ligne pour que les numéros restent justes.
  const source = readFileSync(file, 'utf8').replace(/\/\*[\s\S]*?\*\//g, (block) =>
    block.replace(/[^\n]/g, ' '),
  );
  const lines = source.split('\n');
  const hexException = ALLOWED_HEX.find((e) => e.file === rel);
  const hexExempt = Boolean(hexException) || HEX_EXEMPT_SUFFIXES.some((s) => rel.endsWith(s));

  lines.forEach((line, index) => {
    // Les commentaires documentent souvent la valeur d'un jeton ; c'est légitime.
    const code = line.replace(/\/\/.*$/, "");

    if (!hexExempt) {
      for (const match of code.match(HEX) ?? []) {
        if (LOOKS_LIKE_COLOR(match)) {
          violations.push(`${rel}:${index + 1}  hex littéral ${match}`);
        }
      }
    } else if (hexException && (code.match(HEX) ?? []).some(LOOKS_LIKE_COLOR)) {
      usedExceptions.add(rel);
    }

    for (const match of code.match(PALETTE_CLASS) ?? []) {
      violations.push(`${rel}:${index + 1}  classe hors charte ${match}`);
    }
  });
}

const staleExceptions = ALLOWED_HEX.filter((e) => !usedExceptions.has(e.file));

if (violations.length || staleExceptions.length) {
  if (violations.length) {
    console.error(`\n✖ ${violations.length} couleur(s) hors charte sous src/ :\n`);
    for (const v of violations) {
      console.error(`   ${v}`);
    }
    console.error(
      '\n  Utiliser un jeton de src/index.css (bg-accent, text-ink, border-subtle…).',
    );
    console.error('  Les teintes se font par opacité : bg-accent/10, jamais un nouvel hex.\n');
  }
  if (staleExceptions.length) {
    console.error('\n✖ Exception(s) devenues inutiles — les retirer de scripts/check-palette.mjs :\n');
    for (const e of staleExceptions) {
      console.error(`   ${e.file}  (${e.reason})`);
    }
    console.error('');
  }
  process.exit(1);
}

console.log(`✓ palette : ${files.length} fichiers, aucune couleur hors charte.`);
