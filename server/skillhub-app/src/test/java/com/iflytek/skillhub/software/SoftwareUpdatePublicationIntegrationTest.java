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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Publication through real application services, H2 repositories and local storage bytes. */
@SpringBootTest(properties = {
        "skillhub.software.storage.temp-dir=./target/sw-update-tmp",
        "skillhub.storage.local.base-path=./target/sw-update-base"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SoftwareUpdatePublicationIntegrationTest {
    @Autowired MockMvc http;
    @Autowired SoftwareUpdateAppService updates;
    @Autowired SoftwareUploadAppService uploads;
    @Autowired SoftwareApplicationRepository applications;
    @Autowired SoftwareReleaseRepository releases;
    @Autowired SoftwarePackageService packages;
    @Autowired SoftwareInspectionTaskService inspections;
    @Autowired SoftwarePolicyStateRepository policyState;
    @Autowired SoftwarePolicyRevisionRepository policies;
    @Autowired SoftwareStorageAccountRepository accounts;
    @Autowired SoftwareQuotaService quota;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;
    @org.springframework.boot.test.mock.mockito.SpyBean
    com.iflytek.skillhub.storage.software.SoftwareTransferStorage storage;

    @Test
    void windowsElectronInstallerRequiresAnExplicitNsisDeclaration() throws Exception {
        seed();
        long appId = tx.execute(status -> {
            var app = new SoftwareApplication("nsis-" + System.nanoTime(), "NSIS confirmation", null, null, null, "admin");
            app.publish();
            return applications.save(app).getId();
        });
        long catalogId = tx.execute(status -> releases.save(new SoftwareRelease(appId, "1.0.0", "notes", "admin")).getId());
        byte[] binary = "synthetic-exe".getBytes(StandardCharsets.UTF_8);
        var upload = uploads.createSession(new SoftwareUploadSessionCreateRequest(catalogId, "setup.exe", "EXE", "WINDOWS", "X86_64", null, null, "1.0.0", (long) binary.length), "admin");
        uploads.putChunk(upload.id(), 1, binary.length, new ByteArrayInputStream(binary), "admin");
        long packageId = uploads.complete(upload.id(), "admin").packageRevisionId();
        var inspection = inspections.claim("nsis-fixture", java.time.Instant.now());
        assertThat(inspection.packageRevisionId()).isEqualTo(packageId);
        inspections.submitResult(inspection.taskId(), "nsis-fixture", inspection.attemptToken(),
                new SoftwareInspectionTaskService.ResultSubmission(SoftwareInspectionOutcome.SUCCEEDED,
                        null, "{\"version\":\"1.0.0\"}", "fixture", "1", java.time.Instant.now(), java.time.Instant.now()));
        packages.confirm(packageId, "admin");
        packages.publish(packageId);
        var draft = updates.create(new SoftwareUpdateCreateRequest(catalogId, "1.0.0", "v1.0.0", "WINDOWS", "X86_64", "stable", "default", "ELECTRON", null), "admin");
        String endpoint = "/api/v1/admin/software/update-releases/" + draft.id();
        http.perform(post(endpoint + "/package-assets").with(authentication(auth("SOFTWARE_ADMIN"))).with(csrf())
                        .contentType("application/json").content("{\"packageRevisionId\":" + packageId + ",\"relativePath\":\"dist/setup.exe\"}"))
                .andExpect(status().isBadRequest());
        http.perform(get(endpoint).with(authentication(auth("SOFTWARE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.assets.length()").value(0));
        http.perform(post(endpoint + "/package-assets").with(authentication(auth("SOFTWARE_ADMIN"))).with(csrf())
                        .contentType("application/json").content("{\"packageRevisionId\":" + packageId + ",\"relativePath\":\"dist/setup.exe\",\"confirmedInstallerFamily\":\"INNO\"}"))
                .andExpect(status().isBadRequest());
        String confirmed = http.perform(post(endpoint + "/package-assets").with(authentication(auth("SOFTWARE_ADMIN"))).with(csrf())
                        .contentType("application/json").content("{\"packageRevisionId\":" + packageId + ",\"relativePath\":\"dist/setup.exe\",\"confirmedInstallerFamily\":\"NSIS\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.confirmedInstallerFamily").value("NSIS"))
                .andExpect(jsonPath("$.data.installerFamilyConfirmedBy").value("admin"))
                .andExpect(jsonPath("$.data.installerFamilyConfirmedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        var asset = new com.fasterxml.jackson.databind.ObjectMapper().readTree(confirmed).path("data");
        byte[] yaml = ("version: 1.0.0\nfiles:\n - url: setup.exe\n   size: " + binary.length
                + "\n   sha512: " + asset.path("sha512Base64").asText() + "\n").getBytes(StandardCharsets.UTF_8);
        updates.uploadAsset(draft.id(), "dist/latest.yml", "METADATA", yaml.length, new ByteArrayInputStream(yaml), "admin");
        http.perform(post(endpoint + "/publish").with(authentication(auth("SOFTWARE_ADMIN"))).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PUBLISHED"));
        updates.setAnonymous(appId, true, "admin");
        updates.saveChannel(appId, new SoftwareUpdateChannelRequest("windows-stable", "nsis/app-" + appId,
                "WINDOWS", "X86_64", "stable", "default", draft.id()), "admin");
        String slug = applications.findById(appId).orElseThrow().getSlug();
        String feed = "/updates/" + slug + "/windows-stable/latest.yml";
        String fixed = "/releases/" + slug + "/" + draft.id() + "/assets/dist/setup.exe";
        http.perform(get(feed)).andExpect(status().isOk());
        http.perform(get(fixed)).andExpect(status().isOk()).andExpect(content().bytes(binary));
        // Simulate a pre-migration snapshot: no declaration was backfilled for its generic EXE.
        jdbc.update("UPDATE software_release_asset SET confirmed_installer_family=NULL, installer_family_confirmed_by=NULL, installer_family_confirmed_at=NULL WHERE id=?", asset.path("id").asLong());
        http.perform(post(endpoint + "/validate").with(authentication(auth("SOFTWARE_ADMIN"))).with(csrf()))
                .andExpect(status().isBadRequest());
        http.perform(put("/api/v1/admin/software/apps/" + appId + "/update-channels").with(authentication(auth("SOFTWARE_ADMIN"))).with(csrf())
                        .contentType("application/json").content("{\"slug\":\"another\",\"virtualProjectPath\":\"nsis/legacy-" + appId + "\",\"targetOs\":\"WINDOWS\",\"targetArch\":\"X86_64\",\"channel\":\"stable\",\"variant\":\"default\",\"targetReleaseId\":" + draft.id() + "}"))
                .andExpect(status().isBadRequest());
        http.perform(get(feed).header("If-None-Match", "\"old-cache\"")).andExpect(status().isNotFound());
        http.perform(get(fixed).header("Range", "bytes=0-1")).andExpect(status().isNotFound());
    }

    @Test
    void baizeManifestCannotPublishAReleaseNotesUrlWithoutTheReferencedAsset() throws Exception {
        seed();
        long appId = tx.execute(status -> {
            var application = new SoftwareApplication("missing-notes-" + System.nanoTime(), "Missing notes", null, null, null, "admin");
            application.publish();
            return applications.save(application).getId();
        });
        long catalogId = tx.execute(status -> releases.save(new SoftwareRelease(appId, "Human version", "Original notes", "admin")).getId());
        byte[] bytes = "portable-installer".getBytes(StandardCharsets.UTF_8);
        var upload = uploads.createSession(new SoftwareUploadSessionCreateRequest(catalogId, "portable.zip", "ZIP", "WINDOWS", "X86_64", null, null, "1.0.0", (long) bytes.length), "admin");
        uploads.putChunk(upload.id(), 1, (long) bytes.length, new ByteArrayInputStream(bytes), "admin");
        long packageId = uploads.complete(upload.id(), "admin").packageRevisionId();
        packages.confirm(packageId, "admin");
        packages.publish(packageId);
        var draft = updates.create(new SoftwareUpdateCreateRequest(catalogId, "1.0.0", "v1.0.0", "WINDOWS", "X86_64", "stable", "portable", "BAIZE", "0.70.0"), "admin");
        var installer = updates.linkPackage(draft.id(), packageId, "dist/portable.zip", "admin");
        byte[] json = ("{\"schemaVersion\":1,\"version\":\"1.0.0\",\"piVersion\":\"0.70.0\",\"platform\":\"win32\",\"arch\":\"x64\",\"channel\":\"stable\",\"publishedAt\":\"2026-10-15T08:00:00Z\",\"package\":\"portable.zip\",\"sha256\":\"" + installer.sha256() + "\",\"bytes\":" + installer.byteSize() + ",\"releasesUrl\":\"notes.html\"}").getBytes(StandardCharsets.UTF_8);
        updates.uploadAsset(draft.id(), "dist/latest.json", "METADATA", json.length, new ByteArrayInputStream(json), "admin");

        String endpoint = "/api/v1/admin/software/update-releases/" + draft.id();
        http.perform(post(endpoint + "/publish").with(authentication(auth("SOFTWARE_ADMIN"))).with(csrf()))
                .andExpect(status().isBadRequest());
        http.perform(get(endpoint).with(authentication(auth("SOFTWARE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void administratorPublishesCompleteOriginalAssetsWithoutDuplicatingInstallerStorage() {
        seed();
        long initial = quota.totalCommittedBytes();
        long appId = tx.execute(status -> {
            var app = new SoftwareApplication("update-" + System.nanoTime(), "Update App", null, null, null, "admin");
            app.publish();
            return applications.save(app).getId();
        });
        long catalogId = tx.execute(status -> releases.save(new SoftwareRelease(appId, "Display 2026 Q4", "notes", "admin")).getId());
        byte[] installer = "abc".getBytes(StandardCharsets.UTF_8);
        var session = uploads.createSession(new SoftwareUploadSessionCreateRequest(catalogId, "app.zip", "ZIP",
                "MACOS", "X86_64", null, null, "1.2.3", 3L), "admin");
        uploads.putChunk(session.id(), 1, 3L, new ByteArrayInputStream(installer), "admin");
        long packageId = uploads.complete(session.id(), "admin").packageRevisionId();
        packages.confirm(packageId, "admin");
        packages.publish(packageId);
        var draft = updates.create(new SoftwareUpdateCreateRequest(catalogId, "1.2.3", "v1.2.3", "MACOS",
                "X86_64", "stable", "default", "ELECTRON", null), "admin");
        var linked = updates.linkPackage(draft.id(), packageId, "app.zip", "admin");
        assertThat(linked.sha256()).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(quota.totalCommittedBytes()).isEqualTo(initial + 3);
        assertThatThrownBy(() -> updates.publish(draft.id(), "admin"))
                .hasMessageContaining("error.software.update.metadata");
        String yaml = "version: 1.2.3\nfiles:\n  - url: app.zip\n    size: 3\n    sha512: " + linked.sha512Base64() + "\n";
        byte[] metadata = yaml.getBytes(StandardCharsets.UTF_8);
        updates.uploadAsset(draft.id(), "latest.yml", "METADATA", metadata.length,
                new ByteArrayInputStream(metadata), "admin");
        var published = updates.publish(draft.id(), "admin");
        assertThat(published.status()).isEqualTo("PUBLISHED");
        assertThat(published.assets()).extracting(asset -> asset.relativePath()).containsExactlyInAnyOrder("app.zip", "latest.yml");
        assertThat(quota.totalCommittedBytes()).isEqualTo(initial + 3L + metadata.length);
        assertThatThrownBy(() -> updates.linkPackage(draft.id(), packageId, "other.zip", "admin"))
                .hasMessageContaining("error.software.update.immutable");
        assertThatThrownBy(() -> updates.removeAsset(draft.id(), linked.id(), "admin"))
                .hasMessageContaining("error.software.update.immutable");
        packages.takeDown(packageId);
        assertThatThrownBy(() -> updates.requireGovernedPackages(updates.requireRelease(draft.id())))
                .hasMessageContaining("error.software.update.invalid");
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

    @Test
    void machineTagCannotResolveToTwoSnapshotsForTheSameTarget() throws Exception {
        seed();
        long appId = tx.execute(status -> applications.save(new SoftwareApplication("identity-" + System.nanoTime(),
                "Identity App", null, null, null, "admin")).getId());
        long catalogId = tx.execute(status -> releases.save(new SoftwareRelease(appId, "Display Q4", null, "admin")).getId());
        var request = new SoftwareUpdateCreateRequest(catalogId, "1.0.0", "v1.0.0", "MACOS", "ARM64", "stable", "default", "ELECTRON", null);
        updates.create(request, "admin");
        assertThatThrownBy(() -> updates.create(request, "admin"))
                .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainConflictException.class)
                .hasMessageContaining("error.software.update.identityConflict");
        String alias = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(new SoftwareUpdateCreateRequest(
                catalogId, "1.0.0", "1.0.0", "MACOS", "ARM64", "stable", "default", "ELECTRON", null));
        http.perform(post("/api/v1/admin/software/update-releases").with(authentication(auth("SOFTWARE_ADMIN"))).with(csrf())
                .contentType("application/json").content(alias)).andExpect(status().isConflict());
    }

    @Test
    void managementHttpRequiresSoftwareRoleAndCsrfAndReceivesOriginalAssetBytes() throws Exception {
        seed();
        long appId = tx.execute(status -> applications.save(new SoftwareApplication("http-" + System.nanoTime(), "HTTP App", null, null, null, "admin")).getId());
        long catalogId = tx.execute(status -> releases.save(new SoftwareRelease(appId, "Display HTTP", null, "admin")).getId());
        String endpoint = "/api/v1/admin/software/update-releases";
        http.perform(get(endpoint).param("catalogReleaseId", "" + catalogId)).andExpect(status().isUnauthorized());
        http.perform(get(endpoint).param("catalogReleaseId", "" + catalogId).with(authentication(auth("SKILL_ADMIN"))))
                .andExpect(status().isForbidden());
        String request = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(new SoftwareUpdateCreateRequest(
                catalogId, "2.0.0", "v2.0.0", "WINDOWS", "X86_64", "stable", "default", "ELECTRON", null));
        http.perform(post(endpoint).with(authentication(auth("SOFTWARE_ADMIN"))).contentType("application/json").content(request))
                .andExpect(status().isUnauthorized());
        String created = http.perform(post(endpoint).with(authentication(auth("SOFTWARE_ADMIN"))).with(csrf())
                .contentType("application/json").content(request)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.machineVersion").value("2.0.0")).andReturn().getResponse().getContentAsString();
        long id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(created).path("data").path("id").asLong();
        String longestVersion = "1.2.3+" + "x".repeat(249);
        String longTag = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(new SoftwareUpdateCreateRequest(
                catalogId, longestVersion, "v" + longestVersion, "WINDOWS", "X86_64", "stable", "default", "ELECTRON", null));
        http.perform(post(endpoint).with(authentication(auth("SOFTWARE_ADMIN"))).with(csrf())
                .contentType("application/json").content(longTag)).andExpect(status().isBadRequest());
        byte[] raw = "version: 2.0.0\nfiles: []\n".getBytes(StandardCharsets.UTF_8);
        http.perform(post(endpoint + "/" + id + "/assets").with(authentication(auth("SOFTWARE_ADMIN"))).with(csrf())
                .queryParam("path", "dist/白泽.latest.yml").queryParam("role", "METADATA")
                .contentType("application/octet-stream").content(raw))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.relativePath").value("dist/白泽.latest.yml"))
                .andExpect(jsonPath("$.data.byteSize").value(raw.length));
        long committed = quota.totalCommittedBytes();
        http.perform(post(endpoint + "/" + id + "/assets").with(authentication(auth("SOFTWARE_ADMIN"))).with(csrf())
                .queryParam("path", "unconfirmed.EXE").queryParam("role", "OTHER")
                .contentType("application/octet-stream").content(new byte[] {1, 2, 3}))
                .andExpect(status().isBadRequest());
        assertThat(quota.totalCommittedBytes()).isEqualTo(committed);
    }

    private org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth(String role) {
        var principal = new com.iflytek.skillhub.auth.rbac.PlatformPrincipal("admin", "Admin", "admin@example.test", "", "local", java.util.Set.of(role));
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test
    void draftAssetReplacementRetainsCapacityUntilFailedStorageDeletionIsRetried() {
        seed();
        long appId = tx.execute(status -> applications.save(new SoftwareApplication("cleanup-" + System.nanoTime(), "Cleanup App", null, null, null, "admin")).getId());
        long catalogId = tx.execute(status -> releases.save(new SoftwareRelease(appId, "Cleanup Display", null, "admin")).getId());
        var draft = updates.create(new SoftwareUpdateCreateRequest(catalogId, "3.0.0", "v3.0.0", "WINDOWS", "X86_64", "stable", "default", "ELECTRON", null), "admin");
        long initial = quota.totalCommittedBytes();
        var asset = updates.uploadAsset(draft.id(), "notes.txt", "OTHER", 3, new ByteArrayInputStream(new byte[] {1, 2, 3}), "admin");
        org.mockito.Mockito.doThrow(new IllegalStateException("storage unavailable")).when(storage).deleteObject(org.mockito.ArgumentMatchers.anyString());
        try {
            updates.removeAsset(draft.id(), asset.id(), "admin");
            assertThat(updates.get(draft.id()).assets()).isEmpty();
            assertThat(quota.totalCommittedBytes()).isEqualTo(initial + 3);
        } finally {
            org.mockito.Mockito.doCallRealMethod().when(storage).deleteObject(org.mockito.ArgumentMatchers.anyString());
        }
        updates.cleanupExpiredUploads();
        assertThat(quota.totalCommittedBytes()).isEqualTo(initial);
        updates.uploadAsset(draft.id(), "notes.txt", "OTHER", 3, new ByteArrayInputStream(new byte[] {4, 5, 6}), "admin");
        assertThat(updates.get(draft.id()).assets()).hasSize(1);
    }

    @Test
    void interruptedUploadRetainsItsReservationUntilVerifiedAbortSucceeds() {
        seed();
        long appId = tx.execute(status -> applications.save(new SoftwareApplication("abort-" + System.nanoTime(), "Abort App", null, null, null, "admin")).getId());
        long catalogId = tx.execute(status -> releases.save(new SoftwareRelease(appId, "Abort Display", null, "admin")).getId());
        var draft = updates.create(new SoftwareUpdateCreateRequest(catalogId, "4.0.0", "v4.0.0", "WINDOWS", "X86_64", "stable", "default", "ELECTRON", null), "admin");
        long initial = quota.totalCommittedBytes();
        org.mockito.Mockito.doThrow(new IllegalStateException("abort unavailable")).when(storage)
                .abortUploadVerified(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        try {
            assertThatThrownBy(() -> updates.uploadAsset(draft.id(), "broken.txt", "OTHER", 3,
                    new ByteArrayInputStream(new byte[] {1}), "admin")).isInstanceOf(RuntimeException.class);
            assertThat(quota.totalCommittedBytes()).isEqualTo(initial + 3);
            assertThat(updates.get(draft.id()).assets()).isEmpty();
        } finally {
            org.mockito.Mockito.doCallRealMethod().when(storage)
                    .abortUploadVerified(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        }
        updates.cleanupExpiredUploads();
        assertThat(quota.totalCommittedBytes()).isEqualTo(initial);
    }
}
