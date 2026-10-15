package com.iflytek.skillhub.service.plugin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.plugin.*;
import com.iflytek.skillhub.domain.plugin.service.*;
import com.iflytek.skillhub.domain.shared.exception.*;
import com.iflytek.skillhub.dto.plugin.PluginImportDtos.*;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.repository.PluginImportQueryRepository;
import com.iflytek.skillhub.repository.PluginImportQueryRepository.StoredCandidate;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.storage.ObjectStorageService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Upload/preview/selected import orchestration. Each selected candidate commits independently. */
@Service
public class PluginOfflineImportAppService {
    private final PluginImportBatchRepository batches;
    private final PluginImportSettingsRepository settings;
    private final PluginOfflineImportWorkflow workflow;
    private final OfflinePluginScanner scanner;
    private final ObjectStorageService storage;
    private final PluginImportQueryRepository queries;
    private final AuditLogService audit;
    private final RequestIdAccessor requestIds;
    private final TransactionTemplate transactions;
    private final ObjectMapper mapper;

    public PluginOfflineImportAppService(
            PluginImportBatchRepository batches,
            PluginImportSettingsRepository settings,
            PluginOfflineImportWorkflow workflow,
            OfflinePluginScanner scanner,
            ObjectStorageService storage,
            PluginImportQueryRepository queries,
            AuditLogService audit,
            RequestIdAccessor requestIds,
            PlatformTransactionManager manager,
            ObjectMapper mapper) {
        this.batches = batches;
        this.settings = settings;
        this.workflow = workflow;
        this.scanner = scanner;
        this.storage = storage;
        this.queries = queries;
        this.audit = audit;
        this.requestIds = requestIds;
        this.transactions = new TransactionTemplate(manager);
        this.mapper = mapper;
    }

    public PluginImportLimits limits() {
        return settings.findById(1L)
                .map(
                        s -> {
                            try {
                                return mapper.readValue(
                                        s.getLimitsJson(), PluginImportLimits.class);
                            } catch (IOException ex) {
                                throw new IllegalStateException(
                                        "Invalid stored plugin budgets", ex);
                            }
                        })
                .orElseGet(PluginImportLimits::defaults);
    }

    public PluginImportLimits configure(
            PluginImportLimits limits, PluginActor actor, AuditRequestContext context) {
        if (!actor.reviewer()) throw new DomainForbiddenException("error.plugin.permission");
        limits.validate();
        transactions.executeWithoutResult(
                tx -> {
                    try {
                        var value =
                                settings.findById(1L)
                                        .orElseGet(() -> new PluginImportSettings("{}"));
                        value.update(mapper.writeValueAsString(limits));
                        settings.save(value);
                        record("plugin.import.configure", null, actor, context);
                    } catch (IOException ex) {
                        throw new IllegalStateException(ex);
                    }
                });
        return limits;
    }

    public ImportPreviewView preview(
            Long namespace,
            boolean repository,
            String format,
            InputStream input,
            PluginActor actor,
            AuditRequestContext context)
            throws IOException {
        workflow.requireImport(namespace, repository, actor);
        PluginImportLimits limits = limits();
        Path upload =
                Files.createTempFile("astronhub-upload-", "." + format.toLowerCase(Locale.ROOT));
        String id = UUID.randomUUID().toString();
        List<String> staged = new ArrayList<>();
        try {
            long max = repository ? limits.repositoryBytes() : limits.packageBytes(), size = 0;
            try (OutputStream out = Files.newOutputStream(upload)) {
                byte[] b = new byte[8192];
                int n;
                while ((n = input.read(b)) != -1) {
                    size += n;
                    if (size > max)
                        throw new DomainBadRequestException("error.plugin.archive", "size");
                    out.write(b, 0, n);
                }
            }
            try (var scan = scanner.scan(upload, format, limits)) {
                if (!repository
                        && scan.candidates().stream()
                                        .filter(c -> c.error() == null)
                                        .map(OfflinePluginScanner.Candidate::directory)
                                        .distinct()
                                        .count()
                                > 1)
                    throw new DomainBadRequestException("error.plugin.importRepositoryRequired");
                List<StoredCandidate> values = new ArrayList<>();
                for (var candidate : scan.candidates()) {
                    String key = null;
                    if (candidate.artifact() != null) {
                        key =
                                "plugins/imports/"
                                        + id
                                        + "/"
                                        + candidate.id()
                                        + "."
                                        + candidate.format().toLowerCase(Locale.ROOT);
                        staged.add(key);
                        try (InputStream artifact = Files.newInputStream(candidate.artifact())) {
                            storage.putObject(
                                    key,
                                    artifact,
                                    Files.size(candidate.artifact()),
                                    candidate.format().equals("TGZ")
                                            ? "application/gzip"
                                            : "application/zip");
                        }
                    }
                    values.add(
                            new StoredCandidate(
                                    candidate.id(),
                                    candidate.directory(),
                                    candidate.runtime(),
                                    candidate.nativeName(),
                                    candidate.version(),
                                    candidate.suggestedSlug(),
                                    candidate.sha256(),
                                    candidate.format(),
                                    candidate.inventoryJson(),
                                    candidate.manifestJson(),
                                    candidate.error(),
                                    key));
                }
                String json = mapper.writeValueAsString(values);
                Instant expires = Instant.now().plus(Duration.ofHours(24));
                PluginImportBatch batch =
                        new PluginImportBatch(
                                id,
                                namespace,
                                actor.userId(),
                                repository,
                                json,
                                mapper.writeValueAsString(limits),
                                expires);
                transactions.executeWithoutResult(
                        tx -> {
                            batches.save(batch);
                            record("plugin.import.preview", null, actor, context);
                        });
                staged.clear();
                return queries.preview(batch, values, actor);
            }
        } finally {
            Files.deleteIfExists(upload);
            for (String key : staged) {
                try {
                    storage.deleteObject(key);
                } catch (RuntimeException ignored) {
                    /* Orphan retained under isolated import prefix for operator cleanup. */
                }
            }
        }
    }

    public ImportPreviewView get(String id, PluginActor actor) {
        PluginImportBatch batch = requireBatch(id, actor);
        return queries.preview(batch, stored(batch), actor);
    }

    public ImportResultsView apply(
            String id,
            ImportSelectionRequest request,
            PluginActor actor,
            AuditRequestContext context) {
        PluginImportBatch batch = requireBatch(id, actor);
        List<StoredCandidate> candidates = stored(batch);
        List<ImportResultView> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ImportSelection selection : request.selections()) {
            if (!seen.add(selection.candidateId()))
                throw new DomainBadRequestException("error.plugin.archiveConflict");
            StoredCandidate candidate =
                    candidates.stream()
                            .filter(c -> c.id().equals(selection.candidateId()))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new DomainBadRequestException(
                                                    "error.plugin.importCandidate"));
            if (candidate.error() != null) {
                results.add(
                        new ImportResultView(
                                candidate.id(), "INVALID", null, null, candidate.error()));
                continue;
            }
            try {
                // Independent durable artifact: scan expiry cleanup cannot remove an imported
                // release's bytes.
                Path temp = Files.createTempFile("astronhub-candidate-", ".bin");
                String target =
                        "plugins/archives/"
                                + UUID.randomUUID()
                                + "/"
                                + candidate.sha256()
                                + "."
                                + candidate.format().toLowerCase(Locale.ROOT);
                boolean keep = false;
                try {
                    try (InputStream source = storage.getObject(candidate.objectKey());
                            OutputStream out = Files.newOutputStream(temp)) {
                        byte[] b = new byte[8192];
                        int n;
                        long size = 0;
                        while ((n = source.read(b)) != -1) {
                            size += n;
                            if (size > snapshotLimits(batch).packageBytes())
                                throw new DomainBadRequestException("error.plugin.archive", "size");
                            out.write(b, 0, n);
                        }
                    }
                    if (!candidate.sha256().equals(OfflinePluginScanner.sha256(temp)))
                        throw new DomainBadRequestException("error.plugin.archive", "digest");
                    try (InputStream artifact = Files.newInputStream(temp)) {
                        storage.putObject(
                                target,
                                artifact,
                                Files.size(temp),
                                candidate.format().equals("TGZ")
                                        ? "application/gzip"
                                        : "application/zip");
                    }
                    var result =
                            transactions.execute(
                                    tx -> {
                                        var imported =
                                                workflow.importCandidate(
                                                        batch.getNamespaceId(),
                                                        batch.isRepository(),
                                                        selection.projectId(),
                                                        selection.slug() == null
                                                                ? candidate.suggestedSlug()
                                                                : selection.slug(),
                                                        selection.visibility() == null
                                                                ? PluginVisibility.PRIVATE
                                                                : selection.visibility(),
                                                        candidate.runtime(),
                                                        candidate.nativeName(),
                                                        candidate.version(),
                                                        candidate.sha256(),
                                                        target,
                                                        candidate.inventoryJson(),
                                                        candidate.manifestJson(),
                                                        actor);
                                        record(
                                                "plugin.import."
                                                        + imported.status()
                                                                .toLowerCase(Locale.ROOT),
                                                imported.releaseId(),
                                                actor,
                                                context);
                                        return imported;
                                    });
                    keep = result != null && result.status().equals("IMPORTED");
                    results.add(
                            new ImportResultView(
                                    candidate.id(),
                                    result.status(),
                                    result.projectId(),
                                    result.releaseId(),
                                    null));
                } finally {
                    Files.deleteIfExists(temp);
                    if (!keep) storage.deleteObject(target);
                }
            } catch (Exception ex) {
                results.add(
                        new ImportResultView(
                                candidate.id(), "FAILED", null, null, "IMPORT_FAILED"));
            }
        }
        return new ImportResultsView(List.copyOf(results));
    }

    private PluginImportBatch requireBatch(String id, PluginActor actor) {
        PluginImportBatch batch =
                batches.findById(id)
                        .orElseThrow(
                                () -> new DomainNotFoundException("error.plugin.importNotFound"));
        if (!batch.getOwnerId().equals(actor.userId()))
            throw new DomainForbiddenException("error.plugin.permission");
        workflow.requireImport(batch.getNamespaceId(), batch.isRepository(), actor);
        if (batch.getExpiresAt().isBefore(Instant.now()))
            throw new DomainBadRequestException("error.plugin.importExpired");
        return batch;
    }

    @org.springframework.scheduling.annotation.Scheduled(
            fixedDelayString = "${skillhub.plugin.import-cleanup-delay-ms:3600000}")
    public void cleanupExpired() {
        for (var batch : batches.findTop100ByExpiresAtBefore(Instant.now())) {
            boolean removed = true;
            for (var candidate : stored(batch))
                if (candidate.objectKey() != null)
                    try {
                        storage.deleteObject(candidate.objectKey());
                    } catch (RuntimeException ex) {
                        removed = false;
                    }
            if (removed) transactions.executeWithoutResult(tx -> batches.deleteById(batch.getId()));
        }
    }

    private PluginImportLimits snapshotLimits(PluginImportBatch batch) {
        try {
            return mapper.readValue(batch.getLimitsJson(), PluginImportLimits.class);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private List<StoredCandidate> stored(PluginImportBatch batch) {
        try {
            return mapper.readValue(
                    batch.getPreviewJson(), new TypeReference<List<StoredCandidate>>() {});
        } catch (IOException ex) {
            throw new IllegalStateException("Invalid stored plugin preview", ex);
        }
    }

    private void record(String action, Long id, PluginActor actor, AuditRequestContext context) {
        audit.record(
                actor.userId(),
                action,
                "PLUGIN_IMPORT",
                id,
                requestIds.current(),
                context.clientIp(),
                context.userAgent(),
                "{}");
    }
}
