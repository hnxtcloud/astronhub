package com.iflytek.skillhub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.software.SoftwareApplication;
import com.iflytek.skillhub.domain.software.SoftwareApplicationService;
import com.iflytek.skillhub.domain.software.SoftwareCategory;
import com.iflytek.skillhub.domain.software.SoftwareCategoryService;
import com.iflytek.skillhub.domain.software.SoftwarePackageRevision;
import com.iflytek.skillhub.domain.software.SoftwarePackageRevisionRepository;
import com.iflytek.skillhub.domain.software.SoftwarePackageService;
import com.iflytek.skillhub.domain.software.SoftwareRecommendation;
import com.iflytek.skillhub.domain.software.SoftwareRecommendationService;
import com.iflytek.skillhub.domain.software.SoftwareRelease;
import com.iflytek.skillhub.domain.software.SoftwareReleaseRepository;
import com.iflytek.skillhub.domain.software.SoftwareReleaseService;
import com.iflytek.skillhub.dto.software.SoftwareAppCreateRequest;
import com.iflytek.skillhub.dto.software.SoftwareAppUpdateRequest;
import com.iflytek.skillhub.dto.software.SoftwareCategoryCreateRequest;
import com.iflytek.skillhub.dto.software.SoftwareCategoryResponse;
import com.iflytek.skillhub.dto.software.SoftwareCategoryUpdateRequest;
import com.iflytek.skillhub.dto.software.SoftwarePackageNoteRequest;
import com.iflytek.skillhub.dto.software.SoftwareRecommendationItemRequest;
import com.iflytek.skillhub.dto.software.SoftwareRecommendationResponse;
import com.iflytek.skillhub.dto.software.SoftwareReleaseCreateRequest;
import com.iflytek.skillhub.dto.software.SoftwareReleaseUpdateRequest;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Software-center administration use cases: applications, releases, package
 * governance, recommendations, and the category dictionary. Controllers stay
 * transport-only; this service orchestrates domain services and audit.
 */
@Service
public class SoftwareAdminAppService {

    private static final ObjectMapper AUDIT_MAPPER = new ObjectMapper();

    private final SoftwareApplicationService applicationService;
    private final SoftwareReleaseService releaseService;
    private final SoftwarePackageService packageService;
    private final SoftwareRecommendationService recommendationService;
    private final SoftwareCategoryService categoryService;
    private final SoftwareReleaseRepository releaseRepository;
    private final SoftwarePackageRevisionRepository packageRevisionRepository;
    private final AuditLogService auditLogService;
    private final RequestIdAccessor requestIdAccessor;

    public SoftwareAdminAppService(SoftwareApplicationService applicationService,
                                   SoftwareReleaseService releaseService,
                                   SoftwarePackageService packageService,
                                   SoftwareRecommendationService recommendationService,
                                   SoftwareCategoryService categoryService,
                                   SoftwareReleaseRepository releaseRepository,
                                   SoftwarePackageRevisionRepository packageRevisionRepository,
                                   AuditLogService auditLogService,
                                   RequestIdAccessor requestIdAccessor) {
        this.applicationService = applicationService;
        this.releaseService = releaseService;
        this.packageService = packageService;
        this.recommendationService = recommendationService;
        this.categoryService = categoryService;
        this.releaseRepository = releaseRepository;
        this.packageRevisionRepository = packageRevisionRepository;
        this.auditLogService = auditLogService;
        this.requestIdAccessor = requestIdAccessor;
    }

    // ---- Applications -------------------------------------------------

    @Transactional
    public SoftwareApplication createApp(SoftwareAppCreateRequest request, String actorId,
                                         AuditRequestContext auditContext) {
        SoftwareApplication app = applicationService.create(request.slug(), request.name(),
                request.description(), request.iconObjectKey(), request.categoryCode(), actorId);
        recordAudit("software.app.create", "SOFTWARE_APP", app.getId(), actorId, auditContext,
                jsonField("slug", app.getSlug()));
        return app;
    }

    @Transactional
    public SoftwareApplication updateApp(Long appId, SoftwareAppUpdateRequest request, String actorId,
                                         AuditRequestContext auditContext) {
        SoftwareApplication app = applicationService.updateProfile(appId, request.name(),
                request.description(), request.iconObjectKey(), request.categoryCode(), actorId);
        recordAudit("software.app.update", "SOFTWARE_APP", app.getId(), actorId, auditContext,
                jsonField("name", app.getName()));
        return app;
    }

    @Transactional
    public SoftwareApplication publishApp(Long appId, String actorId, AuditRequestContext auditContext) {
        SoftwareApplication app = applicationService.publish(appId);
        recordAudit("software.app.publish", "SOFTWARE_APP", appId, actorId, auditContext,
                jsonField("slug", app.getSlug()));
        return app;
    }

    /**
     * Takes the application down: new download requests for all of its packages
     * are refused from this transaction's commit on, and recommendations inside
     * the application are cleared. Already-started transfers may finish.
     */
    @Transactional
    public SoftwareApplication takeDownApp(Long appId, String actorId, AuditRequestContext auditContext) {
        SoftwareApplication app = applicationService.takeDown(appId);
        List<Long> releaseIds = releaseRepository.findByApplicationIdOrderByIdDesc(appId).stream()
                .map(SoftwareRelease::getId)
                .toList();
        int clearedRecommendations = 0;
        for (Long releaseId : releaseIds) {
            clearedRecommendations += recommendationService.findByRelease(releaseId).size();
            recommendationService.replaceAll(releaseId, List.of(), actorId);
        }
        recordAudit("software.app.takedown", "SOFTWARE_APP", appId, actorId, auditContext,
                jsonField("clearedRecommendations", clearedRecommendations));
        return app;
    }

    /** Restores the application without reviving independently taken-down packages. */
    @Transactional
    public SoftwareApplication restoreApp(Long appId, String actorId, AuditRequestContext auditContext) {
        SoftwareApplication app = applicationService.restore(appId);
        recordAudit("software.app.restore", "SOFTWARE_APP", appId, actorId, auditContext, null);
        return app;
    }

    // ---- Releases -----------------------------------------------------

    @Transactional
    public SoftwareRelease createRelease(Long appId, SoftwareReleaseCreateRequest request, String actorId,
                                         AuditRequestContext auditContext) {
        SoftwareRelease release = releaseService.create(appId, request.displayVersion(),
                request.releaseNotes(), actorId);
        recordAudit("software.release.create", "SOFTWARE_RELEASE", release.getId(), actorId, auditContext,
                jsonField("displayVersion", release.getDisplayVersion()));
        return release;
    }

    @Transactional
    public SoftwareRelease updateRelease(Long releaseId, SoftwareReleaseUpdateRequest request, String actorId,
                                         AuditRequestContext auditContext) {
        SoftwareRelease release = releaseService.updateNotes(releaseId, request.releaseNotes());
        recordAudit("software.release.update", "SOFTWARE_RELEASE", releaseId, actorId, auditContext, null);
        return release;
    }

    public List<SoftwareRelease> listReleases(Long appId) {
        applicationService.requireApplication(appId);
        return releaseRepository.findByApplicationIdOrderByIdDesc(appId);
    }

    // ---- Packages -----------------------------------------------------

    public List<SoftwarePackageRevision> listPackages(Long releaseId) {
        releaseService.requireRelease(releaseId);
        return packageRevisionRepository.findByReleaseIdOrderByIdDesc(releaseId);
    }

    @Transactional
    public SoftwarePackageRevision confirmPackage(Long packageId, String actorId,
                                                  AuditRequestContext auditContext) {
        SoftwarePackageRevision revision = packageService.confirm(packageId, actorId);
        recordAudit("software.package.confirm", "SOFTWARE_PACKAGE", packageId, actorId, auditContext, null);
        return revision;
    }

    @Transactional
    public SoftwarePackageRevision updatePackageNote(Long packageId, SoftwarePackageNoteRequest request,
                                                     String actorId, AuditRequestContext auditContext) {
        SoftwarePackageRevision revision = packageService.updateNote(packageId, request.applicabilityNote(),
                actorId);
        recordAudit("software.package.note", "SOFTWARE_PACKAGE", packageId, actorId, auditContext, null);
        return revision;
    }

    @Transactional
    public SoftwarePackageRevision publishPackage(Long packageId, String actorId,
                                                  AuditRequestContext auditContext) {
        SoftwarePackageRevision revision = packageService.publish(packageId);
        recordAudit("software.package.publish", "SOFTWARE_PACKAGE", packageId, actorId, auditContext,
                jsonField("revisionNo", revision.getRevisionNo()));
        return revision;
    }

    /** Package takedown clears recommendations referencing it; history stays. */
    @Transactional
    public SoftwarePackageRevision takeDownPackage(Long packageId, String actorId,
                                                   AuditRequestContext auditContext) {
        SoftwarePackageRevision revision = packageService.takeDown(packageId);
        recordAudit("software.package.takedown", "SOFTWARE_PACKAGE", packageId, actorId, auditContext, null);
        return revision;
    }

    @Transactional
    public SoftwarePackageRevision restorePackage(Long packageId, String actorId,
                                                  AuditRequestContext auditContext) {
        SoftwarePackageRevision revision = packageService.restore(packageId);
        recordAudit("software.package.restore", "SOFTWARE_PACKAGE", packageId, actorId, auditContext, null);
        return revision;
    }

    // ---- Recommendations ----------------------------------------------

    @Transactional
    public List<SoftwareRecommendation> setRecommendations(Long releaseId,
                                                           List<SoftwareRecommendationItemRequest> items,
                                                           String actorId,
                                                           AuditRequestContext auditContext) {
        List<SoftwareRecommendationService.RecommendationRequest> requests = items == null ? List.of()
                : items.stream()
                        .map(item -> new SoftwareRecommendationService.RecommendationRequest(
                                item.packageRevisionId(), item.targetOs(), item.targetArch(),
                                item.targetDistro(), item.format(), item.note()))
                        .toList();
        List<SoftwareRecommendation> saved = recommendationService.replaceAll(releaseId, requests, actorId);
        recordAudit("software.recommendation.set", "SOFTWARE_RELEASE", releaseId, actorId, auditContext,
                jsonField("count", saved.size()));
        return saved;
    }

    public List<SoftwareRecommendationResponse> listRecommendations(Long releaseId) {
        return recommendationService.findByRelease(releaseId).stream()
                .map(this::toRecommendationResponse)
                .toList();
    }

    // ---- Categories ---------------------------------------------------

    @Transactional
    public SoftwareCategoryResponse createCategory(SoftwareCategoryCreateRequest request, String actorId,
                                                   AuditRequestContext auditContext) {
        SoftwareCategory category = categoryService.create(request.code(), request.name(),
                request.sortOrder(), actorId);
        recordAudit("software.category.create", "SOFTWARE_CATEGORY", category.getId(), actorId, auditContext,
                jsonField("code", category.getCode()));
        return toCategoryResponse(category);
    }

    @Transactional
    public SoftwareCategoryResponse updateCategory(Long categoryId, SoftwareCategoryUpdateRequest request,
                                                   String actorId, AuditRequestContext auditContext) {
        SoftwareCategory category = categoryService.update(categoryId, request.name(), request.sortOrder());
        recordAudit("software.category.update", "SOFTWARE_CATEGORY", categoryId, actorId, auditContext, null);
        return toCategoryResponse(category);
    }

    @Transactional
    public void deleteCategory(Long categoryId, String actorId, AuditRequestContext auditContext) {
        SoftwareCategory category = categoryService.requireCategory(categoryId);
        categoryService.delete(categoryId);
        recordAudit("software.category.delete", "SOFTWARE_CATEGORY", categoryId, actorId, auditContext,
                jsonField("code", category.getCode()));
    }

    public List<SoftwareCategoryResponse> listCategories() {
        return categoryService.listAll().stream()
                .map(this::toCategoryResponse)
                .toList();
    }

    private SoftwareCategoryResponse toCategoryResponse(SoftwareCategory category) {
        return new SoftwareCategoryResponse(category.getId(), category.getCode(), category.getName(),
                category.getSortOrder());
    }

    private SoftwareRecommendationResponse toRecommendationResponse(SoftwareRecommendation recommendation) {
        return new SoftwareRecommendationResponse(
                recommendation.getId(), recommendation.getPackageRevisionId(),
                recommendation.getTargetOs().name(), recommendation.getTargetArch().name(),
                recommendation.getTargetDistro(), recommendation.getFormat().name(),
                recommendation.getNote(), recommendation.getCreatedAt()
        );
    }

    private void recordAudit(String action, String targetType, Long targetId, String actorId,
                             AuditRequestContext auditContext, String detailJson) {
        // Persisted in the same transaction as the mutation: a failed audit
        // rolls the business change back so no unaudited governance action
        // can ever commit.
        auditLogService.record(
                actorId,
                action,
                targetType,
                targetId,
                requestIdAccessor.current(),
                auditContext != null ? auditContext.clientIp() : null,
                auditContext != null ? auditContext.userAgent() : null,
                detailJson
        );
    }

    private String jsonField(String key, Object value) {
        try {
            return AUDIT_MAPPER.writeValueAsString(java.util.Map.of(key, value));
        } catch (Exception ex) {
            return null;
        }
    }
}
