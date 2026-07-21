import { defineConfig, devices } from '@playwright/test';

// Runs against the seeded stack from `docker compose --profile e2e up`
// (frontend on :8088). Override with E2E_BASE_URL to hit a local dev server.
export default defineConfig({
  testDir: './tests',
  timeout: 90_000,
  expect: { timeout: 20_000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  // On CI also emit the HTML report the workflow uploads as an artifact —
  // with 'list' alone there was nothing on disk to upload — plus a machine-readable
  // JSON summary. The JSON exists so the workflow can assert WHICH journeys ran:
  // downloading the HTML report needs repository admin rights, so without it "the E2E
  // job is green" cannot be shown to mean "both journeys passed" by anyone auditing
  // from outside. A suite that silently stopped running one of its two tests would
  // otherwise still show a green tick.
  reporter: process.env.CI
    ? [['list'], ['html', { open: 'never' }], ['json', { outputFile: 'results.json' }]]
    : [['list']],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8088',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
