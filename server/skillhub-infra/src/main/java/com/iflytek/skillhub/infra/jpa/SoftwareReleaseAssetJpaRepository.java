package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.software.SoftwareReleaseAsset;
import com.iflytek.skillhub.domain.software.SoftwareReleaseAssetRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SoftwareReleaseAssetJpaRepository extends JpaRepository<SoftwareReleaseAsset, Long>, SoftwareReleaseAssetRepository { }
