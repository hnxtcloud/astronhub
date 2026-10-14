# Software Center — Deployment & Operations

Status: implementation complete (phases 1–5). This document is the operator-facing
deployment guide, operations manual, and an honest acceptance checklist for the
software center (`/api/v1/software/**`, `/api/v1/admin/software/**`,
`/api/v1/internal/software/inspector/**`).

Design sources: `.scratch/software-center/spec.md` and the decision tickets under
`.scratch/software-center/issues/` (product rules are normative there).

## 1. Components

| Component | Artifact | Notes |
|-----------|----------|-------|
| Backend | `server/` Spring Boot app | Serves portal/admin APIs, chunked upload, download sessions, persistent inspection tasks, dynamic policy |
| Inspector worker | `inspector/` Docker image (python:3.12-slim + msitools/rpm) | Claims persistent tasks over the restricted internal API; no DB/admin credentials |
| Database | PostgreSQL 16 | Flyway V70–V73: software schema, transfer, inspection, and download-session timestamps |
| Object storage | Local FS or S3/MinIO | `skillhub.storage.provider=local` (default) or `s3` |
| Reverse proxy | Nginx / LB | Must not clip upload chunks (see §3) |

## 2. First deployment

### 2.1 Backend

1. Deploy the backend image with the standard env (DB, Redis, auth). Flyway applies
   V70–V73 on startup; they create the software-center schema **and seed the
   initial state**:
   - policy revision 1 with the confirmed first-version defaults, pointed at by the
     singleton `software_policy_state` row;
   - the singleton `software_storage_account` quota row (`used_bytes`/`reserved_bytes`).
   No manual seeding is required on PostgreSQL. (Local H2 test runs bypass Flyway and
   use `.scratch/e2e-seed.sql` equivalents — that is test-only.)
2. Set the inspector token **only if this deployment runs the worker**:

   ```
   SKILLHUB_SOFTWARE_INSPECTOR_TOKEN=<deployment secret>
   ```

   Secret handling rules:
   - the value is a deployment secret; inject it via the secret manager, never on a
     command line that lands in shell history or CI logs, and never log it (the
     backend deliberately logs neither the configured nor the presented token);
   - an empty/unset value fails every internal inspector request closed — the
     software center keeps working for manual (ZIP/TAR) formats, but no auto
     inspection runs;
   - the token is valid **only** on `/api/v1/internal/software/**` and grants no
     user/admin identity; the worker holds no database or administrator credentials.

### 2.2 Upgrading a database from before the upstream merge

The upstream merge reserves V47–V68 for SkillHub migrations. AstronHub's plugin/MCP
migration moves from V66 to V69, and software-center migrations move from V47–V49
to V70–V72. Their SQL contents and checksums are unchanged. A fresh database needs
no special handling.

For an existing AstronHub database, back it up and stop all backend instances before
upgrading. Remap **only** history rows with the exact old AstronHub script names;
do not change upstream history rows or drop application tables. Run this transaction
against the application's database (adjust the schema if Flyway uses a custom one):

```sql
BEGIN;
LOCK TABLE flyway_schema_history IN EXCLUSIVE MODE;
DO $$
DECLARE
    migration RECORD;
BEGIN
    FOR migration IN
        SELECT * FROM (VALUES
            ('66', 'V66__astronhub_plugin_mcp_catalog.sql', '69', 'V69__astronhub_plugin_mcp_catalog.sql'),
            ('47', 'V47__software_center.sql', '70', 'V70__software_center.sql'),
            ('48', 'V48__software_center_transfer.sql', '71', 'V71__software_center_transfer.sql'),
            ('49', 'V49__software_center_inspection.sql', '72', 'V72__software_center_inspection.sql')
        ) AS mapping(old_version, old_script, new_version, new_script)
    LOOP
        IF EXISTS (SELECT 1 FROM flyway_schema_history
                   WHERE version = migration.old_version AND script = migration.old_script) THEN
            IF EXISTS (SELECT 1 FROM flyway_schema_history WHERE version = migration.new_version) THEN
                RAISE EXCEPTION 'Migration version % is already occupied', migration.new_version;
            END IF;
            UPDATE flyway_schema_history
               SET version = migration.new_version, script = migration.new_script
             WHERE version = migration.old_version AND script = migration.old_script;
        END IF;
    END LOOP;
END $$;
COMMIT;
```

Start one backend instance with `SPRING_FLYWAY_OUT_OF_ORDER=true` for this upgrade
so that upstream migrations missing below V72 are applied. Verify migration success
and application health, then remove this temporary setting before starting the other
instances. Keep the backup until the upgrade has been accepted.

### 2.3 Inspector worker

```bash
docker build -t skillhub-inspector ./inspector
docker run --rm \
  -e SKILLHUB_SOFTWARE_INSPECTOR_TOKEN=<same-as-backend> \
  -e SKILLHUB_BASE_URL=http://backend:8080 \
  --cpus=1 --memory=1g --read-only --tmpfs /tmp:size=2g \
  skillhub-inspector
```

- Scale: one task per worker is the default policy
  (`maxConcurrentInspectionsPerWorker`); run more containers for more parallelism.
- The toolchain inside the image: `msiinfo` (MSI), `dpkg-deb` (DEB), `rpm -qp`
  (RPM), stdlib PE parsing (EXE). ZIP/TAR.GZ/TAR.XZ are never auto-inspected.
- Auto-inspected formats: MSI/EXE/DEB/RPM only.

### 2.3 Storage selection

- `SKILLHUB_STORAGE_PROVIDER=local` — package objects under
  `STORAGE_BASE_PATH`; single-node deployments only.
- `SKILLHUB_STORAGE_PROVIDER=s3` — S3/MinIO via the existing `skillhub.storage.s3.*`
  keys (endpoint, bucket, access/secret key, force-path-style for MinIO).
- **Bucket prerequisite**: the software transfer storage does not create the bucket.
  Pre-create it before the first software upload:

  ```bash
  mc alias set skillhub-s3 https://<s3-endpoint> <access-key> <secret-key>
  mc mb skillhub-s3/skillhub
  ```

  (`SKILLHUB_STORAGE_S3_AUTO_CREATE_BUCKET=true` covers the skill-storage path only;
  do not rely on it for a software-center-only deployment.)
- S3 multipart rules already enforced by policy validation: `chunkSizeBytes` must be
  ≥ 5 MiB (`S3_MIN_PART_SIZE`) and ≤ `maxPackageBytes`.

### 2.4 Reverse proxy body limit (hard deployment constraint)

Upload chunks are `PUT /api/v1/admin/software/uploads/{id}/chunks/{n}` with body size
= the effective chunk size. The policy validator refuses to save a
`chunkSizeBytes` larger than the deployment limit

```
skillhub.software.limits.proxy-max-body-bytes   # default 104857600 (100 MiB)
```

so set this value to match (and stay below) the real reverse-proxy
`client_max_body_size` / LB body limit. If you raise the proxy limit, raise this
config too, otherwise the admin UI will keep rejecting larger chunk sizes.

## 3. Dynamic policy

- Read/update/history/rollback: `/api/v1/admin/software/settings` (revision-checked
  writes with `expectedRevision`; every change appends a new `software_policy_revision`
  row; rollback writes an old revision's values as a **new** revision — history is
  never rewritten).
- Validation happens server-side on every save (units are integer bytes / explicit
  minute/second fields): package cap ≥ 1 MiB and ≤ total quota; chunk size within
  [5 MiB, min(package cap, proxy limit)]; `maxActiveUploadsPerAdmin` in [1, 100]; the
  full rule set lives in `SoftwarePolicyValidator`.
- **Which tasks take effect when**: inspection tasks snapshot the policy at claim
  time (`policyRevision` on the task), so timeout/attempt changes affect tasks
  claimed after the change. Upload/size caps apply to new sessions immediately.
- No restart is needed after a policy change (backend or worker).

## 4. Operations manual

### 4.1 Monitoring

There is no push-based health feed; poll the admin APIs:

- `GET /api/v1/admin/software/inspections?status=PENDING` — backlog (`total`).
  A growing non-zero backlog with no LEASED activity means **no worker is
  claiming** (worker down, wrong token, or network).
- `GET /api/v1/admin/software/inspections?status=LEASED` — in-flight tasks. A row
  whose `leaseExpiresAt` is in the past for more than ~1 minute indicates a worker
  died mid-attempt: the reclaim pass (`SoftwareInspectionReclaimTask`, every 60 s)
  either makes it claimable again (budget left) or closes it `EXHAUSTED`
  (budget spent) so it becomes visible.
- Quota: the storage account singleton tracks `used_bytes` + `reserved_bytes`;
  uploads reserve first and confirm on completion, and bytes are released only
  after deletion is confirmed in storage.

### 4.2 Inspection task states and operator actions

| State | Meaning | Operator action |
|-------|---------|-----------------|
| `PENDING` | waiting for a worker | none if a worker runs; otherwise check worker/token |
| `LEASED` | a worker holds the attempt | wait; stale leases auto-recover (§4.1) |
| `SUCCEEDED` | metadata extracted | confirm the package (portal admin UI) |
| `BASIC_FAILED` | malformed package / limit violation | **cannot be confirmed away**; retry re-runs the same checks — if it persists, remove the package |
| `METADATA_FAILED` | basic checks passed, version extraction failed | `POST /api/v1/admin/software/inspections/{taskId}/confirm-metadata` with hand-filled `rawVersionFields` — this confirms and fills in one step |
| `EXHAUSTED` | auto-retry budget spent on infra failures | `POST .../inspections/{taskId}/retry` (max 3 manual retries) |

Confirmation gate (enforced server-side, mirrors the confirmed product rules):

- `BASIC_FAILED` → confirmation rejected, never waivable;
- `METADATA_FAILED` → confirmation rejected **unless** the manual fill-in travels
  with it (`confirm-metadata`);
- `PENDING`/`LEASED`/`EXHAUSTED` → confirmation rejected: required validation is
  incomplete and hand-typed metadata must not bypass it;
- `SUCCEEDED`, or no task at all (manual formats) → confirmation allowed;
- re-confirming an already-confirmed package stays allowed (human confirmation
  wins over extraction).

Attempt history is append-only and attempt numbers are monotonic per task; a
manual retry extends the auto-budget horizon instead of resetting the counter, so
`GET /api/v1/admin/software/inspections/{taskId}` shows the full evidence trail
(parser name/version, trigger AUTO/MANUAL, error codes, raw extracted fields).

### 4.3 Uploads, duplicates, and cleanup

- Sessions: create → PUT chunks (each with `X-Chunk-Sha256`) → complete. Progress
  is resumable: an interrupted session keeps accepted chunks until the idle timeout
  (`uploadIdleTimeoutMinutes`).
- Cancelling a session marks it `CANCELLED`; completing it afterwards fails.
- Identical bytes (same SHA-256) re-uploaded to the same application return
  `409 duplicateSha256` — that is the crash-recovery signal that the previous
  revision already exists; do not treat it as an error to retry blindly.
- Temp/failed upload remnants are cleaned by `SoftwareUploadCleanupTask` on the
  `cleanupScanIntervalMinutes` cadence, retaining failed temp data for
  `failedTempRetentionHours`.

### 4.4 Policy rollback

`POST /api/v1/admin/software/settings/rollback` with a historical `revision`
writes that revision's values as a new revision (see §3). Use it to undo a bad
tighten; the audit columns (`changed_by`, `change_reason`) record who/why.

## 5. Acceptance status (honest)

Per the phase-5 rule: **a single-storage sample success does not count as dual-storage
completion.** Verified locally vs. what still requires docker/MinIO staging:

### 5.1 Verified locally (this repository, no docker)

- Full backend suite: 869 tests green (`./mvnw -pl skillhub-app -am test`),
  including S3 multipart unit coverage (`S3SoftwareTransferStorageTest` against the
  S3 API model) and local transfer storage tests (merge, retransmit, Range, abort,
  cleanup, traversal).
- Happy-path E2E over real HTTP (test profile, LocalFileStorage): login → create
  app/release → chunked upload (256 B payload) → progress/resume view → complete →
  persistent inspection task → claim/content/heartbeat/result → confirm → publish →
  portal catalog/detail → download session → Range (206) + full download SHA-256 →
  policy read/validate/update/history/rollback.
- Fault/policy/load E2E: corrupted chunk digest and wrong chunk size rejected with
  the session still usable; cancel semantics; policy tighten (per-package cap,
  chunk bounds, per-admin active session cap) enforced server-side; inspector bad
  token 401; lease exclusivity between two workers; forged attempt token rejected;
  `BASIC_FAILED` blocks confirmation and manual retry recovers; `METADATA_FAILED`
  blocks confirmation until the manual fill-in; un-inspected (`PENDING`) packages
  cannot be confirmed; 6 parallel uploads with distinct payloads; parallel claim
  contention leases distinct tasks; 10/10 Range requests return 206/10 bytes; policy
  restore.
- Frontend: typecheck + lint + Vitest suite green (phase 4; no frontend changes in
  phase 5).

### 5.2 Verified against a real S3 server (native MinIO, no docker)

The dual-storage regression was executed locally against a **real MinIO server**
(`minio.exe` `RELEASE.2025-09-07T16-13-09Z`, the last tag published with binaries on
GitHub releases — dl.min.io now returns 410 Gone for community downloads; the same
version should be pinned for any repeat run). Backend booted with
`SKILLHUB_STORAGE_PROVIDER=s3` + path-style MinIO endpoint; the `skillhub` bucket was
pre-created with `mc` (see §2.3 — the software transfer storage does **not**
auto-create the bucket).

On the S3-backed backend, all three suites passed:

- happy-path E2E (`.scratch/e2e.sh`): multipart chunk PUT → `CompleteMultipartUpload`
  → inspector content streamed out of S3 → confirm/publish → Range (206) and full
  download with matching SHA-256, object verified present in the bucket via `mc ls`;
- fault/policy/load E2E (`.scratch/e2e-faults.sh`): all steps green against S3,
  including 6 parallel multipart uploads and 10/10 Range 206 responses;
- delete/quota regression (`.scratch/e2e-s3-delete.sh`): a cancelled session aborts
  its S3 multipart upload (`mc ls --incomplete` clean) and releases its reservation;
  used/reserved accounting rejects reservations beyond the quota; a duplicate-SHA-256
  completion returns 409 **and deletes the merged object from the bucket** (verified
  by object absence in `mc ls`) while the first upload's object remains; the account
  stays consistent afterwards.

Note: the global quota account is persistent state — the delete/quota suite needs a
fresh database for its arithmetic baseline.

### 5.3 Inspector worker loop — verified natively; parsing toolchain needs Linux

`python -m inspector.worker` (from `inspector/`, the same module the container runs)
was pointed at the S3-backed backend on Windows. The full worker loop verified live:
claim → stream package content from the backend → attempt parsing → report. MSI
parsing stopped where expected on Windows — no `msiinfo`/`rpm`/`dpkg-deb` — and the
worker reported an honest `INFRA_FAILED parser.toolMissing`; the task returned to
`PENDING` with retry backoff and both attempts are visible in the inspection audit
trail. The extraction toolchain itself (msitools/rpm in the container image) therefore
still requires the Linux container/staging for end-to-end metadata extraction.

### 5.4 Still requires staging (docker + PostgreSQL)

1. **Inspector container E2E**: boot the real `skillhub-inspector` image (msitools/
   rpm toolchain) against a staging backend and drive MSI/DEB/RPM/EXE and a broken
   package through the full task lifecycle to `SUCCEEDED`/`BASIC_FAILED`.
2. **PostgreSQL Flyway path**: apply V47–V49 to a real PostgreSQL instance
   (including the policy/account seeds) and run the E2E against it; local runs used
   H2 in PostgreSQL mode with an equivalent seed.
3. **Proxy constraint check**: verify Nginx `client_max_body_size` vs
   `chunkSizeBytes` in the containerized path (a chunk larger than the proxy limit
   must fail at the proxy, not corrupt a session).
4. **Staging-scale load**: larger parallel upload/inspection load than the local
   6-worker sample.
