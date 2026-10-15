"""Public CLI failure cases: credentials and original build files stay protected."""
import http.server
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import threading
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'publish-software-release.py'


class Api(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        self.reply()

    def do_POST(self):
        payload = self.rfile.read(int(self.headers.get('Content-Length', '0')))
        self.payload = json.loads(payload) if self.headers.get('Content-Type') == 'application/json' and payload else None
        self.reply()

    def reply(self):
        self.server.calls.append(self.path)
        if hasattr(self.server, 'scenario'):
            data = self.server.scenario(self)
        elif self.path.endswith('/releases'):
            data = {'id': 1}
        elif self.path.endswith('/packages'):
            data = []
        elif self.path.endswith('/uploads'):
            data = {'id': 2}
        else:
            self.send_response(403)
            self.end_headers()
            return
        body = json.dumps({'code': 0, 'data': data}).encode()
        self.send_response(200)
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *_):
        pass


class CliSafetyTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        (self.root / 'app.zip').write_bytes(b'original-installer')
        self.original = b'original-metadata-must-remain-byte-for-byte'
        (self.root / 'state.json.tmp').write_bytes(self.original)
        (self.root / 'manifest.json').write_text(json.dumps({
            'displayVersion': 'fixture', 'update': {'targetOs': 'MACOS', 'targetArch': 'X86_64'},
            'installers': [{'file': 'app.zip', 'path': 'app.zip', 'format': 'ZIP', 'originalFullVersion': '1.0.0'}],
            'assets': [{'file': 'state.json.tmp', 'path': 'latest-mac.yml', 'role': 'METADATA'}]}), encoding='utf-8')
        self.server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Api)
        self.server.calls = []
        self.addCleanup(self.server.server_close)
        self.addCleanup(self.server.shutdown)
        threading.Thread(target=self.server.serve_forever, daemon=True).start()

    def run_cli(self, url):
        env = os.environ.copy()
        env['ASTRONHUB_PUBLISH_TOKEN'] = 'synthetic-cli-secret'
        result = subprocess.run(['python', str(SCRIPT), '--server', url, '--app', '1', '--manifest', str(self.root / 'manifest.json'),
                                 '--state', str(self.root / 'state.json')], env=env, capture_output=True, text=True, timeout=15)
        self.assertNotIn('synthetic-cli-secret', result.stdout + result.stderr)
        return result

    def test_empty_username_with_password_is_rejected_before_any_network_request(self):
        result = self.run_cli(f'http://:pass@127.0.0.1:{self.server.server_port}')
        self.assertEqual(result.returncode, 1)
        self.assertIn('Server must', result.stderr)
        self.assertEqual(self.server.calls, [])
        self.assertFalse((self.root / 'state.json').exists())

    def test_windows_electron_requires_explicit_nsis_confirmation_before_network(self):
        path = self.root / 'manifest.json'
        manifest = json.loads(path.read_text(encoding='utf-8'))
        manifest['update'].update(targetOs='WINDOWS', protocol='ELECTRON')
        manifest['installers'][0]['format'] = 'EXE'
        path.write_text(json.dumps(manifest), encoding='utf-8')
        result = self.run_cli(f'http://127.0.0.1:{self.server.server_port}')
        self.assertEqual(result.returncode, 1)
        self.assertIn('NSIS', result.stderr)
        self.assertEqual(self.server.calls, [])
        self.assertFalse((self.root / 'state.json').exists())

    def test_nsis_declaration_is_forwarded_and_unconfirmed_remote_assets_cannot_resume(self):
        path = self.root / 'manifest.json'
        manifest = json.loads(path.read_text(encoding='utf-8'))
        manifest['update'] = {'machineVersion': '1.0.0', 'tag': 'v1.0.0', 'targetOs': 'WINDOWS',
                              'targetArch': 'X86_64', 'channel': 'stable', 'variant': 'default', 'protocol': 'ELECTRON'}
        item = manifest['installers'][0]
        item.update(format='EXE', confirmedInstallerFamily='NSIS')
        path.write_text(json.dumps(manifest), encoding='utf-8')
        package = dict(item, id=3, targetOs='WINDOWS', targetArch='X86_64', status='PUBLISHED',
                       sha256=hashlib.sha256(b'original-installer').hexdigest(), byteSize=18, confirmedBy='publisher')
        snapshot = dict(manifest['update'], id=5, status='DRAFT', assets=[])
        links = []

        def scenario(handler):
            route = handler.path
            if route.endswith('/releases'):
                return {'id': 1}
            if route.endswith('/releases/1/packages'):
                return [package]
            if route.endswith('/packages/3'):
                return {'packageRevision': package}
            if route.endswith('/releases/1/update-releases'):
                return [snapshot]
            if route.endswith('/package-assets'):
                links.append(handler.payload)
                snapshot['assets'].append({'relativePath': item['path'], 'role': 'INSTALLER', 'packageRevisionId': 3,
                                           'sha256': package['sha256'], 'byteSize': 18, 'confirmedInstallerFamily': 'NSIS',
                                           'installerFamilyConfirmedBy': 'publisher', 'installerFamilyConfirmedAt': '2026-10-15T00:00:00Z'})
                return snapshot['assets'][-1]
            if '/assets?' in route:
                asset = {'relativePath': 'latest-mac.yml', 'role': 'METADATA',
                         'sha256': hashlib.sha256(self.original).hexdigest(), 'byteSize': len(self.original)}
                snapshot['assets'].append(asset)
                return asset
            if route.endswith('/validate'):
                return {'valid': True}
            raise AssertionError(route)

        self.server.scenario = scenario
        result = self.run_cli(f'http://127.0.0.1:{self.server.server_port}')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(links, [{'packageRevisionId': 3, 'relativePath': 'app.zip', 'confirmedInstallerFamily': 'NSIS'}])
        snapshot['assets'][0].pop('confirmedInstallerFamily')
        result = self.run_cli(f'http://127.0.0.1:{self.server.server_port}')
        self.assertEqual(result.returncode, 1)
        self.assertIn('confirmation', result.stderr)
        self.assertEqual(len(links), 1)

    def test_resume_checkpoint_cannot_overwrite_an_original_asset_named_like_a_temp_file(self):
        result = self.run_cli(f'http://127.0.0.1:{self.server.server_port}')
        self.assertEqual(result.returncode, 1)
        self.assertIn('HTTP 403', result.stderr)
        self.assertEqual((self.root / 'state.json.tmp').read_bytes(), self.original)
        self.assertNotIn('synthetic-cli-secret', (self.root / 'state.json').read_text(encoding='utf-8'))


if __name__ == '__main__':
    unittest.main()
