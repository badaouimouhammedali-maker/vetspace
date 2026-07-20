#!/usr/bin/env node
/**
 * Refuses to ship a build that still carries a placeholder or a local address.
 *
 * The failure this prevents: `vercel.json` shipped with REPLACE-ME in the /api rewrite
 * means every API call 404s on the live site, and a localhost URL baked into the bundle
 * means the SPA quietly talks to a machine that is not there. Both look fine in CI —
 * the build succeeds either way — and only surface as a broken deployment.
 *
 * Runs after `vite build`, over vercel.json and the emitted dist/.
 */
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, dirname, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');

/** Placeholders that must never reach a deployment, wherever they appear. */
const FORBIDDEN_EVERYWHERE = [
  { pattern: /REPLACE-ME/i, why: 'unreplaced placeholder' },
  { pattern: /change-me/i, why: 'unreplaced placeholder' },
  { pattern: /your-api\.up\.railway\.app/i, why: 'example hostname from the docs' },
];

/**
 * Local addresses are only meaningful in OUR code — a baked-in VITE_API_URL pointing at a
 * dev machine. Third-party bundles legitimately contain the string: axios, for one, ships
 * `window.location.href || "http://localhost"` as an internal fallback. Flagging those would
 * block every deploy for a non-issue, so the local-address rule skips vendor chunks.
 */
const FORBIDDEN_IN_APP_CODE = [
  { pattern: /["'`]https?:\/\/localhost(:\d+)?/i, why: 'local address' },
  { pattern: /["'`]https?:\/\/127\.0\.0\.1(:\d+)?/, why: 'local address' },
];

/** Chunk names produced from node_modules by vite.config.ts manualChunks. */
const VENDOR_CHUNK = /^(vendor|react|query|router|charts|htmldeps)-/;

/**
 * Source-map files legitimately embed original paths, and the licence comments in
 * vendor bundles mention example.com-style hosts; only scan what actually ships and runs.
 */
const SCANNED_EXTENSIONS = ['.js', '.css', '.html', '.json'];

function walk(dir) {
  const out = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      out.push(...walk(full));
    } else if (SCANNED_EXTENSIONS.some((ext) => entry.endsWith(ext)) && !entry.endsWith('.map')) {
      out.push(full);
    }
  }
  return out;
}

const problems = [];

function scan(file, label, { isVendor = false } = {}) {
  let text;
  try {
    text = readFileSync(file, 'utf8');
  } catch {
    problems.push(`${label}: could not be read`);
    return;
  }
  const rules = isVendor
    ? FORBIDDEN_EVERYWHERE
    : [...FORBIDDEN_EVERYWHERE, ...FORBIDDEN_IN_APP_CODE];
  for (const { pattern, why } of rules) {
    const match = text.match(pattern);
    if (match) {
      // Show a little context so the offending line is findable, but never dump the file.
      const at = text.indexOf(match[0]);
      const snippet = text.slice(Math.max(0, at - 40), at + match[0].length + 40).replace(/\s+/g, ' ');
      problems.push(`${label}: ${why} "${match[0]}"\n      …${snippet}…`);
      break; // one finding per file is enough to fail it
    }
  }
}

scan(join(root, 'vercel.json'), 'vercel.json');

let distFiles = [];
try {
  distFiles = walk(join(root, 'dist'));
} catch {
  problems.push('dist/: not found — run the build before this check');
}
for (const file of distFiles) {
  const base = file.split('/').pop();
  scan(file, relative(root, file), { isVendor: VENDOR_CHUNK.test(base) });
}

if (problems.length > 0) {
  console.error('\n\x1b[31m✗ predeploy check failed\x1b[0m — this build must not be deployed:\n');
  problems.forEach((p, i) => console.error(`  ${i + 1}. ${p}\n`));
  console.error('  Set the API domain in vercel.json and VITE_API_URL, then rebuild.');
  console.error('  See docs/deploy.md §3.\n');
  process.exit(1);
}

console.log(`\x1b[32m✓ predeploy check passed\x1b[0m (vercel.json + ${distFiles.length} built files)`);
