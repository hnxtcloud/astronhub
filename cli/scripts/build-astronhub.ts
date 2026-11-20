import { chmod, copyFile, cp, mkdir, readFile, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../', import.meta.url))
const source = resolve(root, 'astronhub')
const destination = resolve(root, 'dist/astronhub-cli')
const pkg = JSON.parse(await readFile(resolve(source, 'package.json'), 'utf8')) as { version: string }
await mkdir(resolve(destination, 'bin'), { recursive: true })
const output = resolve(destination, 'bin/astronhub.mjs')
const result = await Bun.build({
  entrypoints: [resolve(root, 'src/astronhub.ts')],
  target: 'node',
  format: 'esm',
  minify: false,
  define: { ASTRONHUB_VERSION: JSON.stringify(pkg.version) }
})
if (!result.success) throw new AggregateError(result.logs, 'AstronHub CLI build failed')
await writeFile(output, await result.outputs[0]!.text())
await chmod(output, 0o755)
for (const name of ['package.json', 'README.md']) await copyFile(resolve(source, name), resolve(destination, name))
await copyFile(resolve(root, 'LICENSE'), resolve(destination, 'LICENSE'))
await cp(resolve(source, 'skills'), resolve(destination, 'skills'), { recursive: true })
console.log(`Built astronhub-cli ${pkg.version}: ${destination}`)
