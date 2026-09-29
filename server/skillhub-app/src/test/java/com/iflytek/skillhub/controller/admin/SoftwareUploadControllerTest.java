package com.iflytek.skillhub.controller.admin;

import static org.mockito.ArgumentMatchers.any;
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
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.dto.software.SoftwareChunkReceiptResponse;
import com.iflytek.skillhub.dto.software.SoftwareUploadCompleteResponse;
import com.iflytek.skillhub.dto.software.SoftwareUploadProgressResponse;
import com.iflytek.skillhub.dto.software.SoftwareUploadSessionResponse;
import com.iflytek.skillhub.service.SoftwareUploadAppService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SoftwareUploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SoftwareUploadAppService uploadAppService;

    @Test
    void uploadRoutes_rejectAnonymousCallers() throws Exception {
        mockMvc.perform(post("/api/v1/admin/software/uploads")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadRoutes_rejectRolesWithoutSoftwareDuty() throws Exception {
        mockMvc.perform(get("/api/v1/admin/software/uploads/1")
                        .with(authentication(skillAdminAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void createSession_returnsChunkPlan() throws Exception {
        when(uploadAppService.createSession(any(), eq("software-admin")))
                .thenReturn(new SoftwareUploadSessionResponse(7L, "ACTIVE", "app.msi", "MSI",
                        "WINDOWS", "X86_64", null, 1024, 512, 2, 0, 1,
                        Instant.parse("2026-09-29T00:00:00Z"), Instant.parse("2026-09-30T00:00:00Z")));

        mockMvc.perform(post("/api/v1/admin/software/uploads")
                        .with(authentication(softwareAdminAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"releaseId": 10, "fileName": "app.msi", "format": "MSI",
                                 "targetOs": "WINDOWS", "targetArch": "X86_64",
                                 "originalFullVersion": "1.0.0.0", "byteSize": 1024}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalChunks").value(2))
                .andExpect(jsonPath("$.data.chunkSizeBytes").value(512));
    }

    @Test
    void putChunk_streamsBytesAndReturnsReceipt() throws Exception {
        when(uploadAppService.putChunk(eq(7L), eq(1), eq(4L), any(), eq("software-admin")))
                .thenReturn(new SoftwareChunkReceiptResponse(1, 4, "a".repeat(64), false));

        mockMvc.perform(put("/api/v1/admin/software/uploads/7/chunks/1")
                        .with(authentication(softwareAdminAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(new byte[] {1, 2, 3, 4}))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chunkNo").value(1))
                .andExpect(jsonPath("$.data.alreadyPresent").value(false));
    }

    @Test
    void putChunk_conflictingBytesSurfaceAs409() throws Exception {
        when(uploadAppService.putChunk(eq(7L), eq(1), eq(4L), any(), eq("software-admin")))
                .thenThrow(new DomainConflictException("error.software.upload.chunkConflict", 1));

        mockMvc.perform(put("/api/v1/admin/software/uploads/7/chunks/1")
                        .with(authentication(softwareAdminAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(new byte[] {1, 2, 3, 4}))
                .andExpect(status().isConflict());
    }

    @Test
    void complete_returnsPackageRevision() throws Exception {
        when(uploadAppService.complete(7L, "software-admin"))
                .thenReturn(new SoftwareUploadCompleteResponse(7L, "COMPLETED", 500L, 1,
                        "app.msi", "a".repeat(64), 1024));

        mockMvc.perform(post("/api/v1/admin/software/uploads/7/complete")
                        .with(authentication(softwareAdminAuth()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.packageRevisionId").value(500))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    void progress_returnsManifest() throws Exception {
        when(uploadAppService.progress(7L, "software-admin"))
                .thenReturn(new SoftwareUploadProgressResponse(7L, "ACTIVE", 2, 512, 512,
                        List.of(new SoftwareUploadProgressResponse.UploadedChunk(1, 512, "a".repeat(64))),
                        Instant.parse("2026-09-30T00:00:00Z")));

        mockMvc.perform(get("/api/v1/admin/software/uploads/7")
                        .with(authentication(softwareAdminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadedBytes").value(512))
                .andExpect(jsonPath("$.data.chunks[0].chunkNo").value(1));
    }

    private UsernamePasswordAuthenticationToken softwareAdminAuth() {
        PlatformPrincipal principal = new PlatformPrincipal("software-admin", "sw", "s@example.com", "",
                "github", Set.of("SOFTWARE_ADMIN"));
        return new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_SOFTWARE_ADMIN")));
    }

    private UsernamePasswordAuthenticationToken skillAdminAuth() {
        PlatformPrincipal principal = new PlatformPrincipal("reviewer", "rv", "r@example.com", "",
                "github", Set.of("SKILL_ADMIN"));
        return new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_SKILL_ADMIN")));
    }
}
