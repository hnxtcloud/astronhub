-- Raw secrets remain exclusively in api_token as prefix + hash.
CREATE TABLE software_publisher_grant (
    token_id BIGINT PRIMARY KEY REFERENCES api_token(id) ON DELETE CASCADE,
    application_id BIGINT NOT NULL REFERENCES software_application(id) ON DELETE CASCADE,
    owner_user_id VARCHAR(128) NOT NULL,
    granted_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ
);
CREATE INDEX idx_software_publisher_application ON software_publisher_grant(application_id, created_at DESC);
