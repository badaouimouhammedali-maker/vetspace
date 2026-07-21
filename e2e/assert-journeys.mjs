#!/usr/bin/env node
/**
 * Asserts that BOTH end-to-end journeys actually ran and passed.
 *
 * `playwright test` exits 0 when it runs zero tests, and it exits 0 if one of two spec
 * files silently stops being picked up — a bad glob, a rename, a file excluded by a
 * config change. In both cases CI goes green while the coverage it is supposed to
 * provide has quietly disappeared. Green has to mean "the student journey and the admin
 * journey both passed", so that claim is checked here rather than assumed.
 *
 * It also makes the result auditable without repository admin rights: job logs and
 * artifacts need them, so this prints the per-journey verdict into the step summary.
 */
import { readFileSync, appendFileSync } from 'node:fs';

/** Every journey that must exist and pass. Adding one here makes CI enforce it. */
const REQUIRED = [
  { file: 'smoke.spec.ts', what: 'student journey' },
  { file: 'admin.spec.ts', what: 'admin journey' },
];

let report;
try {
  report = JSON.parse(readFileSync(new URL('./results.json', import.meta.url), 'utf8'));
} catch (err) {
  console.error(`✗ could not read e2e/results.json — did playwright run? (${err.message})`);
  process.exit(1);
}

/** Playwright nests suites; flatten to (file, title, status) triples. */
function collect(suites, out = []) {
  for (const suite of suites ?? []) {
    for (const spec of suite.specs ?? []) {
      const status = spec.tests?.[0]?.results?.at(-1)?.status ?? 'unknown';
      out.push({ file: suite.file ?? spec.file ?? '', title: spec.title, ok: spec.ok, status });
    }
    collect(suite.suites, out);
  }
  return out;
}

const specs = collect(report.suites);
const problems = [];
const lines = ['| Journey | Spec | Result |', '| --- | --- | --- |'];

for (const { file, what } of REQUIRED) {
  const matches = specs.filter((s) => s.file.endsWith(file));
  if (matches.length === 0) {
    problems.push(`${what} (${file}) did not run at all`);
    lines.push(`| ${what} | \`${file}\` | **DID NOT RUN** |`);
    continue;
  }
  for (const m of matches) {
    if (!m.ok || m.status !== 'passed') {
      problems.push(`${what} (${file}) finished as "${m.status}"`);
    }
    lines.push(`| ${what} | \`${file}\` | ${m.ok && m.status === 'passed' ? 'passed' : `**${m.status}**`} |`);
  }
}

// Anything that ran but is not on the required list still has to pass.
for (const s of specs) {
  if (!REQUIRED.some((r) => s.file.endsWith(r.file)) && (!s.ok || s.status !== 'passed')) {
    problems.push(`${s.file}: "${s.title}" finished as "${s.status}"`);
  }
}

const summary = [`### E2E journeys (${specs.length} spec${specs.length === 1 ? '' : 's'})`, '', ...lines, ''];
if (process.env.GITHUB_STEP_SUMMARY) {
  appendFileSync(process.env.GITHUB_STEP_SUMMARY, summary.join('\n') + '\n');
}
console.log(summary.join('\n'));

if (problems.length > 0) {
  console.error('\n✗ E2E journey check failed:');
  problems.forEach((p, i) => console.error(`  ${i + 1}. ${p}`));
  process.exit(1);
}
console.log(`✓ all ${REQUIRED.length} required journeys ran and passed`);
