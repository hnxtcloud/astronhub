-- Software center first version: standalone application catalog, release
-- identity, package revisions, recommendations, versioned runtime policy,
-- and the SOFTWARE_ADMIN role. Skill tables and lifecycle stay untouched.

CREATE TABLE software_category (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_by VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_software_category_code UNIQUE (code)
);

CREATE TABLE software_application (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    icon_object_key VARCHAR(512),
    category_code VARCHAR(64),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    download_count BIGINT NOT NULL DEFAULT 0,
    entity_version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(128),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_software_application_slug UNIQUE (slug),
    CONSTRAINT ck_software_application_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'TAKEN_DOWN'))
);

CREATE TABLE software_release (
    id BIGSERIAL PRIMARY KEY,
    application_id BIGINT NOT NULL,
    display_version VARCHAR(255) NOT NULL,
    release_notes TEXT,
    created_by VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_software_release_application FOREIGN KEY (application_id)
        REFERENCES software_application(id),
    CONSTRAINT uk_software_release_version UNIQUE (application_id, display_version)
);

CREATE TABLE software_package_revision (
    id BIGSERIAL PRIMARY KEY,
    release_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    revision_no INT NOT NULL,
    target_os VARCHAR(20) NOT NULL,
    target_arch VARCHAR(20) NOT NULL,
    target_distro VARCHAR(64),
    applicability_note VARCHAR(512),
    format VARCHAR(20) NOT NULL,
    original_full_version VARCHAR(255) NOT NULL,
    raw_version_fields JSONB,
    file_name VARCHAR(255) NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    byte_size BIGINT NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    confirmed_by VARCHAR(128),
    confirmed_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    entity_version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_software_package_release FOREIGN KEY (release_id)
        REFERENCES software_release(id),
    CONSTRAINT fk_software_package_application FOREIGN KEY (application_id)
        REFERENCES software_application(id),
    CONSTRAINT uk_software_package_app_sha256 UNIQUE (application_id, sha256),
    CONSTRAINT ck_software_package_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'TAKEN_DOWN'))
);

-- The display version/revision sequence is scoped per target including the
-- (nullable) distribution; functional indexes make NULL distros comparable.
CREATE UNIQUE INDEX uk_software_package_revision_scope
    ON software_package_revision (release_id, target_os, target_arch,
        COALESCE(target_distro, ''), original_full_version, revision_no);

CREATE TABLE software_recommendation (
    id BIGSERIAL PRIMARY KEY,
    release_id BIGINT NOT NULL,
    package_revision_id BIGINT NOT NULL,
    target_os VARCHAR(20) NOT NULL,
    target_arch VARCHAR(20) NOT NULL,
    target_distro VARCHAR(64),
    format VARCHAR(20) NOT NULL,
    note VARCHAR(512),
    created_by VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_software_recommendation_release FOREIGN KEY (release_id)
        REFERENCES software_release(id),
    CONSTRAINT fk_software_recommendation_package FOREIGN KEY (package_revision_id)
        REFERENCES software_package_revision(id)
);

CREATE UNIQUE INDEX uk_software_recommendation_scope
    ON software_recommendation (release_id, target_os, target_arch,
        COALESCE(target_distro, ''), format);
CREATE INDEX idx_software_recommendation_package ON software_recommendation(package_revision_id);

CREATE TABLE software_policy_revision (
    id BIGSERIAL PRIMARY KEY,
    revision BIGINT NOT NULL,
    schema_version INT NOT NULL,
    config JSONB NOT NULL,
    changed_by VARCHAR(128) NOT NULL,
    change_reason VARCHAR(512) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_software_policy_revision_number UNIQUE (revision)
);

CREATE TABLE software_policy_state (
    id BIGINT PRIMARY KEY,
    active_revision_id BIGINT NOT NULL,
    entity_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_software_policy_state_revision FOREIGN KEY (active_revision_id)
        REFERENCES software_policy_revision(id)
);

CREATE INDEX idx_software_application_catalog
    ON software_application(status, category_code, updated_at DESC);
CREATE INDEX idx_software_release_application ON software_release(application_id);
CREATE INDEX idx_software_package_release ON software_package_revision(release_id, status);
CREATE INDEX idx_software_package_application_sha ON software_package_revision(application_id, sha256);

-- SOFTWARE_ADMIN is an additive duty: grant/revoke must never touch the
-- grantee's other roles. Only SUPER_ADMIN may grant or revoke it.
INSERT INTO role (code, name, description, is_system) VALUES
('SOFTWARE_ADMIN', '软件管理员', '软件中心应用、安装包与发布治理', TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO permission (code, name, group_code) VALUES
('software:manage', '管理软件中心内容', 'software'),
('software:publish', '发布与下架软件包', 'software'),
('software:settings.read', '查看软件中心运行策略', 'software'),
('software:settings.write', '修改软件中心运行策略', 'software'),
('software:admin_grant', '授予或撤销软件管理员', 'software')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.code = 'SOFTWARE_ADMIN'
  AND p.code IN ('software:manage', 'software:publish', 'software:settings.read')
ON CONFLICT DO NOTHING;

-- Seed the authoritative policy revision with the confirmed first-version
-- defaults and point the singleton state row at it.
INSERT INTO software_policy_revision (revision, schema_version, config, changed_by, change_reason)
SELECT 1, 1,
    '{"schemaVersion":1,"maxPackageBytes":5368709120,"totalQuotaBytes":536870912000,"maxActiveUploadsPerAdmin":2,"chunkSizeBytes":8388608,"uploadIdleTimeoutMinutes":1440,"failedTempRetentionHours":168,"cleanupScanIntervalMinutes":5,"maxConcurrentInspectionsPerWorker":1,"inspectionTotalTimeoutSeconds":900,"inspectionSubprocessTimeoutSeconds":60,"inspectionSoftMemoryLimitBytes":536870912,"inspectionMaxExtractedBytes":21474836480,"inspectionMaxExtractedEntries":10000,"maxAutoInspectionAttempts":3,"downloadSessionIdleTimeoutMinutes":1440}'::jsonb,
    'system',
    'Initial software center policy (first-version defaults)'
WHERE NOT EXISTS (SELECT 1 FROM software_policy_revision WHERE revision = 1);

INSERT INTO software_policy_state (id, active_revision_id)
SELECT 1, id FROM software_policy_revision WHERE revision = 1
ON CONFLICT (id) DO NOTHING;
