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
  // with 'list' alone there was nothing on disk to upload.
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : [['list']],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8088',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
