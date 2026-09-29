"""HTTP client for the internal inspector task API."""

from __future__ import annotations

import json
import urllib.error
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any

TOKEN_HEADER = "X-Inspector-Token"


class StaleLeaseError(Exception):
    """The server rejected the attempt token (409): stop working on the task."""


@dataclass(frozen=True)
class ClaimedTask:
    task_id: int
    package_revision_id: int
    attempt_no: int
    attempt_token: str
    object_key: str
    format: str
    policy_revision: int
    total_timeout_seconds: int
    subprocess_timeout_seconds: int
    soft_memory_limit_bytes: int
    max_extracted_bytes: int
    max_extracted_entries: int
    lease_expires_at: str


class BackendClient:
    def __init__(self, base_url: str, token: str) -> None:
        self._base_url = base_url.rstrip("/")
        self._token = token

    def _request(self, method: str, path: str, body: dict[str, Any] | None = None,
                 timeout: int = 60) -> dict[str, Any]:
        data = None
        headers = {TOKEN_HEADER: self._token}
        if body is not None:
            data = json.dumps(body).encode("utf-8")
            headers["Content-Type"] = "application/json"
        request = urllib.request.Request(
            f"{self._base_url}{path}", data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                payload = response.read()
                return json.loads(payload) if payload else {}
        except urllib.error.HTTPError as ex:
            if ex.code == 409:
                raise StaleLeaseError(f"stale lease on {path}") from ex
            raise

    def claim(self, worker_id: str) -> ClaimedTask | None:
        payload = self._request(
            "POST", "/api/v1/internal/software/inspector/tasks/claim",
            {"workerId": worker_id})
        if not payload.get("claimed"):
            return None
        return ClaimedTask(
            task_id=payload["taskId"],
            package_revision_id=payload["packageRevisionId"],
            attempt_no=payload["attemptNo"],
            attempt_token=payload["attemptToken"],
            object_key=payload["objectKey"],
            format=payload["format"],
            policy_revision=payload["policyRevision"],
            total_timeout_seconds=payload["totalTimeoutSeconds"],
            subprocess_timeout_seconds=payload["subprocessTimeoutSeconds"],
            soft_memory_limit_bytes=payload["softMemoryLimitBytes"],
            max_extracted_bytes=payload["maxExtractedBytes"],
            max_extracted_entries=payload["maxExtractedEntries"],
            lease_expires_at=payload["leaseExpiresAt"],
        )

    def heartbeat(self, task_id: int, worker_id: str, attempt_token: str) -> None:
        self._request(
            "POST", f"/api/v1/internal/software/inspector/tasks/{task_id}/heartbeat",
            {"workerId": worker_id, "attemptToken": attempt_token})

    def download_content(self, task_id: int, target_path: str,
                         timeout: int = 600, max_bytes: int | None = None) -> str:
        """Streams the immutable package bytes to target_path."""
        request = urllib.request.Request(
            f"{self._base_url}/api/v1/internal/software/inspector/tasks/{task_id}/content",
            headers={TOKEN_HEADER: self._token})
        written = 0
        with urllib.request.urlopen(request, timeout=timeout) as response, \
                open(target_path, "wb") as out:
            while True:
                chunk = response.read(1024 * 1024)
                if not chunk:
                    break
                written += len(chunk)
                if max_bytes is not None and written > max_bytes:
                    raise ValueError(f"content exceeds {max_bytes} bytes")
                out.write(chunk)
        return target_path

    def submit_result(self, task_id: int, worker_id: str, attempt_token: str, *,
                      outcome: str, error_code: str | None = None,
                      raw_version_fields: dict[str, str] | None = None,
                      parser_name: str | None = None, parser_version: str | None = None,
                      started_at: datetime | None = None,
                      finished_at: datetime | None = None) -> dict[str, Any]:
        body: dict[str, Any] = {
            "workerId": worker_id,
            "attemptToken": attempt_token,
            "outcome": outcome,
        }
        if error_code is not None:
            body["errorCode"] = error_code
        if raw_version_fields is not None:
            body["rawVersionFields"] = raw_version_fields
        if parser_name is not None:
            body["parserName"] = parser_name
        if parser_version is not None:
            body["parserVersion"] = parser_version
        if started_at is not None:
            body["startedAt"] = started_at.astimezone(timezone.utc).isoformat()
        if finished_at is not None:
            body["finishedAt"] = finished_at.astimezone(timezone.utc).isoformat()
        return self._request(
            "POST", f"/api/v1/internal/software/inspector/tasks/{task_id}/result", body)
