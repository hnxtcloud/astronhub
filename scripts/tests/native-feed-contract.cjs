/* Uses explicitly supplied, installed client modules against a running synthetic HTTP fixture.
 * No application startup, configuration changes, installation or differential-update claim.
 */
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { createRequire } = require('node:module')
const { pipeline } = require('node:stream/promises')
const { Readable, Writable } = require('node:stream')

async function main() {
  const [electronRoot, baizeRoot, genericBase, baizeUrl, genericPreview, baizePreview] = process.argv.slice(2)
  assert.ok(electronRoot && baizeRoot && genericBase && baizeUrl && genericPreview && baizePreview,
    'Provide installed client roots and stable/preview live fixture URLs')
  const electronPackage = fs.realpathSync(path.resolve(electronRoot, 'package.json'))
  const nativeRequire = createRequire(electronPackage)
  const { GenericProvider } = require(path.join(path.dirname(electronPackage), 'out/providers/GenericProvider.js'))
  const { findFile, parseUpdateInfo, resolveFiles } = require(path.join(path.dirname(electronPackage), 'out/providers/Provider.js'))
  const { HttpExecutor, CancellationToken, DigestTransform } = nativeRequire('builder-util-runtime')
  const semver = nativeRequire('semver')
  class LocalExecutor extends HttpExecutor {
    createRequest(options, callback) {
      assert.equal(options.protocol, 'http:', 'Fixture must remain local HTTP')
      assert.equal(options.hostname, 'localhost')
      return require('node:http').request(options, response => {
        assert.equal(response.headers.location, undefined, 'Update reads must not redirect')
        callback(response)
      })
    }
  }
  const executor = new LocalExecutor()
  const provider = new GenericProvider({ provider: 'generic', url: genericBase },
    { channel: null, isAddNoCacheQuery: true }, { platform: 'darwin', executor })
  const info = await provider.getLatestVersion()
  assert.equal(info.version, '1.2.3')
  assert.equal(semver.valid(info.version), info.version)
  const chosen = findFile(provider.resolveFiles(info), 'zip', ['dmg', 'pkg'])
  assert.ok(chosen && chosen.url.pathname.endsWith('.zip'))
  assert.equal(chosen.url.origin, new URL(genericBase).origin)
  assert.ok(chosen.url.pathname.includes('/releases/'))
  const installer = await executor.downloadToBuffer(chosen.url, { cancellationToken: new CancellationToken() })
  assert.equal(installer.length, chosen.info.size)
  await pipeline(Readable.from([installer]), new DigestTransform(chosen.info.sha512),
    new Writable({ write(chunk, encoding, next) { next() } }))
  assert.equal(installer.toString('utf8'), '0123456789')
  const previewProvider = new GenericProvider({ provider: 'generic', url: genericPreview, channel: 'preview' },
    { channel: null, isAddNoCacheQuery: true }, { platform: 'darwin', executor })
  assert.equal((await previewProvider.getLatestVersion()).version, '2.0.0-beta.1')
  const wrongChannel = await fetch(new URL('latest-mac.yml', genericPreview), { redirect: 'manual' })
  assert.equal(wrongChannel.status, 404)

  // Exercise the real native file-selection rules on supported and unsupported metadata.
  const parsed = parseUpdateInfo('version: 1.2.3\nfiles:\n - url: setup.exe\n   sha512: test\n', 'latest.yml', new URL(genericBase))
  assert.ok(findFile(resolveFiles(parsed, new URL(genericBase)), 'exe').url.pathname.endsWith('.exe'))
  assert.equal(findFile(resolveFiles({ version: '1.2.3', files: [{ url: 'only.dmg', sha512: 'test' }] }, new URL(genericBase)), 'zip', ['dmg', 'pkg']), undefined)
  assert.equal(semver.valid('9007199254740992.0.0'), null)
  assert.equal(semver.valid('9007199254740991.0.0'), '9007199254740991.0.0')
  assert.equal(semver.compare('1.0.0-beta.9007199254740992', '1.0.0-beta.9007199254740993'), 0)

  const { parseFeed } = require(path.resolve(baizeRoot, 'server/pi-update-feed.js'))
  // Execute the exact pure comparator source without loading the application or its transports.
  const serviceSource = fs.readFileSync(path.resolve(baizeRoot, 'server/pi-update-service.js'), 'utf8')
  const compareStart = serviceSource.indexOf('function versionParts(value)')
  const compareEnd = serviceSource.indexOf('function relation(', compareStart)
  assert.ok(compareStart >= 0 && compareEnd > compareStart)
  const baizeCompare = require('node:vm').runInNewContext(`${serviceSource.slice(compareStart, compareEnd)}; compareVersions`)
  assert.equal(baizeCompare('1.2.3+' + 'x'.repeat(94), '1.2.3+' + 'x'.repeat(94)), 0)
  assert.equal(baizeCompare('1.2.3+' + 'x'.repeat(95), '1.2.3+' + 'x'.repeat(95)), null)
  const response = await fetch(baizeUrl, { redirect: 'manual' })
  assert.equal(response.status, 200)
  assert.equal(response.headers.get('location'), null)
  const manifest = await response.json()
  const release = parseFeed(manifest, { source: baizeUrl, platform: 'win32', arch: 'x64', channel: 'stable', compareVersions: baizeCompare })
  assert.ok(release)
  assert.equal(new URL(release.downloadUrl).origin, new URL(baizeUrl).origin)
  const download = await fetch(release.downloadUrl, { redirect: 'manual' })
  assert.equal(download.status, 200)
  assert.equal(download.headers.get('location'), null)
  const portable = Buffer.from(await download.arrayBuffer())
  assert.equal(portable.length, release.bytes)
  await pipeline(Readable.from([portable]), new DigestTransform(release.sha256, 'sha256', 'hex'),
    new Writable({ write(chunk, encoding, next) { next() } }))
  const previewManifest = await (await fetch(baizePreview, { redirect: 'manual' })).json()
  assert.equal(parseFeed(previewManifest, { source: baizePreview, platform: 'win32', arch: 'x64', channel: 'stable', compareVersions: baizeCompare }), null)
  assert.equal(parseFeed(previewManifest, { source: baizePreview, platform: 'win32', arch: 'x64', channel: 'preview', compareVersions: baizeCompare }).version, '2.0.0-beta.1')
  assert.throws(() => parseFeed({ ...manifest, piVersion: '1.2.3+' + 'x'.repeat(95) },
    { source: baizeUrl, platform: 'win32', arch: 'x64', channel: 'stable', compareVersions: baizeCompare }))
  console.log(`Installed electron-updater ${JSON.parse(fs.readFileSync(electronPackage, 'utf8')).version} GenericProvider and real Baize parseFeed accepted live feeds and exact full-download SHA512/SHA256 bytes`)
}

main().catch(error => { console.error(error.stack); process.exitCode = 1 })
