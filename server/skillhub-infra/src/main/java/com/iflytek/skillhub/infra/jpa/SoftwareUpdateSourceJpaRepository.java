package com.iflytek.skillhub.infra.jpa;
import com.iflytek.skillhub.domain.software.SoftwareUpdateSource;
import com.iflytek.skillhub.domain.software.SoftwareUpdateSourceRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SoftwareUpdateSourceJpaRepository extends JpaRepository<SoftwareUpdateSource, Long>, SoftwareUpdateSourceRepository { }
