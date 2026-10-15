/* Validate a real server fixture with an explicitly supplied installed electron-updater parser.
 * Reads dependency source only. No app startup, settings changes, downloads or installation.
 * node scripts/tests/native-multipart-contract.cjs FIXTURE.json ELECTRON_UPDATER_PACKAGE_DIR
 */
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { Writable } = require('node:stream')

async function main() {
  const [fixturePath, updaterRoot] = process.argv.slice(2)
  if (!fixturePath || !updaterRoot) throw new Error('Provide real server fixture and installed electron-updater package directory')
  const fixture = JSON.parse(fs.readFileSync(fixturePath, 'utf8'))
  const { DataSplitter } = require(path.resolve(updaterRoot, 'out/differentialDownloader/DataSplitter.js'))
  const version = JSON.parse(fs.readFileSync(path.resolve(updaterRoot, 'package.json'), 'utf8')).version
  const match = /^multipart\/byteranges;\s*boundary=([^\s;]+)$/i.exec(fixture.contentType)
  assert.ok(match, 'Server must provide a multipart boundary')
  const chunks = []
  const output = new Writable({ write(chunk, encoding, done) { chunks.push(Buffer.from(chunk)); done() } })
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('Installed multipart parser did not finish')), 5000)
    const splitter = new DataSplitter(output,
      { tasks: [{ kind: 1, start: 0, end: 2 }, { kind: 1, start: 8, end: 10 }], start: 0, end: 2, oldFileFd: -1 },
      new Map([[0, 0], [1, 1]]), match[1], [2, 2], () => { clearTimeout(timer); resolve() }, 4)
    splitter.on('error', error => { clearTimeout(timer); reject(error) })
    splitter.end(Buffer.from(fixture.bodyBase64, 'base64'))
  })
  assert.equal(Buffer.concat(chunks).toString('ascii'), fixture.expected)
  console.log(`Installed electron-updater ${version} accepted exact real HTTP multipart bytes (0189)`)
}
main().catch(error => { console.error(error.message); process.exitCode = 1 })
