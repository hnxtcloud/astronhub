ALTER TABLE plugin_publication_channel ADD COLUMN active_snapshot TEXT;
ALTER TABLE plugin_publication_channel ADD COLUMN plan_revision INTEGER NOT NULL DEFAULT 0;
ALTER TABLE plugin_publication_channel ADD COLUMN next_attempt_at TIMESTAMPTZ;
ALTER TABLE plugin_publication_channel ADD COLUMN last_claimed_at TIMESTAMPTZ;
ALTER TABLE plugin_publication_channel ADD COLUMN source_disabled BOOLEAN NOT NULL DEFAULT false;
CREATE INDEX idx_plugin_channel_recovery ON plugin_publication_channel(state,next_attempt_at,lease_until);
