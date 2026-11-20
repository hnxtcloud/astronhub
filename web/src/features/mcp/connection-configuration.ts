import type { McpConnectionProfile } from './mcp-api'

/** Produce a client template without interpreting commands or requesting credential values. */
export function buildMcpClientConfiguration(profile: McpConnectionProfile, name: string): string | null {
  if (!profile.configurationAvailable) return null
  let connection: Record<string, unknown>
  switch (profile.transportType) {
    case 'STDIO': {
      if (!profile.commandTemplate) return null
      connection = {
        command: profile.commandTemplate,
        args: profile.arguments ?? [],
        ...((profile.variables?.length ?? 0) > 0 ? {
          env: Object.fromEntries(profile.variables!.map(variable => [
            variable.name!,
            !variable.secret && variable.defaultValue != null ? variable.defaultValue : `\${${variable.name}}`,
          ])),
        } : {}),
      }
      break
    }
    case 'STREAMABLE_HTTP':
    case 'LEGACY_HTTP_SSE':
      if (!profile.endpointTemplate) return null
      connection = { type: profile.transportType === 'STREAMABLE_HTTP' ? 'http' : 'sse', url: profile.endpointTemplate }
      break
    default:
      return null
  }
  return JSON.stringify({ mcpServers: { [name]: connection } }, null, 2)
}
