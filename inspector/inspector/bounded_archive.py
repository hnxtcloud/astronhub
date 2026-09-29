"""Bounded basic validation for container formats the worker opens itself.

The caps and traversal checks here realize the spec's basic-validation rules:
hitting a limit is a BASIC_FAILED verdict (blocks publication), never a
metadata-missing case. Only structures are walked — nothing is extracted.
"""

from __future__ import annotations

import os
import struct


class BasicValidationError(Exception):
    """Basic validation failed: the package is malformed or violates limits."""

    def __init__(self, error_code: str) -> None:
        super().__init__(error_code)
        self.error_code = error_code


def _reject(error_code: str) -> None:
    raise BasicValidationError(error_code)


def _safe_member_name(name: str) -> bool:
    if not name:
        return False
    if name.startswith("/") or name.startswith("\\") or name.startswith("//"):
        return False
    if ":" in name[:3]:  # drive letters like C:
        return False
    parts = name.replace("\\", "/").split("/")
    return ".." not in parts


def validate_deb_ar(path: str, *, max_members: int, max_total_bytes: int) -> None:
    """Walks the `ar` container of a .deb without extracting members.

    Every member name must be safe, the member count and total declared sizes
    must stay within the policy caps.
    """
    total = 0
    members = 0
    with open(path, "rb") as deb:
        magic = deb.read(8)
        if magic != b"!<arch>\n":
            _reject("validation.debNotArArchive")
        while True:
            header = deb.read(60)
            if not header:
                break
            if len(header) < 60:
                _reject("validation.debTruncatedHeader")
            try:
                size = int(header[48:58].decode("ascii").strip())
            except ValueError:
                _reject("validation.debBadHeader")
            name = header[0:16].decode("ascii", errors="replace").rstrip()
            if name.endswith("/"):
                name = name[:-1]
            if not _safe_member_name(name):
                _reject("validation.pathTraversal")
            members += 1
            total += size
            if members > max_members:
                _reject("validation.tooManyMembers")
            if total > max_total_bytes:
                _reject("validation.packageTooLarge")
            # member data is padded to an even offset
            deb.seek(size + (size % 2), os.SEEK_CUR)


def validate_pe_structure(path: str) -> None:
    """Checks the DOS/PE headers and section table of a Windows executable.

    The section table must be inside the file and section raw ranges must not
    point outside the file.
    """
    with open(path, "rb") as exe:
        if exe.read(2) != b"MZ":
            _reject("validation.notPeBinary")
        exe.seek(0x3C)
        (pe_offset,) = struct.unpack("<I", exe.read(4))
        if pe_offset <= 0 or pe_offset > os.path.getsize(path) - 24:
            _reject("validation.notPeBinary")
        exe.seek(pe_offset)
        if exe.read(4) != b"PE\x00\x00":
            _reject("validation.notPeBinary")
        # COFF header: machine(2) + section count(2) + timestamp(4) +
        # symbol table(4) + symbols(4) + optional header size(2) + flags(2)
        exe.seek(2, os.SEEK_CUR)
        (section_count,) = struct.unpack("<H", exe.read(2))
        if section_count > 96:
            _reject("validation.tooManySections")
        exe.seek(12, os.SEEK_CUR)
        (optional_header_size,) = struct.unpack("<H", exe.read(2))
        section_table = pe_offset + 4 + 20 + optional_header_size
        file_size = os.path.getsize(path)
        if section_table + 40 * section_count > file_size:
            _reject("validation.truncatedSectionTable")
        for index in range(section_count):
            exe.seek(section_table + 40 * index + 8)
            virtual_size, virtual_address, raw_size, raw_pointer = struct.unpack("<IIII", exe.read(16))
            if raw_size and raw_pointer + raw_size > file_size:
                _reject("validation.sectionOutOfRange")
