import { useTranslation } from 'react-i18next'
import { withBasePath } from '@/shared/lib/base-path'

export function SuiteRetirementNotice() {
  const { t } = useTranslation()
  return (
    <aside className="rounded-md border bg-muted/30 px-4 py-3 text-sm" aria-label={t('suite.retirementTitle')}>
      <p>{t('suite.retirementNotice')}</p>
      <a href={withBasePath('/plugins')} className="mt-2 inline-block font-medium underline underline-offset-4">
        {t('suite.browsePlugins')}
      </a>
    </aside>
  )
}
