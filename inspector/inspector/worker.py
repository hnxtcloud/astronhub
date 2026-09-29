"""Inspector worker main loop.

One claimed task at a time: download the immutable bytes, run basic
validation and metadata extraction outside any transaction, heartbeat the
lease while working, and report the attempt result with the attempt token.
A stale-lease rejection (409) discards the local work — the server refused
the result, so nothing is retried for that attempt.
"""

from __future__ import annotations

import shutil
import tempfile
import threading
import time
from datetime import datetime, timezone
from typing import Callable

from .backend_client import BackendClient, ClaimedTask, StaleLeaseError
from .config import WorkerConfig
from .parsers import ParseContext, inspect


def process_once(client: BackendClient, config: WorkerConfig,
                 work_dir_factory: Callable[[], str] = tempfile.mkdtemp) -> bool:
    """Claims and processes at most one task. Returns True when a task ran."""
    claimed = client.claim(config.worker_id)
    if claimed is None:
        return False
    work_dir = work_dir_factory()
    try:
        _process_claimed(client, config, claimed, work_dir)
    except StaleLeaseError:
        # the server no longer accepts this attempt; drop the work quietly
        pass
    finally:
        shutil.rmtree(work_dir, ignore_errors=True)
    return True


def _process_claimed(client: BackendClient, config: WorkerConfig,
                     claimed: ClaimedTask, work_dir: str) -> None:
    started_at = datetime.now(timezone.utc)
    stop_heartbeat = threading.Event()

    def beat() -> None:
        while not stop_heartbeat.wait(config.heartbeat_interval_seconds):
            try:
                client.heartbeat(claimed.task_id, config.worker_id, claimed.attempt_token)
            except StaleLeaseError:
                return
            except Exception:
                # transient heartbeat failure must not kill the attempt; the
                # lease has slack and the next tick retries
                continue

    heart = threading.Thread(target=beat, daemon=True)
    heart.start()
    try:
        package_path = client.download_content(
            claimed.task_id, f"{work_dir}/package.bin",
            timeout=config.download_timeout_seconds)
        verdict = inspect(claimed.format, ParseContext(
            path=package_path,
            subprocess_timeout_seconds=claimed.subprocess_timeout_seconds,
            max_extracted_bytes=claimed.max_extracted_bytes,
            max_extracted_entries=claimed.max_extracted_entries,
        ))
        client.submit_result(
            claimed.task_id, config.worker_id, claimed.attempt_token,
            outcome=verdict.outcome,
            error_code=verdict.error_code,
            raw_version_fields=verdict.raw_version_fields,
            parser_name=verdict.parser_name,
            parser_version=verdict.parser_version,
            started_at=started_at,
            finished_at=datetime.now(timezone.utc),
        )
    finally:
        stop_heartbeat.set()
        heart.join(timeout=5)


def run_forever(config: WorkerConfig, client: BackendClient | None = None,
                once: Callable[[], bool] | None = None) -> None:
    client = client or BackendClient(config.base_url, config.token)
    loop = once or (lambda: process_once(client, config))
    while True:
        try:
            worked = loop()
        except Exception:
            time.sleep(config.poll_interval_seconds)
            continue
        if not worked:
            time.sleep(config.poll_interval_seconds)


def main() -> None:
    config = WorkerConfig.from_env()
    print(f"inspector worker {config.worker_id} starting against {config.base_url}", flush=True)
    run_forever(config)


if __name__ == "__main__":
    main()
