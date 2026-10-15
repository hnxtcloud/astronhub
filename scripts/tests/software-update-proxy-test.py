"""Exercise actual update proxy routes with an isolated installed Nginx and loopback echo backend.

No downloads or containers. Pass --require-nginx to fail instead of skipping when
Nginx is unavailable. This tests the proxy boundary, not application authorization
or backend archive budgets. Upload probes announce sizes without sending bodies.
"""
from __future__ import annotations
import argparse
import http.client
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import threading
import time

REPO = Path(__file__).resolve().parents[2]
NO_WINDOW = getattr(subprocess, "CREATE_NO_WINDOW", 0)
ETAG = '"fixture-sha256"'


def posix_path(path: Path) -> str:
    value = str(path.resolve()).replace("\\", "/")
    return "/" + value[0].lower() + value[2:] if os.name == "nt" and len(value) > 1 and value[1] == ":" else value


def port() -> int:
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        return listener.getsockname()[1]


class Echo(BaseHTTPRequestHandler):
    def do_GET(self):
        self.reply(False)

    def do_HEAD(self):
        self.reply(True)

    def do_POST(self):
        self.reply(False)

    def reply(self, head: bool):
        body = json.dumps({"rawPath": self.path, "prefix": self.headers.get("X-Forwarded-Prefix"),
                           "range": self.headers.get("Range"), "ifRange": self.headers.get("If-Range"),
                           "ifNoneMatch": self.headers.get("If-None-Match"),
                           "acceptEncoding": self.headers.get("Accept-Encoding"), "padding": "x" * 1200}).encode()
        self.send_response(404 if "missing=1" in self.path else 200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("ETag", ETAG)
        self.send_header("Last-Modified", "Thu, 15 Oct 2026 00:00:00 GMT")
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        if not head:
            self.wfile.write(body)

    def log_message(self, *_):
        pass


def probe(nginx: Path, shell: str, prefix: str, backend_port: int, run: Path) -> int:
    html = run / "html"
    html.mkdir(parents=True)
    (run / "logs").mkdir()
    (run / "temp").mkdir()
    (html / "index.html").write_text("SPA_FALLBACK_MUST_NOT_LEAK", encoding="utf-8")
    (html / "assets").mkdir()
    (html / "assets/app.js").write_text("STATIC_ASSET", encoding="utf-8")
    routing = run / "base-path.conf"
    env = os.environ.copy()
    env.update(SKILLHUB_WEB_BASE_PATH=prefix, SKILLHUB_WEB_API_BASE_URL="",
               SKILLHUB_WEB_ROOT=posix_path(html), SKILLHUB_NGINX_BASE_PATH_CONFIG=posix_path(routing),
               SKILLHUB_WEB_BAKED_BASE_PATH_FILE=posix_path(run / "no-baked-base"))
    subprocess.run([shell, str(REPO / "web/docker-entrypoint.d/20-base-path.sh")], env=env,
                   check=True, capture_output=True, timeout=20, creationflags=NO_WINDOW)
    listen = port()
    template = (REPO / "web/nginx.conf.template").read_text(encoding="utf-8")
    for key, value in {"NGINX_LOCAL_RESOLVERS": "127.0.0.1", "SKILLHUB_API_UPSTREAM": f"http://127.0.0.1:{backend_port}",
                       "SKILLHUB_TRUST_FORWARDED_PROTO": "false"}.items():
        template = template.replace("${" + key + "}", value)
    template = template.replace("listen 80;", f"listen 127.0.0.1:{listen};")
    template = template.replace("/usr/share/nginx/html", '"' + html.as_posix() + '"')
    template = template.replace("/etc/nginx/skillhub-base-path*.conf", '"' + routing.as_posix() + '"')
    template = template.replace("/etc/nginx/skillhub-guide-public-url*.conf", '"' + (run / "absent*.conf").as_posix() + '"')
    config = run / "nginx.conf"
    config.write_text(f'worker_processes 1;\npid "{(run / "nginx.pid").as_posix()}";\n'
                      f'error_log "{(run / "error.log").as_posix()}";\nevents {{ worker_connections 64; }}\n'
                      f'http {{\n{template}\n}}\n', encoding="utf-8")
    command = [str(nginx), "-p", run.as_posix() + "/", "-c", config.as_posix()]
    result = subprocess.run(command + ["-t"], capture_output=True, timeout=10, creationflags=NO_WINDOW)
    if result.returncode:
        raise AssertionError(result.stderr.decode(errors="replace"))
    process = subprocess.Popen(command, creationflags=NO_WINDOW, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    count = 0
    try:
        for _ in range(100):
            if process.poll() is not None:
                raise AssertionError("Nginx exited before readiness")
            try:
                with socket.create_connection(("127.0.0.1", listen), timeout=.1):
                    break
            except OSError:
                time.sleep(.05)
        for suffix, marker in [("/dashboard", b"SPA_FALLBACK_MUST_NOT_LEAK"), ("/assets/app.js", b"STATIC_ASSET")]:
            connection = http.client.HTTPConnection("127.0.0.1", listen, timeout=10)
            connection.request("GET", prefix.rstrip("/") + suffix)
            response = connection.getresponse()
            assert response.status == 200 and response.read() == marker, (prefix, suffix, "ordinary routing changed")
            connection.close()
            count += 1
        routes = ["/api/v4/projects/apps%2Fintelli-dev/repository/tags?per_page=10",
                  "/api/v4/projects/apps%2Fintelli-dev/repository/files/dist%2Flatest.yml/raw?ref=v1.2.3&ref=v2.0.0",
                  "/updates/sample/windows-x64/stable/latest.yml?test=a%2Bb",
                  "/releases/sample/1/assets/dist/app%20Name.exe?test=x+y",
                  "/apps/intelli-dev/-/raw/v1.2.3/dist/app%20Name.exe?ref=v1.2.3"]
        for suffix in routes:
            for method in ("GET", "HEAD"):
                connection = http.client.HTTPConnection("127.0.0.1", listen, timeout=10)
                connection.request(method, prefix.rstrip("/") + suffix,
                                   headers={"Range": "bytes=0-7", "If-Range": ETAG, "If-None-Match": '"old"', "Accept-Encoding": "gzip"})
                response = connection.getresponse()
                body = response.read()
                assert response.status == 200, (prefix, suffix, response.status)
                assert response.getheader("Content-Type") == "application/json", (prefix, suffix, "SPA fallback")
                assert response.getheader("Content-Encoding") is None, "update bytes must not be compressed by proxy"
                assert response.getheader("ETag") == ETAG
                assert response.getheader("Accept-Ranges") == "bytes"
                assert response.getheader("Last-Modified") == "Thu, 15 Oct 2026 00:00:00 GMT"
                assert response.getheader("Cache-Control") == "no-store"
                if method == "GET":
                    payload = json.loads(body)
                    assert payload["rawPath"] == suffix, (prefix, suffix, payload["rawPath"])
                    assert payload["prefix"] == (prefix.rstrip("/") or None)
                    assert payload["range"] == "bytes=0-7"
                    assert payload["ifRange"] == ETAG
                    assert payload["ifNoneMatch"] == '"old"'
                    assert not payload["acceptEncoding"], "upstream must see identity bytes"
                    assert int(response.getheader("Content-Length")) == len(body)
                else:
                    assert not body and int(response.getheader("Content-Length")) > 1200
                count += 1
                connection.close()
        connection = http.client.HTTPConnection("127.0.0.1", listen, timeout=10)
        connection.request("GET", prefix.rstrip("/") + "/updates/missing/latest.yml?missing=1")
        response = connection.getresponse()
        assert response.status == 404 and json.loads(response.read())["rawPath"].endswith("?missing=1")
        connection.close()
        count += 1
        for suffix, status in [("/api/v1/plugin-management/imports?namespaceId=1&format=ZIP", 200),
                               ("/api/v1/admin/software/update-releases/1/assets?path=dist%2Flatest.yml&role=METADATA", 200),
                               ("/api/v1/admin/software/update-releases/1", 413)]:
            connection = http.client.HTTPConnection("127.0.0.1", listen, timeout=10)
            connection.putrequest("POST", prefix.rstrip("/") + suffix)
            connection.putheader("Content-Length", str(101 * 1024 * 1024))
            connection.putheader("Expect", "100-continue")
            connection.endheaders()
            response = connection.getresponse()
            body = response.read()
            assert response.status == status, (prefix, suffix, response.status)
            if status == 200:
                assert json.loads(body)["rawPath"] == suffix
            connection.close()
            count += 1
        return count
    finally:
        subprocess.run(command + ["-s", "quit"], capture_output=True, timeout=10, creationflags=NO_WINDOW)
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--nginx", default=os.environ.get("SKILLHUB_TEST_NGINX") or shutil.which("nginx"))
    parser.add_argument("--shell", default=os.environ.get("SKILLHUB_TEST_SHELL") or shutil.which("sh") or "C:/Program Files/Git/bin/bash.exe")
    parser.add_argument("--require-nginx", action="store_true")
    args = parser.parse_args()
    if not args.nginx:
        if args.require_nginx:
            parser.error("An installed Nginx executable is required")
        print("software-update-proxy-test skipped (nginx unavailable)")
        return
    backend = ThreadingHTTPServer(("127.0.0.1", 0), Echo)
    thread = threading.Thread(target=backend.serve_forever, daemon=True)
    thread.start()
    try:
        with tempfile.TemporaryDirectory(prefix="astronhub-update-proxy-") as temp:
            checks = sum(probe(Path(args.nginx).resolve(), args.shell, prefix, backend.server_port,
                               Path(temp) / str(index)) for index, prefix in enumerate(("/", "/hub/", "/hub.v1/")))
        print(f"software-update-proxy-test passed ({checks} real proxy checks; no upload bodies sent)")
    finally:
        backend.shutdown()
        backend.server_close()
        thread.join(timeout=5)


if __name__ == "__main__":
    main()
