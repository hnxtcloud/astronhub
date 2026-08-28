import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { SearchBar } from '@/features/search/search-bar'
import { SkillCard } from '@/features/skill/skill-card'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { QuickStartSection } from '@/shared/components/quick-start'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { normalizeSearchQuery } from '@/shared/lib/search-query'
import { Button } from '@/shared/ui/button'

export function HomePage() {
  const { t } = useTranslation()
  const navigate = useNavigate()

  const { data: popularSkills, isLoading: isLoadingPopular } = useSearchSkills({
    sort: 'downloads',
    size: 6,
  })

  const { data: latestSkills, isLoading: isLoadingLatest } = useSearchSkills({
    sort: 'newest',
    size: 6,
  })

  const handleSearch = (query: string) => {
    navigate({ to: '/search', search: { q: normalizeSearchQuery(query), sort: 'relevance', page: 0, starredOnly: false } })
  }

  const handleSkillClick = (namespace: string, slug: string) => {
    navigate({ to: `/space/${namespace}/${encodeURIComponent(slug)}` })
  }

  return (
    <div className="space-y-14 pb-8">
      {/* Hero Section */}
      <div className="border-b border-border py-10 md:py-14">
        <div className="max-w-3xl">
          <div className="mb-5 flex items-baseline gap-3">
            <span className="text-xl font-semibold text-primary">星枢</span>
            <span className="font-mono text-xs text-muted-foreground">AstronHub</span>
          </div>
          <h1 className="text-3xl font-semibold leading-tight tracking-tight text-foreground md:text-4xl">
            {t('home.subtitle')}
          </h1>
          <p className="mt-3 max-w-2xl text-base leading-7 text-muted-foreground">
            {t('home.description')}
          </p>
        </div>

        <div className="mt-7 max-w-2xl">
          <SearchBar onSearch={handleSearch} />
        </div>

        <div className="mt-4 flex flex-wrap items-center gap-3">
          <button
            className="rounded-md bg-primary px-5 py-2.5 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90"
            onClick={() => navigate({ to: '/search', search: { q: '', sort: 'relevance', page: 0, starredOnly: false } })}
          >
            {t('home.browseSkills')}
          </button>
          <button
            className="rounded-md border border-border bg-white px-5 py-2.5 text-sm font-medium text-foreground transition-colors hover:bg-secondary"
            onClick={() => navigate({ to: '/dashboard/publish' })}
          >
            {t('home.publishSkill')}
          </button>
        </div>
      </div>

      {/* Popular Downloads Section */}
      <section className="space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-3xl font-bold tracking-tight mb-2" style={{ color: 'hsl(var(--foreground))' }}>
              {t('home.popularTitle')}
            </h2>
            <p style={{ color: 'hsl(var(--text-secondary))' }}>{t('home.popularDescription')}</p>
          </div>
          <Button
            variant="ghost"
            onClick={() => navigate({ to: '/search', search: { q: '', sort: 'downloads', page: 0, starredOnly: false } })}
          >
            {t('home.viewAll')}
          </Button>
        </div>
        {isLoadingPopular ? (
          <SkeletonList count={6} />
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            {popularSkills?.items.map((skill) => (
              <div key={skill.id}>
                <SkillCard
                  skill={skill}
                  onClick={() => handleSkillClick(skill.namespace, skill.slug)}
                />
              </div>
            ))}
          </div>
        )}
      </section>

      {/* Latest Releases Section */}
      <section className="space-y-6 border-t border-border pt-14">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-3xl font-bold tracking-tight mb-2" style={{ color: 'hsl(var(--foreground))' }}>
              {t('home.latestTitle')}
            </h2>
            <p style={{ color: 'hsl(var(--text-secondary))' }}>{t('home.latestDescription')}</p>
          </div>
          <Button
            variant="ghost"
            onClick={() => navigate({ to: '/search', search: { q: '', sort: 'newest', page: 0, starredOnly: false } })}
          >
            {t('home.viewAll')}
          </Button>
        </div>
        {isLoadingLatest ? (
          <SkeletonList count={6} />
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            {latestSkills?.items.map((skill) => (
              <div key={skill.id}>
                <SkillCard
                  skill={skill}
                  onClick={() => handleSkillClick(skill.namespace, skill.slug)}
                />
              </div>
            ))}
          </div>
        )}
      </section>

      {/* Quick Start Section */}
      <QuickStartSection ns="home" />
    </div>
  )
}
