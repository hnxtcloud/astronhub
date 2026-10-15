package com.iflytek.skillhub.infra.jpa;
import com.iflytek.skillhub.domain.software.SoftwareUpdateChannel;
import com.iflytek.skillhub.domain.software.SoftwareUpdateChannelRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SoftwareUpdateChannelJpaRepository extends JpaRepository<SoftwareUpdateChannel, Long>, SoftwareUpdateChannelRepository { }
