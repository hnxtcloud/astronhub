import { mkdir, writeFile } from 'node:fs/promises'
import { expect as playwrightExpect, test, type Page, type TestInfo } from '@playwright/test'
import { setEnglishLocale, setUniqueClientIp } from './helpers/auth-fixtures'
import { csrfHeaders } from './helpers/csrf'

const expect = playwrightExpect.configure({ timeout: 30_000 })
test.use({ actionTimeout: 45_000 })

async function login(page: Page) {
  await setEnglishLocale(page)
  await setUniqueClientIp(page, 'unified-publication')
  const response = await page.request.post('/api/v1/auth/local/login', {
    headers: await csrfHeaders(page),
    data: { username: process.env.E2E_ADMIN_USERNAME ?? 'admin', password: process.env.E2E_ADMIN_PASSWORD ?? 'ChangeMe!2026' },
  })
  expect(response.ok(), `local publisher login (${response.status()})`).toBeTruthy()
}

async function folder(page: Page, info: TestInfo, files: Record<string, string>) {
  const directory = info.outputPath('material')
  for (const [name, content] of Object.entries(files)) {
    const file = info.outputPath('material', name)
    await mkdir(file.substring(0, Math.max(file.lastIndexOf('/'), file.lastIndexOf('\\'))), { recursive: true })
    await writeFile(file, content)
  }
  await page.locator('input[webkitdirectory]').setInputFiles(directory)
  await expect(page.getByRole('heading', { name: 'Review details' })).toBeVisible({ timeout: 90_000 })
}

test('discovery has one catalog entry and works on mobile', async ({ page }) => {
  await setEnglishLocale(page)
  await page.goto('/catalog')
  await expect(page.locator('header').getByRole('link', { name: 'Software', exact: true })).toHaveCount(0)
  await page.setViewportSize({ width: 390, height: 844 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
  await page.screenshot({ path: test.info().outputPath('catalog-mobile.png'), fullPage: true })
})

test('Skill review survives immediate reload and submits once through the unified entry', async ({ page }, info) => {
  test.setTimeout(180_000)
  await login(page)
  const name = `unified-skill-${Date.now()}`
  await page.goto('/dashboard/publish')
  await folder(page, info, { 'SKILL.md': `---\nname: ${name}\ndescription: Unified publishing browser acceptance\nversion: 1.0.0\n---\nUse this skill for a local acceptance check.\n` })
  await page.getByRole('combobox', { name: 'Namespace', exact: true }).selectOption('global')
  await page.getByRole('combobox', { name: 'Visibility', exact: true }).selectOption('PRIVATE')
  await page.reload()
  await expect(page.getByRole('combobox', { name: 'Visibility', exact: true })).toHaveValue('PRIVATE')
  await expect(page.getByText('Saved', { exact: true })).toBeVisible()
  await page.screenshot({ path: info.outputPath('skill-review-desktop.png'), fullPage: true })
  await page.getByRole('button', { name: 'Submit for publication' }).click()
  const warning = page.getByRole('checkbox')
  if (await warning.isVisible().catch(() => false)) {
    await warning.check()
    await page.getByRole('button', { name: /Continue|Retry/ }).click()
  }
  await expect(page.getByText('Submitted. Each item shows its current publication status.', { exact: true })).toBeVisible({ timeout: 90_000 })
  await page.reload()
  await expect(page.getByText('Submitted. Each item shows its current publication status.', { exact: true })).toBeVisible()
  await page.getByRole('link', { name: 'My publications', exact: true }).last().click()
  await expect(page.getByRole('heading', { name })).toBeVisible()
})

test('plugin candidate submits to review and keeps its original management link', async ({ page }, info) => {
  test.setTimeout(180_000)
  await login(page)
  const name = `unified-plugin-${Date.now()}`
  await page.goto('/dashboard/publish')
  await folder(page, info, { '.claude-plugin/plugin.json': JSON.stringify({ name, version: '1.0.0', description: 'Unified browser acceptance' }) })
  await page.getByRole('combobox', { name: 'Namespace', exact: true }).selectOption('global')
  await page.getByRole('combobox', { name: 'Visibility', exact: true }).selectOption('PRIVATE')
  await page.getByRole('button', { name: 'Review package contents' }).click()
  await page.getByRole('checkbox').first().check()
  await page.getByRole('button', { name: 'Submit for publication' }).click()
  await expect(page.getByText('Submitted. Each item shows its current publication status.', { exact: true })).toBeVisible({ timeout: 90_000 })
  await expect(page.getByText('Pending review', { exact: true })).toBeVisible()
  const result = page.getByRole('link', { name: 'View content', exact: true })
  await expect(result).toHaveAttribute('href', /dashboard\/plugins\?projectId=/)
  await result.click()
  await expect(page.getByText(name, { exact: false }).first()).toBeVisible()
})

test('software manual ZIP completes in one flow and appears in unified discovery', async ({ page }, info) => {
  test.setTimeout(180_000)
  await login(page)
  const name = `unified-software-${Date.now()}`
  await page.goto('/dashboard/publish')
  await folder(page, info, { 'README.txt': 'Local manual package for unified publishing browser acceptance.' })
  await page.getByRole('combobox', { name: 'Content type', exact: true }).selectOption('SOFTWARE')
  await page.getByLabel('Name', { exact: true }).fill(name)
  await page.getByRole('textbox', { name: 'Identifier', exact: true }).fill(name)
  await page.getByLabel('Version', { exact: true }).fill('1.0.0')
  await page.getByRole('combobox', { name: 'Operating system', exact: true }).selectOption('windows')
  await page.getByRole('combobox', { name: 'Architecture', exact: true }).selectOption('x86_64')
  await page.getByRole('button', { name: 'Submit for publication' }).click()
  await expect(page.getByText('Submitted. Each item shows its current publication status.', { exact: true })).toBeVisible({ timeout: 90_000 })
  await page.getByRole('link', { name: 'View content', exact: true }).click()
  await expect(page.getByRole('heading', { name })).toBeVisible()
  await page.goto(`/catalog?q=${name}&type=SOFTWARE`)
  await expect(page.getByRole('link', { name: new RegExp(name) }).first()).toBeVisible()
})
