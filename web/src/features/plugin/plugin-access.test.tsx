// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import i18n from '@/i18n/config'
import { PluginMcpInventory, PluginPublishedDetail, PluginProjectGovernance } from './plugin-access'
import type { PluginProject } from './plugin-api'

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})
it('keeps downloads pinned to the selected version and renders file previews as inert text', async () => {
  await i18n.changeLanguage('en')
  const reads: string[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      reads.push(url)
      let data: unknown
      if (url.endsWith('/versions'))
        data = {
          id: 1,
          namespace: 'team',
          slug: 'demo',
          displayName: 'Demo',
          visibility: 'PUBLIC',
          latestReleaseId: 2,
          versions: [
            { id: 2, version: '2.0.0', runtimes: ['claude-code'] },
            { id: 1, version: '1.0.0', runtimes: ['claude-code'] },
          ],
          components: [],
        }
      else if (url.includes('/files?')) data = { files: ['README.md'] }
      else if (url.includes('/file?'))
        data = {
          path: 'README.md',
          content: '<script>window.unsafe = true</script>',
          bytes: 40,
        }
      else if (url.includes('/install?'))
        data = {
          command: 'claude --plugin-dir "./plugins/demo/1.0.0"',
          powershellCommand: 'Invoke-DemoInstall',
          preparation: 'CLAUDE_LOCAL',
          runtimeVerified: false,
          sha256: 'abc',
          downloadPath:
            '/api/v1/plugins/team/demo/versions/1.0.0/download?runtime=claude-code',
        }
      else
        data = {
          id: 1,
          projectId: 1,
          version: url.includes('1.0.0') ? '1.0.0' : '2.0.0',
          distributions: [
            {
              id: 1,
              runtimeKey: 'claude-code',
              nativeName: 'demo',
              nativeVersion: '1.0.0',
              sha256: 'abc',
              compatibilityEvidence: 'MANIFEST_VALIDATED',
              securityAssessment: 'NOT_SCANNED',
              inventoryJson: '[]',
              manifestJson: '{}',
            },
          ],
        }
      return new Response(JSON.stringify({ code: 0, data, msg: 'ok' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    })
  )
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  const result = render(
    <QueryClientProvider client={client}>
      <PluginPublishedDetail namespace="team" slug="demo" />
    </QueryClientProvider>
  )
  await screen.findByRole('heading', { name: 'Demo' })
  fireEvent.change(screen.getByLabelText('Published version'), {
    target: { value: '1.0.0' },
  })
  fireEvent.click(
    await screen.findByRole('button', {
      name: 'Generate installation instructions',
    })
  )
  await screen.findByText('claude --plugin-dir "./plugins/demo/1.0.0"')
  expect(screen.queryByText('Invoke-DemoInstall')).toBeNull()
  fireEvent.change(screen.getByLabelText('Shell'), { target: { value: 'powershell' } })
  expect(screen.getByText('Invoke-DemoInstall')).toBeTruthy()
  expect(screen.queryByText('claude --plugin-dir "./plugins/demo/1.0.0"')).toBeNull()
  fireEvent.change(screen.getByLabelText('Shell'), { target: { value: 'posix' } })
  expect(screen.getByText('claude --plugin-dir "./plugins/demo/1.0.0"')).toBeTruthy()
  expect(
    screen.getByRole('link', { name: 'Download archive' }).getAttribute('href')
  ).toContain('/versions/1.0.0/download')
  fireEvent.click(screen.getByText('Browse package files'))
  fireEvent.click(await screen.findByRole('button', { name: 'README.md' }))
  await screen.findByText('<script>window.unsafe = true</script>')
  expect(result.container.querySelector('script')).toBeNull()
  await waitFor(() =>
    expect(reads.some((url) => url.includes('/versions/1.0.0/file?'))).toBe(
      true
    )
  )
  client.clear()
})

it('allows an authorized archived project to be restored with a reason and external-copy warning', async () => {
  await i18n.changeLanguage('en')
  const writes: unknown[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      writes.push(JSON.parse(String(init?.body)))
      return new Response(JSON.stringify({ code: 0, data: {}, msg: 'ok' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    })
  )
  const project: PluginProject = {
    id: 10,
    namespaceId: 7,
    namespace: 'team',
    slug: 'demo',
    displayName: 'Demo',
    ownerId: 'owner',
    visibility: 'PRIVATE',
    status: 'ARCHIVED',
    hidden: false,
    canManage: false,
    canReview: false,
    canGovern: true,
    releases: [],
  }
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  render(
    <QueryClientProvider client={client}>
      <PluginProjectGovernance project={project} />
    </QueryClientProvider>
  )
  expect(screen.getByText(/Copies already distributed/)).toBeTruthy()
  expect(
    screen.getByRole('button', { name: 'Restore' }).hasAttribute('disabled')
  ).toBe(true)
  fireEvent.change(screen.getByLabelText('Governance reason'), {
    target: { value: 'Restore approved catalog entry' },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Restore' }))
  await waitFor(() =>
    expect(writes).toEqual([
      { action: 'RESTORE', reason: 'Restore approved catalog entry' },
    ])
  )
  client.clear()
})

it('renders the MCP inventory declared in distribution evidence', async () => {
  await i18n.changeLanguage('en')
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      let data: unknown
      if (url.endsWith('/versions'))
        data = {
          id: 1,
          namespace: 'team',
          slug: 'demo',
          displayName: 'Demo',
          visibility: 'PUBLIC',
          latestReleaseId: 1,
          versions: [{ id: 1, version: '1.0.0', runtimes: ['claude-code'] }],
          components: [],
        }
      else
        data = {
          id: 1,
          projectId: 1,
          version: '1.0.0',
          distributions: [
            {
              id: 1,
              runtimeKey: 'claude-code',
              nativeName: 'demo',
              nativeVersion: '1.0.0',
              sha256: 'abc',
              compatibilityEvidence: 'MANIFEST_VALIDATED',
              securityAssessment: 'NOT_SCANNED',
              inventoryJson: JSON.stringify({
                files: ['.claude-plugin/plugin.json', 'mcp/demo.json', '.mcp.json'],
                resources: { mcpServers: ['mcp/demo.json'] },
              }),
              manifestJson: '{}',
            },
          ],
        }
      return new Response(JSON.stringify({ code: 0, data, msg: 'ok' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    })
  )
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <PluginPublishedDetail namespace="team" slug="demo" />
    </QueryClientProvider>
  )
  await screen.findByText('MCP servers in this package')
  screen.getByText('claude-code')
  screen.getByText('mcp/demo.json')
  screen.getByText('.mcp.json')
  client.clear()
})

it('renders nothing when distributions declare no MCP components', () => {
  const distribution = {
    id: 1,
    runtimeKey: 'claude-code',
    nativeName: 'demo',
    nativeVersion: '1.0.0',
    sha256: 'abc',
    compatibilityEvidence: 'MANIFEST_VALIDATED',
    securityAssessment: 'NOT_SCANNED',
    inventoryJson: JSON.stringify({ files: ['README.md'], resources: {} }),
    manifestJson: '{}',
  } as const
  render(<PluginMcpInventory distributions={[distribution]} />)
  expect(screen.queryByText('MCP servers in this package')).toBeNull()
  render(<PluginMcpInventory distributions={[]} />)
  expect(screen.queryByText('MCP servers in this package')).toBeNull()
})
