package com.iflytek.skillhub.config;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Preserve encoded GitLab data separators until the scoped application firewall validates them. */
@Configuration
public class SoftwareUpdateTomcatConfig {
    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> softwareUpdateEncodedPaths() {
        return factory -> factory.addConnectorCustomizers(connector -> connector.setEncodedSolidusHandling("passthrough"));
    }
}
