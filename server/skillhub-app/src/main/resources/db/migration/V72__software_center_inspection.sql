-- Software center: persistent static-inspection tasks and per-attempt records.
-- Workers claim tasks through the restricted internal API; every attempt is
-- recorded for audit. Policy limits are snapshotted at claim time per task.

CREATE TABLE software_inspection_task (
    id BIGSERIAL PRIMARY KEY,
    package_revision_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL,
    attempt_token VARCHAR(64),
    lease_owner VARCHAR(128),
    lease_expires_at TIMESTAMPTZ,
    next_attempt_not_before TIMESTAMPTZ,
    manual_retry_count INT NOT NULL DEFAULT 0,
    -- Policy snapshot refreshed at every claim so a running attempt is always
    -- judged against the currently active dynamic policy.
    policy_revision BIGINT NOT NULL,
    total_timeout_seconds BIGINT NOT NULL,
    subprocess_timeout_seconds BIGINT NOT NULL,
    soft_memory_limit_bytes BIGINT NOT NULL,
    max_extracted_bytes BIGINT NOT NULL,
    max_extracted_entries INT NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    format VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    entity_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_software_inspection_revision UNIQUE (package_revision_id),
    CONSTRAINT ck_software_inspection_status CHECK (status IN
        ('PENDING', 'LEASED', 'SUCCEEDED', 'BASIC_FAILED', 'METADATA_FAILED', 'EXHAUSTED'))
);

CREATE INDEX idx_software_inspection_claim
    ON software_inspection_task (status, next_attempt_not_before, lease_expires_at);

CREATE TABLE software_inspection_attempt (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    attempt_no INT NOT NULL,
    trigger_type VARCHAR(10) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    error_code VARCHAR(128),
    raw_version_fields JSONB,
    parser_name VARCHAR(64),
    parser_version VARCHAR(64),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_software_inspection_attempt_task
        FOREIGN KEY (task_id) REFERENCES software_inspection_task(id) ON DELETE CASCADE,
    CONSTRAINT uk_software_inspection_attempt UNIQUE (task_id, attempt_no),
    CONSTRAINT ck_software_inspection_outcome CHECK (outcome IN
        ('SUCCEEDED', 'BASIC_FAILED', 'METADATA_FAILED', 'INFRA_FAILED'))
);

CREATE INDEX idx_software_inspection_attempt_task
    ON software_inspection_attempt (task_id, attempt_no);
