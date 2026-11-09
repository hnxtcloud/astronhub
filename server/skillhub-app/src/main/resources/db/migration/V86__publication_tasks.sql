CREATE TABLE publication_task (
    id VARCHAR(36) PRIMARY KEY,
    owner_id VARCHAR(128) NOT NULL,
    status VARCHAR(24) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    entity_version BIGINT NOT NULL DEFAULT 0,
    draft_json TEXT NOT NULL,
    progress_json TEXT NOT NULL,
    material_key VARCHAR(512),
    file_name VARCHAR(255),
    byte_size BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_publication_task_owner_updated ON publication_task(owner_id, updated_at DESC);
