import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright Configuration for API Testing
 *
 * See https://playwright.dev/docs/test-configuration
 */
export default defineConfig({
  testDir: './api',

  /* Run tests in files in parallel */
  fullyParallel: true,

  /* Fail the build on CI if you accidentally left test.only in the source code */
  forbidOnly: !!process.env.CI,

  /* Retry on CI only */
  retries: process.env.CI ? 2 : 0,

  /* Opt out of parallel tests on CI */
  workers: process.env.CI ? 1 : undefined,

  /* Reporter to use */
  reporter: [
    ['html', { outputFolder: 'test-results/html', open: 'never' }],
    ['json', { outputFile: 'test-results/results.json' }],
    ['list']
  ],

  /* Shared settings for all the projects below */
  use: {
    /* Base URL to use in actions like `await page.goto('/')` */
    baseURL: process.env.API_BASE_URL || 'http://localhost:57010',

    /* Collect trace when retrying the failed test */
    trace: 'on-first-retry',

    /* Screenshot on failure */
    screenshot: 'only-on-failure',

    /* Video on failure */
    video: 'retain-on-failure',
  },

  /* Configure projects for major browsers */
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],

  /* Run your local dev server before starting the tests */
  webServer: {
    command: 'cd .. && ./mvnw quarkus:dev',
    url: 'http://localhost:57010/healthz',
    reuseExistingServer: true, // Always reuse existing server (likely already running)
    timeout: 120 * 1000, // 2 minutes for Quarkus to start
    cwd: '..',
  },
});
