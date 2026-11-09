// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { PublicationEditor } from './publication-editor'

vi.mock('react-i18next', async () => ({
  ...await vi.importActual<typeof import('react-i18next')>('react-i18next'),
  useTranslation: () => ({ t: (key: string) => key }),
}))
afterEach(() => { cleanup(); sessionStorage.clear(); document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'; vi.unstubAllGlobals() })

it('restores edits after leaving before debounce and saves through the owned task API', async () => {
  document.cookie = 'XSRF-TOKEN=publication-test; path=/'
  const writes: Record<string, unknown>[] = []
  const task = { id: 'draft-task', status: 'DRAFT', revision: 3, materialReady: true, fileName: 'skill.zip', byteSize: 120,
    draft: { type: 'SKILL', name: 'example-skill', slug: 'example-skill', version: '1.0.0', namespace: 'team', visibility: 'PRIVATE' },
    progress: { phase: 'MATERIAL', results: [], warnings: [] } }
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    let data: unknown = task
    if (url.includes('/auth/me')) data = { userId: 'publisher', platformRoles: [] }
    else if (url.includes('/namespaces')) data = [{ id: 7, slug: 'team', displayName: 'Team', status: 'ACTIVE' }]
    else if (init?.method === 'PUT') {
      const body = JSON.parse(String(init.body)) as { revision: number; draft: typeof task.draft }
      writes.push(body); Object.assign(task, { revision: body.revision + 1, draft: body.draft })
    }
    return new Response(JSON.stringify({ code: 0, data, msg: 'ok' }), { status: 200, headers: { 'Content-Type': 'application/json' } })
  }))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={client}><PublicationEditor taskId="draft-task" onTaskChange={() => {}} /></QueryClientProvider>)
  await screen.findByDisplayValue('example-skill')
  expect(screen.queryByLabelText('publication.type')).toBeNull()
  fireEvent.change(screen.getByLabelText('publication.visibility'), { target: { value: 'PUBLIC' } })
  cleanup()
  render(<QueryClientProvider client={client}><PublicationEditor taskId="draft-task" onTaskChange={() => {}} /></QueryClientProvider>)
  expect((screen.getByLabelText('publication.visibility') as HTMLSelectElement).value).toBe('PUBLIC')
  await waitFor(() => expect(writes.length).toBe(1), { timeout: 4000 })
  expect(writes[0]).toMatchObject({ revision: 3, draft: { namespace: 'team', visibility: 'PUBLIC' } })
  client.clear()
})
