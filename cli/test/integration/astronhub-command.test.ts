import { afterEach, describe, expect, test } from 'bun:test'
import { createHash } from 'node:crypto'
import { mkdtemp, mkdir, readFile, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { gzipSync } from 'node:zlib'
import { zipSync, strToU8 } from 'fflate'
import { startFakeRegistry } from '../helpers/fake-registry'
import { detectManager } from '../../src/astronhub/node-install'
import { startRegistryProxy } from '../../src/astronhub/registry-proxy'

const entry = process.env.ASTRONHUB_TEST_ENTRY || fileURLToPath(new URL('../../src/astronhub.ts', import.meta.url))
const runtime = process.env.ASTRONHUB_TEST_ENTRY ? 'node' : process.execPath
const cleanup: Array<() => void | Promise<void>> = []
afterEach(async () => { for (const close of cleanup.splice(0).reverse()) await close() })

async function temporaryProject() {
  const root = await mkdtemp(join(tmpdir(), 'astronhub-test-'))
  const cwd = join(root, 'project')
  const home = join(root, 'home')
  await mkdir(cwd)
  await mkdir(home)
  await writeFile(join(cwd, 'package.json'), JSON.stringify({ name: 'fixture-app', version: '1.0.0', private: true }))
  return { root, cwd, home }
}

async function run(args: string[], fixture: Awaited<ReturnType<typeof temporaryProject>>, extra: Record<string, string> = {}) {
  const env = Object.fromEntries(Object.entries(process.env).filter(([key]) => !/^(ASTRONHUB_|SKILLHUB_)/i.test(key)))
  const child = Bun.spawn([runtime, entry, ...args], {
    cwd: fixture.cwd,
    env: { ...env, CI: 'true', HOME: fixture.home, USERPROFILE: fixture.home, ASTRONHUB_HOME: join(fixture.home, '.astronhub'), ...extra },
    stdout: 'pipe', stderr: 'pipe'
  })
  const [stdout, stderr, code] = await Promise.all([new Response(child.stdout).text(), new Response(child.stderr).text(), child.exited])
  return { stdout, stderr, code }
}

/** Build a small real npm tarball without invoking a package manager or the network. */
function tarball(pkg: Record<string, unknown>) {
  const files = { 'package/package.json': JSON.stringify(pkg), 'package/index.js': 'module.exports = 42\n' }
  const blocks: Buffer[] = []
  for (const [path, text] of Object.entries(files)) {
    const bytes = Buffer.from(text)
    const header = Buffer.alloc(512)
    header.write(path)
    header.write('0000644\0', 100)
    header.write('0000000\0', 108)
    header.write('0000000\0', 116)
    header.write(`${bytes.length.toString(8).padStart(11, '0')}\0`, 124)
    header.write('00000000000\0', 136)
    header.fill(32, 148, 156)
    header.write('0', 156)
    header.write('ustar\0', 257)
    header.write('00', 263)
    const checksum = header.reduce((sum, byte) => sum + byte, 0)
    header.write(`${checksum.toString(8).padStart(6, '0')}\0 `, 148)
    blocks.push(header, bytes, Buffer.alloc((512 - bytes.length % 512) % 512))
  }
  blocks.push(Buffer.alloc(1024))
  return gzipSync(Buffer.concat(blocks))
}

function npmFixture() {
  const requests: string[] = []
  const packages = new Map([
    ['@fixture/parent', { name: '@fixture/parent', version: '1.0.0', main: 'index.js', dependencies: { '@fixture/child': '^1.0.0' } }],
    ['@fixture/child', { name: '@fixture/child', version: '1.0.0', main: 'index.js' }]
  ])
  const archives = new Map([...packages].map(([name, pkg]) => [name, tarball(pkg)]))
  const server = Bun.serve({
    port: 0,
    hostname: '127.0.0.1',
    fetch(request): Response {
      const path = new URL(request.url).pathname
      requests.push(path)
      if (/%2f/i.test(path)) return new Response('Encoded slashes rejected', { status: 400 })
      for (const [name, pkg] of packages) {
        const archive = archives.get(name)!
        if (path === `/repository/npm-public/${name}/-/${name.split('/')[1]}-1.0.0.tgz`) {
          return new Response(archive, { headers: { 'content-type': 'application/octet-stream' } })
        }
        if (path === `/repository/npm-public/${name}`) {
          const version = { ...pkg, dist: {
            tarball: `${server.url}repository/npm-public/${name}/-/${name.split('/')[1]}-1.0.0.tgz`,
            integrity: `sha512-${createHash('sha512').update(archive).digest('base64')}`
          } }
          return Response.json({ name, 'dist-tags': { latest: '1.0.0' }, versions: { '1.0.0': version } })
        }
      }
      return Response.json({ error: 'not found' }, { status: 404 })
    }
  })
  cleanup.push(() => server.stop(true))
  return { url: `${server.url}repository/npm-public/`, requests }
}

describe('AstronHub CLI', () => {
  test('config is isolated; dry-run selects pnpm without contacting the registry', async () => {
    const fixture = await temporaryProject()
    expect((await run(['config', 'set', 'npmRegistry', 'http://127.0.0.1:1/private/'], fixture)).code).toBe(0)
    await writeFile(join(fixture.cwd, 'pnpm-lock.yaml'), 'lockfileVersion: 9.0\n')
    const result = await run(['install', '@types/node@26.6.5', '-D', '--dry-run', '--json'], fixture)
    expect(result.code).toBe(0)
    expect(JSON.parse(result.stdout)).toMatchObject({ pm: 'pnpm', registry: 'http://127.0.0.1:1/private/', dryRun: true })
    expect(await readFile(join(fixture.cwd, 'package.json'), 'utf8')).not.toContain('@types/node')
    const alias = await run(['node', 'add', 'axios', '--dry-run', '--json'], fixture)
    expect(alias.code).toBe(0)
    expect(JSON.parse(alias.stdout)).toMatchObject({ pm: 'pnpm', packages: ['axios'] })
  })

  test('detects workspace declaration, rejects conflicting locks, honors explicit override', async () => {
    const fixture = await temporaryProject()
    await writeFile(join(fixture.cwd, 'pnpm-lock.yaml'), '')
    await writeFile(join(fixture.cwd, 'package-lock.json'), '{}')
    await expect(detectManager(fixture.cwd)).rejects.toThrow('Conflicting lockfiles')
    expect(await detectManager(fixture.cwd, 'npm')).toBe('npm')
    await writeFile(join(fixture.cwd, 'package.json'), JSON.stringify({ packageManager: 'pnpm@10.0.0' }))
    const nested = join(fixture.cwd, 'packages/app')
    await mkdir(nested, { recursive: true })
    expect(await detectManager(nested)).toBe('pnpm')
  })

  test('missing config, unknown commands and invalid package arguments fail in JSON', async () => {
    const fixture = await temporaryProject()
    for (const args of [['install', 'axios'], ['unknown'], ['install', '../outside'], ['install', 'foo@owner/repo']]) {
      const result = await run([...args, '--json'], fixture)
      expect(result.code).toBe(5)
      expect(JSON.parse(result.stderr).ok).toBe(false)
    }
  })

  test('init preserves instructions, is idempotent and refuses to overwrite a different skill', async () => {
    const fixture = await temporaryProject()
    await writeFile(join(fixture.cwd, 'AGENTS.md'), '# Existing instructions\n')
    expect((await run(['init', '-a', 'codex', '--json'], fixture)).code).toBe(0)
    expect((await run(['init', '-a', 'codex', '--json'], fixture)).code).toBe(0)
    const instructions = await readFile(join(fixture.cwd, 'AGENTS.md'), 'utf8')
    expect(instructions.startsWith('# Existing instructions')).toBe(true)
    expect(instructions.match(/<!-- astronhub-cli -->/g)?.length).toBe(1)
    await writeFile(join(fixture.cwd, '.agents/skills/astronhub/SKILL.md'), 'custom skill')
    const conflict = await run(['init', '-a', 'codex', '--json'], fixture)
    expect(conflict.code).toBe(4)
    expect(await readFile(join(fixture.cwd, '.agents/skills/astronhub/SKILL.md'), 'utf8')).toBe('custom skill')
  })

  test('gateway forwards tokens only upstream and translates scoped paths', async () => {
    const received: Array<{ path: string; token: string | null }> = []
    const upstream = Bun.serve({ port: 0, hostname: '127.0.0.1', fetch(request) {
      received.push({ path: new URL(request.url).pathname, token: request.headers.get('authorization') })
      return Response.json({ name: '@scope/pkg' })
    } })
    cleanup.push(() => upstream.stop(true))
    const proxy = await startRegistryProxy(`${upstream.url}private/`, 'private-test-token')
    cleanup.push(() => proxy.close())
    expect((await fetch(`${proxy.url}@scope%2fpkg`)).status).toBe(200)
    expect(received).toEqual([{ path: '/private/@scope/pkg', token: 'Bearer private-test-token' }])
    expect((await fetch(proxy.url, { method: 'POST' })).status).toBe(405)
  })

  for (const pm of ['npm', 'pnpm']) {
    test(`${pm} installs scoped direct and indirect dependencies and writes stable lock URLs`, async () => {
      const fixture = await temporaryProject()
      const registry = npmFixture()
      if (pm === 'pnpm') await writeFile(join(fixture.cwd, 'pnpm-workspace.yaml'), `storeDir: ${JSON.stringify(join(fixture.root, 'pnpm-store'))}\n`)
      const result = await run(['install', '@fixture/parent@1.0.0', '-D', '--pm', pm, '--registry', registry.url, '--ignore-scripts', '--non-interactive', '--json'], fixture, {
        npm_config_cache: join(fixture.root, 'npm-cache')
      })
      if (result.code !== 0) console.error(result.stderr)
      expect(result.code).toBe(0)
      expect(JSON.parse(result.stdout).pm).toBe(pm)
      const pkg = JSON.parse(await readFile(join(fixture.cwd, 'package.json'), 'utf8'))
      expect(pkg.devDependencies['@fixture/parent']).toBeDefined()
      expect(registry.requests).toContain('/repository/npm-public/@fixture/parent')
      expect(registry.requests).toContain('/repository/npm-public/@fixture/child')
      expect(registry.requests.some(path => /%2f/i.test(path))).toBe(false)
      const lock = await readFile(join(fixture.cwd, pm === 'npm' ? 'package-lock.json' : 'pnpm-lock.yaml'), 'utf8')
      const ports = [...lock.matchAll(/127\.0\.0\.1:(\d+)/g)].map(match => match[1])
      expect(ports.every(port => port === new URL(registry.url).port)).toBe(true)
      expect(await readFile(join(fixture.cwd, 'node_modules/@fixture/parent/index.js'), 'utf8')).toContain('42')
      // A second install must work from the saved manifest/lockfile through a fresh gateway.
      const reinstall = await run(['install', '--pm', pm, '--registry', registry.url, '--ignore-scripts', '--non-interactive', '--json'], fixture)
      if (reinstall.code !== 0) console.error(reinstall.stderr)
      expect(reinstall.code).toBe(0)
    }, 60_000)
  }

  test('Skill add uses existing authenticated API, validates/extracts package and records inventory', async () => {
    const fixture = await temporaryProject()
    const skillText = '---\nname: demo\ndescription: A fixture\n---\n# Demo\n'
    const registry = await startFakeRegistry({ token: 'sk_fixture', skills: [{ namespace: 'global', slug: 'demo', version: '1.2.3', zipBytes: zipSync({ 'SKILL.md': strToU8(skillText) }) }] })
    cleanup.push(() => registry.stop())
    const result = await run(['skill', 'add', '@global/demo@1.2.3', '-a', 'codex', '--registry', registry.url, '--non-interactive', '--json'], fixture, { ASTRONHUB_TOKEN: 'sk_fixture' })
    if (result.code !== 0) console.error(result.stderr)
    expect(result.code).toBe(0)
    expect(JSON.parse(result.stdout)).toMatchObject({ ok: true, namespace: 'global', slug: 'demo' })
    expect(await readFile(join(fixture.cwd, '.agents/skills/demo/SKILL.md'), 'utf8')).toBe(skillText)
    expect(JSON.parse(await readFile(join(fixture.home, '.skillhub/inventory.json'), 'utf8')).items).toHaveLength(1)
    const list = await run(['skill', 'list', '--registry', registry.url, '--json'], fixture)
    expect(JSON.parse(list.stdout).items).toHaveLength(1)
  })
})
