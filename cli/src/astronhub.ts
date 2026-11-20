#!/usr/bin/env node
import { cac } from 'cac'
import { homedir } from 'node:os'
import { join } from 'node:path'
import { installCommand } from './commands/install'
import { searchCommand } from './commands/search'
import { listCommand } from './commands/list'
import { removeCommand } from './commands/remove'
import { upgradeCommand } from './commands/upgrade'
import { loginCommand } from './commands/login'
import { logoutCommand } from './commands/logout'
import { whoamiCommand } from './commands/whoami'
import { resolveInstallTargets } from './agents/resolver'
import { CliError } from './shared/errors'
import { EXIT } from './shared/constants'
import { renderError } from './shared/output'
import { configPath, readConfig, setConfig } from './astronhub/config'
import { nodeInstall, type NodeOptions } from './astronhub/node-install'
import { initializeAgent } from './astronhub/agent-init'

declare const ASTRONHUB_VERSION: string
const version = typeof ASTRONHUB_VERSION === 'undefined' ? '0.1.0-dev' : ASTRONHUB_VERSION
const cli = cac('astronhub')
const json = process.argv.includes('--json')

async function run(action: () => Promise<unknown>, jsonOutput = false) {
  try {
    const result = await action()
    if (typeof result === 'string') process.stdout.write(`${result}\n`)
    else process.stdout.write(`${jsonOutput ? JSON.stringify(result) : JSON.stringify(result, null, 2)}\n`)
  } catch (error) {
    const normalized = error instanceof CliError ? error : new CliError(error instanceof Error ? error.message : 'Unexpected failure', EXIT.generic)
    process.stderr.write(`${renderError(normalized, jsonOutput)}\n`)
    process.exitCode = normalized.exitCode
  }
}

function nodeCommand(pattern: string, description: string, grouped = false) {
  const command = cli.command(pattern, description)
    .option('--pm <manager>', 'Package manager: npm or pnpm (auto-detected by default)')
    .option('--registry <url>', 'Private npm registry override')
    .option('-D, --dev', 'Save as a development dependency')
    .option('-E, --exact', 'Save exact versions')
    .option('--dry-run', 'Show plan without networking or changing files')
    .option('--ignore-scripts', 'Skip dependency lifecycle scripts')
    .option('--filter <selector>', 'pnpm workspace filter')
    .option('-w, --workspace-root', 'Install at pnpm workspace root')
    .option('--non-interactive', 'Run without interactive input')
    .option('--json', 'Machine-readable result on stdout; package-manager logs on stderr')
  if (grouped) command.action((action: string, packages: string[], options: NodeOptions) => run(async () => {
    if (action !== 'add' && action !== 'install') throw new CliError('Node action must be add or install', EXIT.usage)
    return nodeInstall(packages, options)
  }, options.json))
  else command.action((packages: string[], options: NodeOptions) => run(() => nodeInstall(packages, options), options.json))
}
nodeCommand('install [...packages]', 'Install Node dependencies through the private registry compatibility gateway')
nodeCommand('node <action> [...packages]', 'Node add/install: aliases for install', true)

interface SkillOptions {
  agent?: string | string[]
  global?: boolean
  dir?: string
  force?: boolean
  registry?: string
  token?: string
  skillVersion?: string
  check?: boolean
  json?: boolean
  nonInteractive?: boolean
  yes?: boolean
  limit?: number
}

cli.command('skill <action> [...values]', 'Manage Skills: add, find, list, remove, update')
  .option('-a, --agent <name>', 'Agent profile; repeat to select multiple')
  .option('-g, --global', 'Install into the user directory (default: current project)')
  .option('--dir <path>', 'Explicit Skill install directory; exclusive with --agent and --global')
  .option('--registry <url>', 'SkillHub server override (separate from npm registry)')
  .option('--token <token>', 'SkillHub API token; prefer ASTRONHUB_TOKEN')
  .option('--skill-version <version>', 'Skill version (or use coordinate@version)')
  .option('--force', 'Replace a managed Skill from the same source')
  .option('--check', 'Preview Skill updates')
  .option('--limit <n>', 'Search result limit', { default: 20 })
  .option('--non-interactive', 'Disable interactive prompts')
  .option('-y, --yes', 'Use the specified targets without prompting')
  .option('--json', 'Output JSON')
  .action((action: string, values: string[], options: SkillOptions) => run(async () => {
    if (!['add', 'find', 'list', 'remove', 'update'].includes(action)) throw new CliError('Skill action must be add, find, list, remove or update', EXIT.usage)
    const config = await readConfig()
    const registry = options.registry || process.env.ASTRONHUB_SKILL_REGISTRY || config.skillRegistry
    if (!registry) throw new CliError('Configure skillRegistry or pass --registry <SkillHub URL>', EXIT.usage)
    const agents = options.agent === undefined ? undefined : Array.isArray(options.agent) ? options.agent : [options.agent]
    const common = { registry, ...(options.token || process.env.ASTRONHUB_TOKEN ? { token: options.token || process.env.ASTRONHUB_TOKEN! } : {}), ...(options.json ? { json: true } : {}) }
    if (options.dir && (agents?.length || options.global)) throw new CliError('--dir is exclusive with --agent and --global', EXIT.usage)
    if (action === 'find') return searchCommand(values.join(' '), { ...common, limit: options.limit ?? 20 })
    if (action === 'list') {
      if (values.length) throw new CliError('skill list takes no coordinates', EXIT.usage)
      return listCommand({ ...common, ...(agents ? { agent: agents } : {}), ...(options.dir ? { dir: options.dir } : {}) })
    }
    if (!values.length || ((action === 'add' || action === 'remove') && values.length !== 1)) {
      throw new CliError(`skill ${action} requires ${action === 'update' ? 'explicit coordinates' : 'one coordinate'}`, EXIT.usage)
    }
    if (action === 'remove') {
      if (options.dir || options.global) throw new CliError('skill remove supports --agent; --dir and -g are not supported', EXIT.usage)
      return removeCommand(values[0]!, { ...common, ...(agents ? { agent: agents } : {}) })
    }
    if (action === 'update') return upgradeCommand(values, {
      ...common, ...(agents ? { agent: agents } : {}), ...(options.dir ? { dir: options.dir } : {}),
      ...(options.force ? { force: true } : {}), ...(options.check ? { check: true } : {})
    })
    if (options.check) throw new CliError('--check is only supported for skill update', EXIT.usage)
    let coordinate = values[0]!
    const separator = coordinate.lastIndexOf('@')
    if (separator > 0) {
      if (options.skillVersion) throw new CliError('Use either coordinate@version or --skill-version', EXIT.usage)
      options.skillVersion = coordinate.slice(separator + 1)
      coordinate = coordinate.slice(0, separator)
      if (!options.skillVersion) throw new CliError('Skill version cannot be empty', EXIT.usage)
    }
    // Generic and Codex use the shared Agent Skills directory for this new CLI.
    const shared = !agents?.length || (agents.length === 1 && agents[0] === 'generic')
    const sharedRoot = join(options.global ? homedir() : process.cwd(), '.agents', 'skills')
    const dir = options.dir
    return installCommand(coordinate, {
      ...common, ...(options.skillVersion ? { version: String(options.skillVersion) } : {}),
      ...(options.force ? { force: true } : {}),
      ...(dir ? { dir } : { ...(agents ? { agent: agents } : {}), scope: options.global ? 'user' : 'project' })
    }, {
      ...(options.nonInteractive || options.yes || options.json ? { isTTY: () => false } : {}),
      resolveInstallTargets: async targetOptions => {
        if (shared && !dir) return [{ agent: 'generic', rootDir: sharedRoot, scope: options.global ? 'user' : 'project', source: 'explicit' }]
        const targets = await resolveInstallTargets(targetOptions)
        return targets.map(target => target.agent === 'codex' ? { ...target, rootDir: sharedRoot } : target)
      }
    })
  }, options.json))

cli.command('config [action] [key] [value]', 'Show config or set npmRegistry, skillRegistry, packageManager')
  .option('--json', 'Output JSON')
  .action((action: string | undefined, key: string | undefined, value: string | undefined, options: { json?: boolean }) => run(async () => {
    if (!action || action === 'show') return { ok: true, path: configPath(), config: await readConfig() }
    if (action !== 'set' || !key || !value) throw new CliError('Usage: astronhub config set <key> <value>', EXIT.usage)
    return { ok: true, path: configPath(), config: await setConfig(key, value) }
  }, options.json))

for (const action of ['login', 'logout', 'whoami'] as const) {
  cli.command(action, `${action} for the SkillHub server`)
    .option('--registry <url>', 'SkillHub server override')
    .option('--token <token>', 'SkillHub API token')
    .option('--no-open', 'Do not open a browser')
    .option('--json', 'Output JSON')
    .option('--non-interactive', 'Require a token instead of device authorization')
    .action((options: { registry?: string; token?: string; open?: boolean; json?: boolean; nonInteractive?: boolean }) => run(async () => {
      const config = await readConfig()
      const registry = options.registry || process.env.ASTRONHUB_SKILL_REGISTRY || config.skillRegistry
      if (!registry) throw new CliError('Configure skillRegistry first', EXIT.usage)
      const token = options.token || process.env.ASTRONHUB_TOKEN
      if (action === 'login' && options.nonInteractive && !token) throw new CliError('Non-interactive login requires ASTRONHUB_TOKEN or --token', EXIT.usage)
      const common = { registry, ...(token ? { token } : {}), ...(options.json ? { json: true } : {}) }
      return action === 'login' ? loginCommand({ ...common, noOpen: options.open === false })
        : action === 'logout' ? logoutCommand(common) : whoamiCommand(common)
    }, options.json))
}

cli.command('init', 'Install the CLI usage Skill and add a pointer to project instructions')
  .option('-a, --agent <name>', 'codex, claude-code or generic', { default: 'generic' })
  .option('--json', 'Output JSON')
  .action((options: { agent: string; json?: boolean }) => run(() => initializeAgent(options.agent), options.json))

cli.help()
const args = process.argv.slice(2)
if (args.length === 1 && ['--version', '-v'].includes(args[0]!)) process.stdout.write(`${version}\n`)
else {
  try {
    const parsed = cli.parse(process.argv, { run: false })
    if (!cli.matchedCommand && parsed.args.length) throw new CliError(`Unknown command: ${parsed.args.join(' ')}`, EXIT.usage)
    if (!args.length) cli.outputHelp()
    else await cli.runMatchedCommand()
  } catch (error) {
    process.stderr.write(`${renderError(error instanceof CliError ? error : new CliError(error instanceof Error ? error.message : 'Invalid command', EXIT.usage), json)}\n`)
    process.exitCode = error instanceof CliError ? error.exitCode : EXIT.usage
  }
}
