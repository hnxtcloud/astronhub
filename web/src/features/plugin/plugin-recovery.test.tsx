// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react'
import { afterEach, it, expect, vi } from 'vitest'
import i18n from '@/i18n/config'
import { PluginRecoveryPanel } from './plugin-recovery'
afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})
it('previews retained npm packages before an explicit source-disable action', async () => {
  await i18n.changeLanguage('en')
  const writes: { url: string; body: unknown }[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      const data =
        init?.method === 'POST'
          ? (writes.push({ url, body: JSON.parse(String(init.body)) }),
            url.endsWith('/preview')
              ? {
                  channel: 'NPM',
                  action: 'WITHDRAW_CURRENT',
                  expectedRevision: 2,
                  effect: 'REMOTE_PACKAGE_RETAINED',
                  digest: 'withdraw-digest',
                }
              : [])
          : [
              {
                id: 8,
                channel: 'NPM',
                state: 'SUCCEEDED',
                originalSnapshot: 'original-registry',
                effectiveSnapshot: 'original-registry',
                planRevision: 2,
                sourceDisabled: false,
                receipt: 'retained-success',
                plans: [],
                attempts: [],
                operations: [],
              },
            ]
      return new Response(JSON.stringify({ code: 0, data, msg: 'ok' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    })
  )
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  render(
    <QueryClientProvider client={client}>
      <PluginRecoveryPanel releaseId={7} canReview approved />
    </QueryClientProvider>
  )
  await screen.findByText('retained-success')
  fireEvent.click(screen.getByText('Withdraw native installation source'))
  fireEvent.change(screen.getByLabelText('Reason for withdrawal'), {
    target: { value: 'Disable local advertisement' },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Preview withdrawal' }))
  await screen.findByRole('button', { name: 'Confirm withdrawal' })
  expect(writes).toHaveLength(1)
  expect(
    screen.getAllByText(/The package remains in the registry/).length
  ).toBeGreaterThan(0)
  fireEvent.click(screen.getByRole('button', { name: 'Confirm withdrawal' }))
  await waitFor(() => expect(writes).toHaveLength(2))
  expect(writes[1].body).toEqual({
    channel: 'NPM',
    action: 'WITHDRAW_CURRENT',
    expectedRevision: 2,
    reason: 'Disable local advertisement',
    previewDigest: 'withdraw-digest',
  })
  client.clear()
})
it('requires preview and reason when replacing a blocked target and retains successful receipt history', async () => {
  await i18n.changeLanguage('en')
  const writes: { url: string; body: unknown }[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      let data: unknown
      if (init?.method === 'POST') {
        writes.push({ url, body: JSON.parse(String(init.body)) })
        data = url.endsWith('/preview')
          ? {
              channel: 'GIT',
              expectedRevision: 0,
              snapshot: '{"target":"internal/new"}',
              digest: 'candidate-digest',
            }
          : []
      } else if (url.includes('/git/settings'))
        data = {
          targets: [
            {
              id: 9,
              namespace: 'team',
              remote: 'https://git.internal/new',
              branch: 'main',
              credentialAvailable: true,
            },
          ],
        }
      else if (url.includes('/npm/registries')) data = []
      else
        data = [
          {
            id: 1,
            channel: 'GIT',
            state: 'BLOCKED',
            originalSnapshot: 'MISSING_CONFIGURATION',
            effectiveSnapshot: 'MISSING_CONFIGURATION',
            planRevision: 0,
            sourceDisabled: false,
            plans: [],
            attempts: [],
            operations: [],
          },
          {
            id: 2,
            channel: 'NPM',
            state: 'SUCCEEDED',
            originalSnapshot: 'old-registry',
            effectiveSnapshot: 'old-registry',
            planRevision: 0,
            sourceDisabled: false,
            receipt: 'retained-npm-proof',
            plans: [],
            attempts: [],
            operations: [],
          },
        ]
      return new Response(JSON.stringify({ code: 0, data, msg: 'ok' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    })
  )
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  render(
    <QueryClientProvider client={client}>
      <PluginRecoveryPanel releaseId={7} canReview approved />
    </QueryClientProvider>
  )
  await screen.findByText('retained-npm-proof')
  await screen.findByRole('option', { name: /git.internal\/new/ })
  fireEvent.change(screen.getByLabelText('Replacement target'), {
    target: { value: '9' },
  })
  fireEvent.change(screen.getByLabelText('Reason for recovery'), {
    target: { value: 'Repair blocked Git target' },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Preview replacement' }))
  await screen.findByText('{"target":"internal/new"}')
  expect(writes).toHaveLength(1)
  fireEvent.click(screen.getByRole('button', { name: 'Confirm replacement' }))
  await waitFor(() => expect(writes).toHaveLength(2))
  expect(writes[1].body).toEqual({
    channel: 'GIT',
    targetRevisionId: 9,
    expectedRevision: 0,
    reason: 'Repair blocked Git target',
    previewDigest: 'candidate-digest',
  })
  client.clear()
})
