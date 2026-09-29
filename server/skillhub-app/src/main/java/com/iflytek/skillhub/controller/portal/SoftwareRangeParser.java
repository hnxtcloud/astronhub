package com.iflytek.skillhub.controller.portal;

/**
 * Parser for a single-byte {@code Range} header against a known object size.
 * Per the download spec: multi-range and malformed headers are ignored (the
 * response becomes a plain 200), only genuinely unsatisfiable single ranges
 * produce 416. Never returns an unbounded range.
 */
final class SoftwareRangeParser {

    private SoftwareRangeParser() {
    }

    interface RangeResult {
        /** No usable Range header: serve the whole object with 200. */
        record Ignored() implements RangeResult {
        }

        /** Single inclusive byte range, end clamped to size-1. */
        record Single(long start, long end) implements RangeResult {
        }

        /** start >= size (or suffix 0): respond 416 with {@code bytes *}{@code /size}. */
        record Unsatisfiable() implements RangeResult {
        }
    }

    static RangeResult parse(String header, long size) {
        if (header == null || !header.startsWith("bytes=")) {
            return new RangeResult.Ignored();
        }
        String spec = header.substring("bytes=".length()).trim();
        if (spec.contains(",") || spec.isEmpty()) {
            // Multi-range requests are intentionally degraded to a full 200
            // rather than a wrongly stitched 206.
            return new RangeResult.Ignored();
        }
        int dash = spec.indexOf('-');
        if (dash < 0) {
            return new RangeResult.Ignored();
        }
        String startPart = spec.substring(0, dash).trim();
        String endPart = spec.substring(dash + 1).trim();
        try {
            if (startPart.isEmpty()) {
                // suffix form: last N bytes
                long suffix = Long.parseLong(endPart);
                if (suffix <= 0 || size == 0) {
                    return size == 0 ? new RangeResult.Ignored() : new RangeResult.Unsatisfiable();
                }
                long start = Math.max(0, size - suffix);
                return new RangeResult.Single(start, size - 1);
            }
            long start = Long.parseLong(startPart);
            if (start < 0 || start >= size) {
                return new RangeResult.Unsatisfiable();
            }
            long end = endPart.isEmpty() ? size - 1 : Long.parseLong(endPart);
            if (end < start) {
                return new RangeResult.Ignored();
            }
            return new RangeResult.Single(start, Math.min(end, size - 1));
        } catch (NumberFormatException malformed) {
            return new RangeResult.Ignored();
        }
    }
}
