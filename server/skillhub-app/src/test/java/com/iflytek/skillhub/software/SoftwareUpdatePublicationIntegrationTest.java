package com.iflytek.skillhub.software;

import static org.assertj.core.api.Assertions.*;
import com.iflytek.skillhub.domain.software.*;
import com.iflytek.skillhub.dto.software.SoftwareUpdateCreateRequest;
import com.iflytek.skillhub.dto.software.SoftwareUpdateChannelRequest;
import com.iflytek.skillhub.dto.software.SoftwareUploadSessionCreateRequest;
import com.iflytek.skillhub.service.SoftwareUpdateAppService;
import com.iflytek.skillhub.service.SoftwareUploadAppService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/** Publication through real application services, H2 repositories and local storage bytes. */
@SpringBootTest(properties = {
        "skillhub.software.storage.temp-dir=./target/sw-update-tmp",
        "skillhub.storage.local.base-path=./target/sw-update-base"
})
@ActiveProfiles("test")
class SoftwareUpdatePublicationIntegrationTest {
    @Autowired SoftwareUpdateAppService updates;
    @Autowired SoftwareUploadAppService uploads;
    @Autowired SoftwareApplicationRepository applications;
    @Autowired SoftwareReleaseRepository releases;
    @Autowired SoftwarePackageService packages;
    @Autowired SoftwarePolicyStateRepository policyState;
    @Autowired SoftwarePolicyRevisionRepository policies;
    @Autowired SoftwareStorageAccountRepository accounts;
    @Autowired SoftwareQuotaService quota;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    @Test
    void administratorPublishesCompleteOriginalAssetsWithoutDuplicatingInstallerStorage() {
        seed();
        long appId = tx.execute(status -> {
            var app = new SoftwareApplication("update-" + System.nanoTime(), "Update App", null, null, null, "admin");
            app.publish();
            return applications.save(app).getId();
        });
        long catalogId = tx.execute(status -> releases.save(new SoftwareRelease(appId, "Display 2026 Q4", "notes", "admin")).getId());
        byte[] installer = "abc".getBytes(StandardCharsets.UTF_8);
        var session = uploads.createSession(new SoftwareUploadSessionCreateRequest(catalogId, "app.zip", "ZIP",
                "WINDOWS", "X86_64", null, null, "1.2.3", 3L), "admin");
        uploads.putChunk(session.id(), 1, 3L, new ByteArrayInputStream(installer), "admin");
        long packageId = uploads.complete(session.id(), "admin").packageRevisionId();
        packages.confirm(packageId, "admin");
        packages.publish(packageId);
        var draft = updates.create(new SoftwareUpdateCreateRequest(catalogId, "1.2.3", "v1.2.3", "WINDOWS",
                "X86_64", "stable", "default", "ELECTRON", null), "admin");
        var linked = updates.linkPackage(draft.id(), packageId, "app.zip", "admin");
        assertThat(linked.sha256()).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(quota.totalCommittedBytes()).isEqualTo(3);
        String yaml = "version: 1.2.3\nfiles:\n  - url: app.zip\n    size: 3\n    sha512: " + linked.sha512Base64() + "\n";
        byte[] metadata = yaml.getBytes(StandardCharsets.UTF_8);
        updates.uploadAsset(draft.id(), "latest.yml", "METADATA", metadata.length,
                new ByteArrayInputStream(metadata), "admin");
        var published = updates.publish(draft.id(), "admin");
        assertThat(published.status()).isEqualTo("PUBLISHED");
        assertThat(published.assets()).extracting(asset -> asset.relativePath()).containsExactlyInAnyOrder("app.zip", "latest.yml");
        assertThat(quota.totalCommittedBytes()).isEqualTo(3L + metadata.length);
        assertThatThrownBy(() -> updates.linkPackage(draft.id(), packageId, "other.zip", "admin"))
                .hasMessageContaining("error.software.update.immutable");
    }

    private void seed() {
        tx.executeWithoutResult(status -> {
            if (policyState.findSingleton().isEmpty()) {
                String config = SoftwarePolicyConfigJson.toJson(SoftwarePolicyConfig.defaults());
                jdbc.update("INSERT INTO software_policy_revision (revision,schema_version,config,changed_by,change_reason,created_at) "
                        + "VALUES (1,1,'" + config + "' FORMAT JSON,'system','test seed',CURRENT_TIMESTAMP)");
                policyState.save(new SoftwarePolicyState(policies.findById(1L).orElseThrow().getId()));
            }
            if (accounts.findSingleton().isEmpty()) accounts.save(new SoftwareStorageAccount(0, 0));
        });
    }

    @Test
    void updateSourcesRequireAnApplicationOptInAndAnExplicitTargetMapping() {
        seed();
        long appId = tx.execute(status -> applications.save(new SoftwareApplication("source-" + System.nanoTime(),
                "Source App", null, null, null, "admin")).getId());
        assertThat(updates.source(appId).anonymousEnabled()).isFalse();
        var channel = updates.saveChannel(appId, new SoftwareUpdateChannelRequest("windows-stable", "apps/source-app",
                "WINDOWS", "X86_64", "stable", "default", null), "admin");
        assertThat(channel.virtualProjectPath()).isEqualTo("apps/source-app");
        assertThat(updates.setAnonymous(appId, true, "admin").anonymousEnabled()).isTrue();
        assertThat(updates.setAnonymous(appId, false, "admin").anonymousEnabled()).isFalse();
    }
}
