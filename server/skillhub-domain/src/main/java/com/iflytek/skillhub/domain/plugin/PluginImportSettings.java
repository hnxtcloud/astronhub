package com.iflytek.skillhub.domain.plugin;

import jakarta.persistence.*;

@Entity
@Table(name = "plugin_import_settings")
public class PluginImportSettings {
    @Id private Long id = 1L;

    @Column(name = "limits_json", nullable = false, columnDefinition = "TEXT")
    private String limitsJson;

    protected PluginImportSettings() {}

    public PluginImportSettings(String json) {
        limitsJson = json;
    }

    public String getLimitsJson() {
        return limitsJson;
    }

    public void update(String json) {
        limitsJson = json;
    }
}
