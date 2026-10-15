import { KeyboardEvent, useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { Card } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Textarea } from '@/shared/ui/textarea'
import { Button } from '@/shared/ui/button'
import { Label } from '@/shared/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  normalizeSelectValue,
} from '@/shared/ui/select'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui/table'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/shared/ui/dialog'
import { Pagination } from '@/shared/components/pagination'
import {
  runChunkedUpload,
  useAdminSoftwareApps,
  useCreateSoftwareApp,
  useCreateSoftwareRelease,
  useSetPackageStatus,
  useSetSoftwareAppStatus,
  useSoftwareCategories,
  useSoftwareReleases,
} from '@/features/software/use-software-queries'

import type { SoftwareAppAdminSummary, SoftwarePackage, SoftwareRelease } from '@/api/types'
import { SoftwareUpdateManager, SoftwareUpdateSourceManager } from '@/features/software/software-update-manager'

const ALL_STATUS = '__all__'

const PACKAGE_FORMATS = ['msi', 'exe', 'deb', 'rpm', 'zip', 'dmg']
const TARGET_OSES = ['windows', 'linux', 'macos']
const TARGET_ARCHES = ['x86_64', 'arm64', 'x86', 'universal']

/**
 * Admin catalog management: applications, releases, and package revisions.
 * Package binaries arrive through the chunked upload session; lifecycle
 * actions follow the confirm → publish state machine.
 */
export function AdminSoftwarePage() {
  const { t, i18n } = useTranslation()
  const [search, setSearch] = useState('')
  const [searchInput, setSearchInput] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [page, setPage] = useState(0)
  const [createAppOpen, setCreateAppOpen] = useState(false)
  const [selectedApp, setSelectedApp] = useState<SoftwareAppAdminSummary | null>(null)

  const { data, isLoading } = useAdminSoftwareApps({
    keyword: search || undefined,
    status: statusFilter || undefined,
    page,
    size: 20,
  })
  const categories = useSoftwareCategories()
  const createApp = useCreateSoftwareApp()
  const setAppStatus = useSetSoftwareAppStatus()

  const apps = data?.items ?? []
  const totalElements = data?.total ?? 0
  const totalPages = Math.max(1, Math.ceil(totalElements / 20))

  useEffect(() => {
    setPage(0)
  }, [search, statusFilter])

  const applySearch = () => setSearch(searchInput.trim())
  const handleSearchKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter') applySearch()
  }

  const formatDate = (iso: string) => formatLocalDateTime(iso, i18n.language)
  const statusLabel = (status: string) => t(`softwareAdmin.status.${status}`, status)

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">{t('softwareAdmin.title')}</h1>
        <Button onClick={() => setCreateAppOpen(true)}>{t('softwareAdmin.createApp')}</Button>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <Input
          value={searchInput}
          onChange={(event) => setSearchInput(event.target.value)}
          onKeyDown={handleSearchKeyDown}
          placeholder={t('softwareAdmin.searchPlaceholder')}
          className="w-64"
        />
        <Select value={statusFilter || ALL_STATUS} onValueChange={(value) => setStatusFilter(normalizeSelectValue(value) === ALL_STATUS ? '' : (normalizeSelectValue(value) ?? ''))}>
          <SelectTrigger className="w-44">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL_STATUS}>{t('softwareAdmin.allStatuses')}</SelectItem>
            <SelectItem value="DRAFT">{statusLabel('DRAFT')}</SelectItem>
            <SelectItem value="PUBLISHED">{statusLabel('PUBLISHED')}</SelectItem>
            <SelectItem value="TAKEN_DOWN">{statusLabel('TAKEN_DOWN')}</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <Card>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t('softwareAdmin.app')}</TableHead>
              <TableHead>{t('softwareAdmin.category')}</TableHead>
              <TableHead>{t('softwareAdmin.status')}</TableHead>
              <TableHead>{t('softwareAdmin.updatedAt')}</TableHead>
              <TableHead className="text-right">{t('softwareAdmin.actions')}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={5} className="text-center text-muted-foreground">
                  {t('common.loading')}
                </TableCell>
              </TableRow>
            ) : apps.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5} className="text-center text-muted-foreground">
                  {t('softwareAdmin.empty')}
                </TableCell>
              </TableRow>
            ) : (
              apps.map((app) => (
                <TableRow key={app.id}>
                  <TableCell>
                    <div className="font-medium">{app.name}</div>
                    <div className="font-mono text-xs text-muted-foreground">@{app.slug}</div>
                  </TableCell>
                  <TableCell className="text-sm">{app.categoryName ?? '—'}</TableCell>
                  <TableCell className="text-sm">{statusLabel(app.status)}</TableCell>
                  <TableCell className="text-sm">{formatDate(app.updatedAt)}</TableCell>
                  <TableCell className="space-x-2 text-right">
                    <Button size="sm" variant="outline" onClick={() => setSelectedApp(app)}>
                      {t('softwareAdmin.manage')}
                    </Button>
                    {app.status === 'PUBLISHED' ? (
                      <Button
                        size="sm"
                        variant="ghost"
                        disabled={setAppStatus.isPending}
                        onClick={() => setAppStatus.mutate({ appId: app.id, action: 'takedown' })}
                      >
                        {t('softwareAdmin.takedown')}
                      </Button>
                    ) : (
                      <Button
                        size="sm"
                        variant="ghost"
                        disabled={setAppStatus.isPending}
                        onClick={() =>
                          setAppStatus.mutate({
                            appId: app.id,
                            action: app.status === 'TAKEN_DOWN' ? 'restore' : 'publish',
                          })
                        }
                      >
                        {app.status === 'TAKEN_DOWN'
                          ? t('softwareAdmin.restore')
                          : t('softwareAdmin.publish')}
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
        {totalPages > 1 ? (
          <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
        ) : null}
      </Card>

      {selectedApp ? (
        <AppReleaseManager key={selectedApp.id} appId={selectedApp.id} appName={selectedApp.name} formatDate={formatDate} />
      ) : null}

      <CreateAppDialog
        open={createAppOpen}
        onOpenChange={setCreateAppOpen}
        categories={categories.data ?? []}
        pending={createApp.isPending}
        onCreate={(request) =>
          createApp.mutate(request, { onSuccess: () => setCreateAppOpen(false) })
        }
      />

    </div>
  )
}

function CreateAppDialog(props: {
  open: boolean
  onOpenChange: (open: boolean) => void
  categories: { code: string; name: string }[]
  pending: boolean
  onCreate: (request: { slug: string; name: string; description?: string; categoryCode?: string }) => void
}) {
  const { t } = useTranslation()
  const [slug, setSlug] = useState('')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [categoryCode, setCategoryCode] = useState('')

  const submit = () => {
    if (!slug.trim() || !name.trim()) return
    props.onCreate({
      slug: slug.trim(),
      name: name.trim(),
      description: description.trim() || undefined,
      categoryCode: categoryCode || undefined,
    })
  }

  return (
    <Dialog open={props.open} onOpenChange={props.onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('softwareAdmin.createApp')}</DialogTitle>
          <DialogDescription>{t('softwareAdmin.createAppDescription')}</DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="software-app-slug">{t('softwareAdmin.slug')}</Label>
            <Input
              id="software-app-slug"
              value={slug}
              onChange={(event) => setSlug(event.target.value)}
              placeholder="visual-studio-code"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="software-app-name">{t('softwareAdmin.name')}</Label>
            <Input id="software-app-name" value={name} onChange={(event) => setName(event.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="software-app-description">{t('softwareAdmin.description')}</Label>
            <Textarea
              id="software-app-description"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              rows={3}
            />
          </div>
          <div className="space-y-2">
            <Label>{t('softwareAdmin.category')}</Label>
            <Select value={categoryCode || '__none__'} onValueChange={(value) => setCategoryCode(normalizeSelectValue(value) === '__none__' ? '' : (normalizeSelectValue(value) ?? ''))}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="__none__">{t('softwareAdmin.noCategory')}</SelectItem>
                {props.categories.map((category) => (
                  <SelectItem key={category.code} value={category.code}>
                    {category.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => props.onOpenChange(false)}>
            {t('common.cancel')}
          </Button>
          <Button onClick={submit} disabled={props.pending || !slug.trim() || !name.trim()}>
            {t('common.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function AppReleaseManager(props: {
  appId: number
  appName: string
  formatDate: (iso: string) => string
}) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const releases = useSoftwareReleases(props.appId)
  const createRelease = useCreateSoftwareRelease()
  const setPackageStatus = useSetPackageStatus()
  const [createReleaseOpen, setCreateReleaseOpen] = useState(false)
  const [uploadTarget, setUploadTarget] = useState<SoftwareRelease | null>(null)
  const [confirmTarget, setConfirmTarget] = useState<SoftwarePackage | null>(null)

  const allReleases = releases.data ?? []
  const statusLabel = (status: string) => t(`softwareAdmin.status.${status}`, status)

  const afterPackageChange = () => {
    queryClient.invalidateQueries({ queryKey: ['software'] })
  }

  return (
    <Card className="space-y-4 p-4">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold">
          {t('softwareAdmin.releasesOf', { name: props.appName })}
        </h2>
        <Button size="sm" variant="outline" onClick={() => setCreateReleaseOpen(true)}>
          {t('softwareAdmin.createRelease')}
        </Button>
      </div>

      <SoftwareUpdateSourceManager appId={props.appId} />

      {releases.isLoading ? (
        <p className="text-sm text-muted-foreground">{t('common.loading')}</p>
      ) : allReleases.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t('softwareAdmin.noReleases')}</p>
      ) : (
        allReleases.map((release) => (
          <div key={release.id} className="space-y-2 rounded-md border p-3">
            <div className="flex items-baseline justify-between">
              <div className="font-medium">{release.displayVersion}</div>
              <div className="text-xs text-muted-foreground">
                {release.publishedAt ? props.formatDate(release.publishedAt) : ''}
              </div>
            </div>
            <Button size="sm" variant="outline" onClick={() => setUploadTarget(release)}>{t('softwareAdmin.uploadPackage')}</Button>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('softwareAdmin.file')}</TableHead>
                  <TableHead>{t('softwareAdmin.platform')}</TableHead>
                  <TableHead>{t('softwareAdmin.packageStatus')}</TableHead>
                  <TableHead className="text-right">{t('softwareAdmin.actions')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {release.packages.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={4} className="text-center text-sm text-muted-foreground">
                      {t('softwareAdmin.noPackages')}
                    </TableCell>
                  </TableRow>
                ) : (
                  release.packages.map((pkg) => (
                    <TableRow key={pkg.id}>
                      <TableCell>
                        <div className="font-mono text-sm">{pkg.fileName}</div>
                        <div className="text-xs text-muted-foreground">
                          v{pkg.revisionNo} · {pkg.originalFullVersion} ·{' '}
                          {(pkg.byteSize / (1024 * 1024)).toFixed(1)} MB
                        </div>
                      </TableCell>
                      <TableCell className="text-sm">
                        {pkg.targetOs}/{pkg.targetArch}
                        {pkg.targetDistro ? ` (${pkg.targetDistro})` : ''}
                      </TableCell>
                      <TableCell className="text-sm">{statusLabel(pkg.status)}</TableCell>
                      <TableCell className="space-x-2 text-right">
                        <Button size="sm" variant="outline" onClick={() => setUploadTarget(release)}>
                          {t('softwareAdmin.uploadPackage')}
                        </Button>
                        {pkg.status === 'DRAFT' && !pkg.confirmedBy ? (
                          <Button
                            size="sm"
                            variant="ghost"
                            disabled={setPackageStatus.isPending}
                            onClick={() => setConfirmTarget(pkg)}
                          >
                            {t('softwareAdmin.confirmPackage')}
                          </Button>
                        ) : null}
                        {pkg.status === 'DRAFT' && pkg.confirmedBy ? (
                          <Button
                            size="sm"
                            variant="ghost"
                            disabled={setPackageStatus.isPending}
                            onClick={() =>
                              setPackageStatus.mutate(
                                { packageId: pkg.id, action: 'publish' },
                                { onSuccess: afterPackageChange },
                              )
                            }
                          >
                            {t('softwareAdmin.publish')}
                          </Button>
                        ) : null}
                        {pkg.status === 'PUBLISHED' ? (
                          <Button
                            size="sm"
                            variant="ghost"
                            disabled={setPackageStatus.isPending}
                            onClick={() =>
                              setPackageStatus.mutate(
                                { packageId: pkg.id, action: 'takedown' },
                                { onSuccess: afterPackageChange },
                              )
                            }
                          >
                            {t('softwareAdmin.takedown')}
                          </Button>
                        ) : null}
                        {pkg.status === 'TAKEN_DOWN' ? (
                          <Button
                            size="sm"
                            variant="ghost"
                            disabled={setPackageStatus.isPending}
                            onClick={() =>
                              setPackageStatus.mutate(
                                { packageId: pkg.id, action: 'restore' },
                                { onSuccess: afterPackageChange },
                              )
                            }
                          >
                            {t('softwareAdmin.restore')}
                          </Button>
                        ) : null}
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
            <SoftwareUpdateManager release={release} />
          </div>
        ))
      )}

      <CreateReleaseDialog
        appId={props.appId}
        open={createReleaseOpen}
        onOpenChange={setCreateReleaseOpen}
        pending={createRelease.isPending}
        onCreate={(request) =>
          createRelease.mutate(request, { onSuccess: () => setCreateReleaseOpen(false) })
        }
      />

      {uploadTarget ? (
        <UploadPackageDialog
          releaseId={uploadTarget.id}
          onClose={() => setUploadTarget(null)}
          onUploaded={afterPackageChange}
        />
      ) : null}

      {confirmTarget ? (
        <ConfirmPackageDialog
          pkg={confirmTarget}
          pending={setPackageStatus.isPending}
          onClose={() => setConfirmTarget(null)}
          onConfirm={(packageId, rawVersionFields) =>
            setPackageStatus.mutate(
              { packageId, action: 'confirm', rawVersionFields },
              { onSuccess: () => {
                setConfirmTarget(null)
                afterPackageChange()
              } },
            )
          }
        />
      ) : null}
    </Card>
  )
}

function CreateReleaseDialog(props: {
  appId: number
  open: boolean
  onOpenChange: (open: boolean) => void
  pending: boolean
  onCreate: (request: { appId: number; displayVersion: string; releaseNotes?: string }) => void
}) {
  const { t } = useTranslation()
  const [displayVersion, setDisplayVersion] = useState('')
  const [releaseNotes, setReleaseNotes] = useState('')

  const submit = () => {
    if (!displayVersion.trim()) return
    props.onCreate({
      appId: props.appId,
      displayVersion: displayVersion.trim(),
      releaseNotes: releaseNotes.trim() || undefined,
    })
  }

  return (
    <Dialog open={props.open} onOpenChange={props.onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('softwareAdmin.createRelease')}</DialogTitle>
          <DialogDescription>{t('softwareAdmin.createReleaseDescription')}</DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="software-release-version">{t('softwareAdmin.displayVersion')}</Label>
            <Input
              id="software-release-version"
              value={displayVersion}
              onChange={(event) => setDisplayVersion(event.target.value)}
              placeholder="1.0.0"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="software-release-notes">{t('softwareAdmin.releaseNotes')}</Label>
            <Textarea
              id="software-release-notes"
              value={releaseNotes}
              onChange={(event) => setReleaseNotes(event.target.value)}
              rows={4}
            />
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => props.onOpenChange(false)}>
            {t('common.cancel')}
          </Button>
          <Button onClick={submit} disabled={props.pending || !displayVersion.trim()}>
            {t('common.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function UploadPackageDialog(props: {
  releaseId: number
  onClose: () => void
  onUploaded: () => void
}) {
  const { t } = useTranslation()
  const [file, setFile] = useState<File | null>(null)
  const [format, setFormat] = useState('zip')
  const [targetOs, setTargetOs] = useState('windows')
  const [targetArch, setTargetArch] = useState('x86_64')
  const [targetDistro, setTargetDistro] = useState('')
  const [originalFullVersion, setOriginalFullVersion] = useState('')
  const [applicabilityNote, setApplicabilityNote] = useState('')
  const [progress, setProgress] = useState<{ uploaded: number; total: number } | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const canSubmit = file != null && originalFullVersion.trim() !== '' && !busy

  const submit = async () => {
    if (!file) return
    setBusy(true)
    setError('')
    setProgress({ uploaded: 0, total: file.size })
    try {
      await runChunkedUpload(
        {
          releaseId: props.releaseId,
          fileName: file.name,
          format,
          targetOs,
          targetArch,
          targetDistro: targetDistro.trim() || null,
          applicabilityNote: applicabilityNote.trim() || null,
          originalFullVersion: originalFullVersion.trim(),
          byteSize: file.size,
        },
        file,
        { onProgress: (uploaded, total) => setProgress({ uploaded, total }) },
      )
      props.onUploaded()
      props.onClose()
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setBusy(false)
      setProgress(null)
    }
  }

  const percent = progress && progress.total > 0
    ? Math.round((progress.uploaded / progress.total) * 100)
    : 0

  return (
    <Dialog open onOpenChange={(open) => { if (!open && !busy) props.onClose() }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('softwareAdmin.uploadPackage')}</DialogTitle>
          <DialogDescription>{t('softwareAdmin.uploadPackageDescription')}</DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="software-upload-file">{t('softwareAdmin.file')}</Label>
            <Input
              id="software-upload-file"
              type="file"
              onChange={(event) => setFile(event.target.files?.[0] ?? null)}
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
              <Label>{t('softwareAdmin.format')}</Label>
              <Select value={format} onValueChange={(value) => setFormat(normalizeSelectValue(value) ?? '')}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {PACKAGE_FORMATS.map((value) => (
                    <SelectItem key={value} value={value}>{value}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label>{t('softwareAdmin.targetOs')}</Label>
              <Select value={targetOs} onValueChange={(value) => setTargetOs(normalizeSelectValue(value) ?? '')}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {TARGET_OSES.map((value) => (
                    <SelectItem key={value} value={value}>{value}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label>{t('softwareAdmin.targetArch')}</Label>
              <Select value={targetArch} onValueChange={(value) => setTargetArch(normalizeSelectValue(value) ?? '')}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {TARGET_ARCHES.map((value) => (
                    <SelectItem key={value} value={value}>{value}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="software-upload-distro">{t('softwareAdmin.targetDistro')}</Label>
              <Input
                id="software-upload-distro"
                value={targetDistro}
                onChange={(event) => setTargetDistro(event.target.value)}
                placeholder="ubuntu-22.04"
              />
            </div>
          </div>
          <div className="space-y-2">
            <Label htmlFor="software-upload-version">{t('softwareAdmin.originalFullVersion')}</Label>
            <Input
              id="software-upload-version"
              value={originalFullVersion}
              onChange={(event) => setOriginalFullVersion(event.target.value)}
              placeholder="1.2.3-build456"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="software-upload-note">{t('softwareAdmin.applicabilityNote')}</Label>
            <Input
              id="software-upload-note"
              value={applicabilityNote}
              onChange={(event) => setApplicabilityNote(event.target.value)}
            />
          </div>
          {busy && progress ? (
            <div className="space-y-1">
              <div className="h-2 w-full overflow-hidden rounded-full bg-muted">
                <div className="h-full bg-primary transition-all" style={{ width: `${percent}%` }} />
              </div>
              <p className="text-xs text-muted-foreground">{percent}%</p>
            </div>
          ) : null}
          {error ? <p className="text-sm text-destructive">{error}</p> : null}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={props.onClose} disabled={busy}>
            {t('common.cancel')}
          </Button>
          <Button onClick={submit} disabled={!canSubmit}>
            {t('softwareAdmin.upload')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function ConfirmPackageDialog(props: {
  pkg: SoftwarePackage
  pending: boolean
  onClose: () => void
  onConfirm: (packageId: number, rawVersionFields?: Record<string, unknown>) => void
}) {
  const { t } = useTranslation()
  const [rawFieldsJson, setRawFieldsJson] = useState('')
  const [jsonError, setJsonError] = useState('')

  const parsedFields = useMemo<{ ok: boolean; fields?: Record<string, unknown> }>(() => {
    const trimmed = rawFieldsJson.trim()
    if (trimmed === '') return { ok: true }
    try {
      const parsed: unknown = JSON.parse(trimmed)
      if (parsed == null || typeof parsed !== 'object' || Array.isArray(parsed)) {
        return { ok: false }
      }
      return { ok: true, fields: parsed as Record<string, unknown> }
    } catch {
      return { ok: false }
    }
  }, [rawFieldsJson])

  const submit = () => {
    if (!parsedFields.ok) {
      setJsonError(t('softwareAdmin.rawFieldsInvalid'))
      return
    }
    props.onConfirm(props.pkg.id, parsedFields.fields)
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open) props.onClose() }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('softwareAdmin.confirmPackage')}</DialogTitle>
          <DialogDescription>
            {t('softwareAdmin.confirmPackageDescription', { file: props.pkg.fileName })}
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-2">
          <Label htmlFor="software-confirm-raw-fields">
            {t('softwareAdmin.rawVersionFields')}
          </Label>
          <Textarea
            id="software-confirm-raw-fields"
            value={rawFieldsJson}
            onChange={(event) => {
              setRawFieldsJson(event.target.value)
              setJsonError('')
            }}
            rows={5}
            placeholder='{"ProductVersion": "1.2.3"}'
            className="font-mono text-xs"
          />
          {jsonError ? <p className="text-sm text-destructive">{jsonError}</p> : null}
          <p className="text-xs text-muted-foreground">{t('softwareAdmin.rawFieldsHint')}</p>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={props.onClose}>
            {t('common.cancel')}
          </Button>
          <Button onClick={submit} disabled={props.pending || !parsedFields.ok}>
            {t('softwareAdmin.confirmPackage')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

export default AdminSoftwarePage
