import { createHash } from 'node:crypto'
import { copyFile, mkdir, readFile, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { sync as spawnSync } from 'cross-spawn'
import { zipSync } from 'fflate'
import './build-astronhub'

const root = fileURLToPath(new URL('../', import.meta.url))
const dist = resolve(root, 'dist')
const builtPackage = resolve(dist, 'astronhub-cli')
const pkg = JSON.parse(await readFile(resolve(builtPackage, 'package.json'), 'utf8')) as {
  name: string; version: string; dependencies?: Record<string, string>; optionalDependencies?: Record<string, string>; peerDependencies?: Record<string, string>
}
if (pkg.name !== 'astronhub-cli' || !/^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$/.test(pkg.version)) throw new Error('Unexpected package identity')
if ([pkg.dependencies, pkg.optionalDependencies, pkg.peerDependencies].some(value => value && Object.keys(value).length > 0)) {
  throw new Error('The intranet transfer package must bundle its dependencies before preparation')
}
const packed = spawnSync('npm', ['pack', builtPackage, '--pack-destination', dist, '--ignore-scripts', '--json'], { cwd: root, encoding: 'utf8' })
if (packed.error || packed.status !== 0) throw new Error(`npm pack failed: ${packed.error?.message ?? packed.stderr}`)
const file = `astronhub-cli-${pkg.version}.tgz`
const archive = await readFile(resolve(dist, file))
const sha256 = createHash('sha256').update(archive).digest('hex')
const folder = `astronhub-cli-intranet-${pkg.version}`
const kit = resolve(dist, folder)
await mkdir(kit, { recursive: true })
await writeFile(resolve(kit, file), archive)
await writeFile(resolve(kit, 'release.json'), `${JSON.stringify({ name: pkg.name, version: pkg.version, file, sha256 }, null, 2)}\n`)
await writeFile(resolve(kit, 'SHA256SUMS'), `${sha256}  ${file}\n`)
await copyFile(resolve(root, 'astronhub/intranet/README.md'), resolve(kit, 'README.md'))
const publisher = await Bun.build({ entrypoints: [resolve(root, 'scripts/publish-astronhub-intranet.ts')], target: 'node', format: 'esm' })
if (!publisher.success) throw new AggregateError(publisher.logs, 'Publisher build failed')
await writeFile(resolve(kit, 'publish.mjs'), await publisher.outputs[0]!.text())
const files = [file, 'release.json', 'SHA256SUMS', 'README.md', 'publish.mjs']
const entries = Object.fromEntries(await Promise.all(files.map(async name => [
  `${folder}/${name}`,
  new Uint8Array(await readFile(resolve(kit, name)))
] as const)))
const zipPath = resolve(dist, `${folder}.zip`)
await writeFile(zipPath, zipSync(entries, { level: 6 }))
console.log(`Transfer kit: ${zipPath}\nPackage SHA-256: ${sha256}\nNo source checkout, Bun or dependency install is required on the intranet.`)
