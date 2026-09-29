package com.iflytek.skillhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Credentials for the internal inspector task API. The shared token is a
 * deployment secret: it must never be logged and defaults to empty, which
 * fails every internal request closed until an operator configures it.
 */
@Component
@ConfigurationProperties(prefix = "skillhub.software.inspector")
public class SoftwareInspectorProperties {

    private String token = "";

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
