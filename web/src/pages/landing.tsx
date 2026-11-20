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
      to: '/catalog',
      search: { q: normalizeSearchQuery(query), page: 0 },
    })
  }

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
    sort: 'downloads' | 'newest',
    isLoading: boolean,
    skills: typeof popularSkills,
  ) => (
    <section className="w-full border-t border-border px-6 py-8 md:py-10">
      <div className="mx-auto max-w-6xl space-y-7">
        <div className="flex items-end justify-between gap-6">
          <div className="max-w-2xl">
            <h2 className="text-2xl font-semibold tracking-tight text-foreground">{title}</h2>
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
      <main className="w-full px-6 py-8 md:py-10">
        <div className="mx-auto max-w-6xl">
          <div className="max-w-3xl">
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
                to="/dashboard/publish"
                className="rounded-md border border-border bg-white px-5 py-2.5 text-sm font-medium text-foreground transition-colors hover:bg-secondary"
              >
                {t('publication.new')}
              </Link>
            </div>
          </div>

        </div>
      </main>

      {skillSection(t('home.popularTitle'), 'downloads', isLoadingPopular, popularSkills)}
      {skillSection(t('home.latestTitle'), 'newest', isLoadingLatest, latestSkills)}
      <LandingQuickStartSection />

      <section className="w-full border-t border-border px-6 py-6">
        <div className="mx-auto max-w-6xl">
          <details>
            <summary className="cursor-pointer text-base font-semibold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">{t('landing.whySkillHub.title')}</summary>
            <div className="mt-4 grid grid-cols-1 border-y border-border md:grid-cols-2">
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
          </details>
        </div>
      </section>
    </>
  )
}
