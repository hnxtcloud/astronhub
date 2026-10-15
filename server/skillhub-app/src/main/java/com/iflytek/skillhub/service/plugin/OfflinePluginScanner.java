package com.iflytek.skillhub.service.plugin;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.plugin.PluginVersions;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.LocalizedDomainException;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.*;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.springframework.stereotype.Component;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Offline static adapters. All expanded bytes live in a private temporary directory, never
 * heap-sized archives.
 */
@Component
public class OfflinePluginScanner {
    private final ObjectMapper mapper =
            new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public Scan scan(Path source, String format, PluginImportLimits limits) throws IOException {
        limits.validate();
        if (Files.size(source) > limits.repositoryBytes()) throw invalid("size");
        Path temp = Files.createTempDirectory("astronhub-plugin-");
        try {
            TreeMap<String, Path> files = new TreeMap<>();
            Map<String, Integer> modes = new HashMap<>();
            if ("ZIP".equals(format)) extractZip(source, temp, files, modes, limits);
            else if ("TGZ".equals(format)) extractTar(source, temp, files, modes, limits);
            else throw invalid("format");
            List<Root> roots = new ArrayList<>();
            for (String path : files.keySet()) {
                if ((path.equals(".claude-plugin/plugin.json")
                        || path.endsWith("/.claude-plugin/plugin.json")))
                    addRoot(roots, path, ".claude-plugin/plugin.json", "claude-code", limits);
                else if ((path.equals(".codex-plugin/plugin.json")
                        || path.endsWith("/.codex-plugin/plugin.json")))
                    addRoot(roots, path, ".codex-plugin/plugin.json", "codex", limits);
                else if (path.equals("plugin.json") || path.endsWith("/plugin.json")) {
                    if (path.contains("/.claude-plugin/") || path.contains("/.codex-plugin/"))
                        continue;
                    JsonNode json;
                    try {
                        json = read(files.get(path));
                    } catch (Exception invalid) {
                        addRoot(roots, path, "plugin.json", "codex", limits);
                        continue;
                    }
                    if (json.has("$schema")) addRoot(roots, path, "plugin.json", "codex", limits);
                } else if (path.equals("package.json") || path.endsWith("/package.json")) {
                    JsonNode json;
                    try {
                        json = read(files.get(path));
                    } catch (Exception invalid) {
                        addRoot(roots, path, "package.json", "pi", limits);
                        continue;
                    }
                    String root = path.substring(0, path.length() - "package.json".length());
                    boolean conventional =
                            List.of("extensions/", "skills/", "prompts/", "themes/").stream()
                                    .anyMatch(
                                            dir ->
                                                    files.keySet().stream()
                                                            .anyMatch(
                                                                    f -> f.startsWith(root + dir)));
                    if (json.has("pi")
                            || json.path("keywords").toString().contains("\"pi-package\"")
                            || conventional) addRoot(roots, path, "package.json", "pi", limits);
                }
            }
            String sourceDigest;
            try {
                sourceDigest = sha256(source);
            } catch (Exception ex) {
                throw new IOException(ex);
            }
            List<Candidate> candidates = new ArrayList<>();
            int id = 0;
            for (Root root : roots) {
                String candidateId = Integer.toString(++id);
                try {
                    candidates.add(
                            candidate(
                                    candidateId,
                                    root,
                                    roots,
                                    files,
                                    modes,
                                    source,
                                    sourceDigest,
                                    format,
                                    temp,
                                    limits));
                } catch (Exception ex) {
                    candidates.add(
                            new Candidate(
                                    candidateId,
                                    root.path(),
                                    root.runtime(),
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    "INVALID",
                                    ex instanceof LocalizedDomainException localized
                                                    && localized.messageArgs().length > 0
                                            ? String.valueOf(localized.messageArgs()[0])
                                            : "manifest",
                                    null));
                }
            }
            return new Scan(temp, List.copyOf(candidates), files.size());
        } catch (Exception ex) {
            cleanup(temp);
            if (ex instanceof IOException io) throw io;
            throw ex;
        }
    }

    /**
     * Dependency-only mode shares the exact archive safety budget and never discovers plugin
     * projects.
     */
    public Scan scanDependency(Path source, PluginImportLimits limits) throws IOException {
        limits.validate();
        if (Files.size(source) > Math.min(limits.packageBytes(), NpmRegistryPublisher.MAX_BYTES))
            throw invalid("packageSize");
        Path temp = Files.createTempDirectory("astronhub-dependency-");
        try {
            TreeMap<String, Path> files = new TreeMap<>();
            Map<String, Integer> modes = new HashMap<>();
            extractTar(source, temp, files, modes, limits);
            if (!files.containsKey("package/package.json")
                    || files.keySet().stream().anyMatch(f -> !f.startsWith("package/")))
                throw invalid("manifest");
            Root root = new Root("package/", "pi", "package/package.json");
            Candidate candidate =
                    candidate(
                            "1",
                            root,
                            List.of(root),
                            files,
                            modes,
                            source,
                            sha256(source),
                            "TGZ",
                            temp,
                            limits);
            return new Scan(temp, List.of(candidate), files.size());
        } catch (Exception ex) {
            cleanup(temp);
            if (ex instanceof IOException io) throw io;
            throw new DomainBadRequestException("error.plugin.archive", "dependency");
        }
    }

    /**
     * Reuses archive safety and disk budgets for exact Git blobs, without checkout or repacking.
     */
    public NativeFiles openNativeFiles(Path source, PluginImportLimits limits) throws IOException {
        limits.validate();
        if (Files.size(source) > Math.min(limits.packageBytes(), NpmRegistryPublisher.MAX_BYTES))
            throw invalid("packageSize");
        Path temp = Files.createTempDirectory("astronhub-git-files-");
        try {
            TreeMap<String, Path> files = new TreeMap<>();
            Map<String, Integer> modes = new HashMap<>();
            extractZip(source, temp, files, modes, limits);
            return new NativeFiles(
                    temp,
                    java.util.Collections.unmodifiableMap(files),
                    java.util.Collections.unmodifiableMap(modes));
        } catch (Exception ex) {
            cleanup(temp);
            if (ex instanceof IOException io) throw io;
            throw ex;
        }
    }

    public record NativeFiles(Path temp, Map<String, Path> files, Map<String, Integer> modes)
            implements AutoCloseable {
        public void close() throws IOException {
            cleanup(temp);
        }
    }

    private void addRoot(
            List<Root> roots,
            String file,
            String marker,
            String runtime,
            PluginImportLimits limits) {
        String root = file.substring(0, file.length() - marker.length());
        if (roots.stream().anyMatch(r -> r.path().equals(root) && r.runtime().equals(runtime))) {
            if (marker.equals("plugin.json"))
                roots.removeIf(r -> r.path().equals(root) && r.runtime().equals(runtime));
            else return;
        }
        if (roots.size() >= limits.candidates()) throw invalid("candidateCount");
        roots.add(new Root(root, runtime, file));
    }

    private Candidate candidate(
            String id,
            Root root,
            List<Root> roots,
            Map<String, Path> files,
            Map<String, Integer> modes,
            Path original,
            String sourceDigest,
            String format,
            Path temp,
            PluginImportLimits limits)
            throws Exception {
        JsonNode json = read(files.get(root.manifest()));
        if (root.runtime().equals("codex")
                && !root.manifest().endsWith(".codex-plugin/plugin.json")
                && !json.path("$schema")
                        .asText()
                        .equals("https://agent-plugins.org/schemas/1.0.0/plugin.schema.json"))
            throw invalid("manifestVersion");
        if (root.runtime().equals("pi") && json.has("pi") && !json.path("pi").isObject())
            throw invalid("resourceType");
        String name = json.path("name").asText(), version = json.path("version").asText();
        if (!name.matches(
                        root.runtime().equals("pi")
                                ? "(?:@[a-z0-9._-]+/)?[a-z0-9][a-z0-9._-]*"
                                : "[a-z0-9]+(?:-[a-z0-9]+)*")
                || name.length() > 200
                || !PluginVersions.valid(version)) throw invalid("manifestIdentity");
        TreeMap<String, Path> selected = new TreeMap<>();
        files.forEach(
                (path, file) -> {
                    if (path.startsWith(root.path())
                            && roots.stream()
                                    .noneMatch(
                                            other ->
                                                    !other.path().equals(root.path())
                                                            && other.path().startsWith(root.path())
                                                            && path.startsWith(other.path())))
                        selected.put(path.substring(root.path().length()), file);
                });
        JsonNode resourceManifest = root.runtime().equals("pi") ? json.path("pi") : json;
        if (root.runtime().equals("codex")) {
            JsonNode extension = json.path("extensions").get("com.openai");
            if (extension != null && !extension.isObject()) throw invalid("resourceType");
            if (extension != null) resourceManifest = extension;
            else if (root.manifest().endsWith("plugin.json")
                    && !root.manifest().endsWith(".codex-plugin/plugin.json")
                    && selected.containsKey(".codex-plugin/plugin.json"))
                resourceManifest = read(selected.get(".codex-plugin/plugin.json"));
        }
        List<String> resourceKeys =
                root.runtime().equals("pi")
                        ? List.of("extensions", "skills", "prompts", "themes")
                        : root.runtime().equals("claude-code")
                                ? List.of(
                                        "commands",
                                        "agents",
                                        "skills",
                                        "outputStyles",
                                        "hooks",
                                        "mcpServers",
                                        "lspServers")
                                : List.of(
                                        "skills", "apps", "mcpServers", "hooks", "onboardingSkill");
        Map<String, Object> resources = new TreeMap<>();
        for (String key : resourceKeys) {
            JsonNode declared = resourceManifest.get(key);
            if (declared != null) {
                if (declared.isTextual())
                    resources.put(
                            key, resolve(declared.asText(), selected, root.runtime().equals("pi")));
                else if (declared.isArray()) {
                    List<String> matches = new ArrayList<>();
                    for (JsonNode value : declared) {
                        if (!value.isTextual()) throw invalid("resourceType");
                        matches.addAll(
                                resolve(value.asText(), selected, root.runtime().equals("pi")));
                    }
                    resources.put(key, matches);
                } else if (!Set.of("hooks", "mcpServers", "lspServers").contains(key)
                        || !declared.isObject()) throw invalid("resourceType");
                else resources.put(key, Map.of("inline", true, "runtimeVerified", false));
            } else {
                List<String> conventional =
                        selected.keySet().stream().filter(p -> p.startsWith(key + "/")).toList();
                if (!conventional.isEmpty()) resources.put(key, conventional);
            }
        }
        if (root.runtime().equals("codex")) {
            JsonNode ui = resourceManifest.path("interface");
            if (resourceManifest.has("interface") && !ui.isObject()) throw invalid("resourceType");
            for (String key : List.of("composerIcon", "logo", "screenshots")) {
                JsonNode declared = ui.get(key);
                if (declared == null) continue;
                List<String> matches = new ArrayList<>();
                if (declared.isTextual())
                    matches.addAll(resolve(declared.asText(), selected, false));
                else if (key.equals("screenshots") && declared.isArray()) {
                    for (JsonNode path : declared) {
                        if (!path.isTextual()) throw invalid("resourceType");
                        matches.addAll(resolve(path.asText(), selected, false));
                    }
                } else throw invalid("resourceType");
                resources.put("interface." + key, matches);
            }
        }
        for (String key : List.of(".mcp.json", "mcp.json", ".app.json", "hooks/hooks.json"))
            if (selected.containsKey(key)) {
                read(selected.get(key));
                resources.putIfAbsent(key, List.of(key));
            }
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("files", selected.keySet());
        evidence.put("resources", resources);
        evidence.put("runtimeTest", "UNKNOWN");
        evidence.put("securityAssessment", "NOT_SCANNED");
        evidence.put(
                "adapter",
                root.runtime().equals("codex")
                        ? (root.manifest().endsWith(".codex-plugin/plugin.json")
                                ? "codex-compat-v1"
                                : "agent-plugins-1.0.0")
                        : root.runtime().equals("pi") ? "pi-package-json" : "claude-plugin-v1");
        Map<String, Object> requirements = new LinkedHashMap<>();
        for (String key :
                List.of(
                        "dependencies",
                        "optionalDependencies",
                        "peerDependencies",
                        "engines",
                        "os",
                        "cpu",
                        "scripts"))
            if (json.has(key)) {
                JsonNode value = json.get(key);
                if (Set.of("os", "cpu").contains(key)) {
                    if (!value.isArray()) throw invalid("dependencyType");
                    for (JsonNode item : value)
                        if (!item.isTextual()) throw invalid("dependencyType");
                } else {
                    if (!value.isObject()) throw invalid("dependencyType");
                    var fields = value.fields();
                    while (fields.hasNext())
                        if (!fields.next().getValue().isTextual()) throw invalid("dependencyType");
                }
                requirements.put(key, value);
            }
        requirements.put("availability", "NOT_CHECKED_OFFLINE");
        requirements.put("lifecycleScriptsExecuted", false);
        evidence.put("requirements", requirements);
        boolean originalZip =
                !root.runtime().equals("pi")
                        && format.equals("ZIP")
                        && root.path().isEmpty()
                        && selected.size() == files.size();
        boolean originalTgz =
                root.runtime().equals("pi")
                        && format.equals("TGZ")
                        && root.path().equals("package/")
                        && roots.size() == 1
                        && selected.size() == files.size();
        String artifactFormat = root.runtime().equals("pi") ? "TGZ" : "ZIP";
        Path artifact =
                temp.resolve(
                        "candidate-" + id + "." + (artifactFormat.equals("TGZ") ? "tgz" : "zip"));
        Map<String, Integer> candidateModes = new TreeMap<>();
        selected.keySet().forEach(path -> candidateModes.put(path, modes.get(root.path() + path)));
        if (originalTgz || originalZip) Files.copy(original, artifact);
        else if (artifactFormat.equals("TGZ")) writeTgz(artifact, selected, candidateModes);
        else writeZip(artifact, selected, candidateModes);
        if (Files.size(artifact) > limits.packageBytes()) throw invalid("packageSize");
        evidence.put(
                "packaging",
                originalTgz
                        ? "ORIGINAL_NPM_TGZ"
                        : originalZip ? "ORIGINAL_NATIVE_ZIP" : "DERIVED_FROM_ARCHIVE");
        evidence.put("fileModes", candidateModes);
        evidence.put("sourceArchiveSha256", sourceDigest);
        evidence.put("sourceArchiveFormat", format);
        evidence.put("sourceDirectory", root.path().isEmpty() ? "." : root.path());
        String slug =
                name.replace('@', '-')
                        .replace('/', '-')
                        .replaceAll("[^a-z0-9-]", "-")
                        .replaceAll("-+", "-")
                        .replaceAll("^-|-$", "");
        if (slug.length() > 64) slug = slug.substring(0, 64).replaceAll("-$", "");
        return new Candidate(
                id,
                root.path(),
                root.runtime(),
                name,
                version,
                slug,
                sha256(artifact),
                artifactFormat,
                mapper.writeValueAsString(evidence),
                mapper.writeValueAsString(json),
                "VALID",
                null,
                artifact);
    }

    private List<String> resolve(String reference, Map<String, Path> files, boolean glob) {
        boolean exclude = glob && reference.startsWith("!");
        String value = exclude ? reference.substring(1) : reference;
        value = value.startsWith("./") ? value.substring(2) : value;
        safePath(value.endsWith("/") ? value.substring(0, value.length() - 1) : value);
        String pattern = value;
        List<String> matches =
                files.keySet().stream()
                        .filter(
                                p ->
                                        p.equals(pattern)
                                                || p.startsWith(pattern + "/")
                                                || (glob
                                                        && FileSystems.getDefault()
                                                                .getPathMatcher("glob:" + pattern)
                                                                .matches(Path.of(p))))
                        .toList();
        if (matches.isEmpty() && !exclude) throw invalid("missingResource");
        return exclude ? List.of(reference) : matches;
    }

    private JsonNode read(Path path) throws IOException {
        if (Files.size(path) > 1048576) throw invalid("manifestSize");
        try (InputStream input = Files.newInputStream(path)) {
            JsonNode value = mapper.readTree(input);
            if (value == null || !value.isObject()) throw invalid("manifest");
            return value;
        }
    }

    private void extractZip(
            Path source,
            Path temp,
            Map<String, Path> files,
            Map<String, Integer> modes,
            PluginImportLimits limits)
            throws IOException {
        Set<String> seen = new HashSet<>(), regular = new HashSet<>();
        long total = 0;
        int count = 0;
        try (var zip =
                org.apache.commons.compress.archivers.zip.ZipFile.builder()
                        .setPath(source)
                        .setUseUnicodeExtraFields(false)
                        .get()) {
            var entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                String name = safePath(stripDirectory(entry.getName()));
                if (++count > limits.files()) throw invalid("fileCount");
                if (!seen.add(name.toLowerCase(Locale.ROOT))) throw invalid("duplicate");
                int type = entry.getUnixMode() & 0170000;
                if (!zip.canReadEntryData(entry)
                        || entry.isUnixSymlink()
                        || (type != 0 && type != 0100000 && type != 0040000)
                        || (entry.getGeneralPurposeBit().usesEncryption())
                        || (entry.getMethod() != 0 && entry.getMethod() != 8)
                        || !entry.isDirectory() && type == 0040000
                        || entry.isDirectory() && type == 0100000) throw invalid("entryType");
                for (var extra : entry.getExtraFields())
                    if (!Set.of(0x5455, 0x000a, 0x7875).contains(extra.getHeaderId().getValue()))
                        throw invalid("extraField");
                if (entry.isDirectory()) continue;
                regular.add(name.toLowerCase(Locale.ROOT));
                Path file = temp.resolve("file-" + count);
                CRC32 crc = new CRC32();
                long size = 0;
                try (InputStream input = zip.getInputStream(entry);
                        OutputStream out = Files.newOutputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = input.read(buffer)) != -1) {
                        size += n;
                        total += n;
                        if (total > limits.expandedBytes()) throw invalid("expandedSize");
                        crc.update(buffer, 0, n);
                        out.write(buffer, 0, n);
                    }
                }
                if (size != entry.getSize() || crc.getValue() != entry.getCrc())
                    throw invalid("zipIntegrity");
                files.put(name, file);
                modes.put(name, entry.getUnixMode() == 0 ? 0644 : entry.getUnixMode() & 0777);
            }
        }
        checkParents(seen, regular);
        Set<String> local = new HashSet<>();
        long localBytes = 0;
        try (var zip = new ZipInputStream(Files.newInputStream(source), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                String name = safePath(stripDirectory(entry.getName()));
                if (!seen.contains(name.toLowerCase(Locale.ROOT))
                        || !local.add(name.toLowerCase(Locale.ROOT))) throw invalid("zipHeaders");
                int n;
                while ((n = zip.read(buffer)) != -1) {
                    localBytes += n;
                    if (localBytes > limits.expandedBytes()) throw invalid("expandedSize");
                }
                zip.closeEntry();
            }
        }
        if (!local.equals(seen)) throw invalid("zipHeaders");
    }

    private void extractTar(
            Path source,
            Path temp,
            Map<String, Path> files,
            Map<String, Integer> modes,
            PluginImportLimits limits)
            throws IOException {
        Set<String> seen = new HashSet<>(), regular = new HashSet<>();
        long total = 0;
        int count = 0;
        Path expanded = temp.resolve("source.tar");
        try (var gzip =
                        GzipCompressorInputStream.builder()
                                .setInputStream(Files.newInputStream(source))
                                .setDecompressConcatenated(true)
                                .get();
                var bounded =
                        new ExpandedInputStream(
                                gzip, limits.expandedBytes() + ((long) limits.files() * 2048));
                var output = Files.newOutputStream(expanded)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = bounded.read(buffer)) != -1) output.write(buffer, 0, n);
        }
        validateTarEnvelope(expanded, limits);
        try (var tar = new BoundedTarInputStream(Files.newInputStream(expanded), limits.files())) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                String name = safePath(stripDirectory(entry.getName()));
                if (++count > limits.files()) throw invalid("fileCount");
                if (!seen.add(name.toLowerCase(Locale.ROOT))) throw invalid("duplicate");
                if (!entry.isCheckSumOK()
                        || entry.isSparse()
                        || (!entry.isFile() && !entry.isDirectory())
                        || entry.isSymbolicLink()
                        || entry.isLink()
                        || !tar.canReadEntryData(entry)) throw invalid("entryType");
                if (entry.isDirectory()) continue;
                regular.add(name.toLowerCase(Locale.ROOT));
                Path file = temp.resolve("file-" + count);
                long size = 0;
                try (OutputStream output = Files.newOutputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = tar.read(buffer)) != -1) {
                        size += n;
                        total += n;
                        if (total > limits.expandedBytes()) throw invalid("expandedSize");
                        output.write(buffer, 0, n);
                    }
                }
                if (size != entry.getSize()) throw invalid("tarIntegrity");
                files.put(name, file);
                modes.put(name, entry.getMode() & 0777);
            }
        }
        checkParents(seen, regular);
    }

    /**
     * Reject nonzero trailing archives and bound PAX allocations before handing a native TGZ back
     * unchanged.
     */
    private void validateTarEnvelope(Path source, PluginImportLimits limits) throws IOException {
        try (var file = new RandomAccessFile(source.toFile(), "r")) {
            byte[] header = new byte[512];
            int count = 0, metadata = 0;
            boolean end = false;
            while (file.getFilePointer() < file.length()) {
                if (file.read(header) != 512) throw invalid("tarFormat");
                boolean zero = true;
                for (byte b : header)
                    if (b != 0) {
                        zero = false;
                        break;
                    }
                if (zero) {
                    end = true;
                    continue;
                }
                if (end || ++count > limits.files())
                    throw invalid(end ? "tarTrailing" : "fileCount");
                TarArchiveEntry entry = new TarArchiveEntry(header);
                if (!entry.isCheckSumOK() || entry.getSize() < 0) throw invalid("tarIntegrity");
                byte type = header[156];
                boolean special = type == 'x' || type == 'g' || type == 'L' || type == 'K';
                if (special) {
                    if (entry.getSize() > 1048576 || ++metadata > 16) throw invalid("tarMetadata");
                } else metadata = 0;
                long padded = ((entry.getSize() + 511) / 512) * 512;
                if (padded < 0 || padded > file.length() - file.getFilePointer())
                    throw invalid("tarIntegrity");
                file.seek(file.getFilePointer() + padded);
            }
            if (!end) throw invalid("tarEndMissing");
        }
    }

    private static final class ExpandedInputStream extends FilterInputStream {
        private final long max;
        private long bytes;

        ExpandedInputStream(InputStream input, long max) {
            super(input);
            this.max = max;
        }

        private void count(long n) {
            if (n > 0 && (bytes += n) > max) throw invalid("expandedSize");
        }

        @Override
        public int read() throws IOException {
            int value = in.read();
            if (value != -1) count(1);
            return value;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = in.read(b, off, len);
            count(n);
            return n;
        }

        @Override
        public long skip(long n) throws IOException {
            byte[] b = new byte[(int) Math.min(n, 8192)];
            long total = 0;
            while (total < n) {
                int read = read(b, 0, (int) Math.min(b.length, n - total));
                if (read < 0) break;
                total += read;
            }
            return total;
        }
    }

    /**
     * Bound hidden PAX/GNU metadata before Commons Compress allocates or recursively consumes it.
     */
    private static final class BoundedTarInputStream extends TarArchiveInputStream {
        private final int max;
        private int count, consecutiveMetadata;

        BoundedTarInputStream(InputStream input, int max) {
            super(input, StandardCharsets.UTF_8.name());
            this.max = max;
        }

        @Override
        protected byte[] readRecord() throws IOException {
            byte[] record = super.readRecord();
            if (record != null && !isEOFRecord(record)) {
                if (++count > max) throw invalid("fileCount");
                byte type = record[156];
                boolean metadata = type == 'x' || type == 'g' || type == 'L' || type == 'K';
                if (metadata) {
                    if (++consecutiveMetadata > 16
                            || new TarArchiveEntry(record).getSize() > 1048576)
                        throw invalid("tarMetadata");
                } else consecutiveMetadata = 0;
            }
            return record;
        }
    }

    private void checkParents(Set<String> names, Set<String> regular) {
        for (String name : names)
            for (int i = name.indexOf('/'); i >= 0; i = name.indexOf('/', i + 1))
                if (regular.contains(name.substring(0, i))) throw invalid("entryType");
    }

    private String stripDirectory(String path) {
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    static String safePath(String path) {
        if (path.isBlank()
                || path.length() > 1024
                || path.startsWith("/")
                || path.contains("\\")
                || path.contains(":")
                || path.contains("\uFFFD")
                || path.chars().anyMatch(c -> c < 32 || c == 127)) throw invalid("path");
        for (String part : path.split("/", -1))
            if (part.isBlank()
                    || part.equals(".")
                    || part.equals("..")
                    || part.endsWith(".")
                    || part.endsWith(" ")
                    || part.matches("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\\..*)?"))
                throw invalid("path");
        return path;
    }

    private void writeZip(Path target, Map<String, Path> files, Map<String, Integer> modes)
            throws IOException {
        try (var zip = new ZipArchiveOutputStream(Files.newOutputStream(target))) {
            zip.setUseZip64(Zip64Mode.Never);
            for (var file : files.entrySet()) {
                ZipArchiveEntry entry = new ZipArchiveEntry(file.getKey());
                entry.setTime(315532800000L);
                entry.setUnixMode(0100000 | modes.get(file.getKey()));
                zip.putArchiveEntry(entry);
                Files.copy(file.getValue(), zip);
                zip.closeArchiveEntry();
            }
        }
    }

    private void writeTgz(Path target, Map<String, Path> files, Map<String, Integer> modes)
            throws IOException {
        try (var gzip = new GZIPOutputStream(Files.newOutputStream(target));
                var tar = new TarArchiveOutputStream(gzip, StandardCharsets.UTF_8.name())) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            for (var file : files.entrySet()) {
                TarArchiveEntry entry = new TarArchiveEntry("package/" + file.getKey());
                entry.setSize(Files.size(file.getValue()));
                entry.setModTime(0);
                entry.setMode(modes.get(file.getKey()));
                tar.putArchiveEntry(entry);
                Files.copy(file.getValue(), tar);
                tar.closeArchiveEntry();
            }
        }
    }

    static String sha256(Path path) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(path)) {
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) != -1) sha.update(b, 0, n);
        }
        return HexFormat.of().formatHex(sha.digest());
    }

    private static DomainBadRequestException invalid(String reason) {
        return new DomainBadRequestException("error.plugin.archive", reason);
    }

    static void cleanup(Path path) throws IOException {
        try (var files = Files.walk(path)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList())
                Files.deleteIfExists(file);
        }
    }

    private record Root(String path, String runtime, String manifest) {}

    public record Candidate(
            String id,
            String directory,
            String runtime,
            String nativeName,
            String version,
            String suggestedSlug,
            String sha256,
            String format,
            String inventoryJson,
            String manifestJson,
            String status,
            String error,
            Path artifact) {}

    public record Scan(Path temp, List<Candidate> candidates, int files) implements AutoCloseable {
        public void close() throws IOException {
            cleanup(temp);
        }
    }
}
