package com.iflytek.skillhub.service.publication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.config.SkillPublishProperties;
import com.iflytek.skillhub.domain.publication.*;
import com.iflytek.skillhub.domain.shared.exception.*;
import com.iflytek.skillhub.domain.software.SoftwarePolicyService;
import com.iflytek.skillhub.dto.publication.PublicationUploadRequest;
import com.iflytek.skillhub.storage.ObjectStorageService;
import java.io.*;
import java.security.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Persisted, bounded staging for files larger than servlet multipart limits. */
@Service
public class PublicationUploadAppService {
    private static final int CHUNK_BYTES = 8 * 1024 * 1024;
    private final PublicationTaskRepository tasks;
    private final ObjectMapper mapper;
    private final ObjectStorageService storage;
    private final PublicationMaterialService materials;
    private final SoftwarePolicyService policies;
    private final SkillPublishProperties skillLimits;
    private final TransactionTemplate transactions;
    private final com.iflytek.skillhub.auth.rbac.RbacService rbac;
    private final com.iflytek.skillhub.service.SoftwareUploadAppService softwareUploads;
    public PublicationUploadAppService(PublicationTaskRepository tasks, ObjectMapper mapper,
            ObjectStorageService storage, PublicationMaterialService materials, SoftwarePolicyService policies,
            SkillPublishProperties skillLimits, PlatformTransactionManager manager, com.iflytek.skillhub.auth.rbac.RbacService rbac,
            com.iflytek.skillhub.service.SoftwareUploadAppService softwareUploads) {
        this.tasks = tasks; this.mapper = mapper; this.storage = storage; this.materials = materials;
        this.policies = policies; this.skillLimits = skillLimits; this.transactions = new TransactionTemplate(manager);
        this.rbac = rbac;
        this.softwareUploads = softwareUploads;
    }
    public void begin(String id, long revision, PublicationUploadRequest request, PlatformPrincipal actor) {
        transactions.executeWithoutResult(tx -> {
            var task = locked(id, actor.userId());
            checkRevision(task, revision);
            requireEditable(task);
            validateSize(actor.userId(), request.byteSize());
            var progress = object(task.getProgressJson());
            var existing = progress.path("materialUpload");
            if (!request.replace() && existing.path("fileName").asText().equals(request.fileName())
                    && existing.path("totalBytes").asLong() == request.byteSize()) return;
            var upload = mapper.createObjectNode().put("fileName", materials.fileName(request.fileName()))
                    .put("totalBytes", request.byteSize()).put("uploadedBytes", 0).put("chunkSizeBytes", CHUNK_BYTES)
                    .put("totalChunks", (request.byteSize() + CHUNK_BYTES - 1) / CHUNK_BYTES)
                    .put("prefix", "publications/" + id + "/staging/" + UUID.randomUUID());
            upload.putArray("chunkDigests");
            if (task.getMaterialKey() != null) upload.put("previousMaterial", task.getMaterialKey());
            progress.set("materialUpload", upload);
            progress.put("phase", "UPLOADING").remove("error");
            task.attach(null, materials.fileName(request.fileName()), request.byteSize());
            task.updateProgress(json(progress), "DRAFT");
        });
    }
    public void validateSize(String owner, long byteSize) {
        var currentRoles = rbac.getUserRoleCodes(owner);
        long maximum = currentRoles.contains("SOFTWARE_ADMIN") || currentRoles.contains("SUPER_ADMIN")
                ? Optional.ofNullable(policies.findActive()).map(p -> p.config().maxPackageBytes()).orElse(skillLimits.getMaxPackageSize())
                : skillLimits.getMaxPackageSize();
        if (byteSize <= 0 || byteSize > maximum) throw new DomainBadRequestException("error.publication.materialSize", maximum);
    }
    public void chunk(String id, long revision, int number, InputStream body, String owner) {
        transactions.executeWithoutResult(tx -> {
            var task = locked(id, owner);
            requireEditable(task);
            var progress = object(task.getProgressJson());
            var upload = progress.path("materialUpload");
            if (!upload.isObject() || number < 1 || number > upload.path("totalChunks").asInt()) throw new DomainBadRequestException("error.publication.material");
            long offset = (number - 1L) * CHUNK_BYTES;
            int length = Math.toIntExact(Math.min(CHUNK_BYTES, upload.path("totalBytes").asLong() - offset));
            byte[] bytes;
            try { bytes = body.readNBytes(length + 1); }
            catch (IOException e) { throw new UncheckedIOException(e); }
            if (bytes.length != length) throw new DomainBadRequestException("error.publication.material");
            String digest = digest(bytes);
            var digests = ((ObjectNode) upload).withArray("chunkDigests");
            if (number <= digests.size()) {
                if (!digests.get(number - 1).asText().equals(digest)) throw new DomainConflictException("error.publication.conflict");
                return; // A lost response may replay identical bytes with the previous revision.
            }
            checkRevision(task, revision);
            if (number != digests.size() + 1) throw new DomainConflictException("error.publication.conflict");
            String key = part(upload.path("prefix").asText(), number, digest);
            storage.putObject(key, new ByteArrayInputStream(bytes), bytes.length, "application/octet-stream");
            digests.add(digest);
            ((ObjectNode) upload).put("uploadedBytes", offset + length);
            task.updateProgress(json(progress), "DRAFT");
        });
    }
    public void complete(String id, long revision, String owner) {
        var cleanup = new ArrayList<String>();
        transactions.executeWithoutResult(tx -> {
            var task = locked(id, owner);
            checkRevision(task, revision); requireEditable(task);
            var progress = object(task.getProgressJson());
            var upload = progress.path("materialUpload");
            if (!upload.isObject() || upload.path("chunkDigests").size() != upload.path("totalChunks").asInt()) throw new DomainBadRequestException("error.publication.material");
            var keys = new ArrayList<String>();
            for (int i = 0; i < upload.path("chunkDigests").size(); i++) keys.add(part(upload.path("prefix").asText(), i + 1, upload.path("chunkDigests").get(i).asText()));
            var iterator = keys.iterator();
            var streams = new Enumeration<InputStream>() {
                public boolean hasMoreElements() { return iterator.hasNext(); }
                public InputStream nextElement() { return storage.getObject(iterator.next()); }
            };
            String key = upload.path("prefix").asText() + "/complete";
            try (var combined = new SequenceInputStream(streams)) {
                storage.putObject(key, combined, task.getByteSize(), "application/octet-stream");
                var detection = materials.detect(materials.stored(key, task.getFileName(), task.getByteSize()));
                var warnings = detection.remove("warnings");
                var draft = object(task.getDraftJson());
                boolean retained = progress.has("appId") || progress.path("results").size() > 0;
                String originalType = draft.path("type").asText();
                if (retained && !detection.path("type").asText().equals("UNKNOWN") && !detection.path("type").asText().equals(originalType)) {
                    throw new DomainBadRequestException("error.publication.type");
                }
                var visibility = draft.get("visibility");
                draft.setAll(detection);
                if (retained) draft.put("type", originalType);
                if (visibility != null && !visibility.isNull()) draft.set("visibility", visibility);
                if (retained && originalType.equals("PLUGIN")) {
                    if (progress.has("preview")) progress.set("priorPreview", progress.remove("preview"));
                    progress.put("revalidateMaterial", true);
                }
                if (retained && originalType.equals("SOFTWARE")) {
                    if (progress.has("uploadId") && !progress.has("packageId")) {
                        var old = softwareUploads.progress(progress.path("uploadId").asLong(), owner);
                        if (old.status().equals("ACTIVE")) softwareUploads.cancel(old.sessionId(), owner);
                    }
                    var rows = progress.path("results");
                    int active = progress.path("activeResultIndex").asInt(-1);
                    if (active >= 0 && active < rows.size() && rows.get(active).hasNonNull("releaseId")) progress.remove("activeResultIndex");
                    progress.remove(List.of("uploadId", "packageId", "uploadedBytes"));
                    progress.put("revalidateMaterial", true);
                }
                task.attach(key, task.getFileName(), task.getByteSize()); task.updateDraft(json(draft));
                progress.remove("materialUpload");
                progress.put("detectedType", detection.path("type").asText()).put("phase", "MATERIAL");
                progress.remove("warnings");
                if (warnings != null) progress.set("warnings", warnings);
                task.updateProgress(json(progress), "DRAFT");
                cleanup.addAll(keys);
                if (upload.hasNonNull("previousMaterial")) cleanup.add(upload.path("previousMaterial").asText());
            } catch (IOException e) { throw new UncheckedIOException(e); }
        });
        for (String key : cleanup) {
            try { storage.deleteObject(key); } catch (RuntimeException ignored) { /* Retain isolated staging objects for operator cleanup. */ }
        }
    }
    private void requireEditable(PublicationTask task) {
        if (task.getByteSize() > skillLimits.getMaxPackageSize()) {
            var roles = rbac.getUserRoleCodes(task.getOwnerId());
            if (!roles.contains("SUPER_ADMIN") && !roles.contains("SOFTWARE_ADMIN")) throw new DomainForbiddenException("error.publication.softwareRole");
        }
        if (!task.getStatus().equals("DRAFT") && !task.getStatus().equals("FAILED") && !task.getStatus().equals("WAITING")) throw new DomainConflictException("error.publication.state");
        var progress = object(task.getProgressJson());
        if (progress.has("appId") || progress.path("results").size() > 0) {
            String type = object(task.getDraftJson()).path("type").asText();
            boolean retry = task.getStatus().equals("FAILED") || task.getStatus().equals("WAITING")
                    || progress.has("materialUpload") || progress.path("revalidateMaterial").asBoolean();
            if (!retry || (!type.equals("PLUGIN") && !type.equals("SOFTWARE"))) throw new DomainConflictException("error.publication.bound");
        }
    }
    private PublicationTask locked(String id, String owner) {
        return tasks.lockById(id).filter(t -> t.getOwnerId().equals(owner)).orElseThrow(() -> new DomainNotFoundException("error.publication.notFound"));
    }
    private void checkRevision(PublicationTask task, long revision) {
        if (task.getRevision() != revision) throw new DomainConflictException("error.publication.conflict");
    }
    private String part(String prefix, int number, String digest) { return prefix + "/" + number + "-" + digest; }
    private String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private ObjectNode object(String value) {
        try { return (ObjectNode) mapper.readTree(value); } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); } catch (IOException e) { throw new UncheckedIOException(e); }
    }
}
