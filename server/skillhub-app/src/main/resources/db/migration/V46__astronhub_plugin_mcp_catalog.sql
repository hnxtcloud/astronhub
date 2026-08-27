-- AstronHub capability catalogs. Skill tables and lifecycle remain unchanged.

CREATE TABLE plugin_project (
    id BIGSERIAL PRIMARY KEY,
    namespace_id BIGINT NOT NULL,
    slug VARCHAR(100) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    summary TEXT,
    owner_id VARCHAR(128) NOT NULL,
    visibility VARCHAR(32) NOT NULL DEFAULT 'PUBLIC',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    hidden BOOLEAN NOT NULL DEFAULT FALSE,
    latest_release_id BIGINT,
    view_count BIGINT NOT NULL DEFAULT 0,
    download_count BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(128),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_plugin_project_namespace FOREIGN KEY (namespace_id) REFERENCES namespace(id),
    CONSTRAINT fk_plugin_project_owner FOREIGN KEY (owner_id) REFERENCES user_account(id),
    UNIQUE(namespace_id, slug)
);

CREATE TABLE plugin_release (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    version VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    changelog TEXT,
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    CONSTRAINT fk_plugin_release_project FOREIGN KEY (project_id) REFERENCES plugin_project(id) ON DELETE CASCADE,
    UNIQUE(project_id, version)
);

ALTER TABLE plugin_project
    ADD CONSTRAINT fk_plugin_project_latest_release
    FOREIGN KEY (latest_release_id) REFERENCES plugin_release(id);

CREATE TABLE plugin_distribution (
    id BIGSERIAL PRIMARY KEY,
    release_id BIGINT NOT NULL,
    runtime_key VARCHAR(64) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_locator TEXT NOT NULL,
    integrity_digest VARCHAR(256),
    availability VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    compatibility_evidence VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    security_assessment VARCHAR(32) NOT NULL DEFAULT 'NOT_SCANNED',
    installation_recipe TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_plugin_distribution_release FOREIGN KEY (release_id) REFERENCES plugin_release(id) ON DELETE CASCADE,
    UNIQUE(release_id, runtime_key)
);

CREATE INDEX idx_plugin_project_catalog
    ON plugin_project(status, hidden, visibility, updated_at DESC);
CREATE INDEX idx_plugin_project_namespace ON plugin_project(namespace_id);
CREATE INDEX idx_plugin_release_project_status ON plugin_release(project_id, status);

CREATE TABLE mcp_server_entry (
    id BIGSERIAL PRIMARY KEY,
    namespace_id BIGINT NOT NULL,
    slug VARCHAR(100) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    summary TEXT,
    owner_id VARCHAR(128) NOT NULL,
    visibility VARCHAR(32) NOT NULL DEFAULT 'PUBLIC',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    hidden BOOLEAN NOT NULL DEFAULT FALSE,
    latest_revision_id BIGINT,
    view_count BIGINT NOT NULL DEFAULT 0,
    profile_copy_count BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(128),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mcp_entry_namespace FOREIGN KEY (namespace_id) REFERENCES namespace(id),
    CONSTRAINT fk_mcp_entry_owner FOREIGN KEY (owner_id) REFERENCES user_account(id),
    UNIQUE(namespace_id, slug)
);

CREATE TABLE mcp_entry_revision (
    id BIGSERIAL PRIMARY KEY,
    entry_id BIGINT NOT NULL,
    revision_number INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    description TEXT,
    source_kind VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    source_snapshot TEXT,
    local_curation TEXT,
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    CONSTRAINT fk_mcp_revision_entry FOREIGN KEY (entry_id) REFERENCES mcp_server_entry(id) ON DELETE CASCADE,
    UNIQUE(entry_id, revision_number)
);

ALTER TABLE mcp_server_entry
    ADD CONSTRAINT fk_mcp_entry_latest_revision
    FOREIGN KEY (latest_revision_id) REFERENCES mcp_entry_revision(id);

CREATE TABLE mcp_connection_profile (
    id BIGSERIAL PRIMARY KEY,
    revision_id BIGINT NOT NULL,
    profile_key VARCHAR(100) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    transport_type VARCHAR(32) NOT NULL,
    endpoint_template TEXT,
    command_template TEXT,
    arguments_json TEXT,
    variables_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mcp_profile_revision FOREIGN KEY (revision_id) REFERENCES mcp_entry_revision(id) ON DELETE CASCADE,
    UNIQUE(revision_id, profile_key)
);

CREATE INDEX idx_mcp_entry_catalog
    ON mcp_server_entry(status, hidden, visibility, updated_at DESC);
CREATE INDEX idx_mcp_entry_namespace ON mcp_server_entry(namespace_id);
CREATE INDEX idx_mcp_revision_entry_status ON mcp_entry_revision(entry_id, status);

INSERT INTO role (code, name, description, is_system) VALUES
('PLUGIN_ADMIN', '插件管理员', '插件目录审核与治理', TRUE),
('MCP_ADMIN', 'MCP 管理员', 'MCP 目录审核与治理', TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO permission (code, name, group_code) VALUES
('plugin:publish', '发布插件', 'plugin'),
('plugin:manage', '管理插件', 'plugin'),
('plugin:review', '审核插件', 'plugin'),
('mcp:publish', '发布 MCP 条目', 'mcp'),
('mcp:manage', '管理 MCP 条目', 'mcp'),
('mcp:review', '审核 MCP 条目', 'mcp')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.code = 'PLUGIN_ADMIN' AND p.code IN ('plugin:publish', 'plugin:manage', 'plugin:review')
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.code = 'MCP_ADMIN' AND p.code IN ('mcp:publish', 'mcp:manage', 'mcp:review')
ON CONFLICT DO NOTHING;
