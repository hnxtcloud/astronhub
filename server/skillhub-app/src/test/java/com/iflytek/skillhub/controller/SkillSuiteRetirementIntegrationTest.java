package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest(properties = "skillhub.suite.retirement.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class SkillSuiteRetirementIntegrationTest {
    @Autowired private MockMvc mvc;
    @MockBean private NamespaceMemberRepository namespaceMembers;
    @MockBean private DeviceAuthService deviceAuthService;

    private static final String DEFINITION = """
            {"namespace":"team","slug":"writers","displayName":"Writers","version":"1.0.0",
             "visibility":"PUBLIC","entrySkill":{"skillVersionId":1,"namespace":"global","slug":"writer","version":"1.0.0"},
             "members":[{"skillVersionId":1,"namespace":"global","slug":"writer","version":"1.0.0"}]}
            """;

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1", "/api/web"})
    void refusesNewContentThroughBothApiAliasesEvenForAdministrator(String prefix) throws Exception {
        for (String route : List.of("/suites", "/suites/7/versions")) {
            mvc.perform(post(prefix + route).with(authentication(admin())).with(csrf())
                            .header("Accept-Language", "en").contentType(MediaType.APPLICATION_JSON).content(DEFINITION))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(containsString("creation and editing have ended")));
        }
        mvc.perform(put(prefix + "/suites/7/versions/8").with(authentication(admin())).with(csrf())
                        .header("Accept-Language", "en").contentType(MediaType.APPLICATION_JSON).content(DEFINITION))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("creation and editing have ended")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1", "/api/web"})
    void refusesNewBundlePreviewAndConfirmation(String prefix) throws Exception {
        mvc.perform(multipart(prefix + "/suite-bundles/preview")
                        .file(new MockMultipartFile("file", "bundle.zip", "application/zip", new byte[]{1}))
                        .with(authentication(admin())).with(csrf()).header("Accept-Language", "en"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("creation and editing have ended")));
        mvc.perform(post(prefix + "/suite-bundles/previews/unconfirmed/confirm")
                        .with(authentication(admin())).with(csrf()).header("Accept-Language", "en")
                        .header("Idempotency-Key", "retired-confirmation")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"warningDigest\":\"digest\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("creation and editing have ended")));
    }

    private UsernamePasswordAuthenticationToken admin() {
        var principal = new PlatformPrincipal("retirement-admin", "Admin", "admin@example.test", null,
                "local", Set.of("SUPER_ADMIN"));
        return new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
    }
}
