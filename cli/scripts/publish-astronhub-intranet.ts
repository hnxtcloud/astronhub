import { createHash } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { parseArgs } from 'node:util'
import spawn from 'cross-spawn'

async function publish(): Promise<void> {
  const { values } = parseArgs({ options: {
    registry: { type: 'string' },
    'dry-run': { type: 'boolean', default: false },
    help: { type: 'boolean', short: 'h', default: false }
  } })
  if (values.help) {
    console.log('Usage: node publish.mjs --registry <private npm hosted URL> [--dry-run]')
    return
  }
  if (!values.registry) throw new Error('Specify --registry with your writable private npm hosted repository URL')
  const registry = new URL(values.registry)
  if (!['http:', 'https:'].includes(registry.protocol) || registry.username || registry.password || registry.search || registry.hash) {
    throw new Error('Registry must be an HTTP(S) URL without credentials, query or fragment')
  }
  if (['npmjs.org', 'npmjs.com'].some(domain => registry.hostname === domain || registry.hostname.endsWith(`.${domain}`))) {
    throw new Error('This transfer kit publishes to a private registry; the public npm registry is not supported')
  }
  const root = dirname(fileURLToPath(import.meta.url))
  const release: unknown = JSON.parse(await readFile(join(root, 'release.json'), 'utf8'))
  if (typeof release !== 'object' || release === null) throw new Error('Invalid release.json')
  const meta = release as Record<string, unknown>
  if (meta.name !== 'astronhub-cli' || typeof meta.version !== 'string'
    || !/^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$/.test(meta.version)
    || meta.file !== `astronhub-cli-${meta.version}.tgz`
    || typeof meta.sha256 !== 'string' || !/^[a-f0-9]{64}$/.test(meta.sha256)) {
    throw new Error('Invalid release metadata; copy the complete original transfer kit')
  }
  const archive = join(root, meta.file as string)
  const checksum = createHash('sha256').update(await readFile(archive)).digest('hex')
  if (checksum !== meta.sha256) throw new Error('SHA-256 mismatch; recopy the original package before publishing')
  console.log(`SHA-256 verified: ${meta.file}`)
  console.log(`${values['dry-run'] ? 'Preview' : 'Publish'} ${meta.name}@${meta.version} -> ${registry.href}`)
  const args = ['publish', archive, '--registry', registry.href, '--ignore-scripts', '--provenance=false', '--tag', 'latest']
  if (values['dry-run']) args.push('--dry-run')
  await new Promise<void>((resolve, reject) => {
    const child = spawn('npm', args, { stdio: 'inherit', cwd: root })
    child.once('error', () => reject(new Error('Cannot start npm; install Node.js 22+ with npm and check PATH')))
    child.once('exit', code => {
      if (code === 0) resolve()
      else reject(new Error(`npm publish failed (exit ${code}); check repository type, login, permissions and existing version`))
    })
  })
  console.log(values['dry-run'] ? 'Dry run complete; no package was uploaded.' : `Published ${meta.name}@${meta.version}`)
}

try { await publish() } catch (error) {
  console.error(error instanceof Error ? error.message : 'Publish failed')
  process.exitCode = 1
}
