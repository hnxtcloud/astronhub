import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { fetchJson, getCsrfHeaders } from '@/api/client'
import type { components } from '@/api/generated/schema'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'

type ImportLimits = components['schemas']['PluginImportLimits']
type ImportCandidate = components['schemas']['ImportCandidateView']
type ImportPreview = components['schemas']['ImportPreviewView']
type ImportResults = components['schemas']['ImportResultsView']
type GeneratedSelection = components['schemas']['ImportSelection']
type Selection = Omit<GeneratedSelection, 'visibility'> & { visibility: NonNullable<GeneratedSelection['visibility']> }
const base='/api/v1/plugin-management'
const selectClass='h-10 w-full rounded-md border border-input bg-background px-3 text-sm'
export function PluginOfflineImportPanel({ reviewer, namespaces, projects }: {
 reviewer: boolean
 namespaces: Array<{id: number; displayName: string; status: string}>
 projects: Array<{id: number; namespaceId: number; displayName: string}>
}) {
 const {t}=useTranslation();const queries=useQueryClient()
 const [namespace,setNamespace]=useState('');const [repository,setRepository]=useState(false);const [file,setFile]=useState<File|null>(null)
 const [preview,setPreview]=useState<ImportPreview|null>(null);const [selected,setSelected]=useState<Record<string,Selection>>({})
 const limits=useQuery({queryKey:['plugin-import','limits'],queryFn:()=>fetchJson<ImportLimits>(`${base}/import-settings`)})
 const upload=useMutation({mutationFn:()=>fetchJson<ImportPreview>(`${base}/imports?namespaceId=${encodeURIComponent(namespace)}&format=${file?.name.toLowerCase().endsWith('.zip')?'ZIP':'TGZ'}&repository=${repository}`,{
  method:'POST',headers:getCsrfHeaders({'Content-Type':'application/octet-stream'}),body:file,timeoutMs:300000,
 }),onSuccess:result=>{setPreview(result);setSelected({})}})
 const apply=useMutation({mutationFn:()=>fetchJson<ImportResults>(`${base}/imports/${preview?.id}/apply`,{method:'POST',headers:getCsrfHeaders({'Content-Type':'application/json'}),body:JSON.stringify({selections:Object.values(selected)} satisfies components['schemas']['ImportSelectionRequest']),timeoutMs:300000}),onSuccess:()=>queries.invalidateQueries({queryKey:['plugin-management']})})
 const toggle=(candidate:ImportCandidate,checked:boolean)=>setSelected(current=>{
  const next={...current};if(checked)next[candidate.id]={candidateId:candidate.id,slug:candidate.suggestedSlug??'',visibility:'PRIVATE'};else delete next[candidate.id];return next
 })
 const change=(id:string,patch:Partial<Selection>)=>setSelected(current=>({...current,[id]:{...current[id],...patch}}))
 return <section className="space-y-4 rounded-lg border p-4">
  <h2 className="font-semibold">{t('pluginImport.title')}</h2><p className="max-w-prose text-sm text-muted-foreground">{t('pluginImport.help')}</p>
  <form className="grid gap-4 sm:grid-cols-2" onSubmit={(event:FormEvent)=>{event.preventDefault();upload.mutate()}}>
   <label className="space-y-1 text-sm">{t('pluginImport.namespace')}<select required className={selectClass} value={namespace} onChange={e=>{setNamespace(e.target.value);setPreview(null)}}><option value="">{t('pluginManagement.selectNamespace')}</option>{namespaces.filter(n=>n.status==='ACTIVE').map(n=><option key={n.id} value={n.id}>{n.displayName}</option>)}</select></label>
   <label className="space-y-1 text-sm">{t('pluginImport.archive')}<Input required type="file" accept=".zip,.tgz,.tar.gz" onChange={e=>{setFile(e.target.files?.[0]??null);setPreview(null)}} /></label>
   <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={repository} onChange={e=>setRepository(e.target.checked)} />{t('pluginImport.repository')}</label>
   <p className="text-xs text-muted-foreground">{t('pluginImport.repositoryPermission')}</p>
   <Button type="submit" disabled={!namespace||!file||upload.isPending}>{t('pluginImport.preview')}</Button>
  </form>
  {limits.data&&<p className="text-xs text-muted-foreground">{t('pluginImport.budgets',{package:limits.data.packageBytes,repository:limits.data.repositoryBytes,expanded:limits.data.expandedBytes,files:limits.data.files,candidates:limits.data.candidates})}</p>}
  {upload.isPending&&<p role="status">{t('common.loading')}</p>}{upload.error&&<p role="alert" className="text-sm text-destructive">{upload.error.message}</p>}
  {preview&&<div className="space-y-4 border-t pt-4"><p className="text-sm text-muted-foreground">{t('pluginImport.expires',{at:preview.expiresAt})}</p>
   {preview.candidates.length===0&&<p>{t('pluginImport.unrecognized')}</p>}
   {preview.candidates.map(candidate=><article key={candidate.id} className="space-y-3 rounded border p-3">
    <div className="flex flex-wrap items-center justify-between gap-3"><label className="flex items-center gap-2 text-sm"><input type="checkbox" aria-label={t('pluginImport.select',{name:candidate.nativeName??candidate.directory})} checked={!!selected[candidate.id]} disabled={candidate.status==='INVALID'||candidate.status==='CONFLICT'||candidate.status==='TARGET_UNAVAILABLE'} onChange={e=>toggle(candidate,e.target.checked)} /><span>{candidate.nativeName?`${candidate.nativeName}@${candidate.version}`:candidate.directory}</span></label><span className="text-sm">{candidate.runtime} · {t(`pluginImport.status.${candidate.status}`)}</span></div>
    <p className="break-all font-mono text-xs text-muted-foreground">{candidate.directory||'.'}</p>{candidate.error&&<p className="text-sm text-destructive">{t(`pluginImport.validation.${candidate.error}`, { defaultValue: t('pluginImport.status.INVALID') })}</p>}
    {selected[candidate.id]&&<div className="grid gap-3 sm:grid-cols-3">
     <label className="space-y-1 text-sm">{t('pluginImport.target')}<select className={selectClass} value={selected[candidate.id].projectId??''} onChange={e=>change(candidate.id,{projectId:e.target.value?Number(e.target.value):undefined})}><option value="">{t('pluginImport.newProject')}</option>{projects.filter(p=>p.namespaceId===preview.namespaceId).map(project=><option key={project.id} value={project.id}>{project.displayName}</option>)}</select></label>
     {!selected[candidate.id].projectId&&<><label className="space-y-1 text-sm">{t('pluginImport.slug')}<Input required maxLength={64} value={selected[candidate.id].slug??''} onChange={e=>change(candidate.id,{slug:e.target.value})} /></label><label className="space-y-1 text-sm">{t('pluginManagement.visibility')}<select className={selectClass} value={selected[candidate.id].visibility} onChange={e=>change(candidate.id,{visibility:e.target.value as Selection['visibility']})}>{(['PRIVATE','NAMESPACE_ONLY','PUBLIC'] as const).map(value=><option key={value} value={value}>{t(`pluginManagement.visibilityValues.${value}`)}</option>)}</select></label></>}
    </div>}
    <details><summary className="cursor-pointer text-sm">{t('pluginImport.evidence')}</summary><pre className="mt-2 max-h-64 overflow-auto whitespace-pre-wrap break-all rounded bg-muted p-3 text-xs">{candidate.inventoryJson}{'\n'}{candidate.manifestJson}</pre></details>
   </article>)}
   <Button disabled={Object.keys(selected).length===0||apply.isPending} onClick={()=>apply.mutate()}>{t('pluginImport.apply')}</Button>
   {apply.error&&<p role="alert" className="text-destructive">{apply.error.message}</p>}{apply.data&&<ul aria-live="polite" className="space-y-2 text-sm">{apply.data.results.map(result=><li key={result.candidateId}>{result.candidateId}: {t(`pluginImport.status.${result.status}`)}{result.error?` (${result.error})`:''}</li>)}</ul>}
  </div>}
  {reviewer&&limits.data&&<ImportBudgetEditor limits={limits.data} />}
 </section>
}
function ImportBudgetEditor({limits}:{limits:ImportLimits}) {
 const {t}=useTranslation();const queries=useQueryClient();const [values,setValues]=useState(limits)
 const save=useMutation({mutationFn:()=>fetchJson<ImportLimits>(`${base}/import-settings`,{method:'PUT',headers:getCsrfHeaders({'Content-Type':'application/json'}),body:JSON.stringify(values)}),onSuccess:()=>queries.invalidateQueries({queryKey:['plugin-import','limits']})})
 return <details className="border-t pt-4"><summary className="cursor-pointer text-sm font-medium">{t('pluginImport.configure')}</summary><form className="mt-3 grid gap-3 sm:grid-cols-2" onSubmit={e=>{e.preventDefault();save.mutate()}}>
  {(Object.keys(values) as Array<keyof ImportLimits>).map(key=><label key={key} className="space-y-1 text-sm">{t(`pluginImport.limit.${key}`)}<Input type="number" required min="1" step="1" value={values[key]} onChange={e=>setValues(current=>({...current,[key]:Number(e.target.value)}))} /></label>)}
  <div><Button type="submit" variant="outline" disabled={save.isPending}>{t('pluginImport.save')}</Button></div>{save.error&&<p role="alert" className="text-destructive">{save.error.message}</p>}{save.isSuccess&&<p role="status">{t('pluginImport.saved')}</p>}
 </form></details>
}
