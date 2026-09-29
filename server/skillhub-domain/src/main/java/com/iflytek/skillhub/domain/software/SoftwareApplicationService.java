package com.iflytek.skillhub.domain.software;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Application-level rules for software applications: profile validation, slug
 * uniqueness, and the draft → published → taken-down lifecycle. Restoring a
 * taken-down application never changes independently taken-down packages.
 */
@Service
public class SoftwareApplicationService {

    private final SoftwareApplicationRepository applicationRepository;

    public SoftwareApplicationService(SoftwareApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    @Transactional
    public SoftwareApplication create(String slug, String name, String description,
                                      String iconObjectKey, String categoryCode, String actorId) {
        String normalizedSlug = SoftwareSlugValidator.normalize(slug);
        requireName(name);
        if (applicationRepository.existsBySlug(normalizedSlug)) {
            throw new DomainBadRequestException("error.software.slug.duplicate", normalizedSlug);
        }
        return applicationRepository.save(
                new SoftwareApplication(normalizedSlug, name.trim(), description, iconObjectKey, categoryCode,
                        actorId));
    }

    @Transactional
    public SoftwareApplication updateProfile(Long applicationId, String name, String description,
                                             String iconObjectKey, String categoryCode, String actorId) {
        SoftwareApplication application = requireApplication(applicationId);
        requireName(name);
        application.updateProfile(name.trim(), description, iconObjectKey, categoryCode);
        application.setUpdatedBy(actorId);
        return applicationRepository.save(application);
    }

    @Transactional
    public SoftwareApplication publish(Long applicationId) {
        SoftwareApplication application = requireApplication(applicationId);
        if (application.getStatus() != SoftwareApplicationStatus.DRAFT
                && application.getStatus() != SoftwareApplicationStatus.TAKEN_DOWN) {
            throw new DomainBadRequestException("error.software.app.invalidTransition",
                    application.getStatus(), SoftwareApplicationStatus.PUBLISHED);
        }
        application.publish();
        return applicationRepository.save(application);
    }

    @Transactional
    public SoftwareApplication takeDown(Long applicationId) {
        SoftwareApplication application = requireApplication(applicationId);
        if (application.getStatus() != SoftwareApplicationStatus.PUBLISHED) {
            throw new DomainBadRequestException("error.software.app.invalidTransition",
                    application.getStatus(), SoftwareApplicationStatus.TAKEN_DOWN);
        }
        application.takeDown();
        return applicationRepository.save(application);
    }

    @Transactional
    public SoftwareApplication restore(Long applicationId) {
        SoftwareApplication application = requireApplication(applicationId);
        if (application.getStatus() != SoftwareApplicationStatus.TAKEN_DOWN) {
            throw new DomainBadRequestException("error.software.app.invalidTransition",
                    application.getStatus(), SoftwareApplicationStatus.PUBLISHED);
        }
        application.restore();
        return applicationRepository.save(application);
    }

    public SoftwareApplication requireApplication(Long applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new DomainNotFoundException("error.software.app.notFound", applicationId));
    }

    public SoftwareApplication requireVisibleApplication(String slug) {
        SoftwareApplication application = applicationRepository.findBySlug(slug)
                .orElseThrow(() -> new DomainNotFoundException("error.software.app.notFound", slug));
        if (application.getStatus() != SoftwareApplicationStatus.PUBLISHED) {
            // Hidden content is indistinguishable from missing content for regular users.
            throw new DomainNotFoundException("error.software.app.notFound", slug);
        }
        return application;
    }

    private void requireName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new DomainBadRequestException("error.software.app.nameRequired");
        }
    }
}
