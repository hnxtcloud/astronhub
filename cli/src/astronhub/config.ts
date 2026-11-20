import { mkdir, readFile, rename, writeFile } from 'node:fs/promises'
import { homedir } from 'node:os'
import { dirname, join } from 'node:path'
import { randomUUID } from 'node:crypto'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'

export interface AstronConfig {
  npmRegistry?: string
  skillRegistry?: string
  packageManager?: 'npm' | 'pnpm'
}

export function configPath(): string {
  return join(process.env.ASTRONHUB_HOME || join(homedir(), '.astronhub'), 'config.json')
}

export function registryUrl(value: string): string {
  let url: URL
  try { url = new URL(value) } catch { throw new CliError('Registry must be an absolute HTTP(S) URL', EXIT.usage) }
  if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash) {
    throw new CliError('Registry must be an HTTP(S) URL without credentials, query or fragment', EXIT.usage)
  }
  return `${url.href.replace(/\/+$/, '')}/`
}

export async function readConfig(): Promise<AstronConfig> {
  let text: string
  try { text = await readFile(configPath(), 'utf8') } catch (error) {
    if ((error as NodeJS.ErrnoException).code === 'ENOENT') return {}
    throw new CliError('Cannot read AstronHub configuration', EXIT.filesystem)
  }
  try {
    const value: unknown = JSON.parse(text)
    if (typeof value !== 'object' || value === null || Array.isArray(value)) throw new Error()
    const config = value as AstronConfig
    if (config.npmRegistry !== undefined) config.npmRegistry = registryUrl(config.npmRegistry)
    if (config.skillRegistry !== undefined) config.skillRegistry = registryUrl(config.skillRegistry)
    if (config.packageManager !== undefined && !['npm', 'pnpm'].includes(config.packageManager)) throw new Error()
    return config
  } catch { throw new CliError('Invalid AstronHub configuration; fix config.json', EXIT.usage) }
}

export async function setConfig(key: string, value: string): Promise<AstronConfig> {
  if (!['npmRegistry', 'skillRegistry', 'packageManager'].includes(key)) {
    throw new CliError('Config key must be npmRegistry, skillRegistry or packageManager', EXIT.usage)
  }
  if (key === 'packageManager' && value !== 'npm' && value !== 'pnpm') {
    throw new CliError('packageManager must be npm or pnpm', EXIT.usage)
  }
  const config = { ...await readConfig(), [key]: key === 'packageManager' ? value : registryUrl(value) }
  const path = configPath()
  await mkdir(dirname(path), { recursive: true })
  const temporary = `${path}.${randomUUID()}.tmp`
  await writeFile(temporary, `${JSON.stringify(config, null, 2)}\n`, { mode: 0o600 })
  await rename(temporary, path)
  return config
}
