import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { join, relative } from 'node:path'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'

const paths: Record<string, { skills: string; instructions: string }> = {
  codex: { skills: '.agents/skills', instructions: 'AGENTS.md' },
  'claude-code': { skills: '.claude/skills', instructions: 'CLAUDE.md' },
  generic: { skills: '.agents/skills', instructions: 'AGENTS.md' }
}

export async function initializeAgent(agent = 'generic', cwd = process.cwd()) {
  const target = paths[agent]
  if (!target) throw new CliError('init supports codex, claude-code and generic', EXIT.usage)
  let skill: string
  try { skill = await readFile(new URL('../skills/astronhub/SKILL.md', import.meta.url), 'utf8') } catch {
    skill = await readFile(new URL('../../astronhub/skills/astronhub/SKILL.md', import.meta.url), 'utf8')
  }
  const skillDir = join(cwd, target.skills, 'astronhub')
  const skillPath = join(skillDir, 'SKILL.md')
  let currentSkill: string | undefined
  try { currentSkill = await readFile(skillPath, 'utf8') } catch (error) {
    if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error
  }
  if (currentSkill !== undefined && currentSkill !== skill) {
    throw new CliError('An existing AstronHub skill differs; preserve or move it before init', EXIT.filesystem)
  }
  const instructionsPath = join(cwd, target.instructions)
  let current = ''
  try { current = await readFile(instructionsPath, 'utf8') } catch (error) {
    if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error
  }
  const pointer = `${target.skills}/astronhub/SKILL.md`
  const marker = '<!-- astronhub-cli -->'
  const block = `${marker}\n## AstronHub\n\nWhen installing project dependencies or finding, installing, removing or updating Agent Skills,\nread \`${pointer}\` and use AstronHub CLI.\n<!-- /astronhub-cli -->\n`
  if (current.includes(marker) && !current.includes(block)) {
    throw new CliError('Existing AstronHub instructions differ; update the managed block manually', EXIT.filesystem)
  }
  await mkdir(skillDir, { recursive: true })
  if (currentSkill === undefined) await writeFile(skillPath, skill, { flag: 'wx' })
  if (!current.includes(marker)) await writeFile(instructionsPath, `${current}${current && !current.endsWith('\n') ? '\n' : ''}\n${block}`)
  return { ok: true, agent, files: [relative(cwd, skillPath), target.instructions], reloadAgent: true }
}
