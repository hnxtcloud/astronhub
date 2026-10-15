package com.iflytek.skillhub.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * Logs inbound HTTP requests with only core parameters to keep log files compact.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final Set<String> SKIP_PREFIXES = Set.of(
            "/actuator", "/favicon.ico", "/assets/"
    );
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();
        if (shouldSkip(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = com.iflytek.skillhub.auth.software.GitLabUpdatePath.requestPath(request);
        boolean read = "GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod());
        boolean updateStream = read && (path.startsWith("/api/v4/projects/") || path.startsWith("/releases/")
                || path.startsWith("/updates/") || path.matches("/[^/]+/[^/]+/-/raw/.+"));
        boolean rawUpload = "POST".equals(request.getMethod()) && (path.equals("/api/v1/plugin-management/imports")
                || path.equals("/api/v1/plugin-management/npm/dependencies")
                || path.matches("/api/v1/plugin-management/releases/[0-9]+/archive")
                || path.matches("/api/v1/admin/software/update-releases/[0-9]+/assets")
                || path.matches("/api/v1/software-publishing/apps/[0-9]+/update-releases/[0-9]+/assets"))
                || "PUT".equals(request.getMethod()) && path.matches("/api/v1/software-publishing/apps/[0-9]+/uploads/[0-9]+/chunks/[0-9]+");
        if (updateStream || rawUpload) {
            // ContentCachingResponseWrapper buffers the entire installer and rewrites HEAD
            // Content-Length to zero. Updates must remain true streaming responses.
            long startTime = System.currentTimeMillis();
            try { filterChain.doFilter(request, response); }
            finally { logRequest(request, response, System.currentTimeMillis() - startTime); }
            return;
        }

        ContentCachingRequestWrapper cachedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(cachedRequest, cachedResponse);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logRequest(cachedRequest, cachedResponse, duration);
            cachedResponse.copyBodyToResponse();
        }
    }

    private void logRequest(HttpServletRequest request, HttpServletResponse response, long duration) {
        String requestUri = request.getRequestURI();
        String queryString = request.getQueryString();
        String fullUrl = queryString != null ? requestUri + "?" + sanitizeQueryString(queryString) : requestUri;

        String contentType = request.getContentType();
        String userAgent = request.getHeader("User-Agent");

        StringBuilder sb = new StringBuilder();
        sb.append(request.getMethod()).append(" ").append(fullUrl);
        sb.append(" | ").append(response.getStatus());
        sb.append(" | ").append(duration).append("ms");
        sb.append(" | ").append(request.getRemoteAddr());
        if (contentType != null) {
            sb.append(" | Content-Type: ").append(contentType);
        }
        if (userAgent != null) {
            sb.append(" | UA: ").append(truncate(userAgent, 80));
        }

        log.info(sb.toString());
    }

    private String sanitizeQueryString(String queryString) {
        return java.util.Arrays.stream(queryString.split("&", -1))
                .map(parameter -> {
                    int separator = parameter.indexOf('=');
                    if (separator < 0) {
                        return parameter;
                    }
                    String name = parameter.substring(0, separator).toLowerCase(Locale.ROOT);
                    return isSensitiveQueryParameter(name)
                            ? parameter.substring(0, separator) + "=[REDACTED]"
                            : parameter;
                })
                .collect(java.util.stream.Collectors.joining("&"));
    }

    private boolean isSensitiveQueryParameter(String name) {
        return Set.of("code", "state", "error", "error_description", "error_uri", "access_token",
                "refresh_token", "id_token", "client_secret").contains(name);
    }

    private boolean shouldSkip(String uri) {
        for (String prefix : SKIP_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...[truncated]";
    }
}
