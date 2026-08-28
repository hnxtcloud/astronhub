import { Link, useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import {
  GitBranch,
  PackageOpen,
  Search as SearchIcon,
  Settings,
  Shield,
  Terminal,
  Users,
} from 'lucide-react'
import { LandingQuickStartSection } from '@/shared/components/landing-quick-start'
import { SkillCard } from '@/features/skill/skill-card'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { normalizeSearchQuery } from '@/shared/lib/search-query'
import { Button } from '@/shared/ui/button'

/** Public overview and entry point for the capability registry. */
export function LandingPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()

  const { data: popularSkills, isLoading: isLoadingPopular } = useSearchSkills({ sort: 'downloads', size: 6 })
  const { data: latestSkills, isLoading: isLoadingLatest } = useSearchSkills({ sort: 'newest', size: 6 })

  const handleSkillClick = (namespace: string, slug: string) => {
    navigate({ to: `/space/${namespace}/${encodeURIComponent(slug)}` })
  }

  const handleSearch = (query: string) => {
    navigate({
      to: '/search',
      search: { q: normalizeSearchQuery(query), sort: 'relevance', page: 0, starredOnly: false },
    })
  }

  const capabilityTypes = [
    { label: t('catalog.types.skill'), description: t('landing.scope.skill.description'), to: '/catalog' as const },
    { label: t('catalog.types.plugin'), description: t('landing.scope.plugin.description'), to: '/plugins' as const },
    { label: t('catalog.types.mcp'), description: t('landing.scope.mcp.description'), to: '/mcp-servers' as const },
  ]

  const features = [
    { icon: Shield, title: t('landing.features.secure.title'), description: t('landing.features.secure.description') },
    { icon: Users, title: t('landing.features.community.title'), description: t('landing.features.community.description') },
    { icon: PackageOpen, title: t('landing.features.integration.title'), description: t('landing.features.integration.description') },
    { icon: GitBranch, title: t('landing.features.versionControl.title'), description: t('landing.features.versionControl.description') },
    { icon: Terminal, title: t('landing.features.cli.title'), description: t('landing.features.cli.description') },
    { icon: Settings, title: t('landing.features.governance.title'), description: t('landing.features.governance.description') },
  ]

  const skillSection = (
    title: string,
    description: string,
    sort: 'downloads' | 'newest',
    isLoading: boolean,
    skills: typeof popularSkills,
  ) => (
    <section className="w-full border-t border-border px-6 py-14 md:py-16">
      <div className="mx-auto max-w-6xl space-y-7">
        <div className="flex items-end justify-between gap-6">
          <div className="max-w-2xl">
            <h2 className="mb-2 text-2xl font-semibold tracking-tight text-foreground">{title}</h2>
            <p className="text-sm leading-6 text-muted-foreground md:text-base">{description}</p>
          </div>
          <Button
            variant="ghost"
            className="shrink-0 rounded-md"
            onClick={() => navigate({ to: '/search', search: { q: '', sort, page: 0, starredOnly: false } })}
          >
            {t('home.viewAll')}
          </Button>
        </div>
        {isLoading ? (
          <SkeletonList count={6} />
        ) : (
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
            {skills?.items.map((skill) => (
              <SkillCard key={skill.id} skill={skill} onClick={() => handleSkillClick(skill.namespace, skill.slug)} />
            ))}
          </div>
        )}
      </div>
    </section>
  )

  return (
    <>
      <main className="w-full px-6 py-14 md:py-20">
        <div className="mx-auto grid max-w-6xl gap-14 lg:grid-cols-[minmax(0,1.25fr)_minmax(320px,0.75fr)] lg:items-center">
          <div className="max-w-3xl">
            <div className="mb-6 flex items-baseline gap-3">
              <span className="text-xl font-semibold text-primary">星枢</span>
              <span className="font-mono text-xs text-muted-foreground">AstronHub</span>
            </div>
            <h1 className="max-w-2xl text-4xl font-semibold leading-[1.12] tracking-tight text-foreground md:text-5xl">
              {t('landing.hero.title')}
            </h1>
            <p className="mt-5 max-w-2xl text-base leading-7 text-muted-foreground md:text-lg">
              {t('landing.hero.subtitle')}
            </p>

            <div className="mt-8 flex max-w-2xl items-center rounded-md border border-border bg-white px-4 py-3 focus-within:border-primary focus-within:ring-2 focus-within:ring-primary/10">
              <SearchIcon className="mr-3 h-5 w-5 shrink-0 text-muted-foreground" strokeWidth={1.75} />
              <input
                type="search"
                placeholder={t('landing.hero.searchPlaceholder')}
                className="hero-input min-w-0 flex-1 bg-transparent text-base text-foreground outline-none"
                onKeyDown={(event) => {
                  if (event.key === 'Enter') handleSearch(event.currentTarget.value)
                }}
              />
            </div>

            <div className="mt-5 flex flex-wrap gap-3">
              <Link
                to="/catalog"
                className="rounded-md bg-primary px-5 py-2.5 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90"
              >
                {t('landing.hero.exploreSkills')}
              </Link>
              <Link
                to="/dashboard"
                className="rounded-md border border-border bg-white px-5 py-2.5 text-sm font-medium text-foreground transition-colors hover:bg-secondary"
              >
                {t('landing.hero.publishSkill')}
              </Link>
            </div>
          </div>

          <aside className="border-y border-border" aria-labelledby="capability-scope-title">
            <div className="py-4">
              <h2 id="capability-scope-title" className="text-sm font-semibold text-foreground">
                {t('landing.scope.title')}
              </h2>
              <p className="mt-1 text-sm leading-6 text-muted-foreground">{t('landing.scope.description')}</p>
            </div>
            {capabilityTypes.map((capability) => (
              <Link
                key={capability.label}
                to={capability.to}
                className="group grid grid-cols-[6rem_1fr] gap-4 border-t border-border py-4 transition-colors hover:bg-secondary/60"
              >
                <span className="font-mono text-sm font-medium text-primary">{capability.label}</span>
                <span className="text-sm leading-6 text-muted-foreground group-hover:text-foreground">
                  {capability.description}
                </span>
              </Link>
            ))}
          </aside>
        </div>
      </main>

      <section className="w-full border-t border-border px-6 py-14 md:py-16">
        <div className="mx-auto max-w-6xl">
          <div className="mb-9 max-w-2xl">
            <h2 className="text-2xl font-semibold tracking-tight text-foreground">{t('landing.whySkillHub.title')}</h2>
            <p className="mt-2 text-base leading-7 text-muted-foreground">{t('landing.whySkillHub.subtitle')}</p>
          </div>
          <div className="grid grid-cols-1 border-y border-border md:grid-cols-2">
            {features.map((feature, index) => {
              const Icon = feature.icon
              return (
                <div
                  key={feature.title}
                  className={`grid grid-cols-[2rem_1fr] gap-4 border-border py-5 md:px-5 ${index > 1 ? 'border-t' : ''} ${index % 2 === 1 ? 'md:border-l' : ''}`}
                >
                  <Icon className="mt-0.5 h-5 w-5 text-primary" strokeWidth={1.75} />
                  <div>
                    <h3 className="text-sm font-semibold text-foreground">{feature.title}</h3>
                    <p className="mt-1 text-sm leading-6 text-muted-foreground">{feature.description}</p>
                  </div>
                </div>
              )
            })}
          </div>
        </div>
      </section>

      <LandingQuickStartSection />
      {skillSection(t('home.popularTitle'), t('home.popularDescription'), 'downloads', isLoadingPopular, popularSkills)}
      {skillSection(t('home.latestTitle'), t('home.latestDescription'), 'newest', isLoadingLatest, latestSkills)}
    </>
  )
}
