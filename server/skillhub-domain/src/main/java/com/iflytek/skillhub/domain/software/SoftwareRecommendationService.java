package com.iflytek.skillhub.domain.software;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recommendation rules: recommendations bind a published package revision of a
 * release to an adaptation scope (OS/architecture/distribution/format).
 * Different formats may each be recommended for the same scope; conflicting
 * recommendations for an identical scope are rejected instead of silently
 * overwritten.
 */
@Service
public class SoftwareRecommendationService {

    private final SoftwareRecommendationRepository recommendationRepository;
    private final SoftwarePackageRevisionRepository packageRepository;
    private final SoftwareReleaseRepository releaseRepository;

    public SoftwareRecommendationService(SoftwareRecommendationRepository recommendationRepository,
                                         SoftwarePackageRevisionRepository packageRepository,
                                         SoftwareReleaseRepository releaseRepository) {
        this.recommendationRepository = recommendationRepository;
        this.packageRepository = packageRepository;
        this.releaseRepository = releaseRepository;
    }

    /** A recommendation request item from the administrator. */
    public record RecommendationRequest(Long packageRevisionId,
                                        SoftwareTargetOs targetOs,
                                        SoftwareTargetArch targetArch,
                                        String targetDistro,
                                        SoftwarePackageFormat format,
                                        String note) {
    }

    /** Replaces all recommendations of a release atomically. */
    @Transactional
    public List<SoftwareRecommendation> replaceAll(Long releaseId,
                                                   List<RecommendationRequest> requests,
                                                   String actorId) {
        releaseRepository.findById(releaseId)
                .orElseThrow(() -> new DomainBadRequestException("error.software.release.notFound", releaseId));

        Set<String> seenScopes = new HashSet<>();
        for (RecommendationRequest request : requests == null ? List.<RecommendationRequest>of() : requests) {
            String scopeKey = scopeKey(request.targetOs(), request.targetArch(),
                    request.targetDistro(), request.format());
            if (!seenScopes.add(scopeKey)) {
                throw new DomainBadRequestException("error.software.recommendation.conflict",
                        request.targetOs(), request.targetArch(), request.targetDistro(), request.format());
            }
            SoftwarePackageRevision revision = packageRepository.findById(request.packageRevisionId())
                    .orElseThrow(() -> new DomainBadRequestException("error.software.package.notFound",
                            request.packageRevisionId()));
            if (!revision.getReleaseId().equals(releaseId)) {
                throw new DomainBadRequestException("error.software.recommendation.releaseMismatch",
                        request.packageRevisionId(), releaseId);
            }
            if (revision.getStatus() != SoftwarePackageStatus.PUBLISHED) {
                throw new DomainBadRequestException("error.software.recommendation.packageNotPublished",
                        request.packageRevisionId());
            }
        }

        List<SoftwareRecommendation> existing = recommendationRepository.findByReleaseId(releaseId);
        if (!existing.isEmpty()) {
            recommendationRepository.deleteAllIn(existing);
        }

        List<SoftwareRecommendation> result = new ArrayList<>();
        for (RecommendationRequest request : requests == null ? List.<RecommendationRequest>of() : requests) {
            result.add(recommendationRepository.save(new SoftwareRecommendation(
                    releaseId,
                    request.packageRevisionId(),
                    request.targetOs(),
                    request.targetArch(),
                    request.targetDistro(),
                    request.format(),
                    request.note(),
                    actorId
            )));
        }
        return result;
    }

    @Transactional
    public void delete(Long recommendationId) {
        SoftwareRecommendation recommendation = recommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new DomainBadRequestException("error.software.recommendation.notFound",
                        recommendationId));
        recommendationRepository.delete(recommendation);
    }

    public List<SoftwareRecommendation> findByRelease(Long releaseId) {
        return recommendationRepository.findByReleaseId(releaseId);
    }

    private String scopeKey(SoftwareTargetOs os, SoftwareTargetArch arch, String distro,
                            SoftwarePackageFormat format) {
        return os + "|" + arch + "|" + Objects.toString(distro, "") + "|" + format;
    }
}
