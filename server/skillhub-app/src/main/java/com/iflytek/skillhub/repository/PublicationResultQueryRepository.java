package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.plugin.PluginReleaseRepository;
import com.iflytek.skillhub.domain.software.SoftwarePackageRevisionRepository;
import com.iflytek.skillhub.dto.publication.PublicationProgressView.PublicationResultView;
import org.springframework.stereotype.Repository;

/** Reads current content state without changing the immutable receipt of the submitted operation. */
@Repository
public class PublicationResultQueryRepository {
    private final SkillVersionRepository skills;
    private final PluginReleaseRepository plugins;
    private final SoftwarePackageRevisionRepository software;
    public PublicationResultQueryRepository(SkillVersionRepository skills, PluginReleaseRepository plugins,
            SoftwarePackageRevisionRepository software) { this.skills = skills; this.plugins = plugins; this.software = software; }
    public PublicationResultView current(PublicationResultView result) {
        if (result.releaseId() == null || "FAILED".equals(result.status())) return result;
        String state = switch (result.type()) {
            case "SKILL" -> skills.findById(result.releaseId()).map(v -> v.getStatus().name()).orElse("UNAVAILABLE");
            case "PLUGIN" -> plugins.findById(result.releaseId()).map(v -> v.getStatus().name()).orElse("UNAVAILABLE");
            case "SOFTWARE" -> result.packageRevisionId() == null ? result.status() : software.findById(result.packageRevisionId()).map(v -> v.getStatus().name()).orElse("UNAVAILABLE");
            default -> result.status();
        };
        return new PublicationResultView(result.candidateId(), result.type(), state, result.namespace(), result.slug(),
                result.version(), result.resourceId(), result.releaseId(), result.error(), result.packageRevisionId());
    }
}
