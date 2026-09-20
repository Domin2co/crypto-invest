import { expect, test } from '@playwright/test'

test('shows the paper trading dashboard', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
  await expect(page.getByLabel('Trading mode: paper')).toHaveText('Paper trading')
})
