// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, expect, it, vi } from 'vitest'
import { SoftwarePublisherManager } from './software-publisher-manager'

vi.mock('react-i18next', async () => ({ ...await vi.importActual<typeof import('react-i18next')>('react-i18next'), useTranslation: () => ({ t: (key: string) => key }) }))
afterEach(() => { cleanup(); vi.unstubAllGlobals() })

it('issues an app-bound credential once, clears it and revokes the grant without exposing it in the list', async () => {
  const requests: string[] = []
  vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
    requests.push(`${init?.method ?? 'GET'} ${url}`)
    return new Response(JSON.stringify({ code: 0, data: init?.method === 'POST'
      ? { rawToken: 'synthetic-once-secret', publisher: { tokenId: 3 } }
      : init?.method === 'DELETE' ? null : [{ tokenId: 3, applicationId: 7, name: 'CI', ownerUserId: 'admin', expiresAt: '2030-01-01T00:00:00Z' }] }))
  }))
  document.cookie = 'XSRF-TOKEN=test'
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><SoftwarePublisherManager appId={7} /></QueryClientProvider>)
  await screen.findByText('CI')
  fireEvent.change(screen.getByLabelText('softwarePublishers.name'), { target: { value: 'CI new' } })
  fireEvent.change(screen.getByLabelText('softwarePublishers.expires'), { target: { value: '2030-01-01' } })
  fireEvent.click(screen.getByRole('button', { name: 'softwarePublishers.create' }))
  expect((await screen.findByLabelText('softwarePublishers.secret') as HTMLInputElement).value).toBe('synthetic-once-secret')
  fireEvent.click(screen.getByRole('button', { name: 'softwarePublishers.clear' }))
  expect(screen.queryByLabelText('softwarePublishers.secret')).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: 'softwarePublishers.revoke' }))
  await waitFor(() => expect(requests).toContain('DELETE /api/v1/admin/software/apps/7/publishers/3'))
  expect(requests).toContain('POST /api/v1/admin/software/apps/7/publishers')
  expect(requests.join(' ')).not.toContain('synthetic-once-secret')
})
