package com.iflytek.skillhub.controller.support;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.repository.SoftwareUpdateReadQueryRepository.AssetTarget;
import com.iflytek.skillhub.storage.software.SoftwareTransferStorage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Bounded binary HTTP transport; callers must resolve the fully governed target before entering. */
@Component
public class SoftwareUpdateAssetResponder {
    private static final int MAX_RANGES = 16;
    private static final long MAX_MULTI_BYTES = 32L * 1024 * 1024;
    private final SoftwareTransferStorage storage;
    private final AuditLogService audit;
    public SoftwareUpdateAssetResponder(SoftwareTransferStorage storage, AuditLogService audit) { this.storage = storage; this.audit = audit; }
    public void respond(AssetTarget target, HttpServletRequest request, HttpServletResponse response) throws IOException {
        respond(target, request, response, null, null);
    }
    public void respondProjection(AssetTarget target, byte[] projection, String mediaType, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        respond(target, request, response, projection, mediaType);
    }
    private void respond(AssetTarget target, HttpServletRequest request, HttpServletResponse response,
            byte[] projection, String projectionMediaType) throws IOException {
        var asset = target.asset();
        try {
            if (storage.objectSize(asset.getObjectKey()) != asset.getByteSize()) throw missing();
        } catch (com.iflytek.skillhub.storage.StorageAccessException unavailable) { throw missing(); }
        String digest = asset.getSha256();
        if (projection != null) {
            try { digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(projection)); }
            catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        }
        long size = projection == null ? asset.getByteSize() : projection.length;
        String etag = "\"" + digest + "\"";
        Instant modified = target.release().getPublishedAt().truncatedTo(ChronoUnit.SECONDS);
        String mime = projection == null ? mime(asset.getRelativePath()) : projectionMediaType;
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("ETag", etag);
        if (projection == null)
            response.setHeader("Last-Modified", DateTimeFormatter.RFC_1123_DATE_TIME.format(modified.atZone(ZoneOffset.UTC)));
        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(
                asset.getRelativePath().substring(asset.getRelativePath().lastIndexOf('/') + 1), StandardCharsets.UTF_8).replace("+", "%20"));
        String ua = request.getHeader("User-Agent");
        audit.record(null, "software.update.asset.read", "SOFTWARE_UPDATE_ASSET", asset.getId(), null,
                request.getRemoteAddr(), ua == null ? null : ua.substring(0, Math.min(512, ua.length())), null);
        String noneMatch = request.getHeader("If-None-Match");
        if (noneMatch != null && java.util.Arrays.stream(noneMatch.split(",")).map(String::trim)
                .anyMatch(value -> value.equals("*") || value.equals(etag) || value.equals("W/" + etag))) {
            response.setStatus(304); return;
        }
        if (projection == null && noneMatch == null && dateAtLeast(request.getHeader("If-Modified-Since"), modified)) { response.setStatus(304); return; }
        String range = request.getHeader("Range");
        String ifRange = request.getHeader("If-Range");
        if (ifRange != null && !ifRange.equals(etag) && (projection != null || !dateAtLeast(ifRange, modified))) range = null;
        List<Interval> intervals = ranges(range, size);
        boolean head = "HEAD".equals(request.getMethod());
        if (intervals != null && intervals.isEmpty()) {
            response.setStatus(416); response.setHeader("Content-Range", "bytes */" + size);
            response.setHeader("Content-Length", "0"); return;
        }
        if (intervals == null) {
            response.setContentType(mime); response.setStatus(200);
            response.setHeader("Content-Length", Long.toString(size));
            if (!head) copy(target, 0, size, response, projection);
        } else if (intervals.size() == 1) {
            var item = intervals.get(0); response.setStatus(206); response.setContentType(mime);
            response.setHeader("Content-Range", item.header(size));
            response.setHeader("Content-Length", Long.toString(item.length()));
            if (!head) copy(target, item.start(), item.length(), response, projection);
        } else {
            String boundary = "astronhub-" + java.util.UUID.randomUUID();
            List<byte[]> headers = intervals.stream().map(item -> ("--" + boundary + "\r\nContent-Type: " + mime
                    + "\r\nContent-Range: " + item.header(size) + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII)).toList();
            byte[] end = ("--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII);
            long length = end.length;
            for (int i = 0; i < intervals.size(); i++) length += headers.get(i).length + intervals.get(i).length() + 2;
            response.setStatus(206); response.setContentType("multipart/byteranges; boundary=" + boundary);
            response.setHeader("Content-Length", Long.toString(length));
            if (!head) {
                var output = response.getOutputStream();
                for (int i = 0; i < intervals.size(); i++) {
                    output.write(headers.get(i)); var item = intervals.get(i);
                    copy(target, item.start(), item.length(), response, projection); output.write(new byte[] {13, 10});
                }
                output.write(end);
            }
        }
    }
    private void copy(AssetTarget target, long offset, long length, HttpServletResponse response, byte[] projection) throws IOException {
        if (projection != null) { response.getOutputStream().write(projection, Math.toIntExact(offset), Math.toIntExact(length)); return; }
        try (var input = storage.readRange(target.asset().getObjectKey(), offset, length)) { input.transferTo(response.getOutputStream()); }
    }
    private static boolean dateAtLeast(String value, Instant modified) {
        if (value == null) return false;
        try { return !DateTimeFormatter.RFC_1123_DATE_TIME.parse(value, Instant::from).isBefore(modified); }
        catch (java.time.DateTimeException invalid) { return false; }
    }
    /** null means a full response; empty means all requested ranges are unsatisfiable. */
    private static List<Interval> ranges(String header, long size) {
        if (header == null || header.length() > 2048 || !header.startsWith("bytes=")) return null;
        String[] parts = header.substring(6).split(",", -1);
        if (parts.length > MAX_RANGES) return null;
        List<Interval> result = new ArrayList<>(); long total = 0;
        try {
            for (String part : parts) {
                var match = java.util.regex.Pattern.compile("^([0-9]*)-([0-9]*)$").matcher(part.trim());
                if (!match.matches() || (match.group(1).isEmpty() && match.group(2).isEmpty())) return null;
                long start; long end;
                if (match.group(1).isEmpty()) {
                    long suffix = Long.parseLong(match.group(2));
                    if (suffix == 0) continue;
                    start = Math.max(0, size - suffix); end = size - 1;
                } else {
                    start = Long.parseLong(match.group(1));
                    end = match.group(2).isEmpty() ? size - 1 : Long.parseLong(match.group(2));
                    if (start > end || start >= size) continue;
                    end = Math.min(end, size - 1);
                }
                var item = new Interval(start, end); total = Math.addExact(total, item.length()); result.add(item);
            }
        } catch (NumberFormatException | ArithmeticException invalid) { return null; }
        if (parts.length > 1 && total > MAX_MULTI_BYTES) return null;
        return result;
    }
    private static String mime(String path) {
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) return "application/yaml";
        if (lower.endsWith(".zip")) return "application/zip";
        return "application/octet-stream";
    }
    private record Interval(long start, long end) {
        long length() { return end - start + 1; }
        String header(long size) { return "bytes " + start + "-" + end + "/" + size; }
    }
    private static DomainNotFoundException missing() { return new DomainNotFoundException("error.software.update.notFound", "published asset"); }
}
