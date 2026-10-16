-- V77 was reserved for GitLab-compatible software update reads (ticket 08).
-- Those reads reuse the V75 update-release schema and need no additional DDL.
-- Keep the reservation to preserve version continuity and all later migration checksums.
SELECT 1;
