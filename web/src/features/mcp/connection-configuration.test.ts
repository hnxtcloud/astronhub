import { describe, expect, it } from 'vitest'
import { buildMcpClientConfiguration } from './connection-configuration'
import type { McpConnectionProfile } from './mcp-api'

describe('MCP client configuration templates', () => {
  it('copies STDIO arguments and uses placeholders for secret variables', () => {
    const profile: McpConnectionProfile = {
      configurationAvailable: true, transportType: 'STDIO', commandTemplate: 'npx',
      arguments: ['-y', 'demo-mcp', '中文目录'],
      variables: [
        { name: 'API_KEY', secret: true, defaultValue: 'must-not-copy' },
        { name: 'REGION', secret: false, defaultValue: 'cn' },
      ],
    }
    const text = buildMcpClientConfiguration(profile, 'knowledge')!
    expect(JSON.parse(text)).toEqual({ mcpServers: { knowledge: {
      command: 'npx', args: ['-y', 'demo-mcp', '中文目录'], env: { API_KEY: '${API_KEY}', REGION: 'cn' },
    } } })
    expect(text).not.toContain('must-not-copy')
  })

  it.each([['STREAMABLE_HTTP', 'http'], ['LEGACY_HTTP_SSE', 'sse']] as const)('copies a %s URL template', (transportType, type) => {
    const text = buildMcpClientConfiguration({ configurationAvailable: true, transportType, endpointTemplate: 'https://example.test/${TENANT}/mcp' }, 'knowledge')!
    expect(JSON.parse(text)).toEqual({ mcpServers: { knowledge: { type, url: 'https://example.test/${TENANT}/mcp' } } })
  })

  it('does not produce apparently working configuration for CUSTOM or invalid profiles', () => {
    expect(buildMcpClientConfiguration({ transportType: 'CUSTOM', configurationAvailable: true }, 'demo')).toBeNull()
    expect(buildMcpClientConfiguration({ transportType: 'STDIO', configurationAvailable: false }, 'demo')).toBeNull()
    expect(buildMcpClientConfiguration({ transportType: 'STDIO', configurationAvailable: true }, 'demo')).toBeNull()
  })
})
