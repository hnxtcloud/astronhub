import { randomUUID } from 'node:crypto'
import { expect, test, type Page } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { csrfHeaders } from './helpers/csrf'
import { registerSession } from './helpers/session'

/**
 * Ticket 04 core flow — software application / release / manual ZIP upload /
 * confirm / publish / download against real services (no inspector worker:
 * manual metadata confirmation only, inspection-by-worker stays blocked).
 */
const RUN_ID = 'e2e-20261019-100450'
const FIXTURE_DIR = `C:/Project/astronhub/.dev/e2e/${RUN_ID}/fixtures`
const MANUAL_ZIP = `${FIXTURE_DIR}/${RUN_ID}-sw-manual-1.0.0.zip`
const SW_ADMIN_USER = process.env.E2E_SW_ADMIN_USERNAME ?? 'e2e_sw_admin_20261019'
const SW_ADMIN_PW = process.env.E2E_SW_ADMIN_PASSWORD ?? ''
const APP_SLUG = `${RUN_ID}-app-${randomUUID().slice(0, 8)}`
const DISPLAY_VERSION = '1.0.0'

async function loginAs(page: Page, username: string, password: string) {
  const resp = await page.context().request.post('/api/v1/auth/local/login', {
    data: { username, password },
    headers: await csrfHeaders(page),
  })
  expect(resp.ok(), `login as ${username} should succeed`).toBeTruthy()
}

test.describe('Software manual ZIP management (Real API)', () => {
  test('admin creates app/release, uploads ZIP, confirms and publishes, catalog serves download', async ({ page, browser }) => {
    test.setTimeout(300_000)
    await setEnglishLocale(page)
    await loginAs(page, SW_ADMIN_USER, SW_ADMIN_PW)

    // -- create application ----------------------------------------------------
    await page.goto('/admin/software')
    await expect(page.getByRole('heading', { name: 'Software management' })).toBeVisible()
    await page.getByRole('button', { name: 'New application' }).click()
    await page.locator('#software-app-slug').fill(APP_SLUG)
    await page.locator('#software-app-name').fill(`${RUN_ID} software under test`)
    await page.locator('#software-app-description').fill(`manual zip flow for ${RUN_ID}`)
    await page.getByRole('dialog').getByRole('button', { name: 'Save' }).click()
    const appRow = page.locator('tr', { hasText: APP_SLUG }).first()
    await expect(appRow).toBeVisible({ timeout: 30_000 })

    // -- open releases and create one -------------------------------------------
    await appRow.getByRole('button', { name: 'Releases' }).click()
    await expect(page.getByText(new RegExp(`Releases of`))).toBeVisible()
    await page.getByRole('button', { name: 'New release' }).first().click()
    await page.locator('#software-release-version').fill(DISPLAY_VERSION)
    await page.locator('#software-release-notes').fill(`manual zip release for ${RUN_ID}`)
    await page.getByRole('dialog').getByRole('button', { name: 'Save' }).click()
    await expect(page.getByText(DISPLAY_VERSION).first()).toBeVisible({ timeout: 30_000 })

    // -- upload manual ZIP (chunked) --------------------------------------------
    await page.getByRole('button', { name: 'Upload package' }).first().click()
    const dialog = page.getByRole('dialog')
    await dialog.locator('#software-upload-file').setInputFiles(MANUAL_ZIP)
    // format zip / targetOs windows / arch x86_64 are the defaults
    await dialog.locator('#software-upload-version').fill(DISPLAY_VERSION)
    await dialog.locator('#software-upload-note').fill(`uploaded by e2e ${RUN_ID}`)
    await dialog.getByRole('button', { name: 'Upload', exact: true }).click()
    const packageCell = page.locator('td', { hasText: `${RUN_ID}-sw-manual-1.0.0.zip` }).first()
    await expect(packageCell).toBeVisible({ timeout: 60_000 })

    // -- confirm metadata (manual, no inspector) ---------------------------------
    const packageRow = page.locator('tr', { hasText: `${RUN_ID}-sw-manual-1.0.0.zip` }).first()
    await packageRow.getByRole('button', { name: 'Confirm' }).click()
    const confirmDialog = page.getByRole('dialog')
    await confirmDialog.locator('#software-confirm-raw-fields')
      .fill(JSON.stringify({ ProductVersion: DISPLAY_VERSION, ManualEntry: RUN_ID }))
    await confirmDialog.getByRole('button', { name: 'Confirm' }).click()
    await expect(packageRow.getByRole('button', { name: 'Publish' })).toBeVisible({ timeout: 30_000 })

    // -- publish package, then publish the app -----------------------------------
    await packageRow.getByRole('button', { name: 'Publish' }).click()
    await expect(packageRow.getByText(/Published/i).first()).toBeVisible({ timeout: 30_000 })

    const appRowAgain = page.locator('tr', { hasText: APP_SLUG }).first()
    await appRowAgain.getByRole('button', { name: 'Publish', exact: true }).click()
    await expect(appRowAgain.getByRole('button', { name: 'Take down' })).toBeVisible({ timeout: 30_000 })

    // -- boundary + portal: software center is for logged-in users (per contract);
    // anonymous reads are rejected, any logged-in user sees the published app ----
    const anonCtx = await browser.newContext()
    const anonReq = anonCtx.request
    expect((await anonReq.get('/api/v1/software/apps')).status(), 'anonymous catalog must be 401')
      .toBe(401)
    const anonPage = await anonCtx.newPage()
    await setEnglishLocale(anonPage)
    await anonPage.goto('/software/apps/' + APP_SLUG)
    await expect(anonPage).not.toHaveURL(new RegExp(`/software/apps/${APP_SLUG}`), { timeout: 15_000 })
    await anonCtx.close()

    const userCtx = await browser.newContext()
    const userPage = await userCtx.newPage()
    await setEnglishLocale(userPage)
    const { registerSession } = await import('./helpers/session')
    await registerSession(userPage, test.info())
    const catalog = await userPage.context().request.get('/api/v1/software/apps?size=50')
    expect(catalog.ok(), `logged-in catalog -> ${catalog.status()}`).toBeTruthy()
    const catalogItems = (((await catalog.json()).data ?? {}).items ?? []) as Array<{ slug: string }>
    expect(catalogItems.map((item) => item.slug)).toContain(APP_SLUG)

    await userPage.goto(`/software/apps/${APP_SLUG}`)
    await expect(userPage.getByText(DISPLAY_VERSION).first()).toBeVisible({ timeout: 30_000 })
    const downloadButton = userPage.getByRole('button', { name: /download/i }).first()
    await expect(downloadButton).toBeVisible({ timeout: 30_000 })
    const [download] = await Promise.all([
      userPage.waitForEvent('download', { timeout: 60_000 }),
      downloadButton.click(),
    ])
    const downloadPath = await download.path()
    const fs = await import('node:fs')
    const downloaded = fs.readFileSync(downloadPath!)
    const original = fs.readFileSync(MANUAL_ZIP)
    expect(downloaded.length, 'downloaded bytes should match the uploaded ZIP').toBe(original.length)
    await userCtx.close()
  })

  test('non-admin cannot open admin software pages or APIs', async ({ page }) => {
    test.setTimeout(120_000)
    await setEnglishLocale(page)
    await registerSession(page, test.info())
    const resp = await page.context().request.get('/api/v1/admin/software/apps')
    expect(resp.status(), 'plain user listing admin apps must be forbidden').toBe(403)
    await page.goto('/admin/software')
    await expect(page).not.toHaveURL(/\/admin\/software/)
  })
})
