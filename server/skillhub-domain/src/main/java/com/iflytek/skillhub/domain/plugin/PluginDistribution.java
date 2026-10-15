package com.iflytek.skillhub.domain.plugin;

import jakarta.persistence.*;
import java.time.Clock;
import java.time.Instant;

@Entity
@Table(name = "plugin_distribution")
public class PluginDistribution {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "release_id", nullable = false)
    private Long releaseId;
    @Column(name = "runtime_key", nullable = false, length = 64)
    private String runtimeKey;
    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;
    @Column(name = "source_locator", nullable = false, columnDefinition = "TEXT")
    private String sourceLocator;
    @Column(name = "integrity_digest", length = 256)
    private String integrityDigest;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private PluginDistributionAvailability availability;
    @Enumerated(EnumType.STRING) @Column(name = "compatibility_evidence", nullable = false, length = 32)
    private PluginCompatibilityEvidence compatibilityEvidence;
    @Enumerated(EnumType.STRING) @Column(name = "security_assessment", nullable = false, length = 32)
    private PluginSecurityAssessment securityAssessment;
    @Column(name = "installation_recipe", columnDefinition = "TEXT")
    private String installationRecipe;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "native_name", length = 200)
    private String nativeName;
    @Column(name = "native_version", length = 64)
    private String nativeVersion;
    @Column(name = "inventory_json", columnDefinition = "TEXT")
    private String inventoryJson;
    @Column(name = "manifest_json", columnDefinition = "TEXT")
    private String manifestJson;

    protected PluginDistribution() {}

    public PluginDistribution(Long releaseId, String runtimeKey, String sourceType, String sourceLocator) {
        this.releaseId = releaseId;
        this.runtimeKey = runtimeKey;
        this.sourceType = sourceType;
        this.sourceLocator = sourceLocator;
        this.availability = PluginDistributionAvailability.UNKNOWN;
        this.compatibilityEvidence = PluginCompatibilityEvidence.UNKNOWN;
        this.securityAssessment = PluginSecurityAssessment.NOT_SCANNED;
    }

    @PrePersist
    protected void onCreate() { createdAt = Instant.now(Clock.systemUTC()); }

    public Long getId() { return id; }
    public Long getReleaseId() { return releaseId; }
    public String getRuntimeKey() { return runtimeKey; }
    public String getSourceType() { return sourceType; }
    public String getSourceLocator() { return sourceLocator; }
    public String getIntegrityDigest() { return integrityDigest; }
    public PluginDistributionAvailability getAvailability() { return availability; }
    public PluginCompatibilityEvidence getCompatibilityEvidence() { return compatibilityEvidence; }
    public PluginSecurityAssessment getSecurityAssessment() { return securityAssessment; }
    public String getInstallationRecipe() { return installationRecipe; }
    public Instant getCreatedAt() { return createdAt; }
    public String getNativeName() { return nativeName; }
    public String getNativeVersion() { return nativeVersion; }
    public String getInventoryJson() { return inventoryJson; }
    public String getManifestJson() { return manifestJson; }

    /** Only import adapters call this after validating actual archive bytes. */
    public static PluginDistribution hostedArchive(Long releaseId, String runtime, String objectKey,
            String sha256, String nativeName, String nativeVersion, String inventory, String manifest) {
        PluginDistribution distribution = new PluginDistribution(releaseId, runtime, "HOSTED_ARCHIVE", objectKey);
        distribution.integrityDigest = sha256;
        distribution.nativeName = nativeName;
        distribution.nativeVersion = nativeVersion;
        distribution.inventoryJson = inventory;
        distribution.manifestJson = manifest;
        distribution.compatibilityEvidence = PluginCompatibilityEvidence.MANIFEST_VALIDATED;
        distribution.availability = PluginDistributionAvailability.AVAILABLE;
        return distribution;
    }
}
