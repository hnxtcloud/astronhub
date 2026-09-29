"""Worker configuration from environment variables."""

from __future__ import annotations

import os
from dataclasses import dataclass
from uuid import uuid4


def _env_int(name: str, default: int) -> int:
    try:
        return int(os.environ.get(name, "").strip() or default)
    except ValueError:
        return default


@dataclass(frozen=True)
class WorkerConfig:
    base_url: str
    token: str
    worker_id: str
    poll_interval_seconds: float
    heartbeat_interval_seconds: float
    download_timeout_seconds: int
    work_dir: str

    @staticmethod
    def from_env() -> "WorkerConfig":
        token = os.environ.get("SKILLHUB_SOFTWARE_INSPECTOR_TOKEN", "").strip()
        if not token:
            raise SystemExit(
                "SKILLHUB_SOFTWARE_INSPECTOR_TOKEN is required; refusing to start unauthenticated"
            )
        base_url = os.environ.get("SKILLHUB_BASE_URL", "http://localhost:8080").rstrip("/")
        worker_id = os.environ.get("INSPECTOR_WORKER_ID", "").strip() or f"inspector-{uuid4()}"
        return WorkerConfig(
            base_url=base_url,
            token=token,
            worker_id=worker_id,
            poll_interval_seconds=_env_int("INSPECTOR_POLL_INTERVAL_SECONDS", 10),
            heartbeat_interval_seconds=_env_int("INSPECTOR_HEARTBEAT_INTERVAL_SECONDS", 30),
            download_timeout_seconds=_env_int("INSPECTOR_DOWNLOAD_TIMEOUT_SECONDS", 600),
            work_dir=os.environ.get("INSPECTOR_WORK_DIR", "/tmp/inspector"),
        )
