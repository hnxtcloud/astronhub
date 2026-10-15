package com.iflytek.skillhub.infra.jpa;
import com.iflytek.skillhub.domain.software.SoftwareAssetUpload;
import com.iflytek.skillhub.domain.software.SoftwareAssetUploadRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SoftwareAssetUploadJpaRepository extends JpaRepository<SoftwareAssetUpload, Long>, SoftwareAssetUploadRepository {
    @Override
    @Query(value = "SELECT * FROM software_asset_upload WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<SoftwareAssetUpload> findLockedById(Long id);
}
