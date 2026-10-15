// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, expect, it, vi } from 'vitest'
import { SoftwareUpdateManager, SoftwareUpdateSourceManager } from './software-update-manager'

vi.mock('react-i18next', async () => ({ ...await vi.importActual<typeof import('react-i18next')>('react-i18next'), useTranslation: () => ({ t: (key: string) => key }) }))
afterEach(() => { cleanup(); vi.unstubAllGlobals() })

it('shows the selected native channel address and explicit migration configuration', async () => {
  vi.stubGlobal('fetch', vi.fn(async (url: string) => new Response(JSON.stringify({ code: 0, data:
    url.includes('/update-source')
      ? { applicationId: 7, anonymousEnabled: false, channels: [{ id: 2, slug: 'mac-stable', virtualProjectPath: 'apps/demo', targetReleaseId: 5 }] }
      : [{ id: 5, protocol: 'ELECTRON', targetOs: 'MACOS', channel: 'stable', assets: [{ role: 'METADATA', relativePath: 'dist/latest-mac.yml' }] }],
  }))))
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <SoftwareUpdateSourceManager appId={7} appSlug="demo" />
  </QueryClientProvider>)
  const address = await screen.findByLabelText('softwareUpdates.feedUrl')
  expect((address as HTMLInputElement).value).toBe(`${window.location.origin}/updates/demo/mac-stable/latest-mac.yml`)
  expect(screen.getByText('softwareUpdates.migrationHint')).toBeTruthy()
  expect(screen.getByText('softwareUpdates.sourceDisabled')).toBeTruthy()
  expect(screen.getByText(/"provider": "generic"/).textContent).toContain('mac-stable/')
  expect(screen.getByText(/"provider": "gitlab"/).textContent).toContain('"baseUrl"')
  expect(screen.getByText(/"provider": "gitlab"/).textContent).toContain('"owner": "apps"')
  expect(screen.getByText(/"provider": "gitlab"/).textContent).toContain('"repo": "demo"')
  expect(screen.getByText(/"provider": "gitlab"/).textContent).toContain('"artifactPath": "dist"')
})

it('does not offer a legacy GitLab configuration without the original native metadata filename', async () => {
  vi.stubGlobal('fetch', vi.fn(async (url: string) => new Response(JSON.stringify({ code: 0, data:
    url.includes('/update-source')
      ? { applicationId: 7, anonymousEnabled: true, channels: [{ id: 2, slug: 'mac-stable', virtualProjectPath: 'apps/demo', targetReleaseId: 5 }] }
      : [{ id: 5, protocol: 'ELECTRON', targetOs: 'MACOS', channel: 'stable', assets: [{ role: 'METADATA', relativePath: 'dist/native.json' }] }],
  }))))
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <SoftwareUpdateSourceManager appId={7} appSlug="demo" />
  </QueryClientProvider>)
  await screen.findByLabelText('softwareUpdates.feedUrl')
  expect(screen.queryByText(/"provider": "gitlab"/)).toBeNull()
})

it('creates a snapshot from an empty release and presents server validation failures', async () => {
  const requests: string[] = []
  const draft = { id: 5, status: 'DRAFT', machineVersion: '1.2.3', tag: 'v1.2.3', assets: [], targetOs: 'WINDOWS', targetArch: 'X86_64', channel: 'stable', variant: 'default' }
  let created = false
  vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
    requests.push(`${init?.method ?? 'GET'} ${url}`)
    if (url.endsWith('/validate')) return new Response(JSON.stringify({ code: 400, msg: 'Missing installer or metadata' }), { status: 400 })
    if (init?.method === 'POST') { created = true; return new Response(JSON.stringify({ code: 0, data: draft })) }
    return new Response(JSON.stringify({ code: 0, data: created ? [draft] : [] }))
  }))
  document.cookie = 'XSRF-TOKEN=test'
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <SoftwareUpdateManager release={{ id: 7, packages: [] }} />
  </QueryClientProvider>)
  fireEvent.change(screen.getByLabelText('softwareUpdates.machineVersion'), { target: { value: '1.2.3' } })
  fireEvent.click(screen.getByRole('button', { name: 'softwareUpdates.create' }))
  await screen.findByText('v1.2.3')
  fireEvent.click(screen.getByRole('button', { name: 'softwareUpdates.validate' }))
  await screen.findByRole('alert')
  expect(screen.getByRole('alert').textContent).toContain('Missing installer or metadata')
  await waitFor(() => expect(requests).toContain('POST /api/v1/admin/software/update-releases/5/validate'))
})

it('keeps anonymous reads off until the application administrator explicitly enables them', async () => {
  const changes: unknown[] = []
  let enabled = false
  vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
    if (url.includes('/update-source')) {
      if (init?.method === 'PUT') { changes.push(JSON.parse(String(init.body))); enabled = true }
      return new Response(JSON.stringify({ code: 0, data: { applicationId: 7, anonymousEnabled: enabled, channels: [] } }))
    }
    return new Response(JSON.stringify({ code: 0, data: [] }))
  }))
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <SoftwareUpdateSourceManager appId={7} appSlug="demo" />
  </QueryClientProvider>)
  const toggle = await screen.findByRole('checkbox', { name: 'softwareUpdates.anonymous' })
  await waitFor(() => expect((toggle as HTMLInputElement).disabled).toBe(false))
  expect((toggle as HTMLInputElement).checked).toBe(false)
  fireEvent.click(toggle)
  await waitFor(() => expect(changes).toEqual([{ anonymousEnabled: true }]))
})

it('offers withdrawal and hides existing-file mutations for a published snapshot', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ code: 0, data: [{ id: 5, tag: 'v1.2.3', status: 'PUBLISHED', assets: [], targetOs: 'WINDOWS', targetArch: 'X86_64', channel: 'stable', variant: 'default' }] }))))
  render(<QueryClientProvider client={new QueryClient()}><SoftwareUpdateManager release={{ id: 7, packages: [] }} /></QueryClientProvider>)
  await screen.findByRole('button', { name: 'softwareUpdates.withdraw' })
  expect(screen.queryByRole('button', { name: 'softwareUpdates.upload' })).toBeNull()
  expect(screen.queryByRole('button', { name: 'softwareUpdates.link' })).toBeNull()
})
