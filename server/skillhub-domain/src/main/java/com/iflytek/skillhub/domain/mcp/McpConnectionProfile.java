package com.iflytek.skillhub.domain.mcp;

import jakarta.persistence.*;
import java.time.Clock;
import java.time.Instant;

@Entity
@Table(name = "mcp_connection_profile")
public class McpConnectionProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "revision_id", nullable = false)
    private Long revisionId;
    @Column(name = "profile_key", nullable = false, length = 100)
    private String profileKey;
    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;
    @Enumerated(EnumType.STRING) @Column(name = "transport_type", nullable = false, length = 32)
    private McpTransportType transportType;
    @Column(name = "endpoint_template", columnDefinition = "TEXT")
    private String endpointTemplate;
    @Column(name = "command_template", columnDefinition = "TEXT")
    private String commandTemplate;
    @Column(name = "arguments_json", columnDefinition = "TEXT")
    private String argumentsJson;
    @Column(name = "variables_json", columnDefinition = "TEXT")
    private String variablesJson;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected McpConnectionProfile() {}

    public McpConnectionProfile(Long revisionId, String profileKey, String displayName,
                                McpTransportType transportType) {
        this.revisionId = revisionId;
        this.profileKey = profileKey;
        this.displayName = displayName;
        this.transportType = transportType;
    }

    @PrePersist
    protected void onCreate() { createdAt = Instant.now(Clock.systemUTC()); }

    public Long getId() { return id; }
    public Long getRevisionId() { return revisionId; }
    public String getProfileKey() { return profileKey; }
    public String getDisplayName() { return displayName; }
    public McpTransportType getTransportType() { return transportType; }
    public String getEndpointTemplate() { return endpointTemplate; }
    public String getCommandTemplate() { return commandTemplate; }
    public String getArgumentsJson() { return argumentsJson; }
    public String getVariablesJson() { return variablesJson; }
    public Instant getCreatedAt() { return createdAt; }
}
