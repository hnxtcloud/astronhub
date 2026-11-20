-- MCP entry revision review decisions (docs/25 MCP Catalog: separate review records, no gateway state).

CREATE TABLE mcp_review_decision (
    id BIGSERIAL PRIMARY KEY,
    revision_id BIGINT NOT NULL,
    reviewer_id VARCHAR(128) NOT NULL,
    decision VARCHAR(32) NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mcp_review_decision_revision
        FOREIGN KEY (revision_id) REFERENCES mcp_entry_revision(id) ON DELETE CASCADE
);

CREATE INDEX idx_mcp_review_decision_revision ON mcp_review_decision(revision_id);
