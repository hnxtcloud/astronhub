// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import i18n from '@/i18n/config'
import { PluginOfflineImportPanel } from './plugin-offline-import'
afterEach(() => { cleanup(); vi.unstubAllGlobals() })
it('previews a repository then imports only selected valid candidates', async () => {
 await i18n.changeLanguage('en')
 const writes: unknown[]=[]
 vi.stubGlobal('fetch',vi.fn(async(input: RequestInfo | URL, init?: RequestInit)=>{
  const url=String(input);let data:unknown={packageBytes:104857600,repositoryBytes:524288000,expandedBytes:2147483648,files:20000,candidates:100}
  if(url.includes('/imports?'))data={id:'scan-1',namespaceId:7,repository:true,expiresAt:'2026-12-01',candidates:[{id:'1',directory:'repo/a',runtime:'pi',nativeName:'pi-tools',version:'1.0.0',suggestedSlug:'pi-tools',status:'NEW_PLUGIN',inventoryJson:'{}',manifestJson:'{}'},{id:'2',directory:'repo/b',runtime:'codex',status:'INVALID',error:'manifest'}]}
  else if(url.endsWith('/apply')) {writes.push(JSON.parse(String(init?.body)));data={results:[{candidateId:'1',status:'IMPORTED',projectId:10,releaseId:11}]}}
  return new Response(JSON.stringify({code:0,data,msg:'ok'}),{status:200,headers:{'Content-Type':'application/json'}})
 }))
 const client=new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}})
 render(<QueryClientProvider client={client}><PluginOfflineImportPanel reviewer namespaces={[{id:7,displayName:'Team',status:'ACTIVE'}]} projects={[]} /></QueryClientProvider>)
 fireEvent.change(screen.getByLabelText('Import namespace'),{target:{value:'7'}})
 fireEvent.click(screen.getByLabelText('Repository with multiple plugins'))
 fireEvent.change(screen.getByLabelText('Offline archive'),{target:{files:[new File(['bytes'],'plugins.tgz')]}})
 fireEvent.submit(screen.getByRole('button',{name:'Preview archive'}).closest('form')!)
 await waitFor(()=>expect(screen.getByText('pi-tools@1.0.0')).toBeTruthy(),{timeout:10000})
 fireEvent.click(screen.getByLabelText('Select pi-tools'))
 fireEvent.click(screen.getByRole('button',{name:'Import selected drafts'}))
 await waitFor(()=>expect(writes).toEqual([{selections:[{candidateId:'1',slug:'pi-tools',visibility:'PRIVATE'}]}]))
 await waitFor(()=>expect(screen.getByText(/Draft imported/)).toBeTruthy(),{timeout:10000})
 client.clear()
})
