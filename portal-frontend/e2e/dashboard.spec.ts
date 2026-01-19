import { test, expect } from '@playwright/test';

test.describe('Dashboard', () => {
  test('should display the dashboard page', async ({ page }) => {
    await page.goto('/dashboard');

    // Check page title
    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
  });

  test('should display KPI cards', async ({ page }) => {
    await page.goto('/dashboard');

    // KPI cards should be present
    await expect(page.getByText('Total Claims')).toBeVisible();
    await expect(page.getByText('Pending')).toBeVisible();
    await expect(page.getByText('In Progress')).toBeVisible();
    await expect(page.getByText('Closed This Month')).toBeVisible();
  });

  test('should navigate to claims list from KPI card', async ({ page }) => {
    await page.goto('/dashboard');

    // Click on Total Claims card
    await page.getByText('Total Claims').click();

    // Should navigate to claims page
    await expect(page).toHaveURL(/\/claims/);
  });

  test('should display sidebar navigation', async ({ page }) => {
    await page.goto('/dashboard');

    // Sidebar should have navigation links
    await expect(page.getByRole('link', { name: /Dashboard/ })).toBeVisible();
    await expect(page.getByRole('link', { name: /Claims/ })).toBeVisible();
  });
});
