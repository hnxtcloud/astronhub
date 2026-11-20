import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from '@tanstack/react-router'
import { Card } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Button } from '@/shared/ui/button'
import { Pagination } from '@/shared/components/pagination'
import {
  usePublishedApps,
  useSoftwareCategories,
} from '@/features/software/use-software-queries'

/**
 * Portal software catalog: browse published applications with keyword and
 * category filters. Downloads happen on the app detail page per package.
 */
export function SoftwareCatalogPage() {
  const { t } = useTranslation()
  const [searchInput, setSearchInput] = useState('')
  const [keyword, setKeyword] = useState('')
  const [categoryCode, setCategoryCode] = useState('')
  const [page, setPage] = useState(0)

  const categories = useSoftwareCategories()
  const apps = usePublishedApps({
    keyword: keyword || undefined,
    categoryCode: categoryCode || undefined,
    page,
    size: 20,
  })

  const totalPages = Math.max(1, Math.ceil((apps.data?.total ?? 0) / 20))

  return (
    <div className="mx-auto w-full max-w-5xl space-y-6 p-4">
      <div>
        <h1 className="text-2xl font-semibold">{t('softwareCatalog.title')}</h1>
        <Link className="mt-2 inline-block text-sm text-primary hover:underline" to="/catalog" search={{ type: 'SOFTWARE' }}>{t('nav.catalog')}</Link>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <Input
          className="max-w-xs"
          placeholder={t('softwareCatalog.searchPlaceholder')}
          value={searchInput}
          onChange={(event) => setSearchInput(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              setKeyword(searchInput.trim())
              setPage(0)
            }
          }}
        />
        <div className="flex flex-wrap gap-2">
          <Button
            variant={categoryCode === '' ? 'default' : 'outline'}
            size="sm"
            onClick={() => {
              setCategoryCode('')
              setPage(0)
            }}
          >
            {t('softwareCatalog.allCategories')}
          </Button>
          {(categories.data ?? []).map((category) => (
            <Button
              key={category.code}
              variant={categoryCode === category.code ? 'default' : 'outline'}
              size="sm"
              onClick={() => {
                setCategoryCode(category.code)
                setPage(0)
              }}
            >
              {category.name}
            </Button>
          ))}
        </div>
      </div>

      {apps.isLoading ? (
        <p className="text-muted-foreground">{t('common.loading')}</p>
      ) : (apps.data?.items.length ?? 0) === 0 ? (
        <Card className="p-8 text-center text-muted-foreground">
          {t('softwareCatalog.empty')}
        </Card>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {(apps.data?.items ?? []).map((app) => (
            <Link key={app.id} to="/software/apps/$slug" params={{ slug: app.slug }}>
              <Card className="h-full p-4 transition-shadow hover:shadow-md">
                <div className="font-medium">{app.name}</div>
                {app.categoryName ? (
                  <div className="text-xs text-muted-foreground">{app.categoryName}</div>
                ) : null}
                <p className="mt-2 line-clamp-2 text-sm text-muted-foreground">
                  {app.description ?? ''}
                </p>
              </Card>
            </Link>
          ))}
        </div>
      )}

      {(apps.data?.total ?? 0) > 20 ? (
        <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
      ) : null}
    </div>
  )
}

export default SoftwareCatalogPage
