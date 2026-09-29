package com.iflytek.skillhub.controller.portal;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.software.SoftwareAppDetailResponse;
import com.iflytek.skillhub.dto.software.SoftwareAppSummaryResponse;
import com.iflytek.skillhub.dto.software.SoftwareCategoryResponse;
import com.iflytek.skillhub.service.SoftwareCatalogAppService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SoftwareCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SoftwareCatalogAppService catalogAppService;

    @Test
    void anonymousAccess_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/software/apps"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void listApps_returnsPublishedAppsForLoggedInUsers() throws Exception {
        when(catalogAppService.listApps(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.anyInt(), Mockito.anyInt()))
                .thenReturn(new PageResponse<>(List.of(new SoftwareAppSummaryResponse(
                        1L, "vscode", "VS Code", "Editor", null, "dev-tools", "开发工具",
                        Instant.parse("2026-09-29T00:00:00Z"))), 1, 0, 20));

        mockMvc.perform(get("/api/v1/software/apps")
                        .with(authentication(userAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].slug").value("vscode"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void getAppDetail_returnsNotFoundForMissingApp() throws Exception {
        when(catalogAppService.getAppDetail("missing")).thenReturn(null);

        mockMvc.perform(get("/api/v1/software/apps/missing")
                        .with(authentication(userAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void listCategories_returnsDictionary() throws Exception {
        when(catalogAppService.listCategories()).thenReturn(List.of(
                new SoftwareCategoryResponse(1L, "dev-tools", "开发工具", 1)));

        mockMvc.perform(get("/api/v1/software/categories")
                        .with(authentication(userAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("dev-tools"));
    }

    private UsernamePasswordAuthenticationToken userAuth() {
        PlatformPrincipal principal = new PlatformPrincipal("user-1", "user", "u@example.com", "",
                "github", Set.of("USER"));
        return new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
