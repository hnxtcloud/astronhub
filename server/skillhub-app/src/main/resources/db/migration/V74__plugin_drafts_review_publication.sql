-- Plugin lifecycle, static artifact evidence and durable, independently executed publication.
ALTER TABLE plugin_project ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE plugin_release ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE plugin_distribution ADD COLUMN native_name VARCHAR(200);
ALTER TABLE plugin_distribution ADD COLUMN native_version VARCHAR(64);
ALTER TABLE plugin_distribution ADD COLUMN inventory_json TEXT;
ALTER TABLE plugin_distribution ADD COLUMN manifest_json TEXT;

CREATE TABLE plugin_review_decision (
    id BIGSERIAL PRIMARY KEY,
    release_id BIGINT NOT NULL REFERENCES plugin_release(id),
    reviewer_id VARCHAR(128) NOT NULL REFERENCES user_account(id),
    decision VARCHAR(32) NOT NULL,
    notes TEXT NOT NULL,
    runtime_test_result VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    test_environment TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_plugin_review_release ON plugin_review_decision(release_id, created_at DESC);
CREATE TABLE plugin_publication_task (
    id BIGSERIAL PRIMARY KEY,
    release_id BIGINT NOT NULL UNIQUE REFERENCES plugin_release(id),
    approved_by VARCHAR(128) NOT NULL REFERENCES user_account(id),
    required_channels VARCHAR(200) NOT NULL,
    completed_channels VARCHAR(200) NOT NULL DEFAULT '',
    status VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
    content_snapshot TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    last_error VARCHAR(200),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    row_version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_plugin_publication_pending ON plugin_publication_task(status, completed_channels, created_at);
