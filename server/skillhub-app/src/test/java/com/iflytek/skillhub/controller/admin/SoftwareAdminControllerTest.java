package com.iflytek.skillhub.controller.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.dto.software.SoftwareAdminGrantResponse;
import com.iflytek.skillhub.dto.software.SoftwareAppAdminSummaryResponse;
import com.iflytek.skillhub.dto.software.SoftwareCategoryResponse;
import com.iflytek.skillhub.repository.SoftwareCatalogQueryRepository;
import com.iflytek.skillhub.service.SoftwareAdminAppService;
import com.iflytek.skillhub.service.SoftwareCatalogAppService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SoftwareAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SoftwareAdminAppService adminAppService;

    @MockBean
    private SoftwareCatalogAppService catalogAppService;

    @MockBean
    private SoftwareCatalogQueryRepository catalogQueryRepository;

    @MockBean
    private com.iflytek.skillhub.service.SoftwareAdminGrantAppService grantAppService;

    @Test
    void adminSoftwareRoutes_rejectSoftwareAdminlessRoles() throws Exception {
        mockMvc.perform(get("/api/v1/admin/software/apps")
                        .with(authentication(skillAdminAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void listApps_showsDraftAndTakenDownForSoftwareAdmin() throws Exception {
        when(catalogQueryRepository.searchAppsForAdmin(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(new SoftwareAppAdminSummaryResponse(
                        1L, "vscode", "VS Code", null, null, null, null, "DRAFT",
                        Instant.parse("2026-09-29T00:00:00Z"), Instant.parse("2026-09-29T00:00:00Z"))),
                        PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/admin/software/apps")
                        .with(authentication(softwareAdminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].status").value("DRAFT"));
    }

    @Test
    void createApp_returnsCreatedApplication() throws Exception {
        when(adminAppService.createApp(any(), eq("software-admin"), any()))
                .thenReturn(new com.iflytek.skillhub.domain.software.SoftwareApplication(
                        "vscode", "VS Code", null, null, null, "software-admin"));

        mockMvc.perform(post("/api/v1/admin/software/apps")
                        .with(authentication(softwareAdminAuth()))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"slug": "vscode", "name": "VS Code", "description": "Editor"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.slug").value("vscode"));
    }

    @Test
    void createCategory_returnsCreatedCategory() throws Exception {
        when(adminAppService.createCategory(any(), eq("software-admin"), any()))
                .thenReturn(new SoftwareCategoryResponse(1L, "dev-tools", "开发工具", 1));

        mockMvc.perform(post("/api/v1/admin/software/categories")
                        .with(authentication(softwareAdminAuth()))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"code": "dev-tools", "name": "开发工具", "sortOrder": 1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("dev-tools"));
    }

    @Test
    void grants_routesAreSuperAdminOnly() throws Exception {
        mockMvc.perform(get("/api/v1/admin/software/admin-grants")
                        .with(authentication(softwareAdminAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void grant_addsDutyForSuperAdmin() throws Exception {
        when(grantAppService.grant(eq("user-9"), eq("admin"), any()))
                .thenReturn(new SoftwareAdminGrantResponse("user-9", "bob", "b@example.com",
                        List.of("SKILL_ADMIN", "SOFTWARE_ADMIN"), Instant.parse("2026-09-29T00:00:00Z")));

        mockMvc.perform(post("/api/v1/admin/software/admin-grants")
                        .with(authentication(superAdminAuth()))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"userId": "user-9"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("user-9"))
                .andExpect(jsonPath("$.data.allRoles[?(@ == 'SKILL_ADMIN')]").exists())
                .andExpect(jsonPath("$.data.allRoles[?(@ == 'SOFTWARE_ADMIN')]").exists());
    }

    @Test
    void revoke_removesOnlySoftwareAdminDuty() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/software/admin-grants/user-9")
                        .with(authentication(superAdminAuth()))
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(grantAppService).revoke(eq("user-9"), eq("admin"), any());
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

    private UsernamePasswordAuthenticationToken skillAdminAuth() {
        PlatformPrincipal principal = new PlatformPrincipal("reviewer", "rv", "r@example.com", "",
                "github", Set.of("SKILL_ADMIN"));
        return new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_SKILL_ADMIN")));
    }
}
