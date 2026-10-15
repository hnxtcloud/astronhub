package com.iflytek.skillhub.service.plugin;

import com.fasterxml.jackson.databind.*;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.plugin.*;
import com.iflytek.skillhub.domain.plugin.service.*;
import com.iflytek.skillhub.domain.shared.exception.*;
import com.iflytek.skillhub.dto.plugin.PluginAccessDtos.*;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.repository.PluginPublishedQueryRepository;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.storage.ObjectStorageService;

import org.apache.commons.compress.archivers.tar.*;
import org.apache.commons.compress.archivers.zip.*;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/**
 * Fixed-release package access. Browsed content is inert UTF-8 text, never rendered or executed on
 * the server.
 */
@Service
public class PluginAccessAppService {
    private static final int PREVIEW_BYTES = 262144;
    private final PluginPublishedQueryRepository queries;
    private final PluginReleaseWorkflow workflow;
    private final PluginDistributionRepository distributions;
    private final ObjectStorageService storage;
    private final PluginManagementAppService management;
    private final ObjectMapper mapper;
    private final AuditLogService audit;
    private final RequestIdAccessor requestIds;

    public PluginAccessAppService(
            PluginPublishedQueryRepository queries,
            PluginReleaseWorkflow workflow,
            PluginDistributionRepository distributions,
            ObjectStorageService storage,
            PluginManagementAppService management,
            ObjectMapper mapper,
            AuditLogService audit,
            RequestIdAccessor requestIds) {
        this.queries = queries;
        this.workflow = workflow;
        this.distributions = distributions;
        this.storage = storage;
        this.management = management;
        this.mapper = mapper;
        this.audit = audit;
        this.requestIds = requestIds;
    }

    public PluginRelease resolve(String ns, String slug, String version, PluginActor actor) {
        return queries.release(ns, slug, version, actor);
    }

    @Transactional
    public PluginManagementAppService.Download download(
            String ns,
            String slug,
            String version,
            String runtime,
            PluginActor actor,
            AuditRequestContext context) {
        return management.openArchive(
                resolve(ns, slug, version, actor).getId(), runtime, false, actor, context);
    }

    public PluginFileListView files(
            Long releaseId, String runtime, boolean inspection, PluginActor actor) {
        return new PluginFileListView(inventory(archive(releaseId, runtime, inspection, actor)));
    }

    @Transactional
    public PluginFileContentView file(
            Long releaseId,
            String runtime,
            String path,
            boolean inspection,
            PluginActor actor,
            AuditRequestContext context)
            throws IOException {
        PluginDistribution archive = archive(releaseId, runtime, inspection, actor);
        if (path == null
                || path.contains("%")
                || !OfflinePluginScanner.safePath(path).equals(path)
                || !inventory(archive).contains(path))
            throw new DomainBadRequestException("error.plugin.filePath");
        Path temp =
                Files.createTempFile(
                        "astronhub-inspect-",
                        archive.getSourceLocator().endsWith(".tgz") ? ".tgz" : ".zip");
        try {
            MessageDigest sha;
            try {
                sha = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException ex) {
                throw new IllegalStateException(ex);
            }
            try (InputStream input =
                            new DigestInputStream(
                                    storage.getObject(archive.getSourceLocator()), sha);
                    OutputStream out = Files.newOutputStream(temp)) {
                byte[] buffer = new byte[8192];
                int n;
                long size = 0;
                while ((n = input.read(buffer)) != -1) {
                    size += n;
                    if (size > 2147483648L)
                        throw new DomainBadRequestException("error.plugin.fileArchive");
                    out.write(buffer, 0, n);
                }
            }
            if (!HexFormat.of().formatHex(sha.digest()).equals(archive.getIntegrityDigest()))
                throw new DomainBadRequestException("error.plugin.fileArchive");
            byte[] content = null;
            if (archive.getSourceLocator().endsWith(".tgz")) {
                try (var gzip = GzipCompressorInputStream.builder().setPath(temp).get();
                        var bounded = new ExpandedBudget(gzip);
                        var tar =
                                new TarArchiveInputStream(bounded, StandardCharsets.UTF_8.name())) {
                    TarArchiveEntry entry;
                    int count = 0;
                    while ((entry = tar.getNextEntry()) != null) {
                        if (++count > 200000)
                            throw new DomainBadRequestException("error.plugin.fileArchive");
                        if (entry.getName().equals("package/" + path)
                                && entry.isFile()
                                && !entry.isLink()
                                && !entry.isSymbolicLink()) {
                            content = readTextBytes(tar);
                            break;
                        }
                    }
                }
            } else {
                try (var zip =
                        org.apache.commons.compress.archivers.zip.ZipFile.builder()
                                .setPath(temp)
                                .get()) {
                    ZipArchiveEntry entry = zip.getEntry(path);
                    if (entry != null && !entry.isDirectory() && !entry.isUnixSymlink())
                        try (InputStream in = zip.getInputStream(entry)) {
                            content = readTextBytes(in);
                        }
                }
            }
            if (content == null) throw new DomainNotFoundException("error.plugin.fileNotFound");
            String text;
            try {
                text =
                        StandardCharsets.UTF_8
                                .newDecoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT)
                                .decode(ByteBuffer.wrap(content))
                                .toString();
            } catch (CharacterCodingException ex) {
                throw new DomainBadRequestException("error.plugin.fileBinary");
            }
            if (text.chars().anyMatch(c -> c < 32 && c != '\n' && c != '\r' && c != '\t'))
                throw new DomainBadRequestException("error.plugin.fileBinary");
            record("plugin.file.inspect", releaseId, actor, context);
            return new PluginFileContentView(path, text, content.length);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Transactional
    public PluginInstallView install(
            Long releaseId, String runtime, PluginActor actor, AuditRequestContext context) {
        var archive = archive(releaseId, runtime, false, actor);
        var release = workflow.readRelease(releaseId);
        var project = workflow.requireProject(release.getProjectId());
        String folder = "./plugins/" + project.getSlug() + "/" + release.getVersion();
        String command, preparation, marketplace = null;
        if (runtime.equals("claude-code")) {
            command = "claude --plugin-dir \"" + folder + "\"";
            preparation = "CLAUDE_LOCAL";
        } else if (runtime.equals("pi")) {
            command = "pi install \"" + folder + "/package\"";
            preparation = "PI_LOCAL";
        } else if (runtime.equals("codex")) {
            String market = "astronhub-local-" + project.getId() + "-" + releaseId;
            String name = archive.getNativeName();
            command =
                    "codex plugin marketplace add ./astronhub-local\ncodex plugin add "
                            + name
                            + "@"
                            + market;
            preparation = "CODEX_LOCAL";
            try {
                marketplace =
                        mapper.writerWithDefaultPrettyPrinter()
                                .writeValueAsString(
                                        Map.of(
                                                "name",
                                                market,
                                                "interface",
                                                Map.of("displayName", project.getDisplayName()),
                                                "plugins",
                                                List.of(
                                                        Map.of(
                                                                "name",
                                                                name,
                                                                "source",
                                                                Map.of(
                                                                        "source", "local", "path",
                                                                        folder),
                                                                "policy",
                                                                Map.of(
                                                                        "installation",
                                                                        "AVAILABLE",
                                                                        "authentication",
                                                                        "ON_INSTALL"),
                                                                "category",
                                                                "Productivity"))));
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        } else throw new DomainBadRequestException("error.plugin.archiveMissing");
        String ns = queries.detailNamespace(project.getNamespaceId());
        String download =
                "/api/v1/plugins/"
                        + segment(ns)
                        + "/"
                        + segment(project.getSlug())
                        + "/versions/"
                        + segment(release.getVersion())
                        + "/download?runtime="
                        + segment(runtime);
        record("plugin.install.instructions", releaseId, actor, context);
        return new PluginInstallView(
                runtime,
                archive.getNativeName(),
                release.getVersion(),
                download,
                project.getSlug()
                        + "-"
                        + release.getVersion()
                        + (runtime.equals("pi") ? ".tgz" : ".zip"),
                archive.getIntegrityDigest(),
                command,
                preparation,
                marketplace,
                false);
    }

    private PluginDistribution archive(
            Long id, String runtime, boolean inspection, PluginActor actor) {
        var release = workflow.readRelease(id);
        workflow.requireDownload(release, actor, inspection);
        return distributions.findByReleaseIdOrderByRuntimeKey(id).stream()
                .filter(d -> d.getRuntimeKey().equals(runtime))
                .findFirst()
                .orElseThrow(() -> new DomainNotFoundException("error.plugin.archiveMissing"));
    }

    private List<String> inventory(PluginDistribution archive) {
        try {
            JsonNode value = mapper.readTree(archive.getInventoryJson());
            JsonNode files = value.isArray() ? value : value.path("files");
            if (!files.isArray()) throw new DomainBadRequestException("error.plugin.fileArchive");
            List<String> result = new ArrayList<>();
            for (JsonNode entry : files) {
                if (!entry.isTextual())
                    throw new DomainBadRequestException("error.plugin.fileArchive");
                result.add(OfflinePluginScanner.safePath(entry.asText()));
            }
            return List.copyOf(result);
        } catch (IOException ex) {
            throw new DomainBadRequestException("error.plugin.fileArchive");
        }
    }

    private byte[] readTextBytes(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(PREVIEW_BYTES + 1);
        if (bytes.length > PREVIEW_BYTES)
            throw new DomainBadRequestException("error.plugin.fileTooLarge");
        return bytes;
    }

    private static String segment(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private void record(String action, Long id, PluginActor actor, AuditRequestContext context) {
        audit.record(
                actor.userId(),
                action,
                "PLUGIN_RELEASE",
                id,
                requestIds.current(),
                context.clientIp(),
                context.userAgent(),
                "{}");
    }

    private static final class ExpandedBudget extends FilterInputStream {
        private long bytes;

        ExpandedBudget(InputStream in) {
            super(in);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = in.read(b, off, len);
            if (n > 0 && (bytes += n) > 21474836480L)
                throw new DomainBadRequestException("error.plugin.fileArchive");
            return n;
        }

        @Override
        public int read() throws IOException {
            int value = in.read();
            if (value >= 0 && ++bytes > 21474836480L)
                throw new DomainBadRequestException("error.plugin.fileArchive");
            return value;
        }

        @Override
        public long skip(long n) throws IOException {
            byte[] buffer = new byte[(int) Math.min(8192, n)];
            long skipped = 0;
            while (skipped < n) {
                int count = read(buffer, 0, (int) Math.min(buffer.length, n - skipped));
                if (count < 0) break;
                skipped += count;
            }
            return skipped;
        }
    }
}
