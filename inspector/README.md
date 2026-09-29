# Software Center Inspector Worker

Independent Linux container that processes the software center's persistent
static-inspection tasks. It claims tasks through the restricted internal API,
streams the immutable package bytes by task id, extracts version metadata,
and reports the attempt result with its attempt token.

**The worker holds no database or admin credentials** — the deployment-scoped
inspector token is its only identity and is valid only on
`/api/v1/internal/software/**`. Python 3.12 standard library only; no
third-party packages.

## What it does per task

1. `POST /api/v1/internal/software/inspector/tasks/claim` — claim a due task
   (short transaction, attempt token, lease).
2. `GET .../tasks/{id}/content` — stream the package bytes into a temp file.
3. Run basic validation and metadata extraction with fixed-argument
   subprocess tools under the claim-time policy snapshot:
   - MSI → `msiinfo export <file> Property` (ProductVersion/ProductName/…)
   - DEB → bounded `ar` walk, then `dpkg-deb -f <file>`
   - RPM → `rpm -qp --qf <template> <file>`
   - EXE → stdlib PE structure check + version-resource string scan
4. Heartbeat the lease every 30 s while working.
5. `POST .../tasks/{id}/result` — report `SUCCEEDED`, `BASIC_FAILED`
   (malformed package or limit violation; blocks publication),
   `METADATA_FAILED` (extraction failed; administrators fill values in
   manually), or `INFRA_FAILED` (recoverable; consumes one auto-retry).

A 409 from the server (stale lease) discards the local attempt silently.

## Configuration

| Variable | Default | Meaning |
|----------|---------|---------|
| `SKILLHUB_SOFTWARE_INSPECTOR_TOKEN` | — (required) | Shared token, must match `SKILLHUB_SOFTWARE_INSPECTOR_TOKEN` on the backend |
| `SKILLHUB_BASE_URL` | `http://localhost:8080` | Backend base URL |
| `INSPECTOR_WORKER_ID` | auto (`inspector-<uuid>`) | Stable worker id for lease ownership |
| `INSPECTOR_POLL_INTERVAL_SECONDS` | `10` | Idle poll interval |
| `INSPECTOR_HEARTBEAT_INTERVAL_SECONDS` | `30` | Lease heartbeat interval |
| `INSPECTOR_DOWNLOAD_TIMEOUT_SECONDS` | `600` | Content download timeout |
| `INSPECTOR_WORK_DIR` | `/tmp/inspector` | Scratch directory |

## Run

```bash
docker build -t skillhub-inspector ./inspector
docker run --rm \
  -e SKILLHUB_SOFTWARE_INSPECTOR_TOKEN=<same-as-backend> \
  -e SKILLHUB_BASE_URL=http://backend:8080 \
  skillhub-inspector
```

Recommended container limits (see the software center spec): 1 CPU, 1 GiB
memory, read-only root filesystem (scratch lives in tmpfs `/tmp`).

## Tests

```bash
cd inspector && python -m pytest tests/ -q
```

The suite uses crafted binary fixtures only; no external tools are needed to
run it.
