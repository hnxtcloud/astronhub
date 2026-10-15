package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.software.SoftwarePublisherGrant;
import com.iflytek.skillhub.domain.software.SoftwarePublisherGrantRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SoftwarePublisherGrantJpaRepository extends JpaRepository<SoftwarePublisherGrant, Long>, SoftwarePublisherGrantRepository { }
