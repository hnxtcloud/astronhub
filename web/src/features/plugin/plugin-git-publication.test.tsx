// @vitest-environment jsdom
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, expect, it, vi } from 'vitest'
import { PluginGitAdminPanel } from './plugin-git-publication'
const { request } = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))
vi.mock('@/api/client', () => ({ fetchJson: request, getCsrfHeaders: (headers: Record<string, string>) => headers }))
beforeEach(() => {
 request.mockReset()
 request.mockImplementation(async (path: string) => path.endsWith('/git/settings') ? { allowedRemotes: ['https://git.internal/team/market.git'], credentialRefs: ['git-publisher'], namespaces: [{ id: 7, slug: 'team', displayName: 'Team' }], targets: [] } : { id: 9 })
})
it('configures an allowed namespace Git target using only a publisher reference', async () => {
 const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
 render(<QueryClientProvider client={client}><PluginGitAdminPanel /></QueryClientProvider>)
 await screen.findByText('git-publisher')
 fireEvent.change(screen.getByLabelText('pluginGit.namespace'), { target: { value: '7' } })
 fireEvent.change(screen.getByLabelText('pluginGit.remote'), { target: { value: 'https://git.internal/team/market.git' } })
 fireEvent.change(screen.getByLabelText('pluginGit.credential'), { target: { value: 'git-publisher' } })
 fireEvent.click(screen.getByText('pluginGit.save'))
 await waitFor(() => expect(request).toHaveBeenCalledWith('/api/v1/plugin-management/git/targets', expect.objectContaining({ body: JSON.stringify({ namespaceId: 7, remote: 'https://git.internal/team/market.git', fetchUrl: 'https://git.internal/team/market.git', branch: 'main', credentialRef: 'git-publisher' }) })))
 expect(screen.queryByLabelText(/token|password/i)).toBeNull()
})
