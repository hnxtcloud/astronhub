import { access, readFile } from 'node:fs/promises'
import { dirname, join, resolve } from 'node:path'
import spawn from 'cross-spawn'
import { validRange } from 'semver'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'
import { readConfig, registryUrl, type AstronConfig } from './config'
import { startRegistryProxy } from './registry-proxy'

type Manager = 'npm' | 'pnpm'
export interface NodeOptions {
  pm?: string
  registry?: string
  dev?: boolean
  exact?: boolean
  dryRun?: boolean
  ignoreScripts?: boolean
  filter?: string
  workspaceRoot?: boolean
  json?: boolean
  nonInteractive?: boolean
}

async function exists(path: string): Promise<boolean> {
  try { await access(path); return true } catch { return false }
}

function managerName(value: string): Manager {
  if (value !== 'npm' && value !== 'pnpm') throw new CliError('Package manager must be npm or pnpm', EXIT.usage)
  return value
}

export async function detectManager(cwd: string, explicit?: string, configured?: AstronConfig['packageManager']): Promise<Manager> {
  if (explicit) return managerName(explicit)
  // Walk up for workspace declarations, but let a nearer declaration or lockfile win.
  for (let dir = resolve(cwd); ; dir = dirname(dir)) {
    let pkg: { packageManager?: unknown } = {}
    try { pkg = JSON.parse(await readFile(join(dir, 'package.json'), 'utf8')) as typeof pkg } catch (error) {
      if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw new CliError('Cannot read project package.json', EXIT.usage)
    }
    if (typeof pkg.packageManager === 'string') return managerName(pkg.packageManager.split('@')[0]!)
    const npm = await exists(join(dir, 'package-lock.json')) || await exists(join(dir, 'npm-shrinkwrap.json'))
    const pnpm = await exists(join(dir, 'pnpm-lock.yaml')) || await exists(join(dir, 'pnpm-workspace.yaml'))
    if (npm && pnpm) throw new CliError('Conflicting lockfiles; pass --pm npm or --pm pnpm', EXIT.usage)
    if (pnpm) return 'pnpm'
    if (npm) return 'npm'
    if (dirname(dir) === dir) break
  }
  return configured ?? 'npm'
}

export function validatePackage(spec: string): void {
  // Registry package specs only. Preserve npm semver ranges/tags without shell evaluation.
  const match = spec.match(/^((?:@[a-z0-9][a-z0-9._-]*\/)?[a-z0-9][a-z0-9._-]*)(?:@([^\r\n]+))?$/i)
  const version = match?.[2]
  if (!match || (version !== undefined && !validRange(version) && !/^[a-zA-Z][a-zA-Z0-9._-]*$/.test(version))) {
    throw new CliError(`Expected a registry package name with optional version: ${spec}`, EXIT.usage)
  }
}

export async function nodeInstall(packages: string[], options: NodeOptions, cwd = process.cwd()) {
  packages.forEach(validatePackage)
  if (!packages.length && (options.dev || options.exact)) throw new CliError('-D and -E require package names', EXIT.usage)
  const config = await readConfig()
  const pm = await detectManager(cwd, options.pm, config.packageManager)
  if (pm !== 'pnpm' && (options.filter || options.workspaceRoot)) throw new CliError('--filter and -w require pnpm', EXIT.usage)
  const value = options.registry || process.env.ASTRONHUB_NPM_REGISTRY || config.npmRegistry
  if (!value) throw new CliError('Configure your npm private registry first', EXIT.usage, {
    next: 'astronhub config set npmRegistry <url>, or pass --registry <url>'
  })
  const registry = registryUrl(value)
  const args = [
    ...(options.filter ? ['--filter', options.filter] : []),
    pm === 'pnpm' && packages.length ? 'add' : 'install',
    ...packages,
    ...(options.dev ? ['--save-dev'] : []),
    ...(options.exact ? ['--save-exact'] : []),
    ...(options.ignoreScripts ? ['--ignore-scripts'] : []),
    ...(options.workspaceRoot ? ['--workspace-root'] : []),
    ...(pm === 'npm' ? ['--no-audit', '--no-fund'] : [])
  ]
  const plan = { ok: true, dryRun: Boolean(options.dryRun), pm, cwd, registry, packages, args, compatibilityGateway: true }
  if (options.dryRun) return plan
  // The gateway covers indirect dependencies too. dist.tarball URLs remain upstream URLs,
  // so package managers can retain stable download locations in their lockfiles.
  const proxy = await startRegistryProxy(registry, process.env.ASTRONHUB_NPM_TOKEN)
  const forwardedSignals: Array<[NodeJS.Signals, () => void]> = []
  try {
    await new Promise<void>((resolveRun, reject) => {
      const child = spawn(pm, [...args, '--registry', proxy.url], {
        cwd,
        env: { ...process.env, ...(options.nonInteractive || options.json ? { CI: 'true' } : {}) },
        stdio: [options.nonInteractive || options.json ? 'ignore' : 'inherit', options.json ? 'pipe' : 'inherit', 'inherit']
      })
      // In JSON mode reserve stdout for the structured final result.
      child.stdout?.pipe(process.stderr)
      for (const signal of ['SIGINT', 'SIGTERM'] as const) {
        const handler = () => { child.kill(signal) }
        forwardedSignals.push([signal, handler])
        process.once(signal, handler)
      }
      child.once('error', () => reject(new CliError(`Cannot start ${pm}; install it and check PATH`, EXIT.usage)))
      child.once('exit', (code, signal) => code === 0
        ? resolveRun()
        : reject(new CliError(`${pm} installation failed${signal ? ` (${signal})` : ''}`, EXIT.generic, { packageManagerExitCode: code })))
    })
    return plan
  } finally {
    for (const [signal, handler] of forwardedSignals) process.removeListener(signal, handler)
    await proxy.close()
  }
}
