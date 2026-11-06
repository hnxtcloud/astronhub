import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'

for (const width of [1280, 390]) {
  test(`legacy Suite catalog shows retirement without a creation entry at ${width}px`, async ({ page }) => {
    await setEnglishLocale(page)
    await page.setViewportSize({ width, height: 844 })
    await page.goto('/suites')
    await expect(page.getByText('Suite creation and editing have ended.', { exact: false })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Browse plugins', exact: true })).toHaveAttribute('href', '/plugins')
    await expect(page.locator('a[href="/dashboard/suites/new"]')).toHaveCount(0)
    await expect(page.locator('header a[href="/suites"]')).toHaveCount(0)
    if (width < 768) {
      await page.getByRole('button', { name: /open navigation/i }).click()
      await expect(page.locator('nav a[href="/suites"]')).toHaveCount(0)
    }
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  })
}
