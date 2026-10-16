-- V81 was reserved for Generic and Baize native update feeds (ticket 09).
-- Those projections reuse the existing update-release schema and need no additional DDL.
-- Keep the reservation to preserve version continuity and all later migration checksums.
SELECT 1;
