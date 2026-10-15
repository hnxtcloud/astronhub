// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, expect, it, vi } from 'vitest'
import { SoftwareUpdateManager } from './software-update-manager'

vi.mock('react-i18next', async () => ({ ...await vi.importActual<typeof import('react-i18next')>('react-i18next'), useTranslation: () => ({ t: (key: string) => key }) }))
afterEach(() => { cleanup(); vi.unstubAllGlobals() })

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
