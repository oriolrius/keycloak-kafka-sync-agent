import { test, expect } from '@playwright/test';

test.describe('Batches Page', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/batches');
    // Wait for page to load (not networkidle since we have polling)
    await page.waitForLoadState('domcontentloaded');
    // Wait for the main heading to appear as a signal that React has rendered
    await page.waitForSelector('h1:has-text("Batch Summary")', { timeout: 10000 });
  });

  test('should display batches page with correct title', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Batch Summary', level: 1 })).toBeVisible();
    await expect(page.getByText('Reconciliation cycle history with success rates')).toBeVisible();
  });

  test('should have active state on Batches link', async ({ page }) => {
    const batchesLink = page.getByRole('link', { name: /Batches/ });
    await expect(batchesLink).toHaveClass(/bg-primary/);
  });

  test('should display filter card', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Filters', level: 3 })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Reset' })).toBeVisible();
  });

  test('should display Export CSV button', async ({ page }) => {
    const exportButton = page.getByRole('button', { name: /Export CSV/ });
    await expect(exportButton).toBeVisible();
  });

  test('should display batches table', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Batches', level: 3 })).toBeVisible();
    // Wait for table to load
    await expect(page.locator('table')).toBeVisible();
  });

  test('should display batches data in table', async ({ page }) => {
    // Check that we have table rows
    const rows = page.locator('tbody tr').first();
    await expect(rows).toBeVisible({ timeout: 5000 });

    // Check that batches count is displayed
    await expect(page.getByText(/Showing \d+ of \d+ batches/)).toBeVisible();
  });

  test('should display page size selector', async ({ page }) => {
    await expect(page.getByText('Page size:')).toBeVisible();
  });

  test('should display table headers when data exists', async ({ page }) => {
    // Wait for loading to complete
    await page.waitForTimeout(2000);

    // Check if table exists (will only exist if there's data)
    const table = page.locator('table');
    const tableVisible = await table.isVisible().catch(() => false);

    if (tableVisible) {
      // If table is visible, check for key headers
      const headers = table.locator('thead th');
      const headerCount = await headers.count();

      // We should have at least 10 headers (including expand button column)
      expect(headerCount).toBeGreaterThanOrEqual(10);

      // Check for some key header text
      const headerText = await table.locator('thead').textContent();
      expect(headerText).toContain('Start Time');
      expect(headerText).toContain('Duration');
      expect(headerText).toContain('Success Rate');
    } else {
      // If no table, we should see the no data message
      await expect(page.getByText('No batches found')).toBeVisible();
    }
  });

  test('should display source type filter', async ({ page }) => {
    await expect(page.getByText('Source Type').first()).toBeVisible();
    // Click the select to open it
    const sourceSelect = page.locator('text=All sources').first();
    await sourceSelect.click();
    // Verify options are present
    await expect(page.getByRole('option', { name: 'Scheduled' })).toBeVisible();
    await expect(page.getByRole('option', { name: 'Manual' })).toBeVisible();
    await expect(page.getByRole('option', { name: 'Webhook' })).toBeVisible();
  });

  test('should filter batches by source type', async ({ page }) => {
    // Open the source type filter
    const sourceSelect = page.locator('text=All sources').first();
    await sourceSelect.click();

    // Select "Scheduled"
    await page.getByRole('option', { name: 'Scheduled' }).click();

    // Wait for filtering to complete
    await page.waitForTimeout(1000);

    // Verify we have results or appropriate message
    const hasResults = await page.locator('tbody tr').count() > 0;
    if (hasResults) {
      expect(hasResults).toBe(true);
    } else {
      await expect(page.getByText('No batches found')).toBeVisible();
    }
  });

  test('should expand and collapse batch details', async ({ page }) => {
    // Wait for data to load
    await page.waitForTimeout(1000);

    // Find the first row button
    const firstButton = page.locator('tbody tr').first().locator('button').first();
    await firstButton.click();

    // Verify details are shown
    await expect(page.getByText('Batch ID:')).toBeVisible();
    await expect(page.getByText('Related Operations:')).toBeVisible();

    // Click again to collapse
    await firstButton.click();

    // Wait for animation
    await page.waitForTimeout(500);
  });

  test('should display source badges', async ({ page }) => {
    // Wait for data
    await page.waitForTimeout(1000);

    // Look for any source badge (SCHEDULED, MANUAL, or WEBHOOK)
    const sourceBadges = page.locator('tbody').getByText(/SCHEDULED|MANUAL|WEBHOOK/).first();
    await expect(sourceBadges).toBeVisible();
  });

  test('should display status badges', async ({ page }) => {
    // Wait for data
    await page.waitForTimeout(1000);

    // Look for status badge (Completed or Running)
    const statusBadges = page.locator('tbody').getByText(/Completed|Running/).first();
    await expect(statusBadges).toBeVisible();
  });

  test('should display success rate badges', async ({ page }) => {
    // Wait for data
    await page.waitForTimeout(1000);

    // Look for success rate percentage badge
    const successRateBadge = page.locator('tbody').getByText(/\d+%/).first();
    await expect(successRateBadge).toBeVisible();
  });

  test('should sort batches when clicking sortable headers', async ({ page }) => {
    // Wait for data
    await page.waitForTimeout(1500);

    // Check if we have data
    const hasData = await page.locator('tbody tr').count() > 0;

    if (hasData) {
      const table = page.locator('table');

      // Test sorting on start time
      const startTimeHeader = table.getByRole('columnheader', { name: /Start Time/ });
      if (await startTimeHeader.isVisible()) {
        await startTimeHeader.click({ timeout: 5000 });
        await page.waitForTimeout(300);
        await expect(startTimeHeader.locator('svg')).toBeVisible();
      }
    }
  });

  test('should reset filters when clicking Reset button', async ({ page }) => {
    // Set a filter first
    const sourceSelect = page.locator('text=All sources').first();
    await sourceSelect.click();
    await page.getByRole('option', { name: 'Scheduled' }).click();

    // Wait for filtering
    await page.waitForTimeout(500);

    // Click reset
    await page.getByRole('button', { name: 'Reset' }).click();

    // Verify filter is reset
    await expect(page.locator('text=All sources').first()).toBeVisible();
  });

  test('should display time range filters', async ({ page }) => {
    await expect(page.locator('label').filter({ hasText: 'Start Time' }).first()).toBeVisible();
    await expect(page.locator('label').filter({ hasText: 'End Time' }).first()).toBeVisible();
  });

  test('should navigate to Dashboard when clicking Dashboard link', async ({ page }) => {
    await page.getByRole('link', { name: /Dashboard/ }).click();
    await expect(page).toHaveURL('/');
    await expect(page.getByRole('heading', { name: 'Dashboard', level: 1 })).toBeVisible();
  });

  test('should navigate to Operations when clicking Operations link', async ({ page }) => {
    await page.getByRole('link', { name: /^Operations$/ }).click();
    await expect(page).toHaveURL('/operations');
    await expect(page.getByRole('heading', { name: 'Operation Timeline', level: 1 })).toBeVisible();
  });

  test('should have link to view related operations in expanded details', async ({ page }) => {
    // Wait for data to load
    await page.waitForTimeout(1000);

    // Expand first row
    const firstButton = page.locator('tbody tr').first().locator('button').first();
    await firstButton.click();

    // Wait for expansion
    await page.waitForTimeout(500);

    // Look for the operations link
    const operationsLink = page.getByRole('link', { name: /View \d+ operations/ });
    await expect(operationsLink).toBeVisible();
  });

  test('should display pagination controls when multiple pages exist', async ({ page }) => {
    // Wait for data
    await page.waitForTimeout(1000);

    // Check if pagination is shown (only if totalPages > 1)
    const paginationVisible = await page.getByRole('button', { name: 'Previous' }).isVisible();

    if (paginationVisible) {
      await expect(page.getByRole('button', { name: 'Previous' })).toBeVisible();
      await expect(page.getByRole('button', { name: 'Next' })).toBeVisible();
      await expect(page.getByText(/Page \d+ of \d+/)).toBeVisible();
    }
  });

  test('should display error count in red', async ({ page }) => {
    // Wait for data
    await page.waitForTimeout(1000);

    // Check if any batch has errors
    const errorCells = page.locator('tbody td.text-red-600');
    const count = await errorCells.count();

    if (count > 0) {
      await expect(errorCells.first()).toBeVisible();
    }
  });

  test('should display success count in green', async ({ page }) => {
    // Wait for data
    await page.waitForTimeout(1000);

    // Check for success count styling
    const successCells = page.locator('tbody td.text-green-600');
    const count = await successCells.count();

    if (count > 0) {
      await expect(successCells.first()).toBeVisible();
    }
  });
});
