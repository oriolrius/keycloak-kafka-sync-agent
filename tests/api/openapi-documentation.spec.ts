import { test, expect } from '@playwright/test';

/**
 * OpenAPI Documentation Tests
 *
 * Verifies that the OpenAPI/Swagger UI is accessible and properly configured
 * with all expected endpoints and schemas.
 */

test.describe('OpenAPI Documentation', () => {
  const BASE_URL = process.env.API_BASE_URL || 'http://localhost:57010';

  test('should load Swagger UI successfully', async ({ page }) => {
    await page.goto(`${BASE_URL}/q/swagger-ui`);

    // Wait for page to load
    await page.waitForLoadState('networkidle');

    // Verify page title
    await expect(page).toHaveTitle(/OpenAPI UI/);

    // Verify API title is present
    await expect(page.locator('h2')).toContainText('keycloak-kafka-sync-agent API');
    await expect(page.locator('h2')).toContainText('1.0.0-SNAPSHOT');
  });

  test('should display Dashboard endpoints section', async ({ page }) => {
    await page.goto(`${BASE_URL}/q/swagger-ui`);
    await page.waitForLoadState('networkidle');

    // Verify Dashboard section exists
    const dashboardSection = page.locator('h3:has-text("Dashboard")');
    await expect(dashboardSection).toBeVisible();

    // Verify Dashboard description
    await expect(page.locator('text=Dashboard API for sync operations monitoring')).toBeVisible();

    // Verify all Dashboard endpoints are present
    await expect(page.locator('text=/api/summary')).toBeVisible();
    await expect(page.locator('text=/api/operations')).toBeVisible();
    await expect(page.locator('text=/api/batches')).toBeVisible();
  });

  test('should display Configuration endpoints section', async ({ page }) => {
    await page.goto(`${BASE_URL}/q/swagger-ui`);
    await page.waitForLoadState('networkidle');

    // Verify Configuration section exists
    const configSection = page.locator('h3:has-text("Configuration")');
    await expect(configSection).toBeVisible();

    // Verify Configuration description
    await expect(page.locator('text=Configuration management API for retention policies')).toBeVisible();

    // Verify retention endpoints
    await expect(page.locator('text=/api/config/retention').first()).toBeVisible();
  });

  test('should display all expected schemas', async ({ page }) => {
    await page.goto(`${BASE_URL}/q/swagger-ui`);
    await page.waitForLoadState('networkidle');

    // Scroll to schemas section
    await page.locator('text=Schemas').click();

    // Verify all DTO schemas are present
    const expectedSchemas = [
      'SummaryResponse',
      'OperationResponse',
      'OperationsPageResponse',
      'BatchResponse',
      'BatchesPageResponse',
      'RetentionConfigResponse',
      'RetentionConfigUpdateRequest',
      'ErrorResponse'
    ];

    for (const schema of expectedSchemas) {
      await expect(page.locator(`text=${schema}`).first()).toBeVisible();
    }
  });

  test('should take screenshot of OpenAPI overview', async ({ page }) => {
    await page.goto(`${BASE_URL}/q/swagger-ui`);
    await page.waitForLoadState('networkidle');

    // Take full page screenshot
    await page.screenshot({
      path: 'tests/screenshots/openapi-overview.png',
      fullPage: true
    });
  });
});
