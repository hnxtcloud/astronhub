/* Opt-in original-client contract probe. No Electron/app launch, settings writes, installation,
 * package scripts, external network, or differential-update claim.
 * node intelli-gitlab-contract-probe.cjs --fixture fixture.json --client-root CLIENT [--out EXTERNAL_DIR]
 * node intelli-gitlab-contract-probe.cjs --prepare-only --client-root CLIENT [--out EXTERNAL_DIR]
 */
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const http = require('node:http');
const vm = require('node:vm');
const { createRequire } = require('node:module');
const { Readable, Writable } = require('node:stream');
const { pipeline } = require('node:stream/promises');
let step = 'arguments';
let output;
const report = { ok: false, mode: 'prepare', checks: [], installation: false,
  applicationStarted: false, differentialUpdateTested: false, fallbackTested: false,
  archiveContentsExecuted: false, clientSettingsChanged: false };
const digest = (bytes, algorithm = 'sha256', encoding = 'hex') =>
  crypto.createHash(algorithm).update(bytes).digest(encoding);
function check(condition, code) { if (!condition) { const e = new Error(code); e.code = code; throw e; } }
function checked(condition, name) { check(condition, name); report.checks.push(name); }
function within(root, candidate) {
  const rel = path.relative(root, candidate);
  return rel === '' || (!rel.startsWith('..' + path.sep) && rel !== '..' && !path.isAbsolute(rel));
}
function repositoryArtifacts(worktree, candidate) {
  return [path.join(worktree, 'target', 'update-contract'),
    path.join(worktree, 'server', 'skillhub-app', 'target', 'update-contract')]
    .some(root => within(root, candidate));
}
function externalOutput(root, client) {
  check(!within(client, root), 'OUTPUT_INSIDE_CLIENT');
  let cursor = root;
  while (true) {
    check(!fs.existsSync(path.join(cursor, '.git')) || repositoryArtifacts(cursor, root), 'OUTPUT_INSIDE_GIT_WORKTREE');
    const parent = path.dirname(cursor); if (parent === cursor) break; cursor = parent;
  }
  fs.mkdirSync(root, { recursive: true });
  // Resolve symlinks before the second boundary check.
  const real = fs.realpathSync(root);
  check(!within(client, real), 'OUTPUT_SYMLINK_INSIDE_CLIENT');
  let realCursor = real;
  while (true) {
    check(!fs.existsSync(path.join(realCursor, '.git')) || repositoryArtifacts(realCursor, real), 'OUTPUT_SYMLINK_INSIDE_GIT_WORKTREE');
    const parent = path.dirname(realCursor); if (parent === realCursor) break; realCursor = parent;
  }
  return fs.mkdtempSync(path.join(real, 'intelli-native-'));
}
function options() {
  const result = {};
  for (let i = 2; i < process.argv.length; i++) {
    const key = process.argv[i];
    if (key === '--prepare-only') result.prepareOnly = true;
    else {
      check(['--fixture', '--client-root', '--out'].includes(key) && process.argv[i + 1], 'UNKNOWN_ARGUMENT');
      result[key.slice(2)] = process.argv[++i];
    }
  }
  check(result.prepareOnly || result.fixture, 'FIXTURE_REQUIRED');
  check(result['client-root'], 'EXPLICIT_CLIENT_ROOT_REQUIRED');
  return result;
}
function localUrl(value) {
  const url = new URL(value);
  // Numeric loopback only: no DNS/proxy settings or user credentials are consulted.
  check(url.protocol === 'http:' && ['127.0.0.1', '[::1]'].includes(url.hostname)
    && !url.username && !url.password && !url.hash, 'NON_LOOPBACK_HTTP_URL');
  return url;
}
async function main() {
  const opts = options();
  const client = fs.realpathSync(path.resolve(opts['client-root']));
  output = externalOutput(path.resolve(opts.out || path.join(__dirname, 'intelli-probe-artifacts')), client);
  const clientRequire = createRequire(path.join(client, 'package.json'));
  const ts = clientRequire('typescript');
  const updaterPackage = clientRequire.resolve('electron-updater/package.json');
  const updaterRoot = path.dirname(updaterPackage);
  const updaterRequire = createRequire(updaterPackage);
  const { GenericProvider } = require(path.join(updaterRoot, 'out/providers/GenericProvider.js'));
  const { findFile } = require(path.join(updaterRoot, 'out/providers/Provider.js'));
  const { DataSplitter } = require(path.join(updaterRoot, 'out/differentialDownloader/DataSplitter.js'));
  const { HttpExecutor, CancellationToken, DigestTransform } = updaterRequire('builder-util-runtime');
  report.dependencies = { node: process.version, typescript: ts.version, electronUpdater: JSON.parse(fs.readFileSync(updaterPackage, 'utf8')).version };
  report.sources = [];
  const origins = new Set();
  const observed = [];
  let suppressedLogs = 0;
  const quiet = Object.freeze({ log() { suppressedLogs++; }, warn() { suppressedLogs++; }, error() { suppressedLogs++; } });
  const rawFetch = globalThis.fetch.bind(globalThis);
  async function fixtureFetch(input, init = {}) {
    const url = localUrl(typeof input === 'string' || input instanceof URL ? String(input) : input.url);
    check(origins.has(url.origin), 'UNEXPECTED_LOOPBACK_ORIGIN');
    const headers = new Headers(init.headers);
    check(!headers.has('authorization') && !headers.has('private-token'), 'CREDENTIALS_DISALLOWED_IN_PROBE');
    // Kept in memory for exact request assertions; never serialize raw URLs, headers or bodies.
    observed.push({ url, method: init.method || 'GET', range: headers.get('range') });
    return rawFetch(url, { ...init, redirect: 'error', signal: init.signal || AbortSignal.timeout(15000) });
  }
  function loadActual(relative, label) {
    step = 'transpile-' + label;
    const source = fs.readFileSync(path.join(client, relative), 'utf8');
    const result = ts.transpileModule(source, { fileName: label + '.ts', reportDiagnostics: true,
      compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS,
        esModuleInterop: true, sourceMap: false, inlineSourceMap: false } });
    check(!(result.diagnostics || []).some(d => d.category === ts.DiagnosticCategory.Error), 'TRANSPILE_DIAGNOSTIC');
    const destination = path.join(output, label + '.cjs');
    fs.writeFileSync(destination, result.outputText);
    report.sources.push({ file: relative.replaceAll('\\', '/'), sha256: digest(source), transpiledSha256: digest(result.outputText) });
    const module = { exports: {} };
    const sourceRequire = name => {
      check(['node:http', 'node:stream'].includes(name), 'UNEXPECTED_CLIENT_RUNTIME_IMPORT');
      return require(name);
    };
    // Only the two requested original modules are evaluated. The type-only build-config import
    // is erased by the installed TS compiler; no app, Electron or user config is imported.
    vm.compileFunction(result.outputText,
      ['exports', 'require', 'module', '__filename', '__dirname', 'console', 'fetch', 'URL', 'URLSearchParams', 'AbortSignal'],
      { filename: destination })(module.exports, sourceRequire, module, destination, output, quiet, fixtureFetch, URL, URLSearchParams, AbortSignal);
    return module.exports;
  }
  const checker = loadActual('src/electron/libs/release-checker.ts', 'release-checker');
  const bridge = loadActual('src/electron/libs/gitlab-feed-proxy.ts', 'gitlab-feed-proxy');
  checked(typeof checker.fetchLatest === 'function' && typeof checker.buildFeedUrl === 'function'
    && typeof bridge.startGitLabFeedProxy === 'function', 'original_typescript_exports_loaded');
  if (opts.prepareOnly) {
    report.ok = true; report.suppressedClientLogs = suppressedLogs;
    fs.writeFileSync(path.join(output, 'report.json'), JSON.stringify(report, null, 2));
    console.log(JSON.stringify({ ok: true, mode: 'prepare', report: path.join(output, 'report.json') }));
    return;
  }
  check(!process.execArgv.some(value => value.startsWith('--use-env-proxy'))
    && !/--use-env-proxy/.test(process.env.NODE_OPTIONS || '')
    && (!process.env.NODE_USE_ENV_PROXY || ['0', 'false'].includes(process.env.NODE_USE_ENV_PROXY.toLowerCase())), 'ENV_PROXY_MODE_DISALLOWED');
  report.mode = 'live-spring-contract';
  step = 'fixture-validation';
  const fixture = JSON.parse(fs.readFileSync(opts.fixture, 'utf8'));
  const permittedKeys = new Set(['baseUrl', 'owner', 'repo', 'artifactPath', 'expectedVersion', 'expectedTag', 'expectedNotes', 'expectedAssetFile', 'assetFilename']);
  check(Object.keys(fixture).every(key => permittedKeys.has(key)), 'UNKNOWN_FIXTURE_FIELD');
  const base = localUrl(fixture.baseUrl);
  check(!base.search && /^\/(?:[A-Za-z0-9_-]+\/?)*$/.test(base.pathname), 'UNSAFE_FIXTURE_BASE_PATH');
  check(['owner', 'repo', 'artifactPath', 'assetFilename'].every(key => typeof fixture[key] === 'string'), 'FIXTURE_COORDINATE_REQUIRED');
  check(/^[a-zA-Z0-9_-]+(?:\/[a-zA-Z0-9_-]+)*$/.test(fixture.owner)
    && /^[a-zA-Z0-9_-]+$/.test(fixture.repo), 'UNSAFE_PROJECT_COORDINATE');
  check(/^(?:[a-zA-Z0-9_-]+\/)*[a-zA-Z0-9_-]*$/.test(fixture.artifactPath)
    && /^[A-Za-z0-9+_.-]+\.zip$/.test(fixture.assetFilename), 'UNSAFE_ASSET_COORDINATE');
  check(/^\d+\.\d+\.\d+$/.test(fixture.expectedVersion)
    && /^[vV]?\d+\.\d+\.\d+$/.test(fixture.expectedTag)
    && typeof fixture.expectedNotes === 'string', 'FIXTURE_EXPECTATION_REQUIRED');
  const expected = fs.readFileSync(fixture.expectedAssetFile);
  check(expected.length >= 10 && expected.length <= 8 * 1024 * 1024, 'SYNTHETIC_ASSET_SIZE_LIMIT');
  // A valid inert fixture ZIP avoids implying that ten arbitrary bytes form an installable ZIP.
  check(expected.readUInt32LE(0) === 0x04034b50, 'SYNTHETIC_ZIP_LOCAL_HEADER_REQUIRED');
  const dist = { baseUrl: base.toString().replace(/\/$/, ''), owner: fixture.owner, repo: fixture.repo, artifactPath: fixture.artifactPath };
  origins.add(base.origin);
  step = 'original-fetchLatest';
  const latest = await checker.fetchLatest(dist);
  check(latest != null, 'ORIGINAL_FETCH_LATEST_NULL');
  checked(latest.version === fixture.expectedVersion && latest.tagName === fixture.expectedTag, 'original_stable_tag_selection');
  checked(latest.releaseNotes === fixture.expectedNotes, 'original_release_notes_selection');
  checked(checker.authHeaders(dist) === undefined, 'anonymous_fixture_no_credentials');
  report.version = latest.version; report.tag = latest.tagName; report.notesSha256 = digest(latest.releaseNotes);
  const expectedWebFeed = dist.baseUrl + '/' + fixture.owner + '/' + fixture.repo + '/-/raw/' + fixture.expectedTag
    + (fixture.artifactPath ? '/' + fixture.artifactPath : '');
  checked(checker.buildFeedUrl(dist, latest.tagName) === expectedWebFeed, 'original_web_raw_feed_url');
  let proxy;
  try {
    step = 'original-startGitLabFeedProxy';
    proxy = await bridge.startGitLabFeedProxy(dist, latest.tagName);
    const proxyBase = localUrl(proxy.url);
    origins.add(proxyBase.origin);
    class FixtureExecutor extends HttpExecutor {
      createRequest(requestOptions, callback) {
        const host = requestOptions.hostname || requestOptions.host;
        const candidate = localUrl((requestOptions.protocol || 'http:') + '//' + host + ':' + requestOptions.port + (requestOptions.path || '/'));
        check(origins.has(candidate.origin), 'EXECUTOR_ORIGIN_NOT_ALLOWED');
        const request = http.request(requestOptions, response => {
          if (response.headers.location !== undefined) {
            response.resume();
            request.destroy(Object.assign(new Error('FIXTURE_REDIRECT_REJECTED'), { code: 'FIXTURE_REDIRECT_REJECTED' }));
            return;
          }
          let received = 0;
          response.on('data', data => {
            received += data.length;
            if (received > 16 * 1024 * 1024) response.destroy(Object.assign(new Error('RESPONSE_LIMIT'), { code: 'RESPONSE_LIMIT' }));
          });
          callback(response);
        });
        request.setTimeout(15000, () => request.destroy(Object.assign(new Error('REQUEST_TIMEOUT'), { code: 'REQUEST_TIMEOUT' })));
        return request;
      }
    }
    step = 'installed-GenericProvider';
    const executor = new FixtureExecutor();
    const provider = new GenericProvider({ provider: 'generic', url: proxy.url },
      { channel: null, isAddNoCacheQuery: true }, { platform: 'darwin', executor });
    const info = await provider.getLatestVersion();
    checked(info.version === fixture.expectedVersion, 'native_metadata_version');
    const chosen = findFile(provider.resolveFiles(info), 'zip', ['dmg', 'pkg']);
    check(chosen != null, 'NATIVE_MAC_ZIP_NOT_SELECTED');
    checked(chosen.url.origin === proxyBase.origin
      && decodeURIComponent(chosen.url.pathname).endsWith('/' + fixture.assetFilename), 'native_same_proxy_zip_selection');
    checked(chosen.info.sha512 === digest(expected, 'sha512', 'base64') && chosen.info.size === expected.length, 'original_metadata_size_sha512');
    step = 'installed-HttpExecutor-full-download';
    const downloaded = await executor.downloadToBuffer(chosen.url, { cancellationToken: new CancellationToken() });
    await pipeline(Readable.from([downloaded]), new DigestTransform(chosen.info.sha512),
      new Writable({ write(chunk, encoding, done) { done(); } }));
    checked(Buffer.from(downloaded).equals(expected), 'native_full_synthetic_zip_exact_bytes_sha512');
    fs.writeFileSync(path.join(output, 'full-synthetic.zip'), downloaded);
    report.bytes = downloaded.length; report.sha512 = chosen.info.sha512;
    step = 'proxy-HEAD';
    const head = await fixtureFetch(chosen.url, { method: 'HEAD' });
    checked(head.status === 200 && Number(head.headers.get('content-length')) === expected.length
      && (await head.arrayBuffer()).byteLength === 0, 'original_proxy_head_length_no_body');
    checked(head.headers.get('accept-ranges') === 'bytes', 'original_proxy_range_advertisement');
    step = 'proxy-single-range';
    const single = await fixtureFetch(chosen.url, { headers: { Range: 'bytes=2-4' } });
    checked(single.status === 206 && single.headers.get('content-range') === `bytes 2-4/${expected.length}`
      && Buffer.from(await single.arrayBuffer()).equals(expected.subarray(2, 5)), 'original_proxy_single_range_exact');
    step = 'proxy-native-multipart';
    const tail = expected.length - 2;
    const multipart = await fixtureFetch(chosen.url, { headers: { Range: `bytes=0-1,${tail}-${tail + 1}` } });
    check(multipart.status === 206, 'MULTIPART_206_REQUIRED');
    const match = /^multipart\/byteranges;\s*boundary=(?:"([^"]+)"|([^\s;]+))$/i.exec(multipart.headers.get('content-type') || '');
    check(match != null, 'MULTIPART_BOUNDARY_REQUIRED');
    const multipartBytes = Buffer.from(await multipart.arrayBuffer());
    const fragments = [];
    const writable = new Writable({ write(chunk, encoding, done) { fragments.push(Buffer.from(chunk)); done(); } });
    await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(Object.assign(new Error('MULTIPART_TIMEOUT'), { code: 'MULTIPART_TIMEOUT' })), 5000);
      const splitter = new DataSplitter(writable,
        { tasks: [{ kind: 1, start: 0, end: 2 }, { kind: 1, start: tail, end: tail + 2 }], start: 0, end: 2, oldFileFd: -1 },
        new Map([[0, 0], [1, 1]]), match[1] || match[2], [2, 2], () => { clearTimeout(timer); resolve(); }, 4);
      splitter.on('error', error => { clearTimeout(timer); reject(error); });
      splitter.end(multipartBytes);
    });
    checked(Buffer.concat(fragments).equals(Buffer.concat([expected.subarray(0, 2), expected.subarray(tail)])), 'installed_DataSplitter_exact_multipart_bytes');
    step = 'exact-original-request-contract';
    const prefix = dist.baseUrl.slice(base.origin.length);
    const project = `${fixture.owner}/${fixture.repo}`.split('/').map(encodeURIComponent).join('%2F');
    const tagsPath = `${prefix}/api/v4/projects/${project}/repository/tags`;
    checked(observed.some(r => r.url.origin === base.origin && r.url.pathname === tagsPath), 'actual_encoded_tags_request');
    const rawPrefix = `${prefix}/api/v4/projects/${project}/repository/files/`;
    const raw = observed.filter(r => r.url.origin === base.origin && r.url.pathname.startsWith(rawPrefix));
    const metadataName = [fixture.artifactPath, 'latest-mac.yml'].filter(Boolean).join('/');
    checked(raw.some(r => r.url.pathname === rawPrefix + encodeURIComponent(metadataName) + '/raw'), 'actual_canonical_mac_metadata_raw_request');
    checked(raw.length >= 5 && raw.every(r => [...r.url.searchParams.keys()].join(',') === 'ref'
      && r.url.searchParams.get('ref') === fixture.expectedTag), 'proxy_binds_selected_tag_and_strips_noCache_query');
    checked(raw.some(r => r.method === 'HEAD') && raw.some(r => r.range === 'bytes=2-4')
      && raw.some(r => r.range === `bytes=0-1,${tail}-${tail + 1}`), 'actual_proxy_method_and_range_passthrough');
    report.ok = true;
  } finally {
    if (proxy) await proxy.stop();
    report.proxyStopped = true;
  }
  report.suppressedClientLogs = suppressedLogs;
  fs.writeFileSync(path.join(output, 'report.json'), JSON.stringify(report, null, 2));
  console.log(JSON.stringify({ ok: true, mode: report.mode, report: path.join(output, 'report.json'), checks: report.checks.length }));
}
const watchdog = setTimeout(() => {
  console.error(JSON.stringify({ ok: false, step, code: 'PROBE_TIMEOUT' }));
  process.exit(2);
}, 120000);
watchdog.unref();
main().then(() => clearTimeout(watchdog)).catch(error => {
  clearTimeout(watchdog);
  report.ok = false; report.failedStep = step;
  // Original client logs and error bodies/stacks can contain server details: never print them.
  report.failureCode = /^[A-Z0-9_]+$/.test(error.code || '') ? error.code : 'CONTRACT_ASSERTION_FAILED';
  if (output) fs.writeFileSync(path.join(output, 'report.json'), JSON.stringify(report, null, 2));
  console.error(JSON.stringify({ ok: false, step, code: report.failureCode, report: output ? path.join(output, 'report.json') : null }));
  process.exitCode = 1;
});
