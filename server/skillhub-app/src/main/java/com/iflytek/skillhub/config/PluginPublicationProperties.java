package com.iflytek.skillhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Explicit deployment-only allowlists. Never resolve administrator input as an environment name.
 */
@Component
@ConfigurationProperties(prefix = "skillhub.plugin.publication")
public class PluginPublicationProperties {
    private List<String> allowedRegistries = new ArrayList<>();
    private Map<String, String> credentials = new HashMap<>();

    public List<String> getAllowedRegistries() {
        return allowedRegistries;
    }

    public void setAllowedRegistries(List<String> value) {
        allowedRegistries = value;
    }

    public Map<String, String> getCredentials() {
        return credentials;
    }

    public void setCredentials(Map<String, String> value) {
        credentials = value;
    }

    public boolean available(String ref) {
        String value = credentials.get(ref);
        return value != null && !value.isBlank() && !value.contains("\r") && !value.contains("\n");
    }

    public String resolve(String ref) {
        String secret = credentials.get(ref);
        if (secret == null || secret.isBlank() || secret.contains("\r") || secret.contains("\n"))
            throw new IllegalStateException("PUBLISHER_CREDENTIAL_UNAVAILABLE");
        return secret;
    }
}
