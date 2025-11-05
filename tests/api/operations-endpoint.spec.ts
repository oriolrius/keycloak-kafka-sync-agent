import { test, expect } from '@playwright/test';

/**
 * Operations Endpoint Tests
 *
 * Verifies the GET /api/operations endpoint functionality including
 * pagination, filtering, and response structure.
 */

test.describe('GET /api/operations', () => {
  const BASE_URL = process.env.API_BASE_URL || 'http://localhost:57010';

  test('should return paginated operations with default parameters', async ({ page }) => {
    await page.goto(`${BASE_URL}/q/swagger-ui/#/Dashboard/get_api_operations`);
    await page.waitForLoadState('networkidle');

    // Click "Try it out"
    await page.getByRole('button', { name: 'Try it out' }).click();

    // Clear all filter fields to test with defaults only
    await page.getByRole('textbox', { name: 'endTime' }).fill('');
    await page.getByRole('textbox', { name: 'startTime' }).fill('');
    await page.getByRole('textbox', { name: 'opType' }).fill('');
    await page.getByRole('textbox', { name: 'principal' }).fill('');
    await page.getByRole('textbox', { name: 'result' }).fill('');

    // Execute request
    await page.getByRole('button', { name: 'Execute' }).click();

    // Wait for response
    await page.waitForSelector('text=Server response');

    // Verify successful response code
    await expect(page.locator('text=200').first()).toBeVisible();

    // Verify response body structure
    const responseBody = page.locator('.response-col_description code').first();
    await expect(responseBody).toBeVisible();

    // Get response text and verify JSON structure
    const responseText = await responseBody.textContent();
    const response = JSON.parse(responseText || '{}');

    // Verify response structure
    expect(response).toHaveProperty('operations');
    expect(response).toHaveProperty('page');
    expect(response).toHaveProperty('pageSize');
    expect(response).toHaveProperty('total');
    expect(response).toHaveProperty('totalPages');

    // Verify pagination defaults
    expect(response.page).toBe(0);
    expect(response.pageSize).toBe(20);

    // Verify operations array structure
    if (response.operations.length > 0) {
      const operation = response.operations[0];
      expect(operation).toHaveProperty('id');
      expect(operation).toHaveProperty('correlationId');
      expect(operation).toHaveProperty('occurredAt');
      expect(operation).toHaveProperty('realm');
      expect(operation).toHaveProperty('clusterId');
      expect(operation).toHaveProperty('principal');
      expect(operation).toHaveProperty('opType');
      expect(operation).toHaveProperty('result');
      expect(operation).toHaveProperty('durationMs');
    }
  });

  test('should display operations endpoint documentation', async ({ page }) => {
    await page.goto(`${BASE_URL}/q/swagger-ui/#/Dashboard/get_api_operations`);
    await page.waitForLoadState('networkidle');

    // Verify endpoint is expanded
    await expect(page.locator('text=Get paginated operations')).toBeVisible();

    // Verify description
    await expect(page.locator('text=Returns paginated list of sync operations')).toBeVisible();

    // Verify all query parameters are documented
    const expectedParams = ['page', 'pageSize', 'startTime', 'endTime', 'principal', 'opType', 'result'];
    for (const param of expectedParams) {
      await expect(page.locator(`text=${param}`).first()).toBeVisible();
    }
  });

  test('should accept and validate opType filter', async ({ page }) => {
    await page.goto(`${BASE_URL}/q/swagger-ui/#/Dashboard/get_api_operations`);
    await page.waitForLoadState('networkidle');

    // Click "Try it out"
    await page.getByRole('button', { name: 'Try it out' }).click();

    // Set opType filter
    await page.getByRole('textbox', { name: 'opType' }).fill('SCRAM_UPSERT');

    // Execute request
    await page.getByRole('button', { name: 'Execute' }).click();

    // Wait for response
    await page.waitForSelector('text=Server response');

    // Verify successful response
    await expect(page.locator('text=200').first()).toBeVisible();
  });

  test('should accept and validate result filter', async ({ page }) => {
    await page.goto(`${BASE_URL}/q/swagger-ui/#/Dashboard/get_api_operations`);
    await page.waitForLoadState('networkidle');

    // Click "Try it out"
    await page.getByRole('button', { name: 'Try it out' }).click();

    // Clear other filters
    await page.getByRole('textbox', { name: 'opType' }).fill('');

    // Set result filter
    await page.getByRole('textbox', { name: 'result' }).fill('SUCCESS');

    // Execute request
    await page.getByRole('button', { name: 'Execute' }).click();

    // Wait for response
    await page.waitForSelector('text=Server response');

    // Verify successful response
    await expect(page.locator('text=200').first()).toBeVisible();
  });

  test('should take screenshot of operations response', async ({ page }) => {
    await page.goto(`${BASE_URL}/q/swagger-ui/#/Dashboard/get_api_operations`);
    await page.waitForLoadState('networkidle');

    // Execute with defaults
    await page.getByRole('button', { name: 'Try it out' }).click();
    await page.getByRole('textbox', { name: 'endTime' }).fill('');
    await page.getByRole('textbox', { name: 'startTime' }).fill('');
    await page.getByRole('textbox', { name: 'opType' }).fill('');
    await page.getByRole('textbox', { name: 'principal' }).fill('');
    await page.getByRole('textbox', { name: 'result' }).fill('');
    await page.getByRole('button', { name: 'Execute' }).click();

    // Wait for response
    await page.waitForSelector('text=Server response');

    // Take screenshot
    await page.screenshot({
      path: 'tests/screenshots/api-operations-response.png',
      fullPage: true
    });
  });
});
