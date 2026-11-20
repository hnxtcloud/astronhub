import { randomUUID } from 'node:crypto'
import { expect, test, type Page } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { csrfHeaders } from './helpers/csrf'
import { createFreshSession, registerSession } from './helpers/session'

/**
 * Ticket 02 core flow — HOSTED plugin lifecycle against real services.
 * Resources are prefixed with the run id; no mocks, no route interception.
 */
const RUN_ID = 'e2e-20261019-100450'
const FIXTURE_DIR = `C:/Project/astronhub/.dev/e2e/${RUN_ID}/fixtures`
const ZIP_V1 = `${FIXTURE_DIR}/${RUN_ID}-claude-demo-0.1.0.zip`
const ZIP_V2 = `${FIXTURE_DIR}/${RUN_ID}-claude-demo-0.1.1.zip`
const PLUGIN_ADMIN_USER = process.env.E2E_PLUGIN_ADMIN_USERNAME ?? 'e2e_plugin_admin_20261019'
const PLUGIN_ADMIN_PW = process.env.E2E_PLUGIN_ADMIN_PASSWORD ?? ''
const SLUG = `${RUN_ID}-plug-${randomUUID().slice(0, 8)}`
const VERSION1 = '0.1.0'
const VERSION2 = '0.1.1'

async function loginAs(page: Page, username: string, password: string) {
  const resp = await page.context().request.post('/api/v1/auth/local/login', {
    data: { username, password },
    headers: await csrfHeaders(page),
  })
  expect(resp.ok(), `login as ${username} should succeed`).toBeTruthy()
}

async function createProject(page: Page, slug: string, displayName: string) {
  const createBox = page.locator('details', { has: page.getByText('Create project') }).first()
  await createBox.locator('summary').first().click()
  const nsSelect = createBox.getByRole('combobox', { name: 'Namespace', exact: true })
  await expect
    .poll(async () => (await nsSelect.locator('option').allTextContents()).filter((text) => text.trim() && text !== 'Select a namespace').length, { timeout: 30_000 })
    .toBeGreaterThan(0)
  const optionTexts = await nsSelect.locator('option').allTextContents()
  const personal = optionTexts.find((text) => text.trim() && text !== 'Select a namespace' && text !== 'Global')
  expect(personal, `author personal namespace among [${optionTexts.join(', ')}]`).toBeTruthy()
  await nsSelect.selectOption({ label: personal })
  await createBox.getByLabel('Slug').fill(slug)
  await createBox.getByLabel('Display name').fill(displayName)
  await createBox.getByLabel('Visibility').selectOption('PRIVATE')
  await createBox.getByLabel('Description').fill(`${displayName} (${RUN_ID})`)
  await createBox.getByRole('button', { name: 'Create project' }).click()
  const item = page.locator('li', { hasText: slug }).first()
  await expect(item).toBeVisible({ timeout: 30_000 })
  await item.locator('button').click()
  const header = await page.getByText(new RegExp(`@[^\\s/]+/${slug}`)).first().textContent()
  const nsSlug = header?.match(/@([^\s/]+)\//)?.[1]
  expect(nsSlug, `namespace slug parsed from "${header}"`).toBeTruthy()
  return { nsSlug: nsSlug! }
}

async function importAndSubmit(page: Page, version: string, zipPath: string) {
  const versionForm = page.locator('details').filter({ has: page.locator('summary', { hasText: /^New version$/ }) }).last()
  if (!(await versionForm.evaluate(element => (element as HTMLDetailsElement).open))) {
    await versionForm.locator('summary').first().click()
  }
  await page.getByLabel('Version', { exact: true }).fill(version)
  await page.getByLabel('Release notes', { exact: true }).fill(`version ${version} for ${RUN_ID}`)
  await page.getByRole('button', { name: 'Create draft' }).click()
  const article = page.locator('article', { hasText: version }).first()
  await expect(article).toBeVisible({ timeout: 30_000 })
  await article.locator('summary').first().click()
  await article.locator('input[type="file"]').setInputFiles(zipPath)
  await article.getByRole('button', { name: 'Import and validate' }).click()
  await expect(article.getByText(/claude-code/i).first()).toBeVisible({ timeout: 30_000 })
  await article.getByText('Manifest and file inventory', { exact: true }).click()
  await expect(article.getByText(/SHA256/i)).toBeVisible()
  await article.getByRole('button', { name: 'Submit for review' }).click()
  await expect(article.getByText('Submitted')).toBeVisible({ timeout: 30_000 })
  return article
}

test.describe('Plugin HOSTED lifecycle (Real API)', () => {
  test('author import -> review approve -> HOSTED publication -> catalog and download', async ({ page, browser }) => {
    test.setTimeout(300_000)
    await setEnglishLocale(page)
    await registerSession(page, test.info())

    await page.goto('/dashboard/plugins')
    await expect(page.getByRole('heading', { name: 'Plugin management' })).toBeVisible()
    const { nsSlug } = await createProject(page, SLUG, `${RUN_ID} plugin under test`)
    await importAndSubmit(page, VERSION1, ZIP_V1)

    // reviewer (PLUGIN_ADMIN) approves from an independent session
    const reviewerCtx = await browser.newContext()
    const reviewerPage = await reviewerCtx.newPage()
    await setEnglishLocale(reviewerPage)
    await loginAs(reviewerPage, PLUGIN_ADMIN_USER, PLUGIN_ADMIN_PW)
    await reviewerPage.goto('/dashboard/plugins')
    await expect(reviewerPage.getByRole('heading', { name: 'Plugin management' })).toBeVisible()
    await reviewerPage.getByText('Pending review only').click()
    await reviewerPage.locator('li', { hasText: SLUG }).first().locator('button').click()
    const reviewArticle = reviewerPage.locator('article', { hasText: VERSION1 }).first()
    await expect(reviewArticle.getByText('Submitted')).toBeVisible({ timeout: 30_000 })
    await reviewArticle.locator('summary').first().click()
    await reviewArticle.getByLabel('Review notes').fill(`approved by ${PLUGIN_ADMIN_USER} for ${RUN_ID}`)
    await reviewArticle.getByRole('button', { name: 'Approve and queue publication' }).click()

    // publication runs through the backend background task
    await expect
      .poll(async () => reviewArticle.locator('[role="status"]').first().textContent(), { timeout: 150_000 })
      .toMatch(/Publication complete|Publication failed/)
    const publicationText = await reviewArticle.locator('[role="status"]').first().textContent()
    expect(publicationText, 'HOSTED channel should succeed').toContain('Publication complete')

    // author side: publication state, original ZIP download, catalog detail
    // (reload clears the selected project, so re-open it from the project list)
    await page.reload()
    await page.locator('li', { hasText: SLUG }).first().locator('button').click()
    const publishedArticle = page.locator('article', { hasText: VERSION1 }).first()
    await expect(publishedArticle.getByText('Published')).toBeVisible({ timeout: 60_000 })
    await publishedArticle.locator('summary').first().click()
    await expect(publishedArticle.getByRole('link', { name: 'Download original ZIP' })).toBeVisible({ timeout: 60_000 })
    const href = await publishedArticle.getByRole('link', { name: 'Download original ZIP' }).getAttribute('href')
    expect(href, 'download link should point at the release download endpoint').toContain('/download')
    const download = await page.context().request.get(href!)
    expect(download.ok(), `download status ${download.status()}`).toBeTruthy()
    const body = await download.body()
    const fs = await import('node:fs')
    const original = fs.readFileSync(ZIP_V1)
    expect(body.length, 'downloaded bytes should match the imported archive').toBe(original.length)

    await page.goto(`/plugins/${encodeURIComponent(nsSlug)}/${SLUG}`)
    await expect(page.getByText(`${RUN_ID} plugin under test`).first()).toBeVisible({ timeout: 30_000 })

    await reviewerCtx.close()
  })

  test('reject keeps release immutable and forces a new version to resubmit', async ({ page, browser }) => {
    test.setTimeout(300_000)
    await setEnglishLocale(page)
    await registerSession(page, test.info())

    await page.goto('/dashboard/plugins')
    const rejectSlug = `${SLUG}-reject`
    await createProject(page, rejectSlug, `${RUN_ID} reject path plugin`)
    await importAndSubmit(page, VERSION2, ZIP_V2)

    const reviewerCtx = await browser.newContext()
    const reviewerPage = await reviewerCtx.newPage()
    await setEnglishLocale(reviewerPage)
    await loginAs(reviewerPage, PLUGIN_ADMIN_USER, PLUGIN_ADMIN_PW)
    await reviewerPage.goto('/dashboard/plugins')
    await reviewerPage.getByText('Pending review only').click()
    await reviewerPage.locator('li', { hasText: rejectSlug }).first().locator('button').click()
    const reviewArticle = reviewerPage.locator('article', { hasText: VERSION2 }).first()
    await reviewArticle.locator('summary').first().click()
    await reviewArticle.getByLabel('Review notes').fill(`quality issues — ${randomUUID()}`)
    await reviewArticle.getByRole('button', { name: 'Reject' }).click()
    await expect(reviewArticle.getByText('Rejected')).toBeVisible({ timeout: 30_000 })

    // author side: REJECTED is final for that release; submit is no longer offered
    await page.reload()
    await page.locator('li', { hasText: rejectSlug }).first().locator('button').click()
    const rejected = page.locator('article', { hasText: VERSION2 }).first()
    await expect(rejected.getByText('Rejected')).toBeVisible({ timeout: 30_000 })
    await expect(rejected.getByRole('button', { name: 'Submit for review' })).toHaveCount(0)
    await reviewerCtx.close()
  })

  test('review actions are gated: plain author cannot approve, outsider cannot see private project', async ({ page, browser }) => {
    test.setTimeout(240_000)
    await setEnglishLocale(page)
    await registerSession(page, test.info())

    // data prep through the real API (author session)
    const namespaces = await page.context().request.get('/api/v1/me/namespaces')
    expect(namespaces.ok(), `GET /me/namespaces -> ${namespaces.status()}`).toBeTruthy()
    const nsList = (((await namespaces.json()).data ?? []) as Array<{ id: string, status?: string }>)
      .filter((ns) => ns.status === undefined || ns.status === 'ACTIVE')
    expect(nsList.length, 'author should have at least one active namespace').toBeGreaterThan(0)
    const slug = `${SLUG}-gate-${randomUUID().slice(0, 8)}`
    const created = await page.context().request.post('/api/v1/plugin-management/projects', {
      data: { namespaceId: nsList[0].id, slug, displayName: `${RUN_ID} gate plugin`, visibility: 'PRIVATE' },
      headers: await csrfHeaders(page),
    })
    expect(created.ok()).toBeTruthy()
    const project = ((await created.json()).data) as { id: number }
    const release = await page.context().request.post(`/api/v1/plugin-management/projects/${project.id}/releases`, {
      data: { version: VERSION1, changelog: 'gate test' },
      headers: await csrfHeaders(page),
    })
    expect(release.ok()).toBeTruthy()
    const releaseId = ((await release.json()).data as { id: number }).id

    // author is not a reviewer -> approve must be forbidden
    const approve = await page.context().request.post(`/api/v1/plugin-management/releases/${releaseId}/approve`, {
      data: { notes: 'attempt by author', runtimeTestResult: 'PASSED' },
      headers: await csrfHeaders(page),
    })
    expect(approve.status(), 'author approve must be 403').toBe(403)

    // an unrelated fresh user must not list the private project
    const outsiderCtx = await browser.newContext()
    const outsiderPage = await outsiderCtx.newPage()
    await setEnglishLocale(outsiderPage)
    await createFreshSession(outsiderPage, test.info())
    const outsiderProjects = await outsiderPage.context().request.get('/api/v1/plugin-management/projects?size=50')
    expect(outsiderProjects.ok()).toBeTruthy()
    const outsiderItems = (((await outsiderProjects.json()).data ?? {}).items ?? []) as Array<{ slug: string }>
    expect(outsiderItems.map((item) => item.slug)).not.toContain(slug)
    await outsiderCtx.close()
  })
})
