"""Public CLI failure cases: credentials and original build files stay protected."""
import http.server
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
        self.rfile.read(int(self.headers.get('Content-Length', '0')))
        self.reply()

    def reply(self):
        self.server.calls.append(self.path)
        if self.path.endswith('/releases'):
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

    def test_resume_checkpoint_cannot_overwrite_an_original_asset_named_like_a_temp_file(self):
        result = self.run_cli(f'http://127.0.0.1:{self.server.server_port}')
        self.assertEqual(result.returncode, 1)
        self.assertIn('HTTP 403', result.stderr)
        self.assertEqual((self.root / 'state.json.tmp').read_bytes(), self.original)
        self.assertNotIn('synthetic-cli-secret', (self.root / 'state.json').read_text(encoding='utf-8'))


if __name__ == '__main__':
    unittest.main()
