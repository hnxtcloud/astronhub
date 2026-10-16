#!/usr/bin/env python3
"""Publish original build artifacts through AstronHub's app-bound machine API.

Only Python's standard library is required. Credentials come exclusively from
ASTRONHUB_PUBLISH_TOKEN. State contains IDs and digests, never credentials.
"""
from __future__ import annotations
import argparse
import hashlib
import http.client
import json
import os
from pathlib import Path
import sys
import tempfile
import time
from urllib.parse import urlsplit, urlencode


class PendingInspection(Exception):
    pass


def digest(path: Path) -> str:
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


class Publisher:
    def __init__(self, args):
        self.args = args
        self.token = os.environ.get('ASTRONHUB_PUBLISH_TOKEN', '').strip()
        if not self.token:
            raise ValueError('Set ASTRONHUB_PUBLISH_TOKEN using your CI secret store.')
        self.server = urlsplit(args.server.rstrip('/'))
        if (self.server.scheme not in ('http', 'https') or not self.server.hostname
                or self.server.username is not None or self.server.password is not None or self.server.query or self.server.fragment):
            raise ValueError('Server must be an HTTP(S) origin with an optional deployment path; no credentials, query or fragment.')
        self.base = self.server.path.rstrip('/') + f'/api/v1/software-publishing/apps/{args.app}'
        self.manifest_path = Path(args.manifest).resolve()
        self.manifest = json.loads(self.manifest_path.read_text(encoding='utf-8-sig'))
        update = self.manifest.get('update', {})
        if update.get('protocol') == 'ELECTRON' and update.get('targetOs') == 'WINDOWS':
            if any(item.get('confirmedInstallerFamily') != 'NSIS' for item in self.manifest.get('installers', [])):
                raise ValueError('Windows Electron installers require explicit confirmedInstallerFamily=NSIS in the manifest.')
        if args.activate_channel and (not args.publish or not self.manifest.get('channel')):
            raise ValueError('--activate-channel requires --publish and an explicit channel mapping in the manifest.')
        self.files = {}
        for item in self.manifest.get('installers', []) + self.manifest.get('assets', []):
            remote = item['path']
            if not remote or remote in self.files or remote.startswith('/') or '\\' in remote or any(part in ('', '.', '..') for part in remote.split('/')):
                raise ValueError('Artifact paths must be unique, relative paths without traversal.')
            local = (self.manifest_path.parent / item['file']).resolve()
            self.files[remote] = {'local': local, 'sha256': digest(local), 'size': local.stat().st_size}
        if not self.manifest.get('installers') or not self.manifest.get('assets'):
            raise ValueError('A complete release needs installers and metadata assets.')
        self.state_path = Path(args.state).resolve()
        if self.state_path == self.manifest_path or any(self.state_path == item['local'] for item in self.files.values()):
            raise ValueError('State file must differ from manifest and artifact files.')
        identity = {'server': args.server.rstrip('/'), 'app': args.app, 'manifestSha256': digest(self.manifest_path),
                    'files': {path: {'sha256': item['sha256'], 'size': item['size']} for path, item in self.files.items()}}
        self.state = json.loads(self.state_path.read_text(encoding='utf-8')) if self.state_path.exists() else {'identity': identity, 'uploads': {}}
        if self.state.get('identity') != identity:
            raise ValueError('Resume state does not match server, application, manifest or local artifact bytes. Use the original artifacts or a new version/state file.')

    def save(self):
        # Exclusive creation must not collide with an original build artifact.
        descriptor, temporary_name = tempfile.mkstemp(prefix='.astronhub-state-', suffix='.tmp', dir=self.state_path.parent)
        temporary = Path(temporary_name)
        try:
            with os.fdopen(descriptor, 'w', encoding='utf-8') as output:
                output.write(json.dumps(self.state, ensure_ascii=False, indent=2))
            temporary.replace(self.state_path)
        finally:
            temporary.unlink(missing_ok=True)

    def request(self, method, path, body=None, file=None, chunk=None):
        connection_type = http.client.HTTPSConnection if self.server.scheme == 'https' else http.client.HTTPConnection
        connection = connection_type(self.server.hostname, self.server.port, timeout=300)
        headers = {'Authorization': 'Bearer ' + self.token, 'Accept': 'application/json'}
        if file is not None:
            payload = file.open('rb')
            headers.update({'Content-Type': 'application/octet-stream', 'Content-Length': str(file.stat().st_size)})
        elif chunk is not None:
            payload = chunk
            headers.update({'Content-Type': 'application/octet-stream', 'Content-Length': str(len(chunk)),
                            'X-Chunk-Sha256': hashlib.sha256(chunk).hexdigest()})
        else:
            payload = json.dumps(body).encode() if body is not None else None
            headers['Content-Type'] = 'application/json'
        try:
            connection.request(method, self.base + path, payload, headers)
            response = connection.getresponse()
            raw = response.read(2 * 1024 * 1024 + 1)
            if len(raw) > 2 * 1024 * 1024:
                raise RuntimeError('Unexpected oversized API response.')
            if response.status != 200:
                # Do not echo response bodies: proxies or errors can reflect credentials.
                raise RuntimeError(f'{method} {path.split("?")[0]} failed: HTTP {response.status}. Check the software inspection/admin console and request logs.')
            result = json.loads(raw)
            if result.get('code') != 0:
                raise RuntimeError('API returned an unsuccessful result.')
            return result.get('data')
        finally:
            if file is not None:
                payload.close()
            connection.close()

    def installer(self, item, release, update):
        local = self.files[item['path']]
        candidates = self.request('GET', f'/releases/{release}/packages')
        package = next((pkg for pkg in candidates if pkg['sha256'] == local['sha256']), None)
        if package is None:
            upload_id = self.state['uploads'].get(item['path'])
            if upload_id is None:
                admitted = self.request('POST', '/uploads', {'releaseId': release, 'fileName': local['local'].name,
                    'format': item['format'], 'targetOs': update['targetOs'], 'targetArch': update['targetArch'],
                    'targetDistro': item.get('targetDistro'), 'applicabilityNote': item.get('applicabilityNote'),
                    'originalFullVersion': item['originalFullVersion'], 'byteSize': local['size']})
                upload_id = admitted['id']
                self.state['uploads'][item['path']] = upload_id
                self.save()
            progress = self.request('GET', f'/uploads/{upload_id}')
            if progress['status'] == 'ACTIVE':
                chunks = {part['chunkNo']: part for part in progress['chunks']}
                with local['local'].open('rb') as source:
                    for number in range(1, progress['totalChunks'] + 1):
                        data = source.read(progress['chunkSizeBytes'])
                        existing = chunks.get(number)
                        if existing is not None:
                            if existing['sha256'] != hashlib.sha256(data).hexdigest() or existing['byteSize'] != len(data):
                                raise ValueError('Remote chunk differs from the original local artifact.')
                        else:
                            self.request('PUT', f'/uploads/{upload_id}/chunks/{number}', chunk=data)
            elif progress['status'] != 'COMPLETED':
                raise RuntimeError('Upload is not active/completed. Inspect or cancel it in the admin console before retrying.')
            completed = self.request('POST', f'/uploads/{upload_id}/complete')
            package = self.request('GET', f'/packages/{completed["packageRevisionId"]}')['packageRevision']
        expected = {'format': item['format'], 'targetOs': update['targetOs'], 'targetArch': update['targetArch'],
                    'originalFullVersion': item['originalFullVersion'], 'sha256': local['sha256'], 'byteSize': local['size']}
        if any(package.get(key) != value for key, value in expected.items()):
            raise ValueError('Existing package facts differ from the manifest.')
        package_id = package['id']
        deadline = time.monotonic() + self.args.wait_seconds
        while True:
            status = self.request('GET', f'/packages/{package_id}')
            task = (status.get('inspection') or {}).get('task')
            outcome = task['status'] if task else None
            if outcome not in ('PENDING', 'LEASED', 'EXHAUSTED'):
                break
            if time.monotonic() >= deadline or outcome == 'EXHAUSTED':
                raise PendingInspection(f'Package #{package_id} inspection is {outcome}. Resume with the same manifest/state after inspection succeeds; exhausted attempts need an administrator retry.')
            time.sleep(min(2, max(0, deadline - time.monotonic())))
        if package['status'] != 'PUBLISHED':
            if outcome == 'BASIC_FAILED':
                raise ValueError(f'Package #{package_id} failed basic inspection; publication is blocked.')
            if not status['packageRevision'].get('confirmedBy'):
                if self.manifest.get('confirmMetadata') is not True:
                    raise ValueError('Explicit confirmMetadata=true is required before package confirmation.')
                if outcome in (None, 'METADATA_FAILED') and not isinstance(item.get('rawVersionFields'), dict):
                    raise ValueError('Manual formats and metadata extraction failures require explicit rawVersionFields.')
                self.request('POST', f'/packages/{package_id}/confirm', {'rawVersionFields': item.get('rawVersionFields')})
            self.request('POST', f'/packages/{package_id}/publish')
        return package_id

    def run(self):
        manifest = self.manifest
        release = self.request('POST', '/releases', {'displayVersion': manifest['displayVersion'], 'releaseNotes': manifest.get('releaseNotes')})['id']
        update = dict(manifest['update'], catalogReleaseId=release)
        package_ids = {item['path']: self.installer(item, release, update) for item in manifest['installers']}
        snapshots = self.request('GET', f'/releases/{release}/update-releases')
        identity = ('tag', 'targetOs', 'targetArch', 'channel', 'variant')
        snapshot = next((row for row in snapshots if all(row.get(key) == update.get(key) for key in identity)), None)
        if snapshot is None:
            snapshot = self.request('POST', '/update-releases', update)
        if any(snapshot.get(key) != update.get(key) for key in ('machineVersion', 'protocol', 'piVersion')):
            raise ValueError('Existing update identity differs from manifest; create a new release version.')
        snapshot_id = snapshot['id']
        self.state['snapshotId'] = snapshot_id
        self.save()
        existing = {asset['relativePath']: asset for asset in snapshot['assets']}
        if set(existing) - set(self.files):
            raise ValueError('Snapshot contains assets not present in the complete manifest.')
        for item in manifest['installers'] + manifest['assets']:
            path = item['path']
            asset = existing.get(path)
            role = 'INSTALLER' if path in package_ids else item['role']
            if asset:
                if asset['sha256'] != self.files[path]['sha256'] or asset['byteSize'] != self.files[path]['size'] or asset['role'] != role:
                    raise ValueError('Existing snapshot asset differs from original artifact bytes or role.')
                if role == 'INSTALLER' and asset['packageRevisionId'] != package_ids[path]:
                    raise ValueError('Existing installer points to a different confirmed package.')
                if role == 'INSTALLER':
                    family = item.get('confirmedInstallerFamily')
                    if (asset.get('confirmedInstallerFamily') != family or (family == 'NSIS'
                            and (not asset.get('installerFamilyConfirmedBy') or not asset.get('installerFamilyConfirmedAt')))):
                        raise ValueError('Existing installer family confirmation differs from the explicit manifest declaration.')
            elif snapshot['status'] != 'DRAFT':
                raise ValueError('Published snapshot is missing a manifest asset and cannot be changed.')
            elif role == 'INSTALLER':
                self.request('POST', f'/update-releases/{snapshot_id}/package-assets', {'packageRevisionId': package_ids[path], 'relativePath': path, 'confirmedInstallerFamily': item.get('confirmedInstallerFamily')})
            else:
                uploaded = self.request('POST', f'/update-releases/{snapshot_id}/assets?' + urlencode({'path': path, 'role': role}), file=self.files[path]['local'])
                if uploaded['sha256'] != self.files[path]['sha256'] or uploaded['byteSize'] != self.files[path]['size']:
                    raise ValueError('Artifact bytes changed during upload; the snapshot remains unpublished.')
        self.request('POST', f'/update-releases/{snapshot_id}/validate')
        if self.args.publish:
            self.request('POST', f'/update-releases/{snapshot_id}/publish')
        if self.args.activate_channel:
            if not self.args.publish or not manifest.get('channel'):
                raise ValueError('--activate-channel requires --publish and an explicit channel mapping in the manifest.')
            self.request('PUT', '/update-channels', dict(manifest['channel'], targetReleaseId=snapshot_id,
                         **{key: update[key] for key in ('targetOs', 'targetArch', 'channel', 'variant')}))
        print(f'Snapshot #{snapshot_id}: {"published" if self.args.publish else "validated draft"}. State saved; credentials were not written.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server', required=True)
    parser.add_argument('--app', required=True, type=int)
    parser.add_argument('--manifest', required=True)
    parser.add_argument('--state', required=True)
    parser.add_argument('--publish', action='store_true')
    parser.add_argument('--activate-channel', action='store_true')
    parser.add_argument('--wait-seconds', type=int, default=0)
    args = parser.parse_args()
    if args.app <= 0 or not 0 <= args.wait_seconds <= 900:
        parser.error('app must be positive; wait-seconds must be between 0 and 900.')
    try:
        Publisher(args).run()
        return 0
    except PendingInspection as error:
        print(str(error), file=sys.stderr)
        return 2
    except Exception as error:
        secret = os.environ.get('ASTRONHUB_PUBLISH_TOKEN', '').strip()
        message = str(error).replace(secret, '[REDACTED]') if secret else str(error)
        print(message, file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
