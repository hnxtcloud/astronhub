package com.iflytek.skillhub.service.publication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iflytek.skillhub.domain.plugin.service.PluginActor;
import com.iflytek.skillhub.domain.publication.PublicationTask;
import com.iflytek.skillhub.domain.shared.exception.*;
import com.iflytek.skillhub.domain.software.*;
import com.iflytek.skillhub.dto.publication.PublicationDraft;
import com.iflytek.skillhub.dto.software.*;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.SoftwareAdminAppService;
import com.iflytek.skillhub.service.SoftwareUploadAppService;
import com.iflytek.skillhub.service.SoftwareInspectionAppService;
import java.io.ByteArrayInputStream;
import java.util.HashSet;
import org.springframework.stereotype.Service;

/** Creates one resumable checkpoint per application, version, transfer chunk and lifecycle action. */
@Service
public class SoftwarePublicationWorkflow implements PublicationWorkflow {
    private final SoftwareAdminAppService admin;
    private final SoftwareUploadAppService uploads;
    private final SoftwareApplicationRepository applications;
    private final SoftwareReleaseRepository releases;
    private final SoftwarePackageRevisionRepository packages;
    private final SoftwareInspectionTaskRepository inspections;
    private final SoftwareInspectionAppService inspectionApp;
    private final PublicationMaterialService materials;
    private final ObjectMapper mapper;
    public SoftwarePublicationWorkflow(SoftwareAdminAppService admin, SoftwareUploadAppService uploads,
            SoftwareApplicationRepository applications, SoftwareReleaseRepository releases,
            SoftwarePackageRevisionRepository packages, SoftwareInspectionTaskRepository inspections,
            SoftwareInspectionAppService inspectionApp, PublicationMaterialService materials, ObjectMapper mapper) {
        this.admin = admin; this.uploads = uploads; this.applications = applications; this.releases = releases;
        this.packages = packages; this.inspections = inspections; this.inspectionApp = inspectionApp;
        this.materials = materials; this.mapper = mapper;
    }
    public String type() { return "SOFTWARE"; }
    public void cancel(PublicationTask task, ObjectNode progress, String owner) {
        if (progress.has("uploadId") && !progress.has("packageId")) {
            var upload = uploads.progress(progress.path("uploadId").asLong(), owner);
            if (upload.status().equals("ACTIVE")) uploads.cancel(upload.sessionId(), owner);
        }
    }
    public String advance(PublicationTask task, PublicationDraft draft, ObjectNode progress,
            PluginActor actor, AuditRequestContext audit) throws java.io.IOException {
        if (!actor.superAdmin() && !actor.platformRoles().contains("SOFTWARE_ADMIN")) throw new DomainForbiddenException("error.publication.softwareRole");
        if (blank(draft.slug()) || blank(draft.name()) || blank(draft.version()) || blank(draft.format())
                || blank(draft.targetOs()) || blank(draft.targetArch())) throw new DomainBadRequestException("error.publication.required");
        try {
            SoftwarePackageFormat.valueOf(draft.format().toUpperCase(java.util.Locale.ROOT).replace('.', '_').replace('-', '_'));
            SoftwareTargetOs.valueOf(draft.targetOs().toUpperCase(java.util.Locale.ROOT));
            SoftwareTargetArch.valueOf(draft.targetArch().toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException invalid) { throw new DomainBadRequestException("error.publication.required"); }
        if (!progress.has("appId")) {
            SoftwareApplication app;
            if (draft.appId() != null) {
                app = applications.findById(draft.appId()).orElseThrow(() -> new DomainBadRequestException("error.software.app.notFound", draft.appId()));
                if (!app.getSlug().equals(draft.slug())) throw new DomainConflictException("error.publication.target");
            } else {
                if (applications.findBySlug(draft.slug()).isPresent()) throw new DomainConflictException("error.publication.target");
                app = admin.createApp(new SoftwareAppCreateRequest(draft.slug(), draft.name(), draft.description(), null, null), actor.userId(), audit);
            }
            if (app.getStatus() == SoftwareApplicationStatus.TAKEN_DOWN) throw new DomainConflictException("error.publication.target");
            progress.put("appId", app.getId()).put("phase", "APPLICATION");
            var confirmedDraft = (ObjectNode) mapper.valueToTree(draft);
            confirmedDraft.put("appId", app.getId()); task.updateDraft(mapper.writeValueAsString(confirmedDraft));
            receipt(progress, draft).put("resourceId", app.getId()).put("status", "DRAFT");
            return "RUNNING";
        }
        long appId = progress.path("appId").asLong();
        var app = applications.findById(appId).orElseThrow(() -> new DomainBadRequestException("error.software.app.notFound", appId));
        if (app.getStatus() == SoftwareApplicationStatus.TAKEN_DOWN) throw new DomainConflictException("error.publication.target");
        receipt(progress, draft).put("resourceId", appId);
        if (!progress.has("releaseId")) {
            var release = releases.findByApplicationIdAndDisplayVersion(appId, draft.version()).orElseGet(() ->
                    admin.createRelease(appId, new SoftwareReleaseCreateRequest(draft.version(), draft.description()), actor.userId(), audit));
            progress.put("releaseId", release.getId()).put("phase", "VERSION");
            receipt(progress, draft).put("releaseId", release.getId()).put("version", release.getDisplayVersion());
            return "RUNNING";
        }
        if (!progress.has("uploadId")) {
            var upload = uploads.createSession(new SoftwareUploadSessionCreateRequest(progress.path("releaseId").asLong(),
                    task.getFileName(), draft.format(), draft.targetOs(), draft.targetArch(), draft.targetDistro(),
                    null, draft.version(), task.getByteSize()), actor.userId());
            progress.put("uploadId", upload.id()).put("phase", "UPLOADING");
            return "RUNNING";
        }
        if (!progress.has("packageId")) {
            long uploadId = progress.path("uploadId").asLong();
            var upload = uploads.progress(uploadId, actor.userId());
            var received = new HashSet<Integer>();
            upload.chunks().forEach(chunk -> received.add(chunk.chunkNo()));
            for (int chunk = 1; chunk <= upload.totalChunks(); chunk++) {
                if (received.contains(chunk)) continue;
                long offset = (chunk - 1L) * upload.chunkSizeBytes();
                int length = Math.toIntExact(Math.min(upload.chunkSizeBytes(), task.getByteSize() - offset));
                try (var input = materials.stored(task.getMaterialKey(), task.getFileName(), task.getByteSize()).getInputStream()) {
                    input.skipNBytes(offset);
                    byte[] bytes = input.readNBytes(length);
                    if (bytes.length != length) throw new java.io.IOException("Stored material length changed");
                    uploads.putChunk(uploadId, chunk, length, new ByteArrayInputStream(bytes), actor.userId());
                }
                progress.put("phase", "UPLOADING").put("uploadedBytes", offset + length);
                return "RUNNING";
            }
            var completed = uploads.complete(uploadId, actor.userId());
            if (completed.packageRevisionId() == null) throw new DomainConflictException("error.publication.state");
            progress.put("packageId", completed.packageRevisionId()).put("phase", "INSPECTING");
            receipt(progress, draft)
                    .put("resourceId", appId).put("releaseId", progress.path("releaseId").asLong())
                    .put("packageRevisionId", completed.packageRevisionId()).put("status", "DRAFT");
            return "RUNNING";
        }
        long packageId = progress.path("packageId").asLong();
        var pkg = packages.findById(packageId).orElseThrow(() -> new DomainBadRequestException("error.software.package.notFound", packageId));
        if (pkg.getStatus() == SoftwarePackageStatus.TAKEN_DOWN) throw new DomainConflictException("error.publication.target");
        if (!pkg.isConfirmed()) {
            var inspection = inspections.findByPackageRevisionId(packageId);
            if (inspection.isPresent()) {
                switch (inspection.get().getStatus()) {
                    case PENDING, LEASED -> { progress.put("phase", "INSPECTING"); return "WAITING"; }
                    case EXHAUSTED -> throw new DomainConflictException("error.software.package.inspectionIncomplete", packageId);
                    case BASIC_FAILED -> throw new DomainConflictException("error.software.package.inspectionFailed", packageId);
                    case METADATA_FAILED -> {
                        if (blank(draft.rawVersionFields())) { progress.put("phase", "METADATA_REQUIRED"); return "WAITING"; }
                        inspectionApp.confirmMetadata(inspection.get().getId(), mapper.readTree(draft.rawVersionFields()), actor.userId(), audit);
                    }
                    case SUCCEEDED -> admin.confirmPackage(packageId, actor.userId(), audit);
                }
            } else admin.confirmPackage(packageId, actor.userId(), audit);
            progress.put("phase", "CONFIRMED");
            return "RUNNING";
        }
        if (pkg.getStatus() == SoftwarePackageStatus.DRAFT) {
            admin.publishPackage(packageId, actor.userId(), audit);
            progress.put("phase", "PUBLISHING");
            return "RUNNING";
        }
        if (app.getStatus() == SoftwareApplicationStatus.DRAFT) admin.publishApp(appId, actor.userId(), audit);
        receipt(progress, draft).put("status", "PUBLISHED");
        progress.put("phase", "SUBMITTED");
        return "COMPLETE";
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private ObjectNode receipt(ObjectNode progress, PublicationDraft draft) {
        var rows = progress.withArray("results");
        int index = progress.path("activeResultIndex").asInt(-1);
        if (index < 0 || index >= rows.size()) {
            index = rows.size(); progress.put("activeResultIndex", index);
            rows.addObject().put("candidateId", "software-" + index).put("type", "SOFTWARE")
                    .put("slug", draft.slug()).put("version", draft.version()).put("status", "DRAFT");
        }
        return (ObjectNode) rows.get(index);
    }
}
