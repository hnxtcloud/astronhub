-- Persist the timestamp already required by SoftwareDownloadSession.
ALTER TABLE software_download_session
    ADD COLUMN updated_at TIMESTAMPTZ;

UPDATE software_download_session
SET updated_at = last_activity_at;

ALTER TABLE software_download_session
    ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE software_download_session
    ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;
