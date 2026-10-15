package com.iflytek.skillhub.domain.software;

import java.util.List;
import java.util.Optional;

public interface SoftwarePublisherGrantRepository {
    SoftwarePublisherGrant save(SoftwarePublisherGrant grant);
    Optional<SoftwarePublisherGrant> findById(Long tokenId);
    List<SoftwarePublisherGrant> findByApplicationIdOrderByCreatedAtDesc(Long applicationId);
}
