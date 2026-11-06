-- This migration is the durable cutover. Stop old server instances before applying it.
-- New rows never inherit permission to finish legacy work.
ALTER TABLE skill_suite_version
    ADD COLUMN retirement_completion_allowed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE skill_suite_bundle_operation
    ADD COLUMN retirement_completion_allowed BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE skill_suite_bundle_operation
SET retirement_completion_allowed = TRUE
WHERE status IN ('RUNNING', 'WAITING_FOR_MEMBERS', 'BLOCKED_RETRYABLE', 'SUITE_DRAFT_CREATED');

UPDATE skill_suite_version
SET retirement_completion_allowed = TRUE
WHERE status = 'PENDING_REVIEW'
   OR (status = 'DRAFT' AND id IN (
       SELECT result_suite_version_id FROM skill_suite_bundle_operation
       WHERE status = 'SUITE_DRAFT_CREATED' AND retirement_completion_allowed = TRUE
   ));
