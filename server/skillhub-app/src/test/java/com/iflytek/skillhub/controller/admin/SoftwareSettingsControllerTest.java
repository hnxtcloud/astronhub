package com.iflytek.skillhub.controller.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.dto.software.SoftwareDeploymentLimitsDto;
import com.iflytek.skillhub.dto.software.SoftwarePolicyConfigDto;
import com.iflytek.skillhub.dto.software.SoftwareSettingsResponse;
import com.iflytek.skillhub.service.SoftwareSettingsAppService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SoftwareSettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SoftwareSettingsAppService settingsAppService;

    @Test
    void getSettings_rejectsAnonymousUsers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/software/settings"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void getSettings_readableBySoftwareAdmin() throws Exception {
        when(settingsAppService.getActive()).thenReturn(settingsResponse(3L));

        mockMvc.perform(get("/api/v1/admin/software/settings")
                        .with(authentication(softwareAdminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.revision").value(3))
                .andExpect(jsonPath("$.data.config.maxPackageBytes").value(5368709120L));
    }

    @Test
    void updateSettings_allowedForSuperAdmin() throws Exception {
        when(settingsAppService.update(any(), anyLong(), eq("调低单文件上限"), eq("admin"), any()))
                .thenReturn(settingsResponse(4L));

        mockMvc.perform(put("/api/v1/admin/software/settings")
                        .with(authentication(superAdminAuth()))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "expectedRevision": 3,
                                  "changeReason": "调低单文件上限",
                                  "config": {
                                    "maxPackageBytes": 4294967296,
                                    "totalQuotaBytes": 536870912000,
                                    "maxActiveUploadsPerAdmin": 2,
                                    "chunkSizeBytes": 8388608,
                                    "uploadIdleTimeoutMinutes": 1440,
                                    "failedTempRetentionHours": 168,
                                    "cleanupScanIntervalMinutes": 5,
                                    "maxConcurrentInspectionsPerWorker": 1,
                                    "inspectionTotalTimeoutSeconds": 900,
                                    "inspectionSubprocessTimeoutSeconds": 60,
                                    "inspectionSoftMemoryLimitBytes": 536870912,
                                    "inspectionMaxExtractedBytes": 21474836480,
                                    "inspectionMaxExtractedEntries": 10000,
                                    "maxAutoInspectionAttempts": 3,
                                    "downloadSessionIdleTimeoutMinutes": 1440
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revision").value(4));
    }

    @Test
    void updateSettings_forbiddenForSoftwareAdmin() throws Exception {
        mockMvc.perform(put("/api/v1/admin/software/settings")
                        .with(authentication(softwareAdminAuth()))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "expectedRevision": 3,
                                  "changeReason": "attempt",
                                  "config": {
                                    "maxPackageBytes": 5368709120,
                                    "totalQuotaBytes": 536870912000,
                                    "maxActiveUploadsPerAdmin": 2,
                                    "chunkSizeBytes": 8388608,
                                    "uploadIdleTimeoutMinutes": 1440,
                                    "failedTempRetentionHours": 168,
                                    "cleanupScanIntervalMinutes": 5,
                                    "maxConcurrentInspectionsPerWorker": 1,
                                    "inspectionTotalTimeoutSeconds": 900,
                                    "inspectionSubprocessTimeoutSeconds": 60,
                                    "inspectionSoftMemoryLimitBytes": 536870912,
                                    "inspectionMaxExtractedBytes": 21474836480,
                                    "inspectionMaxExtractedEntries": 10000,
                                    "maxAutoInspectionAttempts": 3,
                                    "downloadSessionIdleTimeoutMinutes": 1440
                                  }
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateSettings_blankChangeReason_rejectedByDomainValidation() throws Exception {
        when(settingsAppService.update(any(), anyLong(), eq(""), eq("admin"), any()))
                .thenThrow(new com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException(
                        "error.software.settings.reasonRequired"));

        mockMvc.perform(put("/api/v1/admin/software/settings")
                        .with(authentication(superAdminAuth()))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "expectedRevision": 3,
                                  "changeReason": "",
                                  "config": {
                                    "maxPackageBytes": 5368709120,
                                    "totalQuotaBytes": 536870912000,
                                    "maxActiveUploadsPerAdmin": 2,
                                    "chunkSizeBytes": 8388608,
                                    "uploadIdleTimeoutMinutes": 1440,
                                    "failedTempRetentionHours": 168,
                                    "cleanupScanIntervalMinutes": 5,
                                    "maxConcurrentInspectionsPerWorker": 1,
                                    "inspectionTotalTimeoutSeconds": 900,
                                    "inspectionSubprocessTimeoutSeconds": 60,
                                    "inspectionSoftMemoryLimitBytes": 536870912,
                                    "inspectionMaxExtractedBytes": 21474836480,
                                    "inspectionMaxExtractedEntries": 10000,
                                    "maxAutoInspectionAttempts": 3,
                                    "downloadSessionIdleTimeoutMinutes": 1440
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void rollback_allowedForSuperAdminOnly() throws Exception {
        when(settingsAppService.rollback(eq(1L), eq("回滚"), eq("admin"), any()))
                .thenReturn(settingsResponse(9L));

        mockMvc.perform(post("/api/v1/admin/software/settings/rollback")
                        .with(authentication(superAdminAuth()))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"fromRevision": 1, "changeReason": "回滚"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revision").value(9));

        mockMvc.perform(post("/api/v1/admin/software/settings/rollback")
                        .with(authentication(softwareAdminAuth()))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"fromRevision": 1, "changeReason": "回滚"}
                                """))
                .andExpect(status().isForbidden());
    }

    private SoftwareSettingsResponse settingsResponse(long revision) {
        return new SoftwareSettingsResponse(
                revision,
                1,
                new SoftwarePolicyConfigDto(5368709120L, 536870912000L, 2, 8388608L, 1440L, 168L, 5L,
                        1, 900L, 60L, 536870912L, 21474836480L, 10_000, 3, 1440L),
                new SoftwareDeploymentLimitsDto(104857600L, 1073741824L, 536870912L, 536870912L),
                "admin",
                Instant.parse("2026-09-29T00:00:00Z")
        );
    }

    private UsernamePasswordAuthenticationToken superAdminAuth() {
        PlatformPrincipal principal = new PlatformPrincipal("admin", "admin", "a@example.com", "",
                "github", Set.of("SUPER_ADMIN"));
        return new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
    }

    private UsernamePasswordAuthenticationToken softwareAdminAuth() {
        PlatformPrincipal principal = new PlatformPrincipal("software-admin", "sw", "s@example.com", "",
                "github", Set.of("SOFTWARE_ADMIN"));
        return new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_SOFTWARE_ADMIN")));
    }
}
