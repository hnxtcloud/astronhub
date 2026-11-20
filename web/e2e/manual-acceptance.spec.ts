import { createHash, randomUUID } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { expect as playwrightExpect, test, type Browser, type BrowserContext, type Locator, type Page } from '@playwright/test'
import type { components } from '../src/api/generated/schema'

// Real UI acceptance. Keep credentials and raw traces in the ignored run directory.
const expect = playwrightExpect.configure({ timeout: 15_000 })
const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? 'http://127.0.0.1:3000'
const runId = process.env.E2E_RUN_ID ?? `manual-${Date.now()}-${randomUUID().slice(0, 6)}`
const runRoot = resolve(process.env.E2E_EVIDENCE_DIR ?? `../.dev/manual-acceptance/runs/${runId}`)
const fixtures = resolve(process.env.E2E_FIXTURE_DIR ?? '../.dev/manual-acceptance/fixtures')
const guide = readFileSync(resolve('../.scratch/plugin-update-e2e/manual-acceptance.md'), 'utf8')
const expected = [...guide.matchAll(/^\| ([A-G]\d{2}) \| (.*?) \| (.*?) \|$/gm)]
const projectSlug = `${runId}-plugin`
const projectName = `${runId} 手动验收插件`
const appSlug = `${runId}-app`
const appName = `${runId} 手动验收软件`
type Outcome = { status: 'passed' | 'failed' | 'blocked'; action: string; expected: string; error?: string }
interface Ledger {
  runId: string
  steps: Record<string, Outcome>
  project?: { slug: string; namespace: string; published: boolean; latest?: string; downloadUrl?: string }
  application?: { slug: string; published: boolean }
  notes: string[]
}
const ledgerPath = resolve(runRoot, 'steps.json')
mkdirSync(runRoot, { recursive: true })
function ledger(): Ledger {
  return existsSync(ledgerPath)
    ? JSON.parse(readFileSync(ledgerPath, 'utf8')) as Ledger
    : { runId, steps: {}, notes: [] }
}
function save(value: Ledger) { writeFileSync(ledgerPath, JSON.stringify(value, null, 2)) }
const roles = {
  author: { username: 'e2e_author_20261019', password: process.env.E2E_AUTHOR_PASSWORD ?? '' },
  reviewer: { username: 'e2e_plugin_admin_20261019', password: process.env.E2E_PLUGIN_ADMIN_PASSWORD ?? '' },
  software: { username: 'e2e_sw_admin_20261019', password: process.env.E2E_SW_ADMIN_PASSWORD ?? '' },
  outsider: { username: 'e2e_outsider_20261019', password: process.env.E2E_OUTSIDER_PASSWORD ?? '' },
}
const openContexts: Array<{ name: string; context: BrowserContext; page: Page }> = []
function sanitized(error: unknown): string {
  let text = error instanceof Error ? error.message : String(error)
  for (const account of Object.values(roles)) if (account.password) text = text.replaceAll(account.password, '[REDACTED]')
  return text
}
async function step(id: string, body: () => Promise<void>) {
  const definition = expected.find(row => row[1] === id)
  if (!definition) throw new Error(`Unknown checklist step ${id}`)
  try {
    await test.step(`${id} ${definition[2]}`, body)
    const value = ledger()
    value.steps[id] = { status: 'passed', action: definition[2], expected: definition[3] }
    save(value)
  } catch (error) {
    const value = ledger()
    value.steps[id] = { status: 'failed', action: definition[2], expected: definition[3], error: sanitized(error) }
    save(value)
    throw error
  }
}
async function session(browser: Browser, name: string): Promise<Page> {
  const context = await browser.newContext({ baseURL, locale: 'zh-CN', acceptDownloads: true })
  await context.addInitScript(() => window.localStorage.setItem('i18nextLng', 'zh'))
  const page = await context.newPage()
  openContexts.push({ name, context, page })
  return page
}
async function login(page: Page, role: keyof typeof roles) {
  const account = roles[role]
  expect(account.password, `${role} credential must be injected`).not.toBe('')
  await page.goto('/login')
  await page.getByLabel('用户名', { exact: true }).fill(account.username)
  await page.getByLabel('密码', { exact: true }).fill(account.password)
  const response = page.waitForResponse(r => r.url().includes('/api/v1/auth/local/login') && r.request().method() === 'POST')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  expect((await response).status()).toBe(200)
  await expect(page).not.toHaveURL(/\/login(?:\?|$)/)
}
async function management(page: Page) {
  await page.goto('/dashboard/plugins')
  await expect(page.getByRole('heading', { name: '插件管理', exact: true })).toBeVisible()
}
function createBox(page: Page) { return page.locator('details').filter({ has: page.locator('summary', { hasText: '创建项目' }) }).first() }
async function pickNamespace(select: Locator) {
  await expect.poll(() => select.locator('option').count()).toBeGreaterThan(1)
  const options = await select.locator('option').evaluateAll(nodes => nodes.map(n => ({ value: (n as HTMLOptionElement).value, text: n.textContent })))
  const personal = options.find(n => n.value && n.text !== 'Global' && n.text !== '全局')
  expect(personal).toBeTruthy()
  await select.selectOption(personal!.value)
  await expect(select).toHaveValue(personal!.value)
}
async function createProject(page: Page, slug: string, name: string) {
  const box = createBox(page)
  if (!await box.getAttribute('open')) {
    if (!await box.locator('select').first().isVisible()) await box.locator('summary').click()
  }
  await pickNamespace(box.getByRole('combobox', { name: '命名空间', exact: true }))
  await box.getByLabel('标识', { exact: true }).fill(slug)
  await box.getByLabel('展示名称', { exact: true }).fill(name)
  await box.getByLabel('可见范围').selectOption('PRIVATE')
  await box.getByRole('button', { name: '创建项目', exact: true }).click()
  await expect(page.locator('li', { hasText: slug }).first()).toBeVisible()
}
async function selectProject(page: Page, slug: string) {
  await page.locator('li', { hasText: slug }).first().locator('button').click()
  await expect(page.getByRole('heading', { name: '版本历史', exact: true })).toBeVisible()
}
function article(page: Page, version: string) {
  return page.locator('article').filter({ has: page.getByRole('heading', { name: version, exact: true }) }).first()
}
async function draft(page: Page, version: string) {
  await page.getByLabel('版本号', { exact: true }).fill(version)
  await page.getByRole('button', { name: '创建草稿', exact: true }).click()
  await expect(article(page, version)).toBeVisible()
}
async function reviewerProject(page: Page, slug: string) {
  await management(page)
  await page.getByLabel('仅待审核', { exact: true }).check()
  await selectProject(page, slug)
}
async function waitPublished(page: Page, version: string) {
  await expect(article(page, version).getByRole('status').first()).toContainText('发布完成', { timeout: 150_000 })
  await expect(article(page, version).getByText('已发布', { exact: true })).toBeVisible()
}
function sha256(bytes: Buffer) { return createHash('sha256').update(bytes).digest('hex') }
async function saveDownload(page: Page, trigger: () => Promise<void>, name: string): Promise<Buffer> {
  const downloading = page.waitForEvent('download', { timeout: 60_000 })
  await trigger()
  const download = await downloading
  const path = resolve(runRoot, name)
  await download.saveAs(path)
  return readFileSync(path)
}
async function softwarePage(page: Page) {
  await page.goto('/admin/software')
  await expect(page.getByRole('heading', { name: '软件管理', exact: true })).toBeVisible()
}
function appRow(page: Page) { return page.locator('tr', { hasText: appSlug }).first() }
function releasePanel(page: Page, version: string) {
  return page.locator('div.rounded-md.border').filter({ has: page.locator('div.font-medium', { hasText: new RegExp(`^${version.replaceAll('.', '\\.')}$`) }) }).last()
}
async function openSoftware(page: Page) {
  await softwarePage(page)
  await appRow(page).getByRole('button', { name: '版本', exact: true }).click()
  await expect(page.getByText(`${appName} 的版本`, { exact: true })).toBeVisible()
}
function updateManager(page: Page, version: string) {
  return releasePanel(page, version).locator('section').filter({ has: page.getByRole('heading', { name: '更新发行快照', exact: true }) }).first()
}
function sourceManager(page: Page) {
  return page.locator('section').filter({ has: page.getByRole('heading', { name: '更新源和渠道', exact: true }) }).first()
}
function snapshot(page: Page, version: string) {
  return updateManager(page, version).locator('article').filter({ has: page.locator('strong', { hasText: `v${version}` }) }).first()
}
async function uploadSoftwareVersion(page: Page, version: string) {
  await page.getByRole('button', { name: '新建版本', exact: true }).click()
  await page.locator('#software-release-version').fill(version)
  await page.getByRole('dialog').getByRole('button', { name: '保存', exact: true }).click()
  const panel = releasePanel(page, version)
  await expect(panel).toBeVisible()
  await panel.getByRole('button', { name: '上传安装包', exact: true }).click()
  await page.locator('#software-upload-file').setInputFiles(resolve(fixtures, `software-${version}/portable.zip`))
  await page.locator('#software-upload-version').fill(version)
  await page.getByRole('dialog').getByRole('button', { name: '上传', exact: true }).click()
  const row = panel.locator('tr', { hasText: 'portable.zip' }).first()
  await expect(row).toBeVisible({ timeout: 60_000 })
  await row.getByRole('button', { name: '确认', exact: true }).click()
  await page.locator('#software-confirm-raw-fields').fill(JSON.stringify({ ProductVersion: version, ManualEntry: runId }))
  await page.getByRole('dialog').getByRole('button', { name: '确认', exact: true }).click()
  await row.getByRole('button', { name: '发布', exact: true }).click()
  await expect(row.getByText('已发布', { exact: true })).toBeVisible()
}
async function createUpdateVersion(page: Page, version: string) {
  const manager = updateManager(page, version)
  await manager.getByLabel('机器版本号', { exact: true }).fill(version)
  await manager.getByLabel('更新协议').selectOption('BAIZE')
  await manager.getByLabel('Pi 版本', { exact: true }).fill('0.70.0')
  await manager.getByRole('button', { name: '创建更新草稿', exact: true }).click()
  const row = snapshot(page, version)
  await expect(row).toBeVisible()
  await row.getByLabel('相对路径 / 上传目录', { exact: true }).fill('dist/portable.zip')
  await row.getByLabel('已发布安装包').selectOption({ label: await row.getByLabel('已发布安装包').locator('option').nth(1).textContent() ?? '' })
  await row.getByRole('button', { name: '关联安装包', exact: true }).click()
  await expect(row.locator('code', { hasText: 'dist/portable.zip' })).toBeVisible()
  for (const [file, role] of [['latest.json', 'METADATA'], ['notes.html', 'OTHER']]) {
    await row.getByLabel('相对路径 / 上传目录', { exact: true }).fill(`dist/${file}`)
    await row.getByLabel('资产用途').selectOption(role)
    await row.locator('input[type=file]').setInputFiles(resolve(fixtures, `software-${version}/${file}`))
    await row.getByRole('button', { name: '上传资产', exact: true }).click()
    await expect(row.locator('code', { hasText: `dist/${file}` })).toBeVisible()
  }
  await row.getByRole('button', { name: '校验', exact: true }).click()
  await expect(row.getByRole('status')).toHaveText('校验通过')
  await row.getByRole('button', { name: '发布快照', exact: true }).click()
  await expect(row.getByText('已发布', { exact: true })).toBeVisible()
}

test.setTimeout(600_000)
test.use({ actionTimeout: 15_000, navigationTimeout: 30_000 })
test.beforeEach(() => { openContexts.length = 0 })
test.afterEach(async ({ browser }, testInfo) => {
  const group = testInfo.title[0]
  const value = ledger()
  for (const row of expected.filter(row => row[1].startsWith(group))) {
    if (!value.steps[row[1]]) value.steps[row[1]] = {
      status: 'blocked', action: row[2], expected: row[3], error: 'Earlier prerequisite or step in this group did not complete; see first failure.',
    }
  }
  save(value)
  if (!browser.isConnected()) return
  for (const item of openContexts) {
    try {
      const path = testInfo.outputPath(`${group}-${item.name}.png`)
      await item.page.screenshot({ path, fullPage: true, timeout: 10_000 })
      await testInfo.attach(`${group}-${item.name}`, { path, contentType: 'image/png' })
    } catch { /* Keep the original assertion failure if a closed page cannot be captured. */ }
    await item.context.close()
  }
})

test('A. 登录与注销 A01–A05', async ({ browser }) => {
  const page = await session(browser, 'author')
  await step('A01', async () => { await page.goto('/login'); await expect(page.getByLabel('用户名', { exact: true })).toBeVisible() })
  await step('A02', async () => { await login(page, 'author') })
  await step('A03', async () => { await management(page) })
  await step('A04', async () => {
    await page.getByRole('button', { name: new RegExp(roles.author.username) }).click()
    await page.getByRole('button', { name: '退出登录', exact: true }).click()
    await expect.poll(async () => (await page.request.get('/api/v1/auth/me')).status()).toBe(401)
  })
  await step('A05', async () => { await page.goto('/dashboard/plugins'); await expect(page).toHaveURL(/\/login/) })
})

test('B. 插件上传审核、下载SHA256与安装指引 B01–B28', async ({ browser }) => {
  const page = await session(browser, 'author')
  const review = await session(browser, 'reviewer')
  await step('B01', async () => { await login(page, 'author') })
  await step('B02', async () => { await management(page) })
  const box = createBox(page)
  await step('B03', async () => { if (!await box.locator('select').first().isVisible()) await box.locator('summary').click(); await expect(box.locator('form')).toBeVisible() })
  await step('B04', async () => { await pickNamespace(box.getByRole('combobox', { name: '命名空间', exact: true })) })
  await step('B05', async () => { await box.getByLabel('标识', { exact: true }).fill(projectSlug); await expect(box.getByLabel('标识', { exact: true })).toHaveValue(projectSlug) })
  await step('B06', async () => { await box.getByLabel('展示名称', { exact: true }).fill(projectName); await expect(box.getByLabel('展示名称', { exact: true })).toHaveValue(projectName) })
  await step('B07', async () => { await box.getByLabel('可见范围').selectOption('PRIVATE'); await expect(box.getByLabel('可见范围')).toHaveValue('PRIVATE') })
  await step('B08', async () => {
    await box.getByRole('button', { name: '创建项目', exact: true }).click()
    await expect(page.locator('li', { hasText: projectSlug })).toBeVisible()
    await selectProject(page, projectSlug)
    const coordinate = await page.locator('li', { hasText: projectSlug }).textContent()
    const namespace = coordinate?.match(/@([^\s/]+)\//)?.[1]
    expect(namespace).toBeTruthy()
    const value = ledger(); value.project = { slug: projectSlug, namespace: namespace!, published: false }; save(value)
  })
  await step('B09', async () => { await page.getByLabel('版本号', { exact: true }).fill('0.1.0'); await expect(page.getByLabel('版本号', { exact: true })).toHaveValue('0.1.0') })
  await step('B10', async () => { await page.getByRole('button', { name: '创建草稿', exact: true }).click(); await expect(article(page, '0.1.0')).toBeVisible() })
  const row = article(page, '0.1.0')
  await step('B11', async () => { await row.locator('input[type=file]').setInputFiles(resolve(fixtures, 'manual-claude-demo-0.1.0.zip')); await expect(row.locator('input[type=file]')).not.toBeEmpty() })
  await step('B12', async () => { await row.getByRole('button', { name: '导入并检查' }).click(); await expect(row.getByText(/claude-code:.*0.1.0/)).toBeVisible(); await expect(row.getByText(/SHA256/)).toBeVisible() })
  await step('B13', async () => { await row.getByText('清单及文件列表', { exact: true }).click(); await expect(row.locator('pre')).toContainText('commands/greet.md') })
  await step('B14', async () => { await row.getByRole('button', { name: '提交审核' }).click(); await expect(row.getByText('已提交', { exact: true })).toBeVisible() })
  await step('B15', async () => { await login(review, 'reviewer') })
  await step('B16', async () => { await management(review) })
  await step('B17', async () => { await review.getByLabel('仅待审核', { exact: true }).check(); await expect(review.getByLabel('仅待审核', { exact: true })).toBeChecked() })
  await step('B18', async () => { await selectProject(review, projectSlug); await expect(article(review, '0.1.0')).toBeVisible() })
  const rr = article(review, '0.1.0')
  await step('B19', async () => { await rr.getByLabel('审核意见').fill(`${runId} 批准托管发布`); await expect(rr.getByLabel('审核意见')).toHaveValue(`${runId} 批准托管发布`) })
  await step('B20', async () => { await expect(rr.getByLabel('运行测试证据')).toHaveValue('UNKNOWN') })
  await step('B21', async () => { await rr.getByRole('button', { name: '批准并创建发布任务' }).click(); await expect(rr.getByRole('status').first()).toBeVisible() })
  await step('B22', async () => { await waitPublished(review, '0.1.0'); const value = ledger(); value.project!.published = true; value.project!.latest = '0.1.0'; save(value) })
  await step('B23', async () => { await page.reload(); await selectProject(page, projectSlug); await expect(article(page, '0.1.0').getByText('已发布', { exact: true })).toBeVisible() })
  let archive: Buffer
  await step('B24', async () => {
    const link = article(page, '0.1.0').getByRole('link', { name: '下载原始 ZIP' })
    const value = ledger(); value.project!.downloadUrl = (await link.getAttribute('href'))!; save(value)
    archive = await saveDownload(page, () => link.click(), 'plugin-0.1.0.zip')
  })
  await step('B25', async () => { expect(sha256(archive)).toBe(sha256(readFileSync(resolve(fixtures, 'manual-claude-demo-0.1.0.zip')))) })
  const value = ledger()
  await step('B26', async () => { await page.goto(`/plugins/${value.project!.namespace}/${projectSlug}`); await expect(page.getByRole('heading', { name: projectName, exact: true })).toBeVisible() })
  await step('B27', async () => { await page.getByRole('button', { name: '生成安装说明' }).click(); await expect(page.locator('pre').first()).toContainText('claude'); await expect(page.getByText(/尚未在你的客户端/)).toBeVisible() })
  await step('B28', async () => { await page.getByText('浏览包文件', { exact: true }).click(); await expect(page.getByRole('button', { name: 'commands/greet.md', exact: true })).toBeVisible() })
})

test('C. 驳回后实际提交和发布新版本 C01–C16', async ({ browser }) => {
  test.skip(!ledger().project?.published, 'B publication prerequisite failed; keep dependent cases blocked.')
  const slug = ledger().project!.slug
  const page = await session(browser, 'author'); await login(page, 'author'); await management(page); await selectProject(page, slug)
  const review = await session(browser, 'reviewer'); await login(review, 'reviewer')
  await step('C01', async () => { await draft(page, '0.1.1') })
  await step('C02', async () => { await article(page, '0.1.1').locator('input[type=file]').setInputFiles(resolve(fixtures, 'manual-claude-demo-0.1.1.zip')) })
  await step('C03', async () => { await article(page, '0.1.1').getByRole('button', { name: '导入并检查' }).click(); await expect(article(page, '0.1.1').getByText(/SHA256/)).toBeVisible() })
  await step('C04', async () => { await article(page, '0.1.1').getByRole('button', { name: '提交审核' }).click(); await expect(article(page, '0.1.1').getByText('已提交', { exact: true })).toBeVisible() })
  await step('C05', async () => { await reviewerProject(review, slug); await expect(article(review, '0.1.1')).toBeVisible() })
  await step('C06', async () => { await article(review, '0.1.1').getByLabel('审核意见').fill(`${runId} 驳回示例`) })
  await step('C07', async () => { await article(review, '0.1.1').getByRole('button', { name: '驳回', exact: true }).click(); await expect(article(review, '0.1.1').getByText('已驳回', { exact: true })).toBeVisible() })
  await step('C08', async () => { await page.reload(); await selectProject(page, slug); await expect(article(page, '0.1.1').getByText('已驳回', { exact: true })).toBeVisible() })
  await step('C09', async () => { await expect(article(page, '0.1.1').getByRole('button', { name: '提交审核' })).toHaveCount(0) })
  await step('C10', async () => { await draft(page, '0.1.2') })
  await step('C11', async () => { await article(page, '0.1.2').locator('input[type=file]').setInputFiles(resolve(fixtures, 'manual-claude-demo-0.1.2.zip')) })
  await step('C12', async () => { await article(page, '0.1.2').getByRole('button', { name: '导入并检查' }).click(); await expect(article(page, '0.1.2').getByText(/SHA256/)).toBeVisible() })
  await step('C13', async () => { await article(page, '0.1.2').getByRole('button', { name: '提交审核' }).click(); await expect(article(page, '0.1.2').getByText('已提交', { exact: true })).toBeVisible(); await reviewerProject(review, slug) })
  await step('C14', async () => { await article(review, '0.1.2').getByLabel('审核意见').fill(`${runId} 修正版本批准`) })
  await step('C15', async () => { await article(review, '0.1.2').getByRole('button', { name: '批准并创建发布任务' }).click(); await expect(article(review, '0.1.2').getByRole('status').first()).toBeVisible() })
  await step('C16', async () => { await waitPublished(review, '0.1.2'); const value = ledger(); value.project!.latest = '0.1.2'; save(value) })
})

test('D. 独立身份隔离和托管治理 D01–D12', async ({ browser }) => {
  test.skip(!ledger().project?.published, 'B publication prerequisite failed.')
  const state = ledger().project!
  const outsider = await session(browser, 'outsider')
  await step('D01', async () => { await login(outsider, 'outsider') })
  await step('D02', async () => { await outsider.goto(`/plugins/${state.namespace}/${state.slug}`); await expect(outsider.getByRole('alert')).toBeVisible(); await expect(outsider.getByRole('heading', { name: projectName, exact: true })).toHaveCount(0) })
  await step('D03', async () => { const response = await outsider.request.get(state.downloadUrl!); expect([403, 404]).toContain(response.status()) })
  const page = await session(browser, 'author'); await login(page, 'author')
  await step('D04', async () => { await page.goto('/admin/software'); await expect(page).not.toHaveURL(/\/admin\/software/); expect((await page.request.get('/api/v1/admin/software/apps')).status()).toBe(403) })
  const review = await session(browser, 'reviewer'); await login(review, 'reviewer'); await management(review); await selectProject(review, state.slug)
  const govern = review.locator('section').filter({ has: review.getByRole('heading', { name: '插件目录治理', exact: true }) }).first()
  await step('D05', async () => { await expect(govern).toBeVisible() })
  await step('D06', async () => { await govern.getByLabel('治理原因').fill(`${runId} 治理测试`); await expect(govern.getByRole('button', { name: '隐藏', exact: true })).toBeEnabled() })
  await step('D07', async () => { await govern.getByRole('button', { name: '隐藏', exact: true }).click(); await expect(govern.getByRole('button', { name: '恢复显示', exact: true })).toBeVisible() })
  await step('D08', async () => { await govern.getByRole('button', { name: '恢复显示', exact: true }).click(); await expect(govern.getByRole('button', { name: '隐藏', exact: true })).toBeVisible() })
  await step('D09', async () => { await govern.getByRole('button', { name: '归档', exact: true }).click(); await expect(govern.getByRole('button', { name: '恢复归档', exact: true })).toBeVisible() })
  await step('D10', async () => { await govern.getByRole('button', { name: '恢复归档', exact: true }).click(); await expect(govern.getByRole('button', { name: '归档', exact: true })).toBeVisible() })
  const row = article(review, '0.1.2')
  await step('D11', async () => { await row.getByLabel('治理原因').fill(`${runId} 版本下架`); await expect(row.getByRole('button', { name: '下架版本', exact: true })).toBeEnabled() })
  await step('D12', async () => { await row.getByRole('button', { name: '下架版本', exact: true }).click(); await expect(row.getByText('已下架', { exact: true })).toBeVisible() })
})

test('E. 软件手工ZIP发布与真实下载SHA256 E01–E27', async ({ browser }) => {
  const page = await session(browser, 'software-admin')
  await step('E01', async () => { await login(page, 'software') })
  await step('E02', async () => { await softwarePage(page) })
  await step('E03', async () => { await page.getByRole('button', { name: '新建应用', exact: true }).click(); await expect(page.getByRole('dialog')).toBeVisible() })
  await step('E04', async () => { await page.locator('#software-app-slug').fill(appSlug); await expect(page.locator('#software-app-slug')).toHaveValue(appSlug) })
  await step('E05', async () => { await page.locator('#software-app-name').fill(appName); await expect(page.locator('#software-app-name')).toHaveValue(appName) })
  await step('E06', async () => { await page.getByRole('dialog').getByRole('button', { name: '保存', exact: true }).click(); await expect(appRow(page)).toBeVisible(); const value = ledger(); value.application = { slug: appSlug, published: false }; save(value) })
  await step('E07', async () => { await appRow(page).getByRole('button', { name: '版本', exact: true }).click(); await expect(page.getByText(`${appName} 的版本`, { exact: true })).toBeVisible() })
  await step('E08', async () => { await page.getByRole('button', { name: '新建版本', exact: true }).click(); await expect(page.getByRole('dialog')).toBeVisible() })
  await step('E09', async () => { await page.locator('#software-release-version').fill('1.0.0'); await expect(page.locator('#software-release-version')).toHaveValue('1.0.0') })
  await step('E10', async () => { await page.getByRole('dialog').getByRole('button', { name: '保存', exact: true }).click(); await expect(releasePanel(page, '1.0.0')).toBeVisible() })
  const panel = releasePanel(page, '1.0.0')
  await step('E11', async () => { await panel.getByRole('button', { name: '上传安装包', exact: true }).click(); await expect(page.getByRole('dialog')).toBeVisible() })
  await step('E12', async () => { await page.locator('#software-upload-file').setInputFiles(resolve(fixtures, 'software-1.0.0/portable.zip')) })
  await step('E13', async () => { await expect(page.getByRole('dialog').getByRole('combobox').nth(0)).toHaveText('zip') })
  await step('E14', async () => { await expect(page.getByRole('dialog').getByRole('combobox').nth(1)).toHaveText('windows') })
  await step('E15', async () => { await expect(page.getByRole('dialog').getByRole('combobox').nth(2)).toHaveText('x86_64') })
  await step('E16', async () => { await page.locator('#software-upload-version').fill('1.0.0'); await expect(page.locator('#software-upload-version')).toHaveValue('1.0.0') })
  await step('E17', async () => { await page.getByRole('dialog').getByRole('button', { name: '上传', exact: true }).click(); await expect(panel.locator('tr', { hasText: 'portable.zip' })).toBeVisible({ timeout: 60_000 }) })
  const row = panel.locator('tr', { hasText: 'portable.zip' }).first()
  await step('E18', async () => { await row.getByRole('button', { name: '确认', exact: true }).click(); await expect(page.getByRole('dialog')).toBeVisible() })
  await step('E19', async () => { await page.locator('#software-confirm-raw-fields').fill(JSON.stringify({ ProductVersion: '1.0.0', ManualEntry: runId })); expect(JSON.parse(await page.locator('#software-confirm-raw-fields').inputValue())).toHaveProperty('ProductVersion', '1.0.0') })
  await step('E20', async () => { await page.getByRole('dialog').getByRole('button', { name: '确认', exact: true }).click(); await expect(row.getByRole('button', { name: '发布', exact: true })).toBeVisible() })
  await step('E21', async () => { await row.getByRole('button', { name: '发布', exact: true }).click(); await expect(row.getByText('已发布', { exact: true })).toBeVisible() })
  await step('E22', async () => { await appRow(page).getByRole('button', { name: '发布', exact: true }).click(); await expect(appRow(page).getByRole('button', { name: '下架', exact: true })).toBeVisible(); const value = ledger(); value.application!.published = true; save(value) })
  const user = await session(browser, 'ordinary-user'); await login(user, 'author')
  await step('E23', async () => { await user.goto('/software'); await expect(user.getByText(appName, { exact: true })).toBeVisible() })
  await step('E24', async () => { await user.getByText(appName, { exact: true }).click(); await expect(user.getByText('1.0.0', { exact: true }).first()).toBeVisible() })
  let download: Buffer
  await step('E25', async () => { download = await saveDownload(user, () => user.getByRole('button', { name: '下载', exact: true }).first().click(), 'software-1.0.0.zip') })
  await step('E26', async () => { expect(sha256(download)).toBe(sha256(readFileSync(resolve(fixtures, 'software-1.0.0/portable.zip')))) })
  const anon = await session(browser, 'anonymous')
  await step('E27', async () => { await anon.goto('/software'); await expect(anon).toHaveURL(/\/login/) })
})

test('F. BAIZE更新草稿、匿名开关和显式版本切换 F01–F34', async ({ browser }) => {
  test.skip(!ledger().application?.published, 'E software publication prerequisite failed.')
  const page = await session(browser, 'software-admin'); await login(page, 'software')
  await step('F01', async () => { await openSoftware(page); await expect(updateManager(page, '1.0.0')).toBeVisible() })
  const manager = updateManager(page, '1.0.0')
  await step('F02', async () => { await manager.getByLabel('机器版本号', { exact: true }).fill('1.0.0'); await expect(manager.getByLabel('机器版本号', { exact: true })).toHaveValue('1.0.0') })
  await step('F03', async () => { await manager.getByLabel('操作系统').selectOption('WINDOWS'); await expect(manager.getByLabel('操作系统')).toHaveValue('WINDOWS') })
  await step('F04', async () => { await manager.getByLabel('架构').selectOption('X86_64'); await expect(manager.getByLabel('架构')).toHaveValue('X86_64') })
  await step('F05', async () => { await manager.getByLabel('渠道').selectOption('stable'); await expect(manager.getByLabel('渠道')).toHaveValue('stable') })
  await step('F06', async () => { await manager.getByLabel('变体', { exact: true }).fill('default'); await expect(manager.getByLabel('变体', { exact: true })).toHaveValue('default') })
  await step('F07', async () => { await manager.getByLabel('更新协议').selectOption('BAIZE'); await expect(manager.getByLabel('Pi 版本', { exact: true })).toBeVisible() })
  await step('F08', async () => { await manager.getByLabel('Pi 版本', { exact: true }).fill('0.70.0'); await expect(manager.getByLabel('Pi 版本', { exact: true })).toHaveValue('0.70.0') })
  await step('F09', async () => { await manager.getByRole('button', { name: '创建更新草稿', exact: true }).click(); await expect(snapshot(page, '1.0.0')).toBeVisible() })
  const row = snapshot(page, '1.0.0')
  await step('F10', async () => { await row.getByLabel('相对路径 / 上传目录', { exact: true }).fill('dist/portable.zip'); await expect(row.getByLabel('相对路径 / 上传目录', { exact: true })).toHaveValue('dist/portable.zip') })
  await step('F11', async () => { const select = row.getByLabel('已发布安装包'); await expect(select.locator('option')).toHaveCount(2); await select.selectOption({ label: await select.locator('option').nth(1).textContent() ?? '' }); expect(await select.inputValue()).not.toBe('') })
  await step('F12', async () => { await row.getByRole('button', { name: '关联安装包', exact: true }).click(); await expect(row.locator('code', { hasText: 'dist/portable.zip' })).toBeVisible() })
  await step('F13', async () => { await row.getByLabel('相对路径 / 上传目录', { exact: true }).fill('dist/latest.json') })
  await step('F14', async () => { await row.getByLabel('资产用途').selectOption('METADATA'); await expect(row.getByLabel('资产用途')).toHaveValue('METADATA') })
  await step('F15', async () => { await row.locator('input[type=file]').setInputFiles(resolve(fixtures, 'software-1.0.0/latest.json')) })
  await step('F16', async () => { await row.getByRole('button', { name: '上传资产', exact: true }).click(); await expect(row.locator('code', { hasText: 'dist/latest.json' })).toBeVisible() })
  await step('F17', async () => { await row.getByLabel('相对路径 / 上传目录', { exact: true }).fill('dist/notes.html') })
  await step('F18', async () => { await row.getByLabel('资产用途').selectOption('OTHER'); await expect(row.getByLabel('资产用途')).toHaveValue('OTHER') })
  await step('F19', async () => { await row.locator('input[type=file]').setInputFiles(resolve(fixtures, 'software-1.0.0/notes.html')) })
  await step('F20', async () => { await row.getByRole('button', { name: '上传资产', exact: true }).click(); await expect(row.locator('code', { hasText: 'dist/notes.html' })).toBeVisible() })
  await step('F21', async () => { await row.getByRole('button', { name: '校验', exact: true }).click(); await expect(row.getByRole('status')).toHaveText('校验通过') })
  await step('F22', async () => { await row.getByRole('button', { name: '发布快照', exact: true }).click(); await expect(row.getByText('已发布', { exact: true })).toBeVisible() })
  const source = sourceManager(page)
  await step('F23', async () => { await source.getByLabel('渠道标识', { exact: true }).fill('manual-stable') })
  await step('F24', async () => { await source.getByLabel('虚拟 GitLab 项目路径', { exact: true }).fill(`manual/${appSlug}`) })
  await step('F25', async () => { await source.getByLabel('已发布目标').selectOption({ label: await source.getByLabel('已发布目标').locator('option').nth(1).textContent() ?? '' }) })
  await step('F26', async () => { await source.getByRole('button', { name: '保存渠道目标', exact: true }).click(); await expect(source.locator('code', { hasText: 'manual-stable' })).toBeVisible(); await expect(source.getByLabel('更新清单地址', { exact: true })).toBeVisible() })
  await step('F27', async () => { await expect(source.getByLabel('允许匿名读取更新', { exact: true })).not.toBeChecked(); await expect(source.getByRole('status')).toContainText('关闭') })
  const anon = await session(browser, 'anonymous-update-client')
  const feed = await source.getByLabel('更新清单地址', { exact: true }).inputValue()
  await step('F28', async () => { const r = await anon.goto(feed); expect([401, 403, 404]).toContain(r!.status()); expect(r!.headers()['content-type']).not.toContain('text/html') })
  await step('F29', async () => { await source.getByLabel('允许匿名读取更新', { exact: true }).click(); await expect(source.getByLabel('允许匿名读取更新', { exact: true })).toBeChecked(); await expect(source.getByLabel('允许匿名读取更新', { exact: true })).toBeChecked() })
  let manifest: components['schemas']['SoftwareBaizeUpdateManifest']
  await step('F30', async () => {
    await expect.poll(async () => (await anon.request.get(feed)).status()).toBe(200)
    const response = await anon.request.get(feed)
    expect(response.headers()['content-type']).toContain('application/json')
    const bytes = await saveDownload(anon, async () => {
      await anon.goto(feed).catch(error => { if (!String(error).includes('Download is starting')) throw error })
    }, 'latest-1.0.0.json')
    manifest = JSON.parse(bytes.toString('utf8')) as components['schemas']['SoftwareBaizeUpdateManifest']
    expect(manifest.version).toBe('1.0.0')
    writeFileSync(resolve(runRoot, 'feed-1.0.0.json'), JSON.stringify(manifest, null, 2))
  })
  let downloaded: Buffer
  await step('F31', async () => { const url = new URL(manifest.package!, feed).href; downloaded = await saveDownload(anon, async () => { await anon.goto(url).catch(error => { if (!String(error).includes('Download is starting')) throw error }) }, 'update-1.0.0.zip') })
  await step('F32', async () => { expect(sha256(downloaded)).toBe(manifest.sha256); expect(sha256(downloaded)).toBe(sha256(readFileSync(resolve(fixtures, 'software-1.0.0/portable.zip')))) })
  await step('F33', async () => { await source.getByLabel('允许匿名读取更新', { exact: true }).click(); await expect(source.getByLabel('允许匿名读取更新', { exact: true })).not.toBeChecked(); await expect(source.getByLabel('允许匿名读取更新', { exact: true })).not.toBeChecked() })
  await step('F34', async () => { await expect.poll(async () => (await anon.request.get(feed)).status()).toBe(404) })
  await test.step('F扩展：上传并发布1.1.0，只有显式切换渠道后才更新清单', async () => {
    await source.getByLabel('允许匿名读取更新', { exact: true }).click(); await expect(source.getByLabel('允许匿名读取更新', { exact: true })).toBeChecked()
    await uploadSoftwareVersion(page, '1.1.0')
    await createUpdateVersion(page, '1.1.0')
    const unchanged = await anon.request.get(feed)
    expect((await unchanged.json() as components['schemas']['SoftwareBaizeUpdateManifest']).version).toBe('1.0.0')
    const option = source.getByLabel('已发布目标').locator('option').filter({ hasText: 'v1.1.0' })
    await source.getByLabel('已发布目标').selectOption(await option.getAttribute('value') ?? '')
    await source.getByRole('button', { name: '保存渠道目标', exact: true }).click()
    await expect.poll(async () => (await (await anon.request.get(feed)).json() as components['schemas']['SoftwareBaizeUpdateManifest']).version).toBe('1.1.0')
    const updated = await (await anon.request.get(feed)).json() as components['schemas']['SoftwareBaizeUpdateManifest']
    expect(sha256(await (await anon.request.get(new URL(updated.package!, feed).href)).body())).toBe(sha256(readFileSync(resolve(fixtures, 'software-1.1.0/portable.zip'))))
    writeFileSync(resolve(runRoot, 'feed-1.1.0.json'), JSON.stringify(updated, null, 2))
    await source.getByLabel('允许匿名读取更新', { exact: true }).click(); await expect(source.getByLabel('允许匿名读取更新', { exact: true })).not.toBeChecked()
    const value = ledger(); value.notes.push('F extension passed: v1.1.0 publication did not move the channel until explicit selection; new download SHA256 matched. Anonymous source disabled after test.'); save(value)
  })
})

test('G. 版本不匹配与不安全归档拒绝 G01–G05', async ({ browser }) => {
  const page = await session(browser, 'author'); await login(page, 'author'); await management(page)
  await step('G01', async () => { await createProject(page, `${runId}-negative`, `${runId} 负例`); await selectProject(page, `${runId}-negative`); await draft(page, '0.1.0') })
  const row = article(page, '0.1.0')
  await step('G02', async () => { await row.locator('input[type=file]').setInputFiles(resolve(fixtures, 'manual-claude-demo-0.1.1.zip')) })
  await step('G03', async () => { const response = page.waitForResponse(r => r.url().includes('/archive') && r.request().method() === 'POST'); await row.getByRole('button', { name: '导入并检查' }).click(); expect((await response).status()).toBe(400); await expect(row.getByRole('alert')).toBeVisible(); await expect(row.getByRole('button', { name: '提交审核' })).toBeDisabled() })
  const panel = page.locator('section').filter({ has: page.getByRole('heading', { name: '离线插件导入', exact: true }) }).first()
  await step('G04', async () => { await pickNamespace(panel.getByLabel('导入命名空间')); await panel.locator('input[type=file]').setInputFiles(resolve(fixtures, 'unsafe-path.zip')) })
  await step('G05', async () => { const response = page.waitForResponse(r => r.url().includes('/imports?') && r.request().method() === 'POST'); await panel.getByRole('button', { name: '预览归档', exact: true }).click(); expect((await response).status()).toBe(400); await expect(panel.getByRole('alert')).toBeVisible(); await expect(panel.getByRole('button', { name: '导入选中草稿' })).toHaveCount(0) })
})
