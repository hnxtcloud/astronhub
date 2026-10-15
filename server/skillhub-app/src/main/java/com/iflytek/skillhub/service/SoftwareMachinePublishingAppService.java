package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.software.*;
import com.iflytek.skillhub.dto.software.*;
import java.util.List;
import java.util.Objects;
import java.io.InputStream;
import com.iflytek.skillhub.domain.shared.exception.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/** Machine surface reuses governed software workflows after exact application authorization. */
@Service
public class SoftwareMachinePublishingAppService {
    private final SoftwarePublisherAppService publishers;
    private final SoftwareAdminAppService admin;
    private final SoftwareReleaseRepository catalog;
    private final SoftwareUploadAppService uploads;
    private final SoftwareUploadSessionService sessions;
    private final SoftwarePackageService packages;
    private final SoftwareInspectionTaskRepository tasks;
    private final SoftwareInspectionAppService inspections;
    private final SoftwareUpdateAppService updates;
    public SoftwareMachinePublishingAppService(SoftwarePublisherAppService publishers, SoftwareAdminAppService admin,
            SoftwareReleaseRepository catalog, SoftwareUploadAppService uploads, SoftwareUploadSessionService sessions,
            SoftwarePackageService packages, SoftwareInspectionTaskRepository tasks, SoftwareInspectionAppService inspections,
            SoftwareUpdateAppService updates) {
        this.publishers = publishers; this.admin = admin;
        this.catalog = catalog; this.uploads = uploads; this.sessions = sessions; this.packages = packages;
        this.tasks = tasks; this.inspections = inspections; this.updates = updates;
    }
    public List<SoftwareReleaseResponse> releases(Long appId, Authentication auth) {
        publishers.requireMachine(appId, auth);
        return admin.listReleases(appId).stream().map(release -> new SoftwareReleaseResponse(release.getId(),
                release.getDisplayVersion(), release.getReleaseNotes(), null, 0, List.of(), List.of())).toList();
    }
    public SoftwareReleaseResponse createRelease(Long appId, SoftwareReleaseCreateRequest body, Authentication auth, AuditRequestContext context) {
        String actor = publishers.requireMachine(appId, auth);
        var existing = catalog.findByApplicationIdAndDisplayVersion(appId, body.displayVersion().trim());
        var release = existing.orElseGet(() -> admin.createRelease(appId, body, actor, context));
        if (!Objects.equals(release.getReleaseNotes(), body.releaseNotes())) throw new DomainConflictException("error.software.update.identityConflict");
        return new SoftwareReleaseResponse(release.getId(), release.getDisplayVersion(), release.getReleaseNotes(), null, 0, List.of(), List.of());
    }
    public List<SoftwarePackageResponse> packages(Long appId, Long catalogId, Authentication auth) {
        publishers.requireMachine(appId, auth); requireCatalog(appId, catalogId);
        return admin.listPackages(catalogId).stream().map(this::project).toList();
    }
    public SoftwareUploadSessionResponse createUpload(Long appId, SoftwareUploadSessionCreateRequest body, Authentication auth) {
        String actor = publishers.requireMachine(appId, auth); requireCatalog(appId, body.releaseId());
        return uploads.createSession(body, actor);
    }
    public SoftwareUploadProgressResponse progress(Long appId, Long id, Authentication auth) {
        String actor = publishers.requireMachine(appId, auth); requireUpload(appId, id);
        return uploads.progress(id, actor);
    }
    public SoftwareChunkReceiptResponse chunk(Long appId, Long id, int number, long bytes, InputStream body, String digest, Authentication auth) {
        String actor = publishers.requireMachine(appId, auth); requireUpload(appId, id);
        var receipt = uploads.putChunk(id, number, bytes, body, actor);
        if (digest != null && !digest.equalsIgnoreCase(receipt.sha256())) throw new DomainBadRequestException("error.software.upload.digestMismatch", number);
        return receipt;
    }
    public SoftwareUploadCompleteResponse complete(Long appId, Long id, Authentication auth) {
        String actor = publishers.requireMachine(appId, auth); requireUpload(appId, id);
        return uploads.complete(id, actor);
    }
    public SoftwareUploadSessionResponse cancel(Long appId, Long id, Authentication auth) {
        String actor = publishers.requireMachine(appId, auth); requireUpload(appId, id);
        return uploads.cancel(id, actor);
    }
    public SoftwareMachinePackageResponse packageStatus(Long appId, Long id, Authentication auth) {
        publishers.requireMachine(appId, auth); var pkg = requirePackage(appId, id);
        var inspection = tasks.findByPackageRevisionId(id).map(task -> inspections.detail(task.getId())).orElse(null);
        return new SoftwareMachinePackageResponse(project(pkg), inspection);
    }
    public SoftwarePackageResponse confirm(Long appId, Long id, JsonNode rawFields, Authentication auth, AuditRequestContext context) {
        String actor = publishers.requireMachine(appId, auth); var pkg = requirePackage(appId, id);
        if (pkg.isConfirmed()) return project(pkg);
        return project(tasks.findByPackageRevisionId(id)
                .map(task -> inspections.confirmMetadata(task.getId(), rawFields, actor, context))
                .orElseGet(() -> inspections.confirmManualMetadata(id, rawFields, actor, context)));
    }
    public SoftwarePackageResponse publishPackage(Long appId, Long id, Authentication auth, AuditRequestContext context) {
        String actor = publishers.requireMachine(appId, auth); var pkg = requirePackage(appId, id);
        return project(pkg.getStatus() == SoftwarePackageStatus.PUBLISHED ? pkg : admin.publishPackage(id, actor, context));
    }
    public SoftwareInspectionDetailResponse retryInspection(Long appId, Long id, Authentication auth, AuditRequestContext context) {
        String actor = publishers.requireMachine(appId, auth); requirePackage(appId, id);
        var task = tasks.findByPackageRevisionId(id).orElseThrow(() -> new DomainNotFoundException("error.software.inspection.taskNotFound", id));
        inspections.retry(task.getId(), actor, context); return inspections.detail(task.getId());
    }
    public SoftwareUpdateReleaseResponse createSnapshot(Long appId, SoftwareUpdateCreateRequest body, Authentication auth) {
        String actor = publishers.requireMachine(appId, auth); requireCatalog(appId, body.catalogReleaseId());
        return updates.create(body, actor);
    }
    public List<SoftwareUpdateReleaseResponse> snapshots(Long appId, Long catalogId, Authentication auth) {
        publishers.requireMachine(appId, auth); requireCatalog(appId, catalogId); return updates.list(catalogId);
    }
    public SoftwareUpdateReleaseResponse snapshot(Long appId, Long id, Authentication auth) {
        publishers.requireMachine(appId, auth); requireSnapshot(appId, id); return updates.get(id);
    }
    public SoftwareReleaseAssetResponse link(Long appId, Long id, SoftwareUpdatePackageLinkRequest body, Authentication auth) {
        String actor = publishers.requireMachine(appId, auth); var snapshot = requireSnapshot(appId, id);
        var pkg = requirePackage(appId, body.packageRevisionId());
        if (!Objects.equals(pkg.getReleaseId(), snapshot.getCatalogReleaseId())) throw forbidden();
        return updates.linkPackage(id, body.packageRevisionId(), body.relativePath(), actor);
    }
    public SoftwareReleaseAssetResponse asset(Long appId, Long id, String path, String role, long size, InputStream body, Authentication auth) {
        String actor = publishers.requireMachine(appId, auth); requireSnapshot(appId, id);
        return updates.uploadAsset(id, path, role, size, body, actor);
    }
    public SoftwareUpdateValidationResponse validate(Long appId, Long id, Authentication auth) {
        publishers.requireMachine(appId, auth); requireSnapshot(appId, id); updates.validate(id);
        return new SoftwareUpdateValidationResponse(true);
    }
    public SoftwareUpdateReleaseResponse publish(Long appId, Long id, Authentication auth) {
        String actor = publishers.requireMachine(appId, auth); var snapshot = requireSnapshot(appId, id);
        if ("PUBLISHED".equals(snapshot.getStatus())) { updates.requireGovernedPackages(snapshot); return updates.get(id); }
        return updates.publish(id, actor);
    }
    public SoftwareUpdateChannelResponse channel(Long appId, SoftwareUpdateChannelRequest body, Authentication auth) {
        String actor = publishers.requireMachine(appId, auth);
        if (body.targetReleaseId() != null) requireSnapshot(appId, body.targetReleaseId());
        return updates.saveChannel(appId, body, actor);
    }
    private SoftwareRelease requireCatalog(Long app, Long id) {
        var release = catalog.findById(id).orElseThrow(() -> new DomainNotFoundException("error.software.release.notFound", id));
        if (!Objects.equals(app, release.getApplicationId())) throw forbidden(); return release;
    }
    private void requireUpload(Long app, Long id) { if (!Objects.equals(app, sessions.requireSession(id).getApplicationId())) throw forbidden(); }
    private SoftwarePackageRevision requirePackage(Long app, Long id) {
        var pkg = packages.requirePackage(id); if (!Objects.equals(app, pkg.getApplicationId())) throw forbidden(); return pkg;
    }
    private SoftwareUpdateRelease requireSnapshot(Long app, Long id) {
        var release = updates.requireRelease(id); if (!Objects.equals(app, release.getApplicationId())) throw forbidden(); return release;
    }
    private SoftwarePackageResponse project(SoftwarePackageRevision pkg) {
        return new SoftwarePackageResponse(pkg.getId(), pkg.getRevisionNo(), pkg.getStatus().name(), pkg.getTargetOs().name(),
                pkg.getTargetArch().name(), pkg.getTargetDistro(), pkg.getFormat().name(), pkg.getOriginalFullVersion(), pkg.getFileName(),
                pkg.getByteSize(), pkg.getSha256(), pkg.getApplicabilityNote(), pkg.getConfirmedBy(), pkg.getConfirmedAt(), pkg.getPublishedAt());
    }
    private static DomainForbiddenException forbidden() { return new DomainForbiddenException("error.software.publisher.forbidden"); }
}
