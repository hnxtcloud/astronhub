import { createServer } from 'node:http'
import { Readable } from 'node:stream'
import { pipeline } from 'node:stream/promises'
import { registryUrl } from './config'

/** Temporary metadata gateway: decode scoped package separators, retain upstream tarball URLs. */
export async function startRegistryProxy(registry: string, token?: string) {
  const upstream = registryUrl(registry)
  const server = createServer(async (request, response) => {
    try {
      if (request.method !== 'GET' && request.method !== 'HEAD') {
        response.writeHead(405).end('Only registry reads are supported')
        return
      }
      const path = request.url ?? '/'
      // Request targets must stay relative to this configured registry.
      if (!path.startsWith('/') || path.startsWith('//') || /[\\\r\n]/.test(path)) {
        response.writeHead(400).end('Invalid registry request')
        return
      }
      const normalized = path.replace(/^\/(?:@|%40)([^/?]+?)%2f/ig, '/@$1/')
      const target = new URL(`${upstream}${normalized.slice(1)}`)
      if (!target.href.startsWith(upstream)) {
        response.writeHead(400).end('Invalid registry path')
        return
      }
      const headers = new Headers({ accept: request.headers.accept ?? 'application/json' })
      if (token) headers.set('authorization', `Bearer ${token}`)
      const result = await fetch(target, {
        method: request.method,
        headers,
        redirect: 'error',
        signal: AbortSignal.timeout(60_000)
      })
      response.statusCode = result.status
      for (const name of ['content-type', 'cache-control', 'etag']) {
        const value = result.headers.get(name)
        if (value) response.setHeader(name, value)
      }
      if (result.body && request.method !== 'HEAD') {
        await pipeline(Readable.fromWeb(result.body as unknown as Parameters<typeof Readable.fromWeb>[0]), response)
      } else response.end()
    } catch {
      if (!response.headersSent) response.writeHead(502, { 'content-type': 'application/json' })
      response.end(JSON.stringify({ error: 'Private registry request failed; check URL, connectivity and ASTRONHUB_NPM_TOKEN' }))
    }
  })
  await new Promise<void>((resolve, reject) => {
    server.once('error', reject)
    server.listen(0, '127.0.0.1', () => { server.off('error', reject); resolve() })
  })
  const address = server.address()
  if (!address || typeof address === 'string') throw new Error('Cannot start registry gateway')
  return {
    url: `http://127.0.0.1:${address.port}/`,
    async close() {
      await new Promise<void>((resolve, reject) => {
        server.close(error => error && (error as NodeJS.ErrnoException).code !== 'ERR_SERVER_NOT_RUNNING' ? reject(error) : resolve())
        server.closeAllConnections()
      })
    }
  }
}
