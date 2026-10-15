package com.iflytek.skillhub.service.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Claude Code manifest v1 with a root .claude-plugin/plugin.json. Scans bytes in memory without
 * extracting files, executing hooks, following links, or resolving external dependencies.
 */
@Component
public class ClaudePluginZipValidator {
    private static final String MANIFEST = ".claude-plugin/plugin.json";
    private static final int MAX_MANIFEST_BYTES = 1024 * 1024;
    private final long maxPackageBytes;
    private final long maxExpandedBytes;
    private final int maxFiles;
    private final ObjectMapper mapper = new ObjectMapper();

    public ClaudePluginZipValidator(@Value("${skillhub.plugin.max-package-bytes:104857600}") long maxPackageBytes,
            @Value("${skillhub.plugin.max-expanded-bytes:2147483648}") long maxExpandedBytes,
            @Value("${skillhub.plugin.max-files:20000}") int maxFiles) {
        if (maxPackageBytes < 1 || maxPackageBytes > Integer.MAX_VALUE - 1 || maxExpandedBytes < 1 || maxFiles < 1) {
            throw new IllegalArgumentException("Invalid plugin archive budgets");
        }
        this.maxPackageBytes = maxPackageBytes;
        this.maxExpandedBytes = maxExpandedBytes;
        this.maxFiles = maxFiles;
    }

    public byte[] readBounded(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes((int) maxPackageBytes + 1);
        if (bytes.length > maxPackageBytes) { throw invalid("size"); }
        return bytes;
    }

    public ValidatedArchive validate(byte[] bytes, String expectedVersion) {
        if (bytes.length > maxPackageBytes || bytes.length < 22) { throw invalid("size"); }
        try {
            Set<String> centralNames = validateCentralDirectory(bytes);
            Set<String> seen = new HashSet<>();
            List<String> files = new ArrayList<>();
            byte[] manifest = null;
            long expanded = 0;
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
                ZipEntry entry;
                byte[] buffer = new byte[8192];
                while ((entry = zip.getNextEntry()) != null) {
                    byte[] extra = entry.getExtra();
                    if (extra != null) validateExtra(extra, 0, extra.length);
                    String name = safePath(entry.getName(), entry.isDirectory());
                    if (!seen.add(name.toLowerCase(Locale.ROOT)) || !centralNames.contains(name)) { throw invalid("duplicate"); }
                    ByteArrayOutputStream manifestBytes = MANIFEST.equals(name) ? new ByteArrayOutputStream() : null;
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        expanded += read;
                        if (expanded > maxExpandedBytes) { throw invalid("expandedSize"); }
                        if (manifestBytes != null) {
                            if (manifestBytes.size() + read > MAX_MANIFEST_BYTES) { throw invalid("manifestSize"); }
                            manifestBytes.write(buffer, 0, read);
                        }
                    }
                    zip.closeEntry();
                    if (manifestBytes != null) { manifest = manifestBytes.toByteArray(); }
                    if (!entry.isDirectory()) { files.add(name); }
                }
            }
            if (seen.size() != centralNames.size() || manifest == null) { throw invalid("manifestMissing"); }
            JsonNode json = mapper.readTree(manifest);
            if (json == null || !json.isObject() || !json.path("name").isTextual()
                    || !json.path("name").asText().matches("[a-z0-9]+(?:-[a-z0-9]+)*")
                    || json.path("name").asText().length() > 200
                    || !json.path("version").isTextual() || !json.path("version").asText().equals(expectedVersion)) {
                throw invalid("manifestIdentity");
            }
            for (String key : List.of("commands", "agents", "skills", "outputStyles")) {
                JsonNode value = json.get(key);
                if (value == null) { continue; }
                if (value.isTextual()) { requireResource(value.asText(), files); }
                else if (value.isArray()) {
                    for (JsonNode item : value) {
                        if (!item.isTextual()) { throw invalid("resourceType"); }
                        requireResource(item.asText(), files);
                    }
                } else { throw invalid("resourceType"); }
            }
            for (String key : List.of("hooks", "mcpServers", "lspServers")) {
                JsonNode value = json.get(key);
                if (value == null) { continue; }
                if (value.isTextual()) { requireResource(value.asText(), files); }
                else if (!value.isObject()) { throw invalid("resourceType"); }
            }
            Collections.sort(files);
            return new ValidatedArchive(json.path("name").asText(), expectedVersion,
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                    mapper.writeValueAsString(files), mapper.writeValueAsString(json));
        } catch (DomainBadRequestException ex) { throw ex; }
        catch (Exception ex) { throw invalid("invalidZip"); }
    }

    private void requireResource(String reference, List<String> files) {
        String value = reference.startsWith("./") ? reference.substring(2) : reference;
        String path = safePath(value, value.endsWith("/"));
        if (files.stream().noneMatch(file -> file.equals(path) || file.startsWith(path + "/"))) {
            throw invalid("missingResource");
        }
    }

    private String safePath(String name, boolean directory) {
        if (name == null || name.isBlank() || name.startsWith("/") || name.contains("\\")
                || name.contains(":") || name.chars().anyMatch(c -> c < 32 || c == 127)) { throw invalid("path"); }
        String normalized = directory && name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
        if (normalized.isBlank() || normalized.length() > 1024) { throw invalid("path"); }
        for (String part : normalized.split("/", -1)) {
            if (part.isBlank() || part.equals(".") || part.equals("..") || part.endsWith(".") || part.endsWith(" ")
                    || part.matches("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\\..*)?")) { throw invalid("path"); }
        }
        return normalized;
    }

    /** Inspect Unix file types in the central directory: ZipInputStream alone cannot detect links. */
    private Set<String> validateCentralDirectory(byte[] bytes) {
        int end = -1;
        for (int i = bytes.length - 22; i >= Math.max(0, bytes.length - 65557); i--) {
            if (u32(bytes, i) == 0x06054b50L && i + 22 + u16(bytes, i + 20) == bytes.length) { end = i; break; }
        }
        if (end < 0 || u16(bytes, end + 4) != 0 || u16(bytes, end + 6) != 0) { throw invalid("zipFormat"); }
        int count = u16(bytes, end + 10);
        if (count < 1 || count > maxFiles || count == 65535 || count != u16(bytes, end + 8)) { throw invalid("fileCount"); }
        long offset = u32(bytes, end + 16), length = u32(bytes, end + 12);
        if (offset + length != end || offset < 0 || offset > Integer.MAX_VALUE) { throw invalid("zipFormat"); }
        int cursor = (int) offset;
        Set<String> names = new HashSet<>(), folded = new HashSet<>(), regularFiles = new HashSet<>();
        for (int n = 0; n < count; n++) {
            if (cursor + 46 > end || u32(bytes, cursor) != 0x02014b50L) { throw invalid("zipFormat"); }
            int flags = u16(bytes, cursor + 8), method = u16(bytes, cursor + 10);
            int nameLength = u16(bytes, cursor + 28), extra = u16(bytes, cursor + 30), comment = u16(bytes, cursor + 32);
            int next = cursor + 46 + nameLength + extra + comment;
            int mode = (int) (u32(bytes, cursor + 38) >>> 16) & 0170000;
            if (next > end || (flags & 1) != 0 || (method != 0 && method != 8)
                    || (mode != 0 && mode != 0100000 && mode != 0040000)
                    || u16(bytes, cursor + 34) != 0 || u32(bytes, cursor + 20) == 0xffffffffL
                    || u32(bytes, cursor + 24) == 0xffffffffL || u32(bytes, cursor + 42) >= offset) {
                throw invalid("unsupportedEntry");
            }
            String name = new String(bytes, cursor + 46, nameLength, StandardCharsets.UTF_8);
            if ((mode == 0040000 && !name.endsWith("/")) || (mode == 0100000 && name.endsWith("/"))) {
                throw invalid("entryType");
            }
            validateExtra(bytes, cursor + 46 + nameLength, extra);
            String path = safePath(name, name.endsWith("/"));
            if (!folded.add(path.toLowerCase(Locale.ROOT))) { throw invalid("duplicate"); }
            if (!name.endsWith("/")) regularFiles.add(path.toLowerCase(Locale.ROOT));
            names.add(path);
            cursor = next;
        }
        if (cursor != end) { throw invalid("zipFormat"); }
        for (String path : folded) {
            for (int separator = path.indexOf('/'); separator >= 0; separator = path.indexOf('/', separator + 1)) {
                if (regularFiles.contains(path.substring(0, separator))) { throw invalid("entryType"); }
            }
        }
        return names;
    }

    /** Reject path aliases, Unix link extras and unknown metadata interpreted differently by native extractors. */
    private void validateExtra(byte[] bytes, int offset, int length) {
        int end = offset + length;
        while (offset < end) {
            if (offset + 4 > end) throw invalid("extraField");
            int tag = u16(bytes, offset), size = u16(bytes, offset + 2);
            if (offset + 4 + size > end || (tag != 0x5455 && tag != 0x000a && tag != 0x7875)) {
                throw invalid("extraField");
            }
            offset += 4 + size;
        }
    }

    private static int u16(byte[] bytes, int offset) {
        return (bytes[offset] & 255) | ((bytes[offset + 1] & 255) << 8);
    }
    private static long u32(byte[] bytes, int offset) {
        return Integer.toUnsignedLong(u16(bytes, offset) | (u16(bytes, offset + 2) << 16));
    }
    private static DomainBadRequestException invalid(String reason) {
        return new DomainBadRequestException("error.plugin.archive", reason);
    }
    public record ValidatedArchive(String nativeName, String nativeVersion, String sha256,
                                   String inventoryJson, String manifestJson) {}
}

