package com.iflytek.skillhub.controller.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.software.SoftwareDownloadSession;
import com.iflytek.skillhub.domain.software.SoftwarePackageFormat;
import com.iflytek.skillhub.domain.software.SoftwarePackageRevision;
import com.iflytek.skillhub.domain.software.SoftwareTargetArch;
import com.iflytek.skillhub.domain.software.SoftwareTargetOs;
import com.iflytek.skillhub.dto.software.SoftwareDownloadSessionResponse;
import com.iflytek.skillhub.service.SoftwareDownloadAppService;
import com.iflytek.skillhub.service.SoftwareDownloadAppService.DownloadTarget;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
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
class SoftwareDownloadControllerTest {

    private static final byte[] PAYLOAD = new byte[1024];
    private static final String SESSION_ID = "d-session-1";

    static {
        for (int i = 0; i < PAYLOAD.length; i++) {
            PAYLOAD[i] = (byte) (i % 251);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SoftwareDownloadAppService downloadAppService;

    private final Instant now = Instant.parse("2026-09-29T00:00:00Z");

    @BeforeEach
    void setUp() {
        when(downloadAppService.createSession(5L, "user-1")).thenReturn(
                new SoftwareDownloadSessionResponse(SESSION_ID,
                        "/api/v1/software/downloads/" + SESSION_ID, now.plusSeconds(86400)));
        when(downloadAppService.open(SESSION_ID, "user-1")).thenReturn(target());
        when(downloadAppService.openRange(org.mockito.ArgumentMatchers.any(), anyLong(), anyLong()))
                .thenAnswer(invocation -> {
                    long offset = invocation.getArgument(1);
                    long length = invocation.getArgument(2);
                    return new ByteArrayInputStream(java.util.Arrays.copyOfRange(PAYLOAD,
                            (int) offset, (int) Math.min(PAYLOAD.length, offset + length)));
                });
    }

    private DownloadTarget target() {
        SoftwareDownloadSession session = new SoftwareDownloadSession(
                SESSION_ID, "user-1", 5L, true, 1440, now);
        SoftwarePackageRevision revision = new SoftwarePackageRevision(
                10L, 1L, 1, SoftwareTargetOs.WINDOWS, SoftwareTargetArch.X86_64, null, null,
                SoftwarePackageFormat.MSI, "1.0.0.0", null, "app.msi",
                "a".repeat(64), PAYLOAD.length, "software/packages/x/app.msi", "admin-1");
        return new DownloadTarget(session, revision, PAYLOAD.length);
    }

    @Test
    void downloadSessions_rejectAnonymousCallers() throws Exception {
        mockMvc.perform(post("/api/v1/software/packages/5/download-sessions").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createDownloadSession_returnsSiteScopedUrl() throws Exception {
        mockMvc.perform(post("/api/v1/software/packages/5/download-sessions")
                        .with(authentication(userAuth("user-1")))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(SESSION_ID))
                .andExpect(jsonPath("$.data.downloadUrl")
                        .value("/api/v1/software/downloads/" + SESSION_ID));
    }

    @Test
    void fullDownload_returns200WithStrongEtagAndExactBytes() throws Exception {
        mockMvc.perform(get("/api/v1/software/downloads/" + SESSION_ID)
                        .with(authentication(userAuth("user-1"))))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"a" + "a".repeat(63) + "\""))
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.startsWith("attachment")))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                        .isEqualTo(PAYLOAD));
    }

    @Test
    void rangeDownload_returns206WithRequestedSlice() throws Exception {
        mockMvc.perform(get("/api/v1/software/downloads/" + SESSION_ID)
                        .with(authentication(userAuth("user-1")))
                        .header("Range", "bytes=100-199"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range",
                        "bytes 100-199/" + PAYLOAD.length))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                        .isEqualTo(java.util.Arrays.copyOfRange(PAYLOAD, 100, 200)));
    }

    @Test
    void suffixRangeDownload_returnsFileTail() throws Exception {
        when(downloadAppService.openRange(org.mockito.ArgumentMatchers.any(), eq(924L), eq(100L)))
                .thenReturn(new ByteArrayInputStream(
                        java.util.Arrays.copyOfRange(PAYLOAD, 924, 1024)));

        mockMvc.perform(get("/api/v1/software/downloads/" + SESSION_ID)
                        .with(authentication(userAuth("user-1")))
                        .header("Range", "bytes=-100"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 924-1023/" + PAYLOAD.length));
    }

    @Test
    void unsatisfiableRange_returns416WithSizedContentRange() throws Exception {
        mockMvc.perform(get("/api/v1/software/downloads/" + SESSION_ID)
                        .with(authentication(userAuth("user-1")))
                        .header("Range", "bytes=5000-"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string("Content-Range", "bytes */" + PAYLOAD.length));
    }

    @Test
    void multiRangeRequest_isDowngradedToFull200() throws Exception {
        mockMvc.perform(get("/api/v1/software/downloads/" + SESSION_ID)
                        .with(authentication(userAuth("user-1")))
                        .header("Range", "bytes=0-10,20-30"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                        .isEqualTo(PAYLOAD));
    }

    @Test
    void ifRangeMismatch_returnsFull200WhileMatchServes206() throws Exception {
        String etag = "\"" + "a".repeat(64) + "\"";

        mockMvc.perform(get("/api/v1/software/downloads/" + SESSION_ID)
                        .with(authentication(userAuth("user-1")))
                        .header("Range", "bytes=0-99")
                        .header("If-Range", "\"stale-etag\""))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                        .isEqualTo(PAYLOAD));

        mockMvc.perform(get("/api/v1/software/downloads/" + SESSION_ID)
                        .with(authentication(userAuth("user-1")))
                        .header("Range", "bytes=0-99")
                        .header("If-Range", etag))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 0-99/" + PAYLOAD.length));
    }

    @Test
    void headRequest_returnsHeadersWithoutBody() throws Exception {
        // Spring's HttpHeadResponseWrapper suppresses the body (and recomputes
        // Content-Length from written bytes, i.e. zero) for HEAD requests; the
        // protocol-relevant validators still come through.
        mockMvc.perform(head("/api/v1/software/downloads/" + SESSION_ID)
                        .with(authentication(userAuth("user-1"))))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"a" + "a".repeat(63) + "\""))
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(result ->
                        assertThat(result.getResponse().getContentAsByteArray()).isEmpty());
    }

    @Test
    void otherUsersSession_isNotFound() throws Exception {
        when(downloadAppService.open(SESSION_ID, "user-2"))
                .thenThrow(new DomainNotFoundException("error.software.download.sessionNotFound",
                        SESSION_ID));

        mockMvc.perform(get("/api/v1/software/downloads/" + SESSION_ID)
                        .with(authentication(userAuth("user-2"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void rangeOnTakenDownPackage_failsWithBadRequest() throws Exception {
        when(downloadAppService.open(SESSION_ID, "user-1"))
                .thenThrow(new com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException(
                        "error.software.download.notPublished", "TAKEN_DOWN"));

        mockMvc.perform(get("/api/v1/software/downloads/" + SESSION_ID)
                        .with(authentication(userAuth("user-1")))
                        .header("Range", "bytes=0-99"))
                .andExpect(status().isBadRequest());
    }

    private UsernamePasswordAuthenticationToken userAuth(String userId) {
        PlatformPrincipal principal = new PlatformPrincipal(userId, "u", userId + "@example.com", "",
                "github", Set.of("USER"));
        return new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
