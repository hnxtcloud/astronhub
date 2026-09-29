-- Software center transfer phase: chunked upload sessions, chunk manifest,
-- download sessions, and the singleton capacity account. Skill tables and
-- lifecycle stay untouched.

CREATE TABLE software_upload_session (
    id BIGSERIAL PRIMARY KEY,
    release_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    owner_user_id VARCHAR(128) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    format VARCHAR(20) NOT NULL,
    target_os VARCHAR(20) NOT NULL,
    target_arch VARCHAR(20) NOT NULL,
    target_distro VARCHAR(64),
    applicability_note VARCHAR(512),
    original_full_version VARCHAR(255) NOT NULL,
    expected_byte_size BIGINT NOT NULL,
    chunk_size_bytes BIGINT NOT NULL,
    policy_revision BIGINT NOT NULL,
    upload_idle_timeout_minutes INT NOT NULL,
    reserved_bytes BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    storage_session_ref VARCHAR(256),
    object_key VARCHAR(512),
    completed_revision_id BIGINT,
    last_activity_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    entity_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_software_upload_release FOREIGN KEY (release_id)
        REFERENCES software_release(id),
    CONSTRAINT fk_software_upload_application FOREIGN KEY (application_id)
        REFERENCES software_application(id),
    CONSTRAINT ck_software_upload_status
        CHECK (status IN ('ACTIVE', 'COMPLETING', 'COMPLETED', 'CANCELLED', 'EXPIRED'))
);

CREATE INDEX idx_software_upload_owner_active
    ON software_upload_session(owner_user_id, status);
CREATE INDEX idx_software_upload_expiration
    ON software_upload_session(status, last_activity_at);

CREATE TABLE software_upload_chunk (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    chunk_no INT NOT NULL,
    byte_size BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_software_upload_chunk_session FOREIGN KEY (session_id)
        REFERENCES software_upload_session(id) ON DELETE CASCADE,
    CONSTRAINT uk_software_upload_chunk UNIQUE (session_id, chunk_no)
);

CREATE TABLE software_download_session (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    package_revision_id BIGINT NOT NULL,
    counted BOOLEAN NOT NULL DEFAULT TRUE,
    idle_timeout_minutes INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_activity_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_software_download_package FOREIGN KEY (package_revision_id)
        REFERENCES software_package_revision(id)
);

CREATE INDEX idx_software_download_package ON software_download_session(package_revision_id);
CREATE INDEX idx_software_download_activity ON software_download_session(user_id, last_activity_at);

-- Singleton capacity account: used counts confirmed package bytes, reserved
-- counts pending upload sessions. Concurrent session creation serializes on
-- this row (pessimistic lock) so quota checks never overcommit.
CREATE TABLE software_storage_account (
    id BIGINT PRIMARY KEY,
    used_bytes BIGINT NOT NULL DEFAULT 0,
    reserved_bytes BIGINT NOT NULL DEFAULT 0,
    entity_version BIGINT NOT NULL DEFAULT 0
);

INSERT INTO software_storage_account (id) SELECT 1
WHERE NOT EXISTS (SELECT 1 FROM software_storage_account WHERE id = 1);

-- Existing package bytes count as used storage from now on.
UPDATE software_storage_account account
SET used_bytes = COALESCE((
    SELECT SUM(p.byte_size) FROM software_package_revision p
    WHERE p.application_id IS NOT NULL
), 0)
WHERE account.id = 1;
