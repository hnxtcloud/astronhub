package com.iflytek.skillhub.domain.software;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Release grouping rules: display versions are trimmed, kept verbatim
 * otherwise, unique per application, and never interpreted as SemVer.
 */
@Service
public class SoftwareReleaseService {

    private static final int MAX_DISPLAY_VERSION_LENGTH = 255;

    private final SoftwareReleaseRepository releaseRepository;
    private final SoftwareApplicationRepository applicationRepository;

    public SoftwareReleaseService(SoftwareReleaseRepository releaseRepository,
                                  SoftwareApplicationRepository applicationRepository) {
        this.releaseRepository = releaseRepository;
        this.applicationRepository = applicationRepository;
    }

    @Transactional
    public SoftwareRelease create(Long applicationId, String displayVersion, String releaseNotes,
                                  String actorId) {
        requireApplication(applicationId);
        String normalizedVersion = normalizeDisplayVersion(displayVersion);
        releaseRepository.findByApplicationIdAndDisplayVersion(applicationId, normalizedVersion)
                .ifPresent(existing -> {
                    throw new DomainBadRequestException("error.software.release.duplicate", normalizedVersion);
                });
        return releaseRepository.save(
                new SoftwareRelease(applicationId, normalizedVersion, releaseNotes, actorId));
    }

    @Transactional
    public SoftwareRelease updateNotes(Long releaseId, String releaseNotes) {
        SoftwareRelease release = requireRelease(releaseId);
        release.updateReleaseNotes(releaseNotes);
        return releaseRepository.save(release);
    }

    public SoftwareRelease requireRelease(Long releaseId) {
        return releaseRepository.findById(releaseId)
                .orElseThrow(() -> new DomainNotFoundException("error.software.release.notFound", releaseId));
    }

    private void requireApplication(Long applicationId) {
        applicationRepository.findById(applicationId)
                .orElseThrow(() -> new DomainNotFoundException("error.software.app.notFound", applicationId));
    }

    private String normalizeDisplayVersion(String displayVersion) {
        if (!StringUtils.hasText(displayVersion) || displayVersion.trim().isEmpty()) {
            throw new DomainBadRequestException("error.software.release.versionRequired");
        }
        String normalized = displayVersion.trim();
        if (normalized.length() > MAX_DISPLAY_VERSION_LENGTH) {
            throw new DomainBadRequestException("error.software.release.versionTooLong",
                    MAX_DISPLAY_VERSION_LENGTH);
        }
        return normalized;
    }
}
