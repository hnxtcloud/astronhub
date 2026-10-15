// @vitest-environment jsdom
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, expect, it, vi } from 'vitest'
import { PluginNpmAdminPanel } from './plugin-npm-publication'
const { request } = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))
vi.mock('@/api/client', () => ({ fetchJson: request, getCsrfHeaders: (headers: Record<string, string>) => headers }))
beforeEach(() => {
  request.mockReset()
  request.mockImplementation(async (path: string) => {
    if (String(path).endsWith('/npm/settings')) return { allowedRegistries: ['https://npm.internal/team/'], credentialRefs: ['npm-publisher'], registries: [{ id: 7, name: 'Internal', registry: 'https://npm.internal/team/', credentialRef: 'npm-publisher', credentialAvailable: true }] }
    if (String(path).endsWith('/npm/dependencies')) return []
    return { id: 8, state: 'AWAITING_CONFIRMATION' }
  })
})
it('saves an allowed registry with a named publisher reference and never requests a publisher token', async () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={client}><PluginNpmAdminPanel /></QueryClientProvider>)
  await screen.findByText('npm-publisher')
  fireEvent.change(screen.getByLabelText('pluginNpm.name'), { target: { value: 'Team registry' } })
  fireEvent.change(screen.getAllByLabelText('pluginNpm.registry')[0], { target: { value: 'https://npm.internal/team/' } })
  fireEvent.change(screen.getByLabelText('pluginNpm.credentialRef'), { target: { value: 'npm-publisher' } })
  fireEvent.click(screen.getByText('pluginNpm.save'))
  await waitFor(() => expect(request).toHaveBeenCalledWith('/api/v1/plugin-management/npm/registries', expect.objectContaining({ body: JSON.stringify({ name: 'Team registry', registry: 'https://npm.internal/team/', credentialRef: 'npm-publisher', defaultRegistry: true }) })))
  expect(screen.queryByLabelText(/token|password/i)).toBeNull()
})
