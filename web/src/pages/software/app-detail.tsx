import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from '@tanstack/react-router'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { Card } from '@/shared/ui/card'
import { Button } from '@/shared/ui/button'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui/table'
import { useAppDetail, useCreateDownloadSession } from '@/features/software/use-software-queries'

/**
 * Portal app detail: releases with their published packages. Downloading
 * issues a short-lived download session and redirects to the streaming URL.
 */
export function SoftwareAppDetailPage() {
  const { t, i18n } = useTranslation()
  const { slug } = useParams({ from: '/software/apps/$slug' })
  const detail = useAppDetail(slug)
  const downloadSession = useCreateDownloadSession()
  const [downloadError, setDownloadError] = useState('')

  const startDownload = async (packageId: number) => {
    setDownloadError('')
    try {
      const session = await downloadSession.mutateAsync(packageId)
      window.location.assign(session.downloadUrl)
    } catch (error) {
      setDownloadError(error instanceof Error ? error.message : String(error))
    }
  }

  if (detail.isLoading) {
    return <p className="p-8 text-muted-foreground">{t('common.loading')}</p>
  }
  if (!detail.data) {
    return <p className="p-8 text-muted-foreground">{t('softwareAppDetail.notFound')}</p>
  }

  const app = detail.data
  return (
    <div className="mx-auto w-full max-w-4xl space-y-6 p-4">
      <div>
        <Link to="/software" className="text-sm text-muted-foreground hover:underline">
          {t('softwareAppDetail.backToCatalog')}
        </Link>
        <h1 className="text-2xl font-semibold">{app.name}</h1>
        <p className="text-muted-foreground">{app.description ?? ''}</p>
      </div>

      {downloadError ? <p className="text-sm text-destructive">{downloadError}</p> : null}

      {app.releases.length === 0 ? (
        <Card className="p-8 text-center text-muted-foreground">
          {t('softwareAppDetail.noReleases')}
        </Card>
      ) : (
        app.releases.map((release) => (
          <Card key={release.id} className="space-y-3 p-4">
            <div className="flex items-baseline justify-between">
              <h2 className="text-lg font-medium">{release.displayVersion}</h2>
              <span className="text-xs text-muted-foreground">
                {release.publishedAt ? formatLocalDateTime(release.publishedAt, i18n.language) : ''}
              </span>
            </div>
            {release.releaseNotes ? (
              <p className="whitespace-pre-line text-sm text-muted-foreground">
                {release.releaseNotes}
              </p>
            ) : null}
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('softwareAppDetail.package')}</TableHead>
                  <TableHead>{t('softwareAppDetail.platform')}</TableHead>
                  <TableHead>{t('softwareAppDetail.size')}</TableHead>
                  <TableHead className="text-right">{t('softwareAppDetail.actions')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {release.packages
                  .filter((pkg) => pkg.status === 'PUBLISHED')
                  .map((pkg) => (
                    <TableRow key={pkg.id}>
                      <TableCell>
                        <div className="font-mono text-sm">{pkg.fileName}</div>
                        <div className="text-xs text-muted-foreground">
                          {pkg.format.toUpperCase()} · {pkg.originalFullVersion}
                        </div>
                      </TableCell>
                      <TableCell className="text-sm">
                        {pkg.targetOs}/{pkg.targetArch}
                        {pkg.targetDistro ? ` (${pkg.targetDistro})` : ''}
                      </TableCell>
                      <TableCell className="text-sm">
                        {(pkg.byteSize / (1024 * 1024)).toFixed(1)} MB
                      </TableCell>
                      <TableCell className="text-right">
                        <Button
                          size="sm"
                          disabled={downloadSession.isPending}
                          onClick={() => startDownload(pkg.id)}
                        >
                          {t('softwareAppDetail.download')}
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
              </TableBody>
            </Table>
          </Card>
        ))
      )}
    </div>
  )
}

export default SoftwareAppDetailPage
