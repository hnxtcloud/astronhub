CREATE TABLE plugin_import_settings (id BIGINT PRIMARY KEY, limits_json TEXT NOT NULL);
CREATE TABLE plugin_import_batch (
 id VARCHAR(36) PRIMARY KEY,
 namespace_id BIGINT NOT NULL REFERENCES namespace(id),
 owner_id VARCHAR(128) NOT NULL REFERENCES user_account(id),
 repository BOOLEAN NOT NULL,
 preview_json TEXT NOT NULL,
 limits_json TEXT NOT NULL,
 expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_plugin_import_expiry ON plugin_import_batch(expires_at);
