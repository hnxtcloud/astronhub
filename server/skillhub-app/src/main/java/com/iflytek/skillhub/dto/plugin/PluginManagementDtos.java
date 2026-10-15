package com.iflytek.skillhub.dto.plugin;

import com.iflytek.skillhub.domain.plugin.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

/** Named records keep the OpenAPI contract explicit without exposing storage locators. */
public final class PluginManagementDtos {
    private PluginManagementDtos() {}
    public record PluginProjectCreateRequest(@NotNull Long namespaceId, @NotBlank String slug,
            @NotBlank @Size(max = 200) String displayName, @Size(max = 30000) String summary,
            @NotNull PluginVisibility visibility) {}
    public record PluginProjectUpdateRequest(@NotBlank @Size(max = 200) String displayName,
                                             @Size(max = 30000) String summary) {}
    public record PluginReleaseCreateRequest(@NotBlank @Size(max = 64) String version,
                                             @Size(max = 30000) String changelog) {}
    public record PluginDraftUpdateRequest(@Size(max = 30000) String changelog) {}
    public record PluginReviewRequest(@NotBlank @Size(max = 10000) String notes,
                                      String runtimeTestResult, @Size(max = 10000) String testEnvironment) {}
    public record PluginProjectView(Long id, Long namespaceId, String namespace, String slug, String displayName,
            String summary, String ownerId, PluginVisibility visibility, PluginProjectStatus status,
            boolean hidden, Long latestReleaseId, boolean canManage, boolean canReview, boolean canGovern, List<PluginReleaseView> releases) {}
    public record PluginReleaseView(Long id, Long projectId, String version, PluginReleaseStatus status,
            String changelog, Instant createdAt, Instant publishedAt, List<PluginDistributionView> distributions,
            List<PluginReviewView> reviews, PluginPublicationView publication, Instant yankedAt, String yankedBy, String yankReason) {}
    public record PluginDistributionView(Long id, String runtimeKey, String nativeName, String nativeVersion,
            String sha256, PluginCompatibilityEvidence compatibilityEvidence, PluginSecurityAssessment securityAssessment,
            String inventoryJson, String manifestJson) {}
    public record PluginReviewView(String reviewerId, String decision, String notes, String runtimeTestResult,
                                   String testEnvironment, Instant createdAt) {}
    public record PluginPublicationView(Long id, String status, String requiredChannels, String completedChannels,
                                        String lastError, int attemptCount, Instant completedAt) {}
}
