from __future__ import annotations

import io
import struct

import pytest

from inspector.backend_client import BackendClient, ClaimedTask, StaleLeaseError
from inspector.bounded_archive import (
    BasicValidationError,
    validate_deb_ar,
    validate_pe_structure,
)
from inspector.parsers import InspectionVerdict, ParseContext, inspect
import inspector.worker as worker


# ---- fixtures --------------------------------------------------------------

def ar_bytes(members: list[tuple[str, int]]) -> bytes:
    out = b"!<arch>\n"
    for name, size in members:
        header = name.encode().ljust(16, b" ") + b"0".ljust(12) + b"0".ljust(6) + \
            b"0".ljust(6) + b"644".ljust(8) + str(size).rjust(10).encode() + b"`\n"
        data = b"x" * size
        out += header + data + (b"\n" if size % 2 else b"")
    return out


def pe_bytes(section_count: int = 2, sections_outside: bool = False) -> bytes:
    pe_offset = 0x80
    blob = bytearray(b"MZ" + b"\x00" * 0x3A)
    # e_lfanew lives at DOS offset 0x3C
    blob[0x3C:0x40] = struct.pack("<I", pe_offset)
    blob += b"\x00" * (pe_offset - len(blob))
    blob += b"PE\x00\x00"
    blob += struct.pack("<H", 0x8664)   # machine
    blob += struct.pack("<H", section_count)
    blob += b"\x00" * 12                 # timestamp, symtab, nsyms
    blob += struct.pack("<H", 224)       # optional header size
    blob += struct.pack("<H", 0x22)      # characteristics
    blob += b"\x00" * 224                # optional header
    for i in range(section_count):
        raw_pointer = 0x2000
        raw_size = 0x10
        if sections_outside:
            raw_pointer = 0x1000000
        blob += b".text".ljust(8, b"\x00") + struct.pack(
            "<IIII", 0x10, 0x1000, raw_size, raw_pointer) + b"\x00" * 24
    # the file must cover the section raw ranges (0x2000 + 0x10)
    blob += b"\x00" * (0x2010 - len(blob))
    return bytes(blob)


def fake_runner(stdout: str = "", returncode: int = 0, stderr: str = ""):
    calls: list[list[str]] = []

    def run(argv, **kwargs):
        calls.append(argv)
        completed = type("C", (), {})()
        completed.stdout = stdout
        completed.stderr = stderr
        completed.returncode = returncode
        return completed

    run.calls = calls  # type: ignore[attr-defined]
    return run


def ctx(package, runner, fmt_timeout: int = 60) -> ParseContext:
    return ParseContext(
        path=str(package),
        subprocess_timeout_seconds=fmt_timeout,
        max_extracted_bytes=10 * 1024 * 1024,
        max_extracted_entries=10_000,
        runner=runner,
    )


# ---- bounded archive -------------------------------------------------------

def test_deb_ar_walk_accepts_clean_archive(tmp_path):
    package = tmp_path / "p.deb"
    package.write_bytes(ar_bytes([("debian-binary", 4), ("control.tar.gz", 100)]))
    validate_deb_ar(str(package), max_members=10, max_total_bytes=1000)


def test_deb_ar_walk_rejects_path_traversal(tmp_path):
    package = tmp_path / "p.deb"
    package.write_bytes(ar_bytes([("../../etc/passwd", 4)]))
    with pytest.raises(BasicValidationError) as ex:
        validate_deb_ar(str(package), max_members=10, max_total_bytes=1000)
    assert ex.value.error_code == "validation.pathTraversal"


def test_deb_ar_walk_enforces_member_and_size_caps(tmp_path):
    package = tmp_path / "p.deb"
    package.write_bytes(ar_bytes([("a", 4), ("b", 4)]))
    with pytest.raises(BasicValidationError) as ex:
        validate_deb_ar(str(package), max_members=1, max_total_bytes=1000)
    assert ex.value.error_code == "validation.tooManyMembers"

    package.write_bytes(ar_bytes([("big", 100)]))
    with pytest.raises(BasicValidationError) as ex:
        validate_deb_ar(str(package), max_members=10, max_total_bytes=50)
    assert ex.value.error_code == "validation.packageTooLarge"


def test_deb_ar_walk_rejects_non_archive(tmp_path):
    package = tmp_path / "p.deb"
    package.write_bytes(b"not an ar archive at all")
    with pytest.raises(BasicValidationError) as ex:
        validate_deb_ar(str(package), max_members=10, max_total_bytes=1000)
    assert ex.value.error_code == "validation.debNotArArchive"


# ---- PE structure ----------------------------------------------------------

def test_pe_structure_accepts_well_formed_binary(tmp_path):
    package = tmp_path / "p.exe"
    package.write_bytes(pe_bytes())
    validate_pe_structure(str(package))


def test_pe_structure_rejects_sections_outside_file(tmp_path):
    package = tmp_path / "p.exe"
    package.write_bytes(pe_bytes(sections_outside=True))
    with pytest.raises(BasicValidationError) as ex:
        validate_pe_structure(str(package))
    assert ex.value.error_code == "validation.sectionOutOfRange"


def test_pe_structure_rejects_non_pe(tmp_path):
    package = tmp_path / "p.exe"
    package.write_bytes(b"ELF" + b"\x00" * 200)
    with pytest.raises(BasicValidationError) as ex:
        validate_pe_structure(str(package))
    assert ex.value.error_code == "validation.notPeBinary"


# ---- parsers ---------------------------------------------------------------

def test_msi_parser_uses_fixed_arguments_and_extracts_fields(tmp_path):
    package = tmp_path / "p.msi"
    package.write_bytes(b"\xd0\xcf\x11\xe0" + b"\x00" * 128)
    runner = fake_runner(stdout="ProductVersion: 1.2.3\nProductName: App\nManufacturer: Acme\n")
    verdict = inspect("MSI", ctx(package, runner))
    assert runner.calls[0] == ["msiinfo", "export", str(package), "Property"]
    assert verdict.outcome == "SUCCEEDED"
    assert verdict.raw_version_fields == {
        "ProductVersion": "1.2.3", "ProductName": "App", "Manufacturer": "Acme"}
    assert verdict.parser_name == "msiinfo"


def test_deb_parser_validates_then_extracts(tmp_path):
    package = tmp_path / "p.deb"
    package.write_bytes(ar_bytes([("control.tar.gz", 10)]))
    runner = fake_runner(stdout="Package: hello\nVersion: 2.0-1\nArchitecture: amd64\n")
    verdict = inspect("DEB", ctx(package, runner))
    assert runner.calls[0] == ["dpkg-deb", "-f", str(package)]
    assert verdict.outcome == "SUCCEEDED"
    assert verdict.raw_version_fields["Version"] == "2.0-1"


def test_deb_parser_reports_basic_failure_on_malformed_container(tmp_path):
    package = tmp_path / "p.deb"
    package.write_bytes(b"garbage")
    verdict = inspect("DEB", ctx(package, fake_runner()))
    assert verdict.outcome == "BASIC_FAILED"
    assert verdict.error_code == "validation.debNotArArchive"


def test_rpm_parser_maps_query_output(tmp_path):
    package = tmp_path / "p.rpm"
    package.write_bytes(b"\xed\xab\xee\xdb" + b"\x00" * 128)
    runner = fake_runner(stdout="hello\n2.0\n1.fc40\nx86_64\n(none)\n")
    verdict = inspect("RPM", ctx(package, runner))
    assert runner.calls[0][:3] == ["rpm", "-qp", "--qf"]
    assert verdict.raw_version_fields == {
        "Name": "hello", "Version": "2.0", "Release": "1.fc40", "Arch": "x86_64"}
    assert verdict.outcome == "SUCCEEDED"


def test_exe_without_version_resource_is_metadata_failed(tmp_path):
    package = tmp_path / "p.exe"
    package.write_bytes(pe_bytes())
    verdict = inspect("EXE", ctx(package, fake_runner()))
    assert verdict.outcome == "METADATA_FAILED"
    assert verdict.error_code == "parser.noVersionResource"


def test_exe_with_version_strings_succeeds(tmp_path):
    blob = bytearray(pe_bytes(section_count=1))
    section_table = 0x80 + 4 + 20 + 224
    resource = ("ProductVersion".encode("utf-16-le") + b"\x00\x00"
                + "9.9.9".encode("utf-16-le") + b"\x00\x00")
    entry = section_table
    blob[entry:entry + 8] = b".rsrc\x00\x00\x00"
    struct.pack_into("<I", blob, entry + 16, len(resource))  # raw_size
    struct.pack_into("<I", blob, entry + 20, len(blob))      # raw_pointer
    blob += resource
    package = tmp_path / "p.exe"
    package.write_bytes(bytes(blob))
    verdict = inspect("EXE", ctx(package, fake_runner()))
    assert verdict.outcome == "SUCCEEDED"
    assert verdict.raw_version_fields is not None
    assert verdict.raw_version_fields["ProductVersion"] == "9.9.9"


def test_missing_tool_is_infra_failure(tmp_path):
    package = tmp_path / "p.msi"
    package.write_bytes(bytes([0xD0, 0xCF, 0x11, 0xE0]) + bytes(64))

    def run(argv, **kwargs):
        raise FileNotFoundError(argv[0])

    verdict = inspect("MSI", ctx(package, run))
    assert verdict.outcome == "INFRA_FAILED"
    assert verdict.error_code == "parser.toolMissing"


def test_subprocess_timeout_is_basic_failure(tmp_path):
    import subprocess

    def run(argv, **kwargs):
        raise subprocess.TimeoutExpired(cmd=argv, timeout=1)

    package = tmp_path / "p.msi"
    package.write_bytes(b"\xd0\xcf\x11\xe0" + b"\x00" * 64)
    verdict = inspect("MSI", ctx(package, run))
    assert verdict.outcome == "BASIC_FAILED"
    assert verdict.error_code == "validation.subprocessTimeout"


def test_unsupported_format_is_basic_failure(tmp_path):
    verdict = inspect("ZIP", ctx(tmp_path / "p.zip", fake_runner()))
    assert verdict.outcome == "BASIC_FAILED"
    assert verdict.error_code == "validation.unsupportedFormat"


# ---- client ----------------------------------------------------------------

class FakeResponse:
    def __init__(self, payload: bytes, status: int = 200):
        self.payload = payload
        self.status = status

    def read(self, n: int = -1) -> bytes:
        data, self.payload = self.payload, b""
        return data

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False


def test_client_result_body_shape(monkeypatch):
    captured = {}

    def fake_urlopen(request, timeout=None):
        captured["method"] = request.method
        captured["url"] = request.full_url
        captured["headers"] = dict(request.header_items())
        captured["body"] = request.data
        return FakeResponse(b'{"taskId": 5, "status": "SUCCEEDED", "attemptNo": 1}')

    monkeypatch.setattr("inspector.backend_client.urllib.request.urlopen", fake_urlopen)
    client = BackendClient("http://backend:8080", "secret-token")
    payload = client.submit_result(
        5, "worker-1", "token-1", outcome="SUCCEEDED",
        raw_version_fields={"Version": "1"}, parser_name="msiinfo",
        parser_version="0.1")

    assert captured["method"] == "POST"
    assert captured["url"].endswith("/api/v1/internal/software/inspector/tasks/5/result")
    headers = {k.lower(): v for k, v in captured["headers"].items()}
    assert headers["x-inspector-token"] == "secret-token"
    import json
    body = json.loads(captured["body"])
    assert body["outcome"] == "SUCCEEDED"
    assert body["attemptToken"] == "token-1"
    assert body["rawVersionFields"] == {"Version": "1"}
    assert payload["status"] == "SUCCEEDED"


def test_client_maps_409_to_stale_lease(monkeypatch):
    import urllib.error

    def fake_urlopen(request, timeout=None):
        raise urllib.error.HTTPError(request.full_url, 409, "conflict", {}, io.BytesIO(b""))

    monkeypatch.setattr("inspector.backend_client.urllib.request.urlopen", fake_urlopen)
    client = BackendClient("http://backend:8080", "t")
    with pytest.raises(StaleLeaseError):
        client.heartbeat(1, "w", "t")


def test_client_download_caps_bytes(monkeypatch, tmp_path):
    def fake_urlopen(request, timeout=None):
        return FakeResponse(b"x" * 100)

    monkeypatch.setattr("inspector.backend_client.urllib.request.urlopen", fake_urlopen)
    client = BackendClient("http://backend:8080", "t")
    target = tmp_path / "out.bin"
    client.download_content(1, str(target), max_bytes=1000)
    assert target.stat().st_size == 100
    with pytest.raises(ValueError):
        client.download_content(1, str(target), max_bytes=50)


# ---- worker loop -----------------------------------------------------------

def test_worker_stale_lease_discards_work(monkeypatch, tmp_path):
    class FakeClient:
        def __init__(self):
            self.submitted = False

        def claim(self, worker_id):
            return ClaimedTask(
                task_id=1, package_revision_id=1, attempt_no=1, attempt_token="t",
                object_key="k", format="MSI", policy_revision=1,
                total_timeout_seconds=900, subprocess_timeout_seconds=60,
                soft_memory_limit_bytes=512, max_extracted_bytes=1024,
                max_extracted_entries=100, lease_expires_at="2026-01-01T00:00:00Z")

        def download_content(self, task_id, path, timeout=None, max_bytes=None):
            open(path, "wb").write(b"data")
            return path

        def heartbeat(self, task_id, worker_id, token):
            raise StaleLeaseError("stale")

        def submit_result(self, *args, **kwargs):
            # the server rejects the stale attempt before accepting anything
            raise StaleLeaseError("stale")

    fake = FakeClient()
    config = type("C", (), {"worker_id": "w", "heartbeat_interval_seconds": 60,
                            "download_timeout_seconds": 10})()
    processed = worker.process_once(fake, config)
    assert processed is True
    assert fake.submitted is False


def test_worker_reports_success(monkeypatch, tmp_path):
    import inspector.parsers as parsers

    class FakeClient:
        def __init__(self):
            self.result = None

        def claim(self, worker_id):
            return ClaimedTask(
                task_id=2, package_revision_id=2, attempt_no=1, attempt_token="t",
                object_key="k", format="MSI", policy_revision=1,
                total_timeout_seconds=900, subprocess_timeout_seconds=60,
                soft_memory_limit_bytes=512, max_extracted_bytes=1024,
                max_extracted_entries=100, lease_expires_at="2026-01-01T00:00:00Z")

        def download_content(self, task_id, path, timeout=None, max_bytes=None):
            return path

        def heartbeat(self, task_id, worker_id, token):
            pass

        def submit_result(self, task_id, worker_id, token, **kwargs):
            self.result = kwargs

    monkeypatch.setattr(
        worker, "inspect",
        lambda fmt, ctx: InspectionVerdict(outcome="SUCCEEDED", raw_version_fields={"v": "1"}))
    fake = FakeClient()
    config = type("C", (), {"worker_id": "w", "heartbeat_interval_seconds": 60,
                            "download_timeout_seconds": 10})()
    worker.process_once(fake, config)
    assert fake.result is not None
    assert fake.result["outcome"] == "SUCCEEDED"
    assert fake.result["raw_version_fields"] == {"v": "1"}
