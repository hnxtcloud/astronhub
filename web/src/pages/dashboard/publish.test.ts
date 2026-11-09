import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { beforeEach, describe, expect, it, vi } from 'vitest'
const mocks = vi.hoisted(() => ({ search: vi.fn(), editor: vi.fn() }))
vi.mock('@tanstack/react-router', () => ({ useNavigate: () => vi.fn(), useSearch: () => mocks.search() }))
vi.mock('react-i18next', async () => ({ ...await vi.importActual<typeof import('react-i18next')>('react-i18next'), useTranslation: () => ({ t: (key: string) => key }) }))
vi.mock('@/features/publication/publication-editor', () => ({ PublicationEditor: (props: unknown) => { mocks.editor(props); return null } }))
vi.mock('@/shared/components/dashboard-page-header', () => ({ DashboardPageHeader: () => null }))
import { PublishPage } from './publish'
describe('PublishPage compatibility', () => {
  beforeEach(() => { vi.clearAllMocks(); mocks.search.mockReturnValue({}) })
  it('preserves namespace and visibility from old update links', () => {
    mocks.search.mockReturnValue({ namespace: ' team-ai ', visibility: 'private', resubmitSkill: 'example', resubmitVersion: '1.0.0' })
    renderToStaticMarkup(createElement(PublishPage))
    expect(mocks.editor.mock.calls[0][0]).toMatchObject({ prefill: { namespace: 'team-ai', visibility: 'PRIVATE', slug: 'example' } })
  })
  it('leaves new publications to use their owning domain defaults', () => {
    renderToStaticMarkup(createElement(PublishPage))
    expect(mocks.editor.mock.calls[0][0].prefill).toBeUndefined()
  })
  it('reopens an owned task from its persistent link', () => {
    mocks.search.mockReturnValue({ task: 'saved-task' })
    renderToStaticMarkup(createElement(PublishPage))
    expect(mocks.editor.mock.calls[0][0].taskId).toBe('saved-task')
  })
})
