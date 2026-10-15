ALTER TABLE plugin_publication_channel ADD COLUMN active_snapshot TEXT;
ALTER TABLE plugin_publication_channel ADD COLUMN plan_revision INTEGER NOT NULL DEFAULT 0;
ALTER TABLE plugin_publication_channel ADD COLUMN next_attempt_at TIMESTAMPTZ;
ALTER TABLE plugin_publication_channel ADD COLUMN last_claimed_at TIMESTAMPTZ;
ALTER TABLE plugin_publication_channel ADD COLUMN source_disabled BOOLEAN NOT NULL DEFAULT false;
CREATE INDEX idx_plugin_channel_recovery ON plugin_publication_channel(state,next_attempt_at,lease_until);
CREATE TABLE plugin_channel_plan_revision (
 id BIGSERIAL PRIMARY KEY,channel_id BIGINT NOT NULL REFERENCES plugin_publication_channel(id),revision INTEGER NOT NULL,
 snapshot TEXT NOT NULL,requested_by VARCHAR(128) REFERENCES user_account(id),reason TEXT NOT NULL,created_at TIMESTAMPTZ NOT NULL,
 UNIQUE(channel_id,revision)
);
CREATE TABLE plugin_channel_attempt (
 id BIGSERIAL PRIMARY KEY,channel_id BIGINT NOT NULL REFERENCES plugin_publication_channel(id),plan_revision INTEGER NOT NULL,
 attempt_token VARCHAR(36) NOT NULL UNIQUE,action VARCHAR(24) NOT NULL,outcome VARCHAR(24) NOT NULL,
 started_at TIMESTAMPTZ NOT NULL,heartbeat_at TIMESTAMPTZ NOT NULL,lease_until TIMESTAMPTZ NOT NULL,finished_at TIMESTAMPTZ,
 receipt TEXT,error_code VARCHAR(80),late_receipt TEXT,row_version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_plugin_attempt_history ON plugin_channel_attempt(channel_id,id DESC);
-- Old failures lack durable no-effect evidence; require original-target reconciliation first.
UPDATE plugin_publication_channel SET state='UNKNOWN' WHERE state='FAILED' AND attempt_count>0;
CREATE TABLE plugin_channel_governance_operation (
 id BIGSERIAL PRIMARY KEY,channel_id BIGINT NOT NULL REFERENCES plugin_publication_channel(id),release_id BIGINT NOT NULL REFERENCES plugin_release(id),task_id BIGINT NOT NULL REFERENCES plugin_publication_task(id),plan_revision INTEGER NOT NULL,
 channel VARCHAR(16) NOT NULL,action VARCHAR(24) NOT NULL,snapshot TEXT NOT NULL,source_receipt TEXT,requested_by VARCHAR(128) NOT NULL REFERENCES user_account(id),reason TEXT NOT NULL,preview_digest VARCHAR(64) NOT NULL,
 state VARCHAR(24) NOT NULL,attempt_token VARCHAR(36),lease_until TIMESTAMPTZ,next_attempt_at TIMESTAMPTZ,attempt_count INTEGER NOT NULL DEFAULT 0,created_at TIMESTAMPTZ NOT NULL,receipt TEXT,last_error VARCHAR(80),row_version BIGINT NOT NULL DEFAULT 0,UNIQUE(channel_id,preview_digest)
);
ALTER TABLE plugin_channel_attempt ADD COLUMN operation_id BIGINT REFERENCES plugin_channel_governance_operation(id);
CREATE INDEX idx_plugin_governance_queue ON plugin_channel_governance_operation(state,next_attempt_at,lease_until);
