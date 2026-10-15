CREATE TABLE plugin_publication_channel (
 id BIGSERIAL PRIMARY KEY, task_id BIGINT NOT NULL REFERENCES plugin_publication_task(id), channel VARCHAR(16) NOT NULL,
 snapshot TEXT NOT NULL, state VARCHAR(32) NOT NULL, attempt_token VARCHAR(36), lease_until TIMESTAMPTZ,
 attempt_count INTEGER NOT NULL DEFAULT 0, last_error VARCHAR(80), receipt TEXT, row_version BIGINT NOT NULL DEFAULT 0,
 UNIQUE(task_id,channel)
);
CREATE TABLE plugin_npm_registry (
 id BIGSERIAL PRIMARY KEY,name VARCHAR(120) NOT NULL,registry VARCHAR(2000) NOT NULL,credential_ref VARCHAR(120) NOT NULL,
 default_registry BOOLEAN NOT NULL,created_by VARCHAR(128) NOT NULL REFERENCES user_account(id),created_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX uk_plugin_npm_default ON plugin_npm_registry(default_registry) WHERE default_registry=true;
CREATE TABLE plugin_npm_selection(release_id BIGINT PRIMARY KEY REFERENCES plugin_release(id),registry_id BIGINT NOT NULL REFERENCES plugin_npm_registry(id));
CREATE TABLE plugin_npm_dependency_upload(
 id BIGSERIAL PRIMARY KEY,uploaded_by VARCHAR(128) NOT NULL REFERENCES user_account(id),source VARCHAR(2000) NOT NULL,
 name VARCHAR(200) NOT NULL,version VARCHAR(64) NOT NULL,sha256 VARCHAR(64) NOT NULL,object_key VARCHAR(1000) NOT NULL,
 manifest_json TEXT NOT NULL,snapshot TEXT NOT NULL,state VARCHAR(32) NOT NULL,attempt_count INTEGER NOT NULL DEFAULT 0,
 last_error VARCHAR(80),receipt TEXT,attempt_token VARCHAR(36),lease_until TIMESTAMPTZ,created_at TIMESTAMPTZ NOT NULL,row_version BIGINT NOT NULL DEFAULT 0
);
