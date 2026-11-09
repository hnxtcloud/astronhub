package com.iflytek.skillhub.service.publication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iflytek.skillhub.controller.support.SkillPackageArchiveExtractor;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.service.plugin.OfflinePluginScanner;
import com.iflytek.skillhub.service.plugin.PluginOfflineImportAppService;
import com.iflytek.skillhub.storage.ObjectStorageService;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Bounded static detection; the owning publication domain still validates all submitted bytes. */
@Service
public class PublicationMaterialService {
    private final ObjectStorageService storage;
    private final OfflinePluginScanner scanner;
    private final PluginOfflineImportAppService imports;
    private final SkillPackageArchiveExtractor skills;
    private final ObjectMapper mapper;
    private final com.iflytek.skillhub.config.SkillPublishProperties limits;
    public PublicationMaterialService(ObjectStorageService storage, OfflinePluginScanner scanner,
            PluginOfflineImportAppService imports, SkillPackageArchiveExtractor skills, ObjectMapper mapper,
            com.iflytek.skillhub.config.SkillPublishProperties limits) {
        this.storage = storage; this.scanner = scanner; this.imports = imports; this.skills = skills; this.mapper = mapper;
        this.limits = limits;
    }
    public ObjectNode detect(MultipartFile file) throws IOException {
        var draft = mapper.createObjectNode().put("type", "UNKNOWN").put("visibility", "PRIVATE");
        draft.putArray("warnings").add("publication.detectionAmbiguous");
        String name = fileName(file.getOriginalFilename());
        String lower = name.toLowerCase(Locale.ROOT);
        String format = lower.endsWith(".tar.gz") || lower.endsWith(".tgz") ? "TGZ" : lower.endsWith(".zip") ? "ZIP" : "";
        if (!format.isEmpty()) {
            if (file.getSize() > imports.limits().repositoryBytes()) return draft;
            Path temp = Files.createTempFile("publication-detect-", ".bin");
            try {
                try (var input = file.getInputStream()) { Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING); }
                try (var scan = scanner.scan(temp, format, imports.limits())) {
                    if (!scan.candidates().isEmpty()) {
                        var first = scan.candidates().getFirst();
                        draft.put("type", "PLUGIN").put("name", first.nativeName()).put("slug", first.suggestedSlug())
                                .put("version", first.version());
                        draft.put("repository", scan.candidates().stream().map(OfflinePluginScanner.Candidate::directory).distinct().count() > 1);
                        draft.remove("warnings");
                        return draft;
                    }
                } catch (DomainBadRequestException uncertain) {
                    draft.putArray("warnings").add("publication.detectionInvalid");
                    return draft;
                }
                if (format.equals("ZIP")) {
                    if (file.getSize() > limits.getMaxPackageSize()) return draft;
                    var extracted = skills.extractWithWarnings(file);
                    var entries = extracted.entries();
                    var skill = entries.stream().filter(e -> e.path().equals("SKILL.md")).findFirst();
                    if (skill.isPresent()) {
                        var metadata = new SkillMetadataParser().parse(new String(skill.get().content(), StandardCharsets.UTF_8));
                        draft.put("type", "SKILL").put("name", metadata.name()).put("slug", com.iflytek.skillhub.domain.namespace.SlugValidator.slugify(metadata.name()))
                                .put("version", metadata.version()).put("description", metadata.description());
                        draft.put("visibility", "PUBLIC");
                        draft.set("warnings", mapper.valueToTree(extracted.warnings()));
                    }
                }
            } finally { Files.deleteIfExists(temp); }
        } else {
            try (var in = file.getInputStream()) {
                byte[] header = in.readNBytes(8);
                boolean executable = header.length > 1 && header[0] == 'M' && header[1] == 'Z';
                boolean msi = header.length == 8 && java.util.HexFormat.of().formatHex(header).equals("d0cf11e0a1b11ae1");
                boolean deb = new String(header, StandardCharsets.US_ASCII).equals("!<arch>\n");
                boolean rpm = header.length >= 4 && java.util.HexFormat.of().formatHex(header, 0, 4).equals("edabeedb");
                if (executable || msi || deb || rpm) {
                    draft.put("type", "SOFTWARE").put("format", executable ? "exe" : msi ? "msi" : deb ? "deb" : "rpm");
                    draft.remove("warnings");
                }
            }
        }
        return draft;
    }
    public MultipartFile stored(String key, String name, long size) {
        return new MultipartFile() {
            public String getName() { return "file"; }
            public String getOriginalFilename() { return name; }
            public String getContentType() { return "application/octet-stream"; }
            public boolean isEmpty() { return size == 0; }
            public long getSize() { return size; }
            public byte[] getBytes() throws IOException {
                if (size > 32 * 1024 * 1024) throw new IOException("Use streaming material access");
                try (var input = getInputStream()) { return input.readAllBytes(); }
            }
            public InputStream getInputStream() { return storage.getObject(key); }
            public void transferTo(File destination) throws IOException { transferTo(destination.toPath()); }
            public void transferTo(Path destination) throws IOException {
                try (var input = getInputStream()) { Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING); }
            }
        };
    }
    public void store(String key, MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new DomainBadRequestException("error.publication.material");
        try (var input = file.getInputStream()) { storage.putObject(key, input, file.getSize(), "application/octet-stream"); }
    }
    public void delete(String key) { if (key != null) storage.deleteObject(key); }
    public void discardAfterCommit(java.util.List<String> keys) {
        Runnable cleanup = () -> keys.forEach(key -> {
            try { delete(key); }
            catch (RuntimeException failure) { org.slf4j.LoggerFactory.getLogger(PublicationMaterialService.class).warn("Staged publication material retained for cleanup: {}", key); }
        });
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) { cleanup.run(); return; }
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
            @Override public void afterCommit() { cleanup.run(); }
        });
    }
    public String fileName(String name) {
        if (name == null) throw new DomainBadRequestException("error.publication.material");
        String base = name.replace('\\', '/'); base = base.substring(base.lastIndexOf('/') + 1);
        if (base.isBlank() || base.length() > 255 || base.indexOf('\0') >= 0) throw new DomainBadRequestException("error.publication.material");
        return base;
    }
}
