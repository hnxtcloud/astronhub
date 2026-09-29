"""Static metadata extraction per package format.

Every external tool runs as a fixed-argument subprocess (no shell, no
interpolation of package content into the command line) under a per-call
timeout from the claim-time policy snapshot. Extraction failure that leaves
basic validation intact is a METADATA_FAILED (manual fill-in allowed); a
malformed package or a limit violation is BASIC_FAILED (blocks publication).
"""

from __future__ import annotations

import struct
import subprocess
from dataclasses import dataclass, field
from typing import Callable

from .bounded_archive import (
    BasicValidationError,
    validate_deb_ar,
    validate_pe_structure,
)

Runner = Callable[..., subprocess.CompletedProcess]


@dataclass
class InspectionVerdict:
    outcome: str  # SUCCEEDED | BASIC_FAILED | METADATA_FAILED
    error_code: str | None = None
    raw_version_fields: dict[str, str] | None = None
    parser_name: str | None = None
    parser_version: str | None = None


@dataclass
class ParseContext:
    path: str
    subprocess_timeout_seconds: int
    max_extracted_bytes: int
    max_extracted_entries: int
    runner: Runner = field(default_factory=lambda: subprocess.run)


def _run(context: ParseContext, argv: list[str]) -> str:
    try:
        completed = context.runner(
            argv, capture_output=True, text=True, timeout=context.subprocess_timeout_seconds)
    except subprocess.TimeoutExpired as ex:
        raise BasicValidationError("validation.subprocessTimeout") from ex
    if completed.returncode != 0:
        raise _ToolError(completed.stderr.strip()[:200] or completed.stdout.strip()[:200])
    output = completed.stdout
    if len(output) > context.max_extracted_bytes:
        raise BasicValidationError("validation.metadataTooLarge")
    return output


class _ToolError(Exception):
    pass


def _parse_key_blocks(output: str) -> dict[str, str]:
    fields: dict[str, str] = {}
    for line in output.splitlines():
        if ":" not in line:
            continue
        key, _, value = line.partition(":")
        key = key.strip()
        if key:
            fields[key] = value.strip()
    return fields


def _require(fields: dict[str, str], *names: str) -> dict[str, str]:
    extracted = {name: fields[name] for name in names if fields.get(name)}
    if not extracted:
        raise _ToolError("no required fields present")
    return extracted


def inspect_msi(context: ParseContext) -> InspectionVerdict:
    # msiinfo export prints "key: value" lines from the Property table.
    output = _run(context, ["msiinfo", "export", context.path, "Property"])
    fields = _parse_key_blocks(output)
    return InspectionVerdict(
        outcome="SUCCEEDED",
        raw_version_fields=_require(fields, "ProductVersion", "ProductName", "Manufacturer"),
        parser_name="msiinfo",
        parser_version=_tool_version(context, "msiinfo"),
    )


def inspect_deb(context: ParseContext) -> InspectionVerdict:
    validate_deb_ar(
        context.path,
        max_members=context.max_extracted_entries,
        max_total_bytes=context.max_extracted_bytes)
    output = _run(context, ["dpkg-deb", "-f", context.path])
    fields = _parse_key_blocks(output)
    return InspectionVerdict(
        outcome="SUCCEEDED",
        raw_version_fields=_require(fields, "Package", "Version", "Architecture"),
        parser_name="dpkg-deb",
        parser_version=_tool_version(context, "dpkg-deb"),
    )


def inspect_rpm(context: ParseContext) -> InspectionVerdict:
    query_format = "%{NAME}\\n%{VERSION}\\n%{RELEASE}\\n%{ARCH}\\n%{EPOCH}\\n"
    output = _run(context, ["rpm", "-qp", "--qf", query_format, context.path])
    lines = [line.strip() for line in output.splitlines() if line.strip()]
    if len(lines) < 4 or lines[0] in ("(none)", "(not set)"):
        raise _ToolError("rpm query returned no package identity")
    fields = {"Name": lines[0], "Version": lines[1], "Release": lines[2], "Arch": lines[3]}
    if len(lines) > 4 and lines[4] not in ("(none)", ""):
        fields["Epoch"] = lines[4]
    return InspectionVerdict(
        outcome="SUCCEEDED",
        raw_version_fields=fields,
        parser_name="rpm",
        parser_version=_tool_version(context, "rpm"),
    )


def inspect_exe(context: ParseContext) -> InspectionVerdict:
    validate_pe_structure(context.path)
    try:
        fields = _parse_version_resource(context.path)
    except _ToolError:
        # structurally valid PE but no parsable version resource: administrators
        # fill the metadata in manually
        return InspectionVerdict(outcome="METADATA_FAILED", error_code="parser.noVersionResource")
    return InspectionVerdict(
        outcome="SUCCEEDED",
        raw_version_fields=fields,
        parser_name="pe-struct",
        parser_version="1",
    )


def _parse_version_resource(path: str) -> dict[str, str]:
    """Minimal walk to the VS_VERSIONINFO StringTable of a PE file."""
    wanted = {"ProductName", "ProductVersion", "FileVersion", "CompanyName"}
    found: dict[str, str] = {}
    with open(path, "rb") as exe:
        blob = exe.read()
    (pe_offset,) = struct.unpack_from("<I", blob, 0x3C)
    (section_count,) = struct.unpack_from("<H", blob, pe_offset + 6)
    (optional_size,) = struct.unpack_from("<H", blob, pe_offset + 20)
    section_table = pe_offset + 24 + optional_size
    for index in range(section_count):
        base = section_table + 40 * index
        name = blob[base:base + 8].rstrip(b"\x00")
        if name != b".rsrc":
            continue
        raw_size, raw_pointer = struct.unpack_from("<II", blob, base + 16)
        resource = blob[raw_pointer:raw_pointer + raw_size]
        # scan for UTF-16 key names of the version StringTable
        for key in wanted:
            needle = key.encode("utf-16-le")
            position = resource.find(needle)
            if position < 0:
                continue
            value_start = position + len(needle) + 2  # skip NUL + value type byte
            # value is a UTF-16 string after the key, terminated by NUL
            value_bytes = b""
            cursor = value_start
            while cursor + 1 < len(resource):
                pair = resource[cursor:cursor + 2]
                if pair == b"\x00\x00":
                    break
                value_bytes += pair
                cursor += 2
            value = value_bytes.decode("utf-16-le", errors="replace").strip("\x00 ").strip()
            if value:
                found[key] = value
    extracted = {k: v for k, v in found.items() if k in ("ProductName", "ProductVersion")}
    if not extracted:
        raise _ToolError("no version strings found")
    return found


def _tool_version(context: ParseContext, tool: str) -> str | None:
    try:
        completed = context.runner(
            [tool, "--version"], capture_output=True, text=True, timeout=15)
        first = (completed.stdout or completed.stderr).strip().splitlines()
        return first[0][:64] if first else None
    except Exception:
        return None


INSPECTORS = {
    "MSI": inspect_msi,
    "DEB": inspect_deb,
    "RPM": inspect_rpm,
    "EXE": inspect_exe,
}


def inspect(fmt: str, context: ParseContext) -> InspectionVerdict:
    inspector = INSPECTORS.get(fmt)
    if inspector is None:
        return InspectionVerdict(
            outcome="BASIC_FAILED", error_code="validation.unsupportedFormat")
    try:
        return inspector(context)
    except BasicValidationError as ex:
        return InspectionVerdict(outcome="BASIC_FAILED", error_code=ex.error_code)
    except _ToolError:
        return InspectionVerdict(outcome="METADATA_FAILED", error_code="parser.extractionFailed")
    except FileNotFoundError:
        # the static tool this format needs is not installed in the container
        return InspectionVerdict(outcome="INFRA_FAILED", error_code="parser.toolMissing")
