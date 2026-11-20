import { randomUUID } from 'node:crypto'
import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { csrfHeaders } from './helpers/csrf'
import { createFreshSession, registerSession } from './helpers/session'

/**
 * Ticket 05 basics — real-cookie session boundaries, CSRF enforcement,
 * cross-user isolation for plugin/software resources. No mock headers.
 */
const RUN_ID = 'e2e-20261019-100450'


test.describe('Permissions, isolation and CSRF (Real API)', () => {
  test('anonymous boundary: protected APIs reject, public catalog stays readable', async ({ page }) => {
    test.setTimeout(120_000)
    const anon = await page.context().browser()!.newContext()
    const req = anon.request

    const me = await req.get('/api/v1/auth/me')
    expect(me.status(), 'anonymous /me must be 401').toBe(401)

    const projects = await req.get('/api/v1/plugin-management/projects')
    expect([401, 403]).toContain(projects.status())

    const adminApps = await req.get('/api/v1/admin/software/apps')
    expect([401, 403]).toContain(adminApps.status())

    const myNs = await req.get('/api/v1/me/namespaces')
    expect([401, 403]).toContain(myNs.status())

    // public catalog reads stay open
    const catalog = await req.get('/api/v1/plugins')
    expect(catalog.ok(), `anonymous plugin catalog -> ${catalog.status()}`).toBeTruthy()

    // UI route guard for anonymous visitors
    const anonPage = await anon.newPage()
    await setEnglishLocale(anonPage)
    await anonPage.goto('/dashboard/plugins')
    await expect(anonPage).not.toHaveURL(/\/dashboard\/plugins/, { timeout: 15_000 })
    await anon.close()
  })

  test('CSRF: state-changing POST without X-XSRF-TOKEN is rejected, with token succeeds', async ({ page }) => {
    test.setTimeout(120_000)
    await setEnglishLocale(page)
    await registerSession(page, test.info())

    // probe with a mutation the author is allowed to perform (plugin project)
    const namespaces = await page.context().request.get('/api/v1/me/namespaces')
    expect(namespaces.ok()).toBeTruthy()
    const nsList = (((await namespaces.json()).data ?? []) as Array<{ id: string, status?: string }>)
      .filter((ns) => ns.status === undefined || ns.status === 'ACTIVE')
    expect(nsList.length).toBeGreaterThan(0)
    const slug = `${RUN_ID}-csrf-${randomUUID().slice(0, 8)}`

    const without = await page.context().request.post('/api/v1/plugin-management/projects', {
      data: { namespaceId: nsList[0].id, slug, displayName: `csrf probe ${RUN_ID}`, visibility: 'PRIVATE' },
    })
    expect(without.status(), 'POST without CSRF token must be rejected').toBe(403)

    const withToken = await page.context().request.post('/api/v1/plugin-management/projects', {
      data: { namespaceId: nsList[0].id, slug, displayName: `csrf probe ${RUN_ID}`, visibility: 'PRIVATE' },
      headers: await csrfHeaders(page),
    })
    expect(withToken.ok(), `POST with CSRF token -> ${withToken.status()}`).toBeTruthy()
  })

  test('cross-user isolation: outsider cannot read or download a private plugin release', async ({ page, browser }) => {
    test.setTimeout(180_000)
    await setEnglishLocale(page)
    await registerSession(page, test.info())

    // author creates a private plugin project with a draft release via API
    const namespaces = await page.context().request.get('/api/v1/me/namespaces')
    expect(namespaces.ok()).toBeTruthy()
    const nsList = (((await namespaces.json()).data ?? []) as Array<{ id: string, status?: string }>)
      .filter((ns) => ns.status === undefined || ns.status === 'ACTIVE')
    expect(nsList.length).toBeGreaterThan(0)
    const slug = `${RUN_ID}-iso-${randomUUID().slice(0, 8)}`
    const created = await page.context().request.post('/api/v1/plugin-management/projects', {
      data: { namespaceId: nsList[0].id, slug, displayName: `${RUN_ID} isolation plugin`, visibility: 'PRIVATE' },
      headers: await csrfHeaders(page),
    })
    expect(created.ok()).toBeTruthy()
    const projectId = ((await created.json()).data as { id: number }).id
    const release = await page.context().request.post(`/api/v1/plugin-management/projects/${projectId}/releases`, {
      data: { version: '0.1.0', changelog: 'isolation probe' },
      headers: await csrfHeaders(page),
    })
    expect(release.ok()).toBeTruthy()
    const releaseId = ((await release.json()).data as { id: number }).id

    // outsider session
    const outsiderCtx = await browser.newContext()
    const outsiderPage = await outsiderCtx.newPage()
    await setEnglishLocale(outsiderPage)
    await createFreshSession(outsiderPage, test.info())

    const detail = await outsiderPage.context().request.get(`/api/v1/plugin-management/projects/${projectId}`)
    expect([403, 404]).toContain(detail.status())

    const draft = await outsiderPage.context().request.get(`/api/v1/plugin-management/releases/${releaseId}`)
    expect([403, 404]).toContain(draft.status())

    const archive = await outsiderPage.context().request.get(`/api/v1/plugin-management/releases/${releaseId}/archive?runtime=claude-code`)
    expect([403, 404]).toContain(archive.status())

    // software: outsider cannot use admin upload endpoints
    const uploadInit = await outsiderPage.context().request.post('/api/v1/admin/software/uploads', {
      data: { releaseId: 999999, fileName: 'x.zip', format: 'zip', targetOs: 'windows', targetArch: 'x86_64', originalFullVersion: '1', byteSize: 1 },
      headers: await csrfHeaders(outsiderPage),
    })
    expect([401, 403, 404]).toContain(uploadInit.status())

    await outsiderCtx.close()
  })

  test('logout invalidates the session server-side', async ({ page }) => {
    test.setTimeout(120_000)
    await setEnglishLocale(page)
    await registerSession(page, test.info())
    expect((await page.context().request.get('/api/v1/auth/me')).ok()).toBeTruthy()
    const logout = await page.context().request.post('/api/v1/auth/logout', {
      headers: await csrfHeaders(page),
      maxRedirects: 0,
    })
    expect([200, 302]).toContain(logout.status())
    await expect.poll(async () => (await page.context().request.get('/api/v1/auth/me')).status(), { timeout: 15_000 }).toBe(401)
  })
})
