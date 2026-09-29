package com.iflytek.skillhub.domain.software;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Package revision lifecycle rules. Publishing requires administrator
 * confirmation of the metadata; taking a package down clears recommendations
 * that reference it. Permanent deletion is a SUPER_ADMIN operation handled at
 * the app layer with audit tombstones, not here.
 */
@Service
public class SoftwarePackageService {

    private final SoftwarePackageRevisionRepository packageRepository;
    private final SoftwareRecommendationRepository recommendationRepository;
    private final SoftwareReleaseRepository releaseRepository;
    private final SoftwareInspectionTaskRepository inspectionTaskRepository;

    public SoftwarePackageService(SoftwarePackageRevisionRepository packageRepository,
                                  SoftwareRecommendationRepository recommendationRepository,
                                  SoftwareReleaseRepository releaseRepository,
                                  SoftwareInspectionTaskRepository inspectionTaskRepository) {
        this.packageRepository = packageRepository;
        this.recommendationRepository = recommendationRepository;
        this.releaseRepository = releaseRepository;
        this.inspectionTaskRepository = inspectionTaskRepository;
    }

    /** Registers a manually reviewed package revision (upload flow lands in the transfer phase). */
    @Transactional
    public SoftwarePackageRevision register(Long releaseId,
                                            Long applicationId,
                                            SoftwareTargetOs targetOs,
                                            SoftwareTargetArch targetArch,
                                            String targetDistro,
                                            String applicabilityNote,
                                            SoftwarePackageFormat format,
                                            String originalFullVersion,
                                            String rawVersionFields,
                                            String fileName,
                                            String sha256,
                                            long byteSize,
                                            String objectKey,
                                            String actorId) {
        requireRelease(releaseId);
        requireFullVersion(originalFullVersion);
        if (sha256 == null || sha256.length() != 64) {
            throw new DomainBadRequestException("error.software.package.sha256Invalid");
        }
        if (byteSize <= 0) {
            throw new DomainBadRequestException("error.software.package.byteSizeInvalid");
        }
        if (packageRepository.existsByApplicationIdAndSha256(applicationId, sha256)) {
            throw new DomainConflictException("error.software.package.duplicateSha256", sha256);
        }
        int nextRevisionNo = nextRevisionNo(releaseId, targetOs, targetArch, targetDistro, originalFullVersion);
        return packageRepository.save(new SoftwarePackageRevision(
                releaseId, applicationId, nextRevisionNo,
                targetOs, targetArch, targetDistro, applicabilityNote, format,
                originalFullVersion, rawVersionFields, fileName, sha256, byteSize, objectKey, actorId));
    }

    /**
     * Records the administrator confirmation required before publishing.
     * The static-inspection gate mirrors the confirmed product rules: a basic
     * validation failure can never be confirmed away; a metadata-only failure
     * requires the manual fill-in to travel with the confirmation; and an
     * inspection that has not completed (pending, leased, or exhausted retry
     * budget) blocks confirmation outright so unfinished validation cannot be
     * bypassed with hand-typed metadata. Re-confirming an already confirmed
     * revision stays allowed — human confirmation wins over extraction.
     */
    @Transactional
    public SoftwarePackageRevision confirm(Long packageId, String actorId, String rawVersionFieldsJson) {
        SoftwarePackageRevision revision = requirePackage(packageId);
        if (revision.getStatus() != SoftwarePackageStatus.DRAFT) {
            throw new DomainBadRequestException("error.software.package.invalidTransition",
                    revision.getStatus(), revision.getStatus());
        }
        if (!revision.isConfirmed()) {
            inspectionTaskRepository.findByPackageRevisionId(packageId).ifPresent(task -> {
                switch (task.getStatus()) {
                    case BASIC_FAILED ->
                            throw new DomainConflictException("error.software.package.inspectionFailed", packageId);
                    case METADATA_FAILED -> {
                        if (rawVersionFieldsJson == null) {
                            throw new DomainConflictException(
                                    "error.software.package.metadataNotResolved", packageId);
                        }
                    }
                    case PENDING, LEASED, EXHAUSTED ->
                            throw new DomainConflictException(
                                    "error.software.package.inspectionIncomplete", packageId);
                    case SUCCEEDED -> {
                        // extraction complete; confirmation may proceed
                    }
                }
            });
        }
        if (rawVersionFieldsJson != null) {
            revision.fillInRawVersionFields(rawVersionFieldsJson);
        }
        revision.confirm(actorId);
        return packageRepository.save(revision);
    }

    /** Confirmation without metadata fill-in (extraction already produced fields). */
    @Transactional
    public SoftwarePackageRevision confirm(Long packageId, String actorId) {
        return confirm(packageId, actorId, null);
    }

    /**
     * Writes inspector-extracted raw metadata onto a not-yet-confirmed
     * revision. Once an administrator has confirmed the package, inspection
     * results never override the human-confirmed state.
     */
    @Transactional
    public void applyInspectionExtractedFields(Long packageRevisionId, String rawVersionFieldsJson) {
        SoftwarePackageRevision revision = requirePackage(packageRevisionId);
        if (revision.isConfirmed()) {
            return;
        }
        revision.fillInRawVersionFields(rawVersionFieldsJson);
        packageRepository.save(revision);
    }

    @Transactional
    public SoftwarePackageRevision updateNote(Long packageId, String applicabilityNote, String actorId) {
        SoftwarePackageRevision revision = requirePackage(packageId);
        revision.updateApplicabilityNote(applicabilityNote);
        return packageRepository.save(revision);
    }

    @Transactional
    public SoftwarePackageRevision publish(Long packageId) {
        SoftwarePackageRevision revision = requirePackage(packageId);
        if (revision.getStatus() != SoftwarePackageStatus.DRAFT) {
            throw new DomainBadRequestException("error.software.package.invalidTransition",
                    revision.getStatus(), SoftwarePackageStatus.PUBLISHED);
        }
        if (!revision.isConfirmed()) {
            throw new DomainBadRequestException("error.software.package.confirmationRequired");
        }
        revision.publish();
        return packageRepository.save(revision);
    }

    /** Takes the package down and clears recommendations that reference it. */
    @Transactional
    public SoftwarePackageRevision takeDown(Long packageId) {
        SoftwarePackageRevision revision = requirePackage(packageId);
        if (revision.getStatus() != SoftwarePackageStatus.PUBLISHED) {
            throw new DomainBadRequestException("error.software.package.invalidTransition",
                    revision.getStatus(), SoftwarePackageStatus.TAKEN_DOWN);
        }
        revision.takeDown();
        List<SoftwareRecommendation> referencing =
                recommendationRepository.findByPackageRevisionId(packageId);
        if (!referencing.isEmpty()) {
            recommendationRepository.deleteAllIn(referencing);
        }
        return packageRepository.save(revision);
    }

    @Transactional
    public SoftwarePackageRevision restore(Long packageId) {
        SoftwarePackageRevision revision = requirePackage(packageId);
        if (revision.getStatus() != SoftwarePackageStatus.TAKEN_DOWN) {
            throw new DomainBadRequestException("error.software.package.invalidTransition",
                    revision.getStatus(), SoftwarePackageStatus.PUBLISHED);
        }
        revision.restore();
        return packageRepository.save(revision);
    }

    public SoftwarePackageRevision requirePackage(Long packageId) {
        return packageRepository.findById(packageId)
                .orElseThrow(() -> new DomainNotFoundException("error.software.package.notFound", packageId));
    }

    /** Looks up an existing revision by exact content hash within one application. */
    public Optional<SoftwarePackageRevision> findByApplicationIdAndSha256(Long applicationId, String sha256) {
        return packageRepository.findByApplicationIdAndSha256(applicationId, sha256);
    }

    private void requireRelease(Long releaseId) {
        releaseRepository.findById(releaseId)
                .orElseThrow(() -> new DomainNotFoundException("error.software.release.notFound", releaseId));
    }

    private int nextRevisionNo(Long releaseId,
                               SoftwareTargetOs targetOs,
                               SoftwareTargetArch targetArch,
                               String targetDistro,
                               String originalFullVersion) {
        return packageRepository.findByReleaseIdOrderByIdDesc(releaseId).stream()
                .filter(existing -> existing.getTargetOs() == targetOs
                        && existing.getTargetArch() == targetArch
                        && java.util.Objects.equals(existing.getTargetDistro(), targetDistro)
                        && existing.getOriginalFullVersion().equals(originalFullVersion))
                .mapToInt(SoftwarePackageRevision::getRevisionNo)
                .max()
                .orElse(0) + 1;
    }

    private void requireFullVersion(String originalFullVersion) {
        if (originalFullVersion == null || originalFullVersion.isBlank()) {
            throw new DomainBadRequestException("error.software.package.fullVersionRequired");
        }
    }
}
