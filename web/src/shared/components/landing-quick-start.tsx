import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Bot, Check, Copy, Terminal, UserRound } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { useCopyToClipboard } from '@/shared/lib/clipboard'
import { resolvePublicRegistryUrl } from '@/shared/lib/registry-url'

type LandingQuickStartTabId = 'agent' | 'human' | 'cli'

interface LandingQuickStartTab {
  id: LandingQuickStartTabId
  label: string
  description: string
  command: string
}

const tabIcons: Record<LandingQuickStartTabId, LucideIcon> = {
  agent: Bot,
  human: UserRound,
  cli: Terminal,
}

function getAppBaseUrl(): string {
  if (typeof window === 'undefined') return ''
  return resolvePublicRegistryUrl(
    window.__SKILLHUB_RUNTIME_CONFIG__?.appBaseUrl,
    `${window.location.protocol}//${window.location.host}`,
  )
}

function CompactCopyButton({ text }: { text: string }) {
  const { t } = useTranslation()
  const [copied, copy] = useCopyToClipboard()
  const label = copied ? (t('copyButton.copied') || 'Copied') : (t('copyButton.copy') || 'Copy')

  return (
    <button
      type="button"
      onClick={async () => {
        try {
          await copy(text)
        } catch (error) {
          console.error('Failed to copy:', error)
        }
      }}
      aria-label={label}
      title={label}
      className="absolute right-2 top-1/2 flex h-9 w-9 -translate-y-1/2 items-center justify-center rounded-md border border-border bg-white text-foreground transition-colors hover:bg-secondary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
    >
      {copied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
    </button>
  )
}

export function LandingQuickStartSection() {
  const { t } = useTranslation()
  const [activeTab, setActiveTab] = useState<LandingQuickStartTabId>('agent')
  const baseUrl = useMemo(() => getAppBaseUrl(), [])
  const tabs: LandingQuickStartTab[] = [
    {
      id: 'agent',
      label: t('landing.quickStart.tabs.agent'),
      description: t('landing.quickStart.agent.description'),
      command: t('landing.quickStart.agent.commandTemplate', {
        defaultValue: t('landing.quickStart.agent.command'),
        url: `${baseUrl}/registry/skill.md`,
      }),
    },
    {
      id: 'human',
      label: t('landing.quickStart.tabs.human'),
      description: t('landing.quickStart.human.description'),
      command: t('landing.quickStart.human.command'),
    },
    {
      id: 'cli',
      label: t('landing.quickStart.tabs.cli'),
      description: t('landing.quickStart.cli.description'),
      command: t('landing.quickStart.cli.command'),
    },
  ]
  const currentTab = tabs.find((tab) => tab.id === activeTab) ?? tabs[0]

  return (
    <section className="w-full border-t border-border px-6 py-14 md:py-16">
      <div className="mx-auto grid max-w-6xl gap-8 lg:grid-cols-[minmax(220px,0.65fr)_minmax(0,1.35fr)]">
        <div>
          <h2 className="text-2xl font-semibold tracking-tight text-foreground">{t('landing.quickStart.title')}</h2>
          <p className="mt-2 max-w-md text-sm leading-6 text-muted-foreground md:text-base">
            {t('landing.quickStart.description', { defaultValue: t('landing.quickStart.subtitle') })}
          </p>
        </div>

        <div className="border-y border-border">
          <div className="flex flex-wrap gap-1 border-b border-border py-2" role="tablist">
            {tabs.map((tab) => {
              const isActive = tab.id === currentTab.id
              const Icon = tabIcons[tab.id]
              return (
                <button
                  key={tab.id}
                  type="button"
                  role="tab"
                  aria-selected={isActive}
                  onClick={() => setActiveTab(tab.id)}
                  className={`flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring ${isActive ? 'bg-secondary text-foreground' : 'text-muted-foreground hover:text-foreground'}`}
                >
                  <Icon className="h-4 w-4" strokeWidth={1.75} />
                  {tab.label}
                </button>
              )
            })}
          </div>

          <div className="py-6">
            <p className="mb-4 text-sm leading-6 text-foreground md:text-base">{currentTab.description}</p>
            <div className="relative rounded-md border border-border bg-slate-950 px-4 py-3 pr-14">
              <div className="overflow-x-auto whitespace-nowrap">
                <code className="font-mono text-sm text-slate-100 md:text-base">{currentTab.command}</code>
              </div>
              <CompactCopyButton text={currentTab.command} />
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}
