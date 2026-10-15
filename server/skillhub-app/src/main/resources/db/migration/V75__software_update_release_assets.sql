-- Update snapshots deliberately do not backfill publication from catalog releases.
CREATE TABLE software_update_release (
    id BIGSERIAL PRIMARY KEY,
    application_id BIGINT NOT NULL REFERENCES software_application(id),
    catalog_release_id BIGINT NOT NULL REFERENCES software_release(id),
    machine_version VARCHAR(255) NOT NULL,
    tag VARCHAR(255) NOT NULL,
    target_os VARCHAR(20) NOT NULL,
    target_arch VARCHAR(20) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    variant VARCHAR(64) NOT NULL,
    protocol VARCHAR(20) NOT NULL,
    pi_version VARCHAR(255),
    release_notes TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    published_by VARCHAR(128),
    entity_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_software_update_identity UNIQUE (application_id, tag, target_os, target_arch, channel, variant),
    CONSTRAINT uk_software_update_machine_identity UNIQUE (application_id, machine_version, target_os, target_arch, channel, variant),
    CONSTRAINT ck_software_update_status CHECK (status IN ('DRAFT','PUBLISHED','WITHDRAWN')),
    CONSTRAINT ck_software_update_channel CHECK (channel IN ('stable','preview')),
    CONSTRAINT ck_software_update_protocol CHECK (protocol IN ('ELECTRON','BAIZE'))
);
CREATE INDEX idx_software_update_catalog ON software_update_release(catalog_release_id);
CREATE INDEX idx_software_update_discovery ON software_update_release(application_id,status,published_at DESC);

CREATE TABLE software_release_asset (
    id BIGSERIAL PRIMARY KEY,
    release_id BIGINT NOT NULL REFERENCES software_update_release(id),
    relative_path VARCHAR(512) NOT NULL,
    role VARCHAR(20) NOT NULL,
    package_revision_id BIGINT REFERENCES software_package_revision(id),
    object_key VARCHAR(512) NOT NULL,
    byte_size BIGINT NOT NULL CHECK (byte_size > 0),
    sha256 VARCHAR(64) NOT NULL,
    sha512_base64 VARCHAR(88) NOT NULL,
    CONSTRAINT uk_software_release_asset_path UNIQUE(release_id,relative_path),
    CONSTRAINT ck_software_release_asset_role CHECK (role IN ('INSTALLER','METADATA','BLOCKMAP','CHECKSUM','SIGNATURE','OTHER')),
    CONSTRAINT ck_software_release_asset_installer CHECK ((role = 'INSTALLER') = (package_revision_id IS NOT NULL))
);
CREATE INDEX idx_software_release_asset_package ON software_release_asset(package_revision_id);

CREATE TABLE software_asset_upload (
    id BIGSERIAL PRIMARY KEY,
    release_id BIGINT NOT NULL REFERENCES software_update_release(id),
    owner_user_id VARCHAR(128) NOT NULL,
    relative_path VARCHAR(512) NOT NULL,
    role VARCHAR(20) NOT NULL,
    object_key VARCHAR(512) NOT NULL UNIQUE,
    storage_session_ref VARCHAR(512),
    reserved_bytes BIGINT NOT NULL CHECK (reserved_bytes > 0),
    state VARCHAR(20) NOT NULL DEFAULT 'RESERVED',
    expires_at TIMESTAMPTZ NOT NULL,
    entity_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_software_asset_upload_state CHECK (state IN ('RESERVED','COMPLETED','CLEANUP','DELETE_USED','CLEANED'))
);
CREATE INDEX idx_software_asset_upload_cleanup ON software_asset_upload(state,expires_at);

CREATE TABLE software_update_source (
    application_id BIGINT PRIMARY KEY REFERENCES software_application(id),
    anonymous_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    entity_version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE software_update_channel (
    id BIGSERIAL PRIMARY KEY,
    application_id BIGINT NOT NULL REFERENCES software_application(id),
    slug VARCHAR(64) NOT NULL,
    virtual_project_path VARCHAR(512) NOT NULL UNIQUE,
    target_os VARCHAR(20) NOT NULL,
    target_arch VARCHAR(20) NOT NULL,
    channel VARCHAR(20) NOT NULL CHECK (channel IN ('stable','preview')),
    variant VARCHAR(64) NOT NULL,
    target_release_id BIGINT REFERENCES software_update_release(id),
    entity_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_software_update_channel_slug UNIQUE(application_id,slug)
);
