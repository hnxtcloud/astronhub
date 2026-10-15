package com.iflytek.skillhub.auth.software;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.web.firewall.*;

/** All ordinary routes retain the unmodified default firewall. */
@Configuration
public class SoftwareUpdateFirewallConfig {
    @Bean
    WebSecurityCustomizer softwareUpdateFirewall() {
        StrictHttpFirewall ordinary = new StrictHttpFirewall();
        StrictHttpFirewall encodedRead = new StrictHttpFirewall();
        encodedRead.setAllowUrlEncodedSlash(true);
        HttpFirewall firewall = new HttpFirewall() {
            @Override public FirewalledRequest getFirewalledRequest(HttpServletRequest request) {
                if (GitLabUpdatePath.parse(request).isEmpty()) return ordinary.getFirewalledRequest(request);
                // Tomcat passthrough leaves %2F even in its decoded servlet path. Validate
                // that path with real separators while preserving the original raw URI for
                // the transport parser. The default encoded-percent/period/backslash and
                // normalization blocklists remain intact on both firewall instances.
                var normalizedServletPath = new jakarta.servlet.http.HttpServletRequestWrapper(request) {
                    private String separators(String value) { return value == null ? null : value.replaceAll("(?i)%2f", "/"); }
                    @Override public String getServletPath() { return separators(super.getServletPath()); }
                    @Override public String getPathInfo() { return separators(super.getPathInfo()); }
                };
                return encodedRead.getFirewalledRequest(normalizedServletPath);
            }
            @Override public HttpServletResponse getFirewalledResponse(HttpServletResponse response) {
                return ordinary.getFirewalledResponse(response);
            }
        };
        return web -> web.httpFirewall(firewall);
    }
}
