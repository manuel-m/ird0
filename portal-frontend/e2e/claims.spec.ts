import { test, expect } from '@playwright/test';

test.describe('Claims Management', () => {
  test('should display the claims list page', async ({ page }) => {
    await page.goto('/claims');

    // Check page title
    await expect(page.getByRole('heading', { name: 'Claims' })).toBeVisible();

    // New Claim button should be present
    await expect(page.getByRole('button', { name: /New Claim/ })).toBeVisible();
  });

  test('should have filters for status and type', async ({ page }) => {
    await page.goto('/claims');

    // Filter dropdowns should be present
    await expect(page.getByLabel('Status')).toBeVisible();
    await expect(page.getByLabel('Type')).toBeVisible();
  });

  test('should navigate to create claim page', async ({ page }) => {
    await page.goto('/claims');

    // Click on New Claim button
    await page.getByRole('button', { name: /New Claim/ }).click();

    // Should navigate to create page
    await expect(page).toHaveURL('/claims/new');
    await expect(page.getByRole('heading', { name: 'Create New Claim' })).toBeVisible();
  });

  test('should display claim creation form', async ({ page }) => {
    await page.goto('/claims/new');

    // Form fields should be present
    await expect(page.getByLabel('Policyholder')).toBeVisible();
    await expect(page.getByLabel('Insurer')).toBeVisible();
    await expect(page.getByLabel('Claim Type')).toBeVisible();
    await expect(page.getByLabel('Incident Date')).toBeVisible();
    await expect(page.getByLabel('Estimated Damage')).toBeVisible();
    await expect(page.getByLabel('Description')).toBeVisible();

    // Submit button should be present
    await expect(page.getByRole('button', { name: 'Create Claim' })).toBeVisible();
  });

  test('should show validation errors on empty form submission', async ({ page }) => {
    await page.goto('/claims/new');

    // Try to submit empty form - button should be disabled
    const submitButton = page.getByRole('button', { name: 'Create Claim' });
    await expect(submitButton).toBeDisabled();
  });

  test('should filter claims by status', async ({ page }) => {
    await page.goto('/claims');

    // Open status dropdown
    await page.getByLabel('Status').click();

    // Select a status
    await page.getByRole('option', { name: 'Declared' }).click();

    // URL should be updated with filter
    await expect(page).toHaveURL(/status=DECLARED/);
  });

  test('should clear filters', async ({ page }) => {
    await page.goto('/claims?status=DECLARED');

    // Click clear filters
    await page.getByRole('button', { name: /Clear Filters/ }).click();

    // URL should not have filter params
    await expect(page).toHaveURL('/claims');
  });
});

test.describe('Claim Detail', () => {
  test('should navigate back from detail page', async ({ page }) => {
    // Navigate to detail page (assuming a claim exists)
    await page.goto('/claims/test-id');

    // Back button should be present
    await expect(page.getByRole('button', { name: 'arrow_back' })).toBeVisible();
  });
});
