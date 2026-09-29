package com.iflytek.skillhub.config;

import com.iflytek.skillhub.filter.SoftwareInspectorAuthFilter;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the inspector authentication filter ahead of the Spring Security
 * chain so rejected internal requests never reach session or CSRF machinery.
 */
@Configuration
public class SoftwareInspectorFilterConfig {

    @Bean
    public FilterRegistrationBean<SoftwareInspectorAuthFilter> softwareInspectorAuthFilter(
            SoftwareInspectorProperties properties) {
        FilterRegistrationBean<SoftwareInspectorAuthFilter> registration =
                new FilterRegistrationBean<>(new SoftwareInspectorAuthFilter(properties));
        registration.addUrlPatterns("/api/v1/internal/software/*");
        registration.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER - 10);
        return registration;
    }
}
