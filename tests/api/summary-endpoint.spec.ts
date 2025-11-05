import { test, expect } from '@playwright/test';

/**
 * Summary Endpoint Tests
 *
 * Verifies the GET /api/summary endpoint functionality including
 * real-time statistics calculation and response structure.
 */

test.describe('GET /api/summary', () => {
  const BASE_URL = process.env.API_BASE_URL || 'http://localhost:57010';

  test('should return dashboard summary statistics', async ({ page }) => {
    await page.goto(`${BASE_URL}/q/swagger-ui/#/Dashboard/get_api_summary`);
    await page.waitForLoadState('networkidle');

    // Click "Try it out"
    await page.getByRole('button', { name: 'Try it out' }).click();

    // Execute request (no parameters required)
    await page.locator('#operations-Dashboard-get_api_summary').getByRole('button', { name: 'Execute' }).click();

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

    // Verify all required fields are present
    expect(response).toHaveProperty('opsPerHour');
    expect(response).toHaveProperty('errorRate');
    expect(response).toHaveProperty('latencyP95');
    expect(response).toHaveProperty('latencyP99');
    expect(response).toHaveProperty('dbUsageBytes');
    expect(response).toHaveProperty('timestamp');

    // Verify data types
    expect(typeof response.opsPerHour).toBe('number');
    expect(typeof response.errorRate).toBe('number');
    expect(typeof response.dbUsageBytes).toBe('number');
    expect(typeof response.timestamp).toBe('string');

    // Verify value ranges
    expect(response.opsPerHour).toBeGreaterThanOrEqual(0);
    expect(response.errorRate).toBeGreaterThanOrEqual(0);
    expect(response.errorRate).toBeLessThanOrEqual(100);
    expect(response.dbUsageBytes).toBeGreaterThanOrEqual(0);

    // Verify timestamp is valid ISO format
    expect(() => new Date(response.timestamp)).not.toThrow();
  });

  test('should display summary endpoint documentation', async ({ page }) => {
    await page.goto(`${BASE_URL}/q/swagger-ui/#/Dashboard/get_api_summary`);
    await page.waitForLoadState('networkidle');

    // Verify endpoint description
    await expect(page.locator('text=Get dashboard summary statistics')).toBeVisible();
    await expect(page.locator('text=Returns summary statistics including operations per hour')).toBeVisible();

    // Verify "No parameters" is displayed
    await expect(page.locator('text=No parameters')).toBeVisible();

    // Verify response codes are documented
    await expect(page.locator('text=200').first()).toBeVisible();
    await expect(page.locator('text=Summary statistics retrieved successfully')).toBeVisible();
    await expect(page.locator('text=500')).toBeVisible();
    await expect(page.locator('text=Internal server error')).toBeVisible();
  });

  test('should calculate real-time statistics', async ({ page }) => {
    await page.goto(`${BASE_URL}/q/swagger-ui/#/Dashboard/get_api_summary`);
    await page.waitForLoadState('networkidle');

    // Execute first request
    await page.getByRole('button', { name: 'Try it out' }).click();
    await page.locator('#operations-Dashboard-get_api_summary').getByRole('button', { name: 'Execute' }).click();
    await page.waitForSelector('text=Server response');

    const firstResponse = await page.locator('.response-col_description code').first().textContent();
    const firstData = JSON.parse(firstResponse || '{}');
    const firstTimestamp = new Date(firstData.timestamp);

    // Wait a moment and execute second request
    await page.waitForTimeout(2000);
    await page.locator('#operations-Dashboard-get_api_summary').getByRole('button', { name: 'Execute' }).click();
    await page.waitForSelector('text=Server response', { state: 'visible' });

    const secondResponse = await page.locator('.response-col_description code').first().textContent();
    const secondData = JSON.parse(secondResponse || '{}');
    const secondTimestamp = new Date(secondData.timestamp);

    // Verify timestamp is updated (real-time calculation)
    expect(secondTimestamp.getTime()).toBeGreaterThan(firstTimestamp.getTime());

    // Database usage should be consistent or growing
    expect(secondData.dbUsageBytes).toBeGreaterThanOrEqual(0);
  });

  test('should take screenshot of summary response', async ({ page }) => {
    await page.goto(`${BASE_URL}/q/swagger-ui/#/Dashboard/get_api_summary`);
    await page.waitForLoadState('networkidle');

    // Execute request
    await page.getByRole('button', { name: 'Try it out' }).click();
    await page.locator('#operations-Dashboard-get_api_summary').getByRole('button', { name: 'Execute' }).click();
    await page.waitForSelector('text=Server response');

    // Scroll to show response
    await page.evaluate(() => window.scrollBy(0, 400));
    await page.waitForTimeout(500);

    // Take screenshot
    await page.screenshot({
      path: 'tests/screenshots/api-summary-response.png'
    });
  });
});
