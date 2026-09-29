package com.iflytek.skillhub.filter;

import com.iflytek.skillhub.config.SoftwareInspectorProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates the internal inspector task API with a deployment-scoped
 * shared token. The comparison is constant-time; a blank configured token
 * fails every request closed, so the API can never silently run unauthenticated.
 * The inspector itself holds no database or admin credentials — this token is
 * its only server-side identity, and it is valid only on
 * {@code /api/v1/internal/software/**}.
 */
public class SoftwareInspectorAuthFilter extends OncePerRequestFilter {

    static final String TOKEN_HEADER = "X-Inspector-Token";

    private static final Logger log = LoggerFactory.getLogger(SoftwareInspectorAuthFilter.class);

    private final SoftwareInspectorProperties properties;

    public SoftwareInspectorAuthFilter(SoftwareInspectorProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String expected = properties.getToken();
        if (expected == null || expected.isBlank()) {
            log.warn("Rejecting inspector request: skillhub.software.inspector.token is not configured");
            reject(response);
            return;
        }
        String provided = request.getHeader(TOKEN_HEADER);
        if (provided == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8))) {
            reject(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":401,\"msg\":\"inspector authentication required\"}");
    }
}
