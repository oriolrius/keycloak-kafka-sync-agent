import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright Configuration for OIDC Testing
 *
 * Tests OIDC authentication against existing Keycloak instance.
 * No webServer needed - uses docker-compose services.
 */
export default defineConfig({
  testDir: './api',
  testMatch: '**/oidc-authentication.spec.ts',

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
    ['html', { outputFolder: 'test-results/html-oidc' }],
    ['json', { outputFile: 'test-results/results-oidc.json' }],
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

  /* No webServer - expects services to be running via docker-compose */
});
