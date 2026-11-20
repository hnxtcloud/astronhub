import { createHash } from 'node:crypto'
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { test, expect as baseExpect, type Browser, type Page } from '@playwright/test'
import type { components } from '../src/api/generated/schema'
import { csrfHeaders } from './helpers/csrf'

// Real services only. These MCP cases exercise the read/catalog surface;
// MCP authoring and publication run through /api/v1/mcp-management and are
// covered by the backend McpManagementIntegrationTest.
const expect = baseExpect.configure({ timeout: 15_000 })
const fixturePrefix = process.env.E2E_MCP_FIXTURE_PREFIX ?? 'capabilities-zh-20261021-130000'
const runId = process.env.E2E_RUN_ID ?? `capabilities-zh-${Date.now()}`
const evidence = resolve(process.env.E2E_EVIDENCE_DIR ?? '../.dev/capability-acceptance/local')
const packages = resolve(process.env.E2E_SKILL_FIXTURE_DIR ?? '../.dev/capability-acceptance/skill-fixtures')
const authorUsername = process.env.E2E_AUTHOR_USERNAME ?? 'e2e_author_20261019'
const outsiderUsername = process.env.E2E_OUTSIDER_USERNAME ?? 'e2e_outsider_20261019'
const mcpNamespace = process.env.E2E_MCP_NAMESPACE ?? 'personal-3364bf77'
type CatalogPage = components['schemas']['PageResponseCapabilityCatalogItem']
type PublishResult = components['schemas']['PublishResponse']
type Namespace = components['schemas']['MyNamespaceResponse']

test.setTimeout(180_000)
test.use({ locale: 'zh-CN', actionTimeout: 15_000 })
test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('i18nextLng', 'zh'))
})
test.afterEach(async ({ page }, info) => {
  const path = info.outputPath('中文验收.png')
  await page.screenshot({ path, fullPage: true })
  await info.attach('中文界面', { path, contentType: 'image/png' })
})

async function login(page: Page, username = authorUsername, password = process.env.E2E_AUTHOR_PASSWORD) {
  expect(password, 'Inject real credentials').toBeTruthy()
  await page.goto('/login')
  await page.getByLabel('用户名', { exact: true }).fill(username)
  await page.getByLabel('密码', { exact: true }).fill(password!)
  const response = page.waitForResponse(r => r.url().includes('/api/v1/auth/local/login') && r.request().method() === 'POST')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  expect((await response).status()).toBe(200)
  await expect(page).not.toHaveURL(/\/login/)
}
async function catalog(page: Page, query: string) {
  await page.goto('/mcp-servers')
  await expect(page.getByRole('heading', { name: '发现 Agent 能力', exact: true })).toBeVisible()
  await page.getByPlaceholder('搜索能力名称、坐标或描述').fill(query)
  await expect(page.getByText('正在加载目录…', { exact: true })).toHaveCount(0)
}
async function mcpList(page: Page) {
  const r = await page.request.get(`/api/v1/mcp-servers?q=${fixturePrefix}`)
  expect(r.status()).toBe(200)
  return (await r.json() as { data: CatalogPage }).data
}
async function independentPage(browser: Browser, username: string, password?: string) {
  const context = await browser.newContext({ baseURL: process.env.PLAYWRIGHT_BASE_URL, locale: 'zh-CN' })
  await context.addInitScript(() => localStorage.setItem('i18nextLng', 'zh'))
  const page = await context.newPage()
  await login(page, username, password)
  return { page, context }
}

test('M01 MCP 中文搜索、传输方式和所有者可见范围', async ({ page }) => {
  await login(page)
  await catalog(page, fixturePrefix)
  await expect(page.locator('main article')).toHaveCount(3)
  for (const kind of ['public', 'private', 'namespace']) {
    const card = page.locator('article').filter({ hasText: `${fixturePrefix}-${kind}` })
    await expect(card.getByRole('heading', { name: `中文验收知识库-${kind}`, exact: true })).toBeVisible()
    await expect(card.getByText('STREAMABLE HTTP', { exact: true })).toBeVisible()
  }
  const list = await mcpList(page)
  expect(list.items?.map(item => item.slug).sort()).toEqual(['namespace', 'private', 'public'].map(k => `${fixturePrefix}-${k}`).sort())
  await page.getByPlaceholder('搜索能力名称、坐标或描述').fill('中文验收知识库-public')
  await expect(page.locator('main article')).toHaveCount(1)
  await page.getByPlaceholder('搜索能力名称、坐标或描述').fill('不存在的中文能力-不存在')
  await expect(page.getByText('当前筛选下还没有已发布条目。', { exact: true })).toBeVisible()
})

test('M02 MCP 外部用户仅见公开条目，私有和未发布条目不可直读', async ({ page }) => {
  await login(page, outsiderUsername, process.env.E2E_OUTSIDER_PASSWORD)
  await catalog(page, fixturePrefix)
  await expect(page.locator('main article')).toHaveCount(1)
  await expect(page.getByRole('heading', { name: '中文验收知识库-public', exact: true })).toBeVisible()
  expect((await mcpList(page)).items?.map(item => item.slug)).toEqual([`${fixturePrefix}-public`])
  for (const kind of ['private', 'namespace', 'hidden', 'archived', 'draft']) {
    const r = await page.request.get(`/api/v1/mcp-servers/${mcpNamespace}/${fixturePrefix}-${kind}`)
    expect(r.status(), kind).toBe(404)
    const profiles = await page.request.get(`/api/v1/mcp-servers/${mcpNamespace}/${fixturePrefix}-${kind}/connection-profiles`)
    expect(profiles.status(), `${kind} connection profiles`).toBe(404)
  }
})

test('M03 MCP 匿名目录访问边界与 mock 身份关闭', async ({ page }) => {
  const me = await page.request.get('/api/v1/auth/me', { headers: { 'X-Mock-User-Id': 'local-admin' } })
  expect(me.status()).toBe(401)
  await catalog(page, fixturePrefix)
  await expect(page.locator('main article')).toHaveCount(1)
  await expect(page.getByRole('heading', { name: '中文验收知识库-public', exact: true })).toBeVisible()
  const r = await page.request.get(`/api/v1/mcp-servers?q=${fixturePrefix}`)
  expect(r.status()).toBe(200)
  expect((await r.json() as { data: CatalogPage }).data.items?.map(i => i.slug)).toEqual([`${fixturePrefix}-public`])
  expect((await page.request.get(`/api/v1/mcp-servers/${mcpNamespace}/${fixturePrefix}-public/connection-profiles`)).status()).toBe(200)
})

test('M04 搜索框承诺的完整 MCP 坐标应能找到条目', async ({ page }) => {
  await login(page)
  await catalog(page, `mcp:@${mcpNamespace}/${fixturePrefix}-public`)
  await expect(page.getByRole('heading', { name: '中文验收知识库-public', exact: true })).toBeVisible()
})

test('M05 MCP 中文详情、连接模板和真实剪贴板复制', async ({ page }) => {
  await login(page)
  await catalog(page, `${fixturePrefix}-public`)
  const card = page.locator('article').filter({ hasText: `${fixturePrefix}-public` })
  await expect(card).toBeVisible()
  await card.getByRole('link', { name: '中文验收知识库-public', exact: true }).click()
  await expect(page).toHaveURL(new RegExp(`/mcp-servers/${mcpNamespace}/${fixturePrefix}-public$`))
  await expect(page.getByRole('heading', { name: '中文验收知识库-public', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '连接配置', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '中文知识检索连接', exact: true })).toBeVisible()
  await expect(page.getByLabel('客户端配置模板')).toContainText('https://example.test/mcp')
  await page.context().grantPermissions(['clipboard-read', 'clipboard-write'])
  await page.getByRole('button', { name: '复制配置', exact: true }).click()
  await expect(page.getByText('已复制', { exact: true })).toBeVisible()
  const copied = JSON.parse(await page.evaluate(() => navigator.clipboard.readText())) as { mcpServers: Record<string, { type: string; url: string }> }
  expect(copied.mcpServers[`${fixturePrefix}-public`]).toEqual({ type: 'http', url: 'https://example.test/mcp' })
})

async function upload(page: Page, filename: string, visibility: string): Promise<PublishResult> {
  const nsResponse = await page.request.get('/api/v1/me/namespaces')
  expect(nsResponse.status()).toBe(200)
  const namespaces = (await nsResponse.json() as { data: Namespace[] }).data
  const namespace = namespaces.find(ns => ns.slug !== 'global' && ns.status === 'ACTIVE')!
  expect(namespace).toBeTruthy()
  await page.goto(`/dashboard/publish?namespace=${namespace.slug}&visibility=${visibility}`)
  await expect(page.getByRole('heading', { name: '发布技能', exact: true })).toBeVisible()
  await expect(page.locator('#namespace')).toContainText(`@${namespace.slug}`)
  await page.locator('input[type=file][accept*=".zip"]').setInputFiles(resolve(packages, filename))
  const response = page.waitForResponse(r => r.request().method() === 'POST' && r.url().includes('/publish'))
  await page.getByRole('button', { name: '确认发布', exact: true }).click()
  const r = await response
  const json = await r.json() as { code: number; msg: string; data: PublishResult }
  expect(r.status(), json.msg).toBe(200)
  expect(json.code, json.msg).toBe(0)
  return json.data
}

test('S01 私有 Skill 中文 ZIP 上传、确认发布、文件预览、下载与用户隔离', async ({ page, browser }) => {
  await login(page)
  const result = await upload(page, '中文私有技能.zip', 'PRIVATE')
  expect(['SCANNING', 'UPLOADED']).toContain(result.status)
  mkdirSync(evidence, { recursive: true })
  writeFileSync(resolve(evidence, 'private-skill.json'), JSON.stringify(result, null, 2))
  await expect.poll(async () => {
    const response = await page.request.get(`/api/v1/skills/${result.namespace}/${result.slug}/versions/${result.version}`)
    expect(response.status()).toBe(200)
    return (await response.json() as { data: components['schemas']['SkillVersionDetailResponse'] }).data.status
  }, { timeout: 180_000 }).toBe('UPLOADED')
  await page.goto('/dashboard/skills')
  const heading = page.getByRole('heading', { name: result.slug!, exact: true })
  await expect(heading).toBeVisible()
  await heading.click()
  await expect(page.getByRole('heading', { name: result.slug!, exact: true })).toBeVisible()
  await page.getByRole('tab', { name: '版本', exact: true }).click()
  await page.getByRole('button', { name: '确认发布', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: '确认发布', exact: true }).click()
  await expect.poll(async () => {
    const r = await page.request.get(`/api/v1/skills/${result.namespace}/${result.slug}/versions/${result.version}`)
    return (await r.json() as { data: components['schemas']['SkillVersionDetailResponse'] }).data.status
  }).toBe('PUBLISHED')
  await page.reload()
  await expect(page.getByText('中文验收技能说明', { exact: true }).first()).toBeVisible()
  const files = await page.request.get(`/api/v1/skills/${result.namespace}/${result.slug}/versions/${result.version}/files`)
  expect(files.status()).toBe(200)
  const fileList = (await files.json() as { data: components['schemas']['SkillFileResponse'][] }).data
  expect(fileList.some(f => f.filePath === '资料/中文说明.md')).toBeTruthy()
  const content = await page.request.get(`/api/v1/skills/${result.namespace}/${result.slug}/versions/${result.version}/file?path=${encodeURIComponent('资料/中文说明.md')}`)
  expect(content.status()).toBe(200)
  expect(await content.text()).toContain('中文内容完整保留：你好，星枢！')
  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: '下载', exact: true }).click()
  const downloaded = await download
  const archive = resolve(evidence, 'skill-download.zip')
  await downloaded.saveAs(archive)
  expect(readFileSync(archive).length).toBeGreaterThan(0)
  writeFileSync(resolve(evidence, 'skill-files.json'), JSON.stringify(fileList, null, 2))
  writeFileSync(resolve(evidence, 'skill-download.sha256'), createHash('sha256').update(readFileSync(archive)).digest('hex'))
  const outsider = await independentPage(browser, outsiderUsername, process.env.E2E_OUTSIDER_PASSWORD)
  try {
    const r = await outsider.page.request.get(`/api/v1/skills/${result.namespace}/${result.slug}/download`)
    expect([403, 404]).toContain(r.status())
  } finally { await outsider.context.close() }
})

test('S02 公开 Skill 中文上传、真实安全扫描、审核、搜索和下载', async ({ page, browser }) => {
  test.setTimeout(300_000)
  await login(page)
  const result = await upload(page, '中文公开技能.zip', 'PUBLIC')
  mkdirSync(evidence, { recursive: true })
  writeFileSync(resolve(evidence, 'public-skill.json'), JSON.stringify(result, null, 2))
  const reviewer = await independentPage(browser, process.env.E2E_SUPER_ADMIN_USERNAME!, process.env.E2E_SUPER_ADMIN_PASSWORD)
  try {
    const response = await reviewer.page.request.get('/api/v1/reviews?status=PENDING&size=100')
    expect(response.status()).toBe(200)
    const reviews = (await response.json() as { data: components['schemas']['PageResponseReviewTaskResponse'] }).data.items!
    const review = reviews.find(r => r.skillSlug === result.slug && r.namespace === result.namespace)!
    expect(review).toBeTruthy()
    await reviewer.page.goto(`/dashboard/reviews/${review.id}`)
    await expect(reviewer.page.getByRole('heading', { name: '审核详情', exact: true })).toBeVisible()
    await expect.poll(async () => {
      const r = await reviewer.page.request.get(`/api/v1/skills/${result.skillId}/versions/${review.skillVersionId}/security-audit`)
      expect(r.status()).toBe(200)
      const audits = (await r.json() as { data: components['schemas']['SecurityAuditResponse'][] }).data
      return audits.some(a => a.scannedAt && a.isSafe && a.verdict === 'SAFE')
    }, { timeout: 180_000 }).toBe(true)
    const audit = await reviewer.page.request.get(`/api/v1/skills/${result.skillId}/versions/${review.skillVersionId}/security-audit`)
    expect(audit.status()).toBe(200)
    const auditBody: unknown = await audit.json()
    writeFileSync(resolve(evidence, 'public-skill-security-audit.json'), JSON.stringify(auditBody, null, 2))
    await expect.soft(reviewer.page.getByRole('button', { name: '通过审核', exact: true }), '真实扫描已 SAFE，审核页应自动解除禁用').toBeEnabled()
    if (await reviewer.page.getByRole('button', { name: '通过审核', exact: true }).isDisabled()) {
      const screenshot = test.info().outputPath('扫描完成但审核按钮未刷新.png')
      await reviewer.page.screenshot({ path: screenshot, fullPage: true })
      await test.info().attach('扫描完成但审核按钮未刷新', { path: screenshot, contentType: 'image/png' })
      test.info().annotations.push({ type: 'workaround', description: 'Manual refresh required after SAFE scan; soft assertion remains failed.' })
      await reviewer.page.reload()
    }
    await expect(reviewer.page.getByRole('button', { name: '通过审核', exact: true })).toBeEnabled()
    await reviewer.page.getByLabel('审核意见（可选）').fill(`${runId} 中文端到端验收`)
    await reviewer.page.getByRole('button', { name: '通过审核', exact: true }).click()
    await reviewer.page.getByRole('dialog').getByRole('button', { name: '通过', exact: true }).click()
    await expect(reviewer.page).not.toHaveURL(new RegExp(`/reviews/${review.id}$`))
    await page.goto('/catalog')
    await page.getByRole('button', { name: 'Skills', exact: true }).click()
    await page.getByPlaceholder('搜索能力名称、坐标或描述').fill(result.slug!)
    await expect(page.getByRole('heading', { name: result.slug!, exact: true })).toBeVisible()
    const download = await page.request.get(`/api/v1/skills/${result.namespace}/${result.slug}/download`)
    expect(download.status()).toBe(200)
    writeFileSync(resolve(evidence, 'public-skill-download.zip'), await download.body())
    writeFileSync(resolve(evidence, 'public-skill-workflow-complete.json'), JSON.stringify({ result, completed: true, manualRefresh: test.info().annotations.some(a => a.type === 'workaround') }, null, 2))
  } finally { await reviewer.context.close() }
})

test('S04 非法 Skill ZIP 在中文界面显示校验错误', async ({ page }) => {
  await login(page)
  const nsResponse = await page.request.get('/api/v1/me/namespaces')
  expect(nsResponse.status()).toBe(200)
  const ns = (await nsResponse.json() as { data: Namespace[] }).data.find(n => n.slug !== 'global')!
  await page.goto(`/dashboard/publish?namespace=${ns.slug}&visibility=PRIVATE`)
  await page.locator('input[type=file][accept*=".zip"]').setInputFiles(resolve(packages, '缺少技能入口.zip'))
  const response = page.waitForResponse(r => r.request().method() === 'POST' && r.url().includes('/publish'))
  await page.getByRole('button', { name: '确认发布', exact: true }).click()
  expect((await response).status()).toBe(400)
  await expect(page.getByText('发布失败', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '确认发布', exact: true })).toBeEnabled()
})

test('S03 缺少 CSRF 的 Skill 写请求被拒绝', async ({ page }) => {
  await login(page)
  const r = await page.request.post('/api/v1/reviews', { data: { skillVersionId: 0 } })
  expect(r.status()).toBe(403)
  const protectedRequest = await page.request.post('/api/v1/reviews', { data: { skillVersionId: 0 }, headers: await csrfHeaders(page) })
  expect(protectedRequest.status()).not.toBe(403)
})

test('S05 扫描中打开审核页，扫描完成后按钮自动恢复', async ({ page, browser }) => {
  const reviewer = await independentPage(browser, process.env.E2E_SUPER_ADMIN_USERNAME!, process.env.E2E_SUPER_ADMIN_PASSWORD)
  try {
    await login(page)
    const result = await upload(page, '中文扫描时序技能.zip', 'PUBLIC')
    let review: components['schemas']['ReviewTaskResponse'] | undefined
    await expect.poll(async () => {
      const r = await reviewer.page.request.get('/api/v1/reviews?status=PENDING&size=100')
      expect(r.status()).toBe(200)
      review = (await r.json() as { data: components['schemas']['PageResponseReviewTaskResponse'] }).data.items?.find(item => item.skillSlug === result.slug)
      return Boolean(review)
    }).toBe(true)
    const detailResponse = reviewer.page.waitForResponse(r => r.url().includes(`/reviews/${review!.id}/skill-detail`))
    await reviewer.page.goto(`/dashboard/reviews/${review!.id}`)
    expect((await detailResponse).status()).toBe(200)
    await expect(reviewer.page.getByRole('heading', { name: '审核详情', exact: true })).toBeVisible()
    const warning = reviewer.page.getByText('安全扫描进行中，暂不能通过审核，请等待扫描完成后再操作。', { exact: true })
    await expect(reviewer.page.getByRole('button', { name: '通过审核', exact: true })).toBeVisible()
    test.skip(!await warning.isVisible(), 'Scan completed before review detail loaded; early-open timing was not exercised.')
    const button = reviewer.page.getByRole('button', { name: '通过审核', exact: true })
    await expect(button).toBeDisabled()
    await expect.poll(async () => {
      const r = await reviewer.page.request.get(`/api/v1/skills/${result.skillId}/versions/${review!.skillVersionId}/security-audit`)
      expect(r.status()).toBe(200)
      const data = await r.json() as { data: components['schemas']['SecurityAuditResponse'][] }
      mkdirSync(evidence, { recursive: true })
      writeFileSync(resolve(evidence, 'early-open-security-audit.json'), JSON.stringify({ result, review, audit: data }, null, 2))
      return data.data.some(a => a.scannedAt && a.verdict === 'SAFE')
    }, { timeout: 120_000 }).toBe(true)
    const screenshot = test.info().outputPath('扫描SAFE后审核页面.png')
    await reviewer.page.screenshot({ path: screenshot, fullPage: true })
    await test.info().attach('扫描SAFE后审核页面', { path: screenshot, contentType: 'image/png' })
    await expect(button, 'API 已 SAFE，原审核页应自动恢复').toBeEnabled()
  } finally { await reviewer.context.close() }
})
