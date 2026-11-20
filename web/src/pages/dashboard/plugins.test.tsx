// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import i18n from '@/i18n/config'
import { PluginManagementPage } from './plugins'

afterEach(() => { cleanup(); vi.unstubAllGlobals() })

it('creates a namespace plugin draft through the management form', async () => {
  await i18n.changeLanguage('en')
  const writes: unknown[] = []
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    let data: unknown = null
    if (url.includes('/auth/me')) data = { id: 'owner', platformRoles: ['USER'] }
    else if (url.endsWith('/projects') && init?.method === 'POST') {
      writes.push(JSON.parse(String(init.body)))
      data = { id: 10, namespaceId: 7, namespace: 'team', slug: 'sample', displayName: 'Sample', visibility: 'PRIVATE', status: 'ACTIVE', releases: [] }
    } else if (url.includes('/projects/10')) data = { id: 10, namespaceId: 7, namespace: 'team', slug: 'sample', displayName: 'Sample', visibility: 'PRIVATE', status: 'ACTIVE', releases: [] }
    else if (url.includes('/projects')) data = { items: [], total: 0, page: 0, size: 20 }
    else if (url.includes('/namespaces')) data = [{ id: 7, slug: 'team', displayName: 'Team', status: 'ACTIVE' }]
    return new Response(JSON.stringify({ code: 0, data, msg: 'ok' }), { status: 200, headers: { 'Content-Type': 'application/json' } })
  }))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={client}><PluginManagementPage /></QueryClientProvider>)
  await waitFor(() => expect(screen.getAllByRole('option', { name: 'Team' }).length).toBeGreaterThan(0), { timeout: 10000 })
  const createDetails = screen.getByText('Create project', { selector: 'summary' }).closest('details')!
  expect(createDetails.open).toBe(false)
  fireEvent.click(screen.getByText('Create project', { selector: 'summary' }))
  fireEvent.change(screen.getByLabelText('Namespace'), { target: { value: '7' } })
  fireEvent.change(screen.getByLabelText('Slug'), { target: { value: 'sample' } })
  fireEvent.change(screen.getByLabelText('Display name'), { target: { value: 'Sample' } })
  fireEvent.click(screen.getByRole('button', { name: 'Create project' }))
  await waitFor(() => expect(writes).toEqual([{
    namespaceId: 7, slug: 'sample', displayName: 'Sample', summary: '', visibility: 'PRIVATE',
  }]))
  await waitFor(() => expect(screen.getByRole('heading', { name: 'Sample' })).toBeTruthy())
  client.clear()
})
