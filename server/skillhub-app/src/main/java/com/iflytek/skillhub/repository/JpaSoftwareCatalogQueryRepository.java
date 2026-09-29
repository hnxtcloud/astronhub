package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.software.SoftwareApplication;
import com.iflytek.skillhub.domain.software.SoftwareApplicationRepository;
import com.iflytek.skillhub.domain.software.SoftwareApplicationStatus;
import com.iflytek.skillhub.domain.software.SoftwareCategory;
import com.iflytek.skillhub.domain.software.SoftwareCategory;
import com.iflytek.skillhub.domain.software.SoftwareCategoryRepository;
import com.iflytek.skillhub.domain.software.SoftwarePackageRevision;
import com.iflytek.skillhub.domain.software.SoftwarePackageRevisionRepository;
import com.iflytek.skillhub.domain.software.SoftwarePackageStatus;
import com.iflytek.skillhub.domain.software.SoftwareRecommendation;
import com.iflytek.skillhub.domain.software.SoftwareRecommendationRepository;
import com.iflytek.skillhub.domain.software.SoftwareRelease;
import com.iflytek.skillhub.domain.software.SoftwareReleaseRepository;
import com.iflytek.skillhub.domain.software.SoftwareTargetArch;
import com.iflytek.skillhub.domain.software.SoftwareTargetOs;
import com.iflytek.skillhub.dto.software.SoftwareAppAdminSummaryResponse;
import com.iflytek.skillhub.dto.software.SoftwareAppDetailResponse;
import com.iflytek.skillhub.dto.software.SoftwareAppSummaryResponse;
import com.iflytek.skillhub.dto.software.SoftwarePackageResponse;
import com.iflytek.skillhub.dto.software.SoftwareRecommendationResponse;
import com.iflytek.skillhub.dto.software.SoftwareReleaseResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * Builds the portal software catalog read model across software aggregates and
 * the category dictionary. EntityManager is used because these presentation
 * queries join tables owned by independent aggregates; putting the join in a
 * domain repository would invert the module boundary. Only published, visible
 * content is ever projected here.
 */
@Repository
public class JpaSoftwareCatalogQueryRepository implements SoftwareCatalogQueryRepository {

    private static final String APPS_FROM = """
            from SoftwareApplication a
            left join SoftwareCategory c on c.code = a.categoryCode
            where a.status = com.iflytek.skillhub.domain.software.SoftwareApplicationStatus.PUBLISHED
            """;

    private final EntityManager entityManager;
    private final SoftwareApplicationRepository applicationRepository;
    private final SoftwareReleaseRepository releaseRepository;
    private final SoftwarePackageRevisionRepository packageRepository;
    private final SoftwareRecommendationRepository recommendationRepository;
    private final SoftwareCategoryRepository categoryRepository;

    public JpaSoftwareCatalogQueryRepository(EntityManager entityManager,
                                             SoftwareApplicationRepository applicationRepository,
                                             SoftwareReleaseRepository releaseRepository,
                                             SoftwarePackageRevisionRepository packageRepository,
                                             SoftwareRecommendationRepository recommendationRepository,
                                             SoftwareCategoryRepository categoryRepository) {
        this.entityManager = entityManager;
        this.applicationRepository = applicationRepository;
        this.releaseRepository = releaseRepository;
        this.packageRepository = packageRepository;
        this.recommendationRepository = recommendationRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    public Page<SoftwareAppSummaryResponse> searchPublishedApps(String keyword,
                                                                String categoryCode,
                                                                SoftwareTargetOs targetOs,
                                                                SoftwareTargetArch targetArch,
                                                                Pageable pageable) {
        StringBuilder where = new StringBuilder();
        if (StringUtils.hasText(keyword)) {
            where.append(" and (lower(a.name) like :keywordLike")
                    .append(" or lower(coalesce(a.description, '')) like :keywordLike)");
        }
        if (StringUtils.hasText(categoryCode)) {
            where.append(" and a.categoryCode = :categoryCode");
        }
        if (targetOs != null || targetArch != null) {
            where.append(" and exists (select p from SoftwarePackageRevision p")
                    .append(" where p.applicationId = a.id")
                    .append(" and p.status = com.iflytek.skillhub.domain.software.SoftwarePackageStatus.PUBLISHED");
            if (targetOs != null) {
                where.append(" and p.targetOs = :targetOs");
            }
            if (targetArch != null) {
                where.append(" and p.targetArch = :targetArch");
            }
            where.append(")");
        }

        TypedQuery<Object[]> query = entityManager.createQuery(
                "select a, c" + APPS_FROM + where + " order by a.updatedAt desc, a.id desc", Object[].class);
        TypedQuery<Long> countQuery = entityManager.createQuery(
                "select count(a)" + APPS_FROM + where, Long.class);
        for (TypedQuery<?> bindable : List.of(query, countQuery)) {
            if (StringUtils.hasText(keyword)) {
                bindable.setParameter("keywordLike", "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%");
            }
            if (StringUtils.hasText(categoryCode)) {
                bindable.setParameter("categoryCode", categoryCode.trim().toLowerCase(Locale.ROOT));
            }
            if (targetOs != null) {
                bindable.setParameter("targetOs", targetOs);
            }
            if (targetArch != null) {
                bindable.setParameter("targetArch", targetArch);
            }
        }
        query.setFirstResult((int) pageable.getOffset()).setMaxResults(pageable.getPageSize());

        Map<String, String> categoryNames = categoryRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .collect(Collectors.toMap(SoftwareCategory::getCode, SoftwareCategory::getName));
        List<SoftwareAppSummaryResponse> items = query.getResultList().stream()
                .map(row -> toSummary((SoftwareApplication) row[0], (String) row[1], categoryNames))
                .toList();
        return new PageImpl<>(items, pageable, countQuery.getSingleResult());
    }

    @Override
    public SoftwareAppDetailResponse findPublishedAppDetail(String slug) {
        SoftwareApplication app = applicationRepository.findBySlug(slug)
                .filter(application -> application.getStatus() == SoftwareApplicationStatus.PUBLISHED)
                .orElse(null);
        if (app == null) {
            return null;
        }
        String categoryName = app.getCategoryCode() == null ? null
                : categoryRepository.findByCode(app.getCategoryCode()).map(SoftwareCategory::getName).orElse(null);

        List<SoftwareRelease> releases = releaseRepository.findByApplicationIdOrderByIdDesc(app.getId());
        Map<Long, List<SoftwarePackageRevision>> packagesByRelease =
                packageRepository.findByApplicationId(app.getId()).stream()
                        .filter(pkg -> pkg.getStatus() == SoftwarePackageStatus.PUBLISHED)
                        .collect(Collectors.groupingBy(SoftwarePackageRevision::getReleaseId));
        Map<Long, List<SoftwareRecommendation>> recommendationsByRelease = releases.stream()
                .collect(Collectors.toMap(SoftwareRelease::getId,
                        release -> recommendationRepository.findByReleaseId(release.getId())));

        List<SoftwareReleaseResponse> releaseResponses = releases.stream()
                .filter(release -> packagesByRelease.containsKey(release.getId()))
                .map(release -> toRelease(release,
                        packagesByRelease.getOrDefault(release.getId(), List.of()),
                        recommendationsByRelease.getOrDefault(release.getId(), List.of())))
                .toList();

        return new SoftwareAppDetailResponse(
                app.getId(), app.getSlug(), app.getName(), app.getDescription(), app.getIconObjectKey(),
                app.getCategoryCode(), categoryName, app.getStatus().name(), app.getCreatedAt(),
                releaseResponses
        );
    }

    @Override
    public Page<SoftwareAppAdminSummaryResponse> searchAppsForAdmin(String keyword,
                                                                    String categoryCode,
                                                                    SoftwareApplicationStatus status,
                                                                    Pageable pageable) {
        StringBuilder where = new StringBuilder();
        if (StringUtils.hasText(keyword)) {
            where.append(" and (lower(a.name) like :keywordLike")
                    .append(" or lower(coalesce(a.description, '')) like :keywordLike)");
        }
        if (StringUtils.hasText(categoryCode)) {
            where.append(" and a.categoryCode = :categoryCode");
        }
        if (status != null) {
            where.append(" and a.status = com.iflytek.skillhub.domain.software."
                    + "SoftwareApplicationStatus.").append(status.name());
        }

        TypedQuery<Object[]> query = entityManager.createQuery(
                "select a, c" + APPS_FROM + where + " order by a.updatedAt desc, a.id desc", Object[].class);
        TypedQuery<Long> countQuery = entityManager.createQuery(
                "select count(a)" + APPS_FROM + where, Long.class);
        for (TypedQuery<?> bindable : List.of(query, countQuery)) {
            if (StringUtils.hasText(keyword)) {
                bindable.setParameter("keywordLike", "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%");
            }
            if (StringUtils.hasText(categoryCode)) {
                bindable.setParameter("categoryCode", categoryCode.trim().toLowerCase(Locale.ROOT));
            }
        }
        query.setFirstResult((int) pageable.getOffset()).setMaxResults(pageable.getPageSize());

        Map<String, String> categoryNames = categoryRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .collect(Collectors.toMap(SoftwareCategory::getCode, SoftwareCategory::getName));
        List<SoftwareAppAdminSummaryResponse> items = query.getResultList().stream()
                .map(row -> toAdminSummary((SoftwareApplication) row[0], (String) row[1], categoryNames))
                .toList();
        return new PageImpl<>(items, pageable, countQuery.getSingleResult());
    }

    private SoftwareAppAdminSummaryResponse toAdminSummary(SoftwareApplication app, String categoryNameRow,
                                                           Map<String, String> categoryNames) {
        String categoryName = categoryNameRow != null ? categoryNameRow
                : (app.getCategoryCode() == null ? null : categoryNames.get(app.getCategoryCode()));
        return new SoftwareAppAdminSummaryResponse(
                app.getId(), app.getSlug(), app.getName(), app.getDescription(), app.getIconObjectKey(),
                app.getCategoryCode(), categoryName, app.getStatus().name(),
                app.getCreatedAt(), app.getUpdatedAt()
        );
    }

    private SoftwareAppSummaryResponse toSummary(SoftwareApplication app, String categoryNameRow,
                                                 Map<String, String> categoryNames) {
        String categoryName = categoryNameRow != null ? categoryNameRow
                : (app.getCategoryCode() == null ? null : categoryNames.get(app.getCategoryCode()));
        return new SoftwareAppSummaryResponse(
                app.getId(), app.getSlug(), app.getName(), app.getDescription(), app.getIconObjectKey(),
                app.getCategoryCode(), categoryName, app.getCreatedAt()
        );
    }

    private SoftwareReleaseResponse toRelease(SoftwareRelease release,
                                              List<SoftwarePackageRevision> packages,
                                              List<SoftwareRecommendation> recommendations) {
        List<SoftwarePackageResponse> packageResponses = packages.stream()
                .sorted(Comparator.comparing(SoftwarePackageRevision::getId).reversed())
                .map(this::toPackage)
                .toList();
        Instant publishedAt = packages.stream()
                .map(SoftwarePackageRevision::getPublishedAt)
                .filter(java.util.Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);
        List<SoftwareRecommendationResponse> recommendationResponses = recommendations.stream()
                .map(this::toRecommendation)
                .toList();
        return new SoftwareReleaseResponse(
                release.getId(), release.getDisplayVersion(), release.getReleaseNotes(),
                publishedAt, packageResponses.size(), packageResponses, recommendationResponses
        );
    }

    private SoftwarePackageResponse toPackage(SoftwarePackageRevision pkg) {
        return new SoftwarePackageResponse(
                pkg.getId(), pkg.getRevisionNo(), pkg.getStatus().name(),
                pkg.getTargetOs().name(), pkg.getTargetArch().name(), pkg.getTargetDistro(),
                pkg.getFormat().name(), pkg.getOriginalFullVersion(), pkg.getFileName(),
                pkg.getByteSize(), pkg.getSha256(), pkg.getApplicabilityNote(),
                pkg.getConfirmedBy(), pkg.getConfirmedAt(), pkg.getPublishedAt()
        );
    }

    private SoftwareRecommendationResponse toRecommendation(SoftwareRecommendation recommendation) {
        return new SoftwareRecommendationResponse(
                recommendation.getId(), recommendation.getPackageRevisionId(),
                recommendation.getTargetOs().name(), recommendation.getTargetArch().name(),
                recommendation.getTargetDistro(), recommendation.getFormat().name(),
                recommendation.getNote(), recommendation.getCreatedAt()
        );
    }
}
