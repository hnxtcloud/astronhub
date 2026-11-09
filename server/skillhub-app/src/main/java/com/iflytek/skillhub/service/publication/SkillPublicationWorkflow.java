package com.iflytek.skillhub.service.publication;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iflytek.skillhub.controller.support.SkillPackageArchiveExtractor;
import com.iflytek.skillhub.domain.plugin.service.PluginActor;
import com.iflytek.skillhub.domain.publication.PublicationTask;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.dto.publication.PublicationDraft;
import com.iflytek.skillhub.service.AuditRequestContext;
import org.springframework.stereotype.Service;

@Service
public class SkillPublicationWorkflow implements PublicationWorkflow {
    private final PublicationMaterialService materials;
    private final SkillPackageArchiveExtractor extractor;
    private final SkillPublishService publisher;
    private final com.iflytek.skillhub.domain.skill.SkillRepository skills;
    public SkillPublicationWorkflow(PublicationMaterialService materials, SkillPackageArchiveExtractor extractor,
            SkillPublishService publisher, com.iflytek.skillhub.domain.skill.SkillRepository skills) {
        this.materials = materials; this.extractor = extractor; this.publisher = publisher; this.skills = skills;
    }
    public String type() { return "SKILL"; }
    public String advance(PublicationTask task, PublicationDraft draft, ObjectNode progress,
            PluginActor actor, AuditRequestContext audit) throws java.io.IOException {
        var extracted = extractor.extractWithWarnings(materials.stored(task.getMaterialKey(), task.getFileName(), task.getByteSize()));
        var skillMd = extracted.entries().stream().filter(e -> e.path().equals("SKILL.md")).findFirst()
                .orElseThrow(() -> new DomainBadRequestException("error.publication.material"));
        var metadata = new com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser().parse(
                new String(skillMd.content(), java.nio.charset.StandardCharsets.UTF_8));
        if (draft.namespace() == null || draft.visibility() == null) throw new DomainBadRequestException("error.publication.required");
        if (draft.slug() != null && !draft.slug().equals(com.iflytek.skillhub.domain.namespace.SlugValidator.slugify(metadata.name()))) {
            throw new DomainBadRequestException("error.publication.target");
        }
        if (draft.version() != null && !draft.version().equals(metadata.version())) throw new DomainBadRequestException("error.publication.target");
        String resolvedSlug = com.iflytek.skillhub.domain.namespace.SlugValidator.slugify(metadata.name());
        var existing = skills.findByNamespaceSlugAndSlug(draft.namespace(), resolvedSlug).stream()
                .filter(skill -> skill.getOwnerId().equals(actor.userId())).findFirst();
        if (existing.isPresent() && !java.util.Objects.equals(existing.get().getId(), draft.targetSkillId())) {
            throw new DomainBadRequestException("error.publication.targetConfirmation");
        }
        if (existing.isEmpty() && draft.targetSkillId() != null) throw new DomainBadRequestException("error.publication.target");
        if (!draft.confirmWarnings() && !extracted.warnings().isEmpty()) {
            throw new DomainBadRequestException("error.skill.publish.precheck.confirmRequired", String.join("\n", extracted.warnings()));
        }
        SkillVisibility visibility;
        try { visibility = SkillVisibility.valueOf(draft.visibility()); }
        catch (IllegalArgumentException invalid) { throw new DomainBadRequestException("error.publication.required"); }
        var result = publisher.publishFromEntries(draft.namespace(), extracted.entries(), actor.userId(),
                visibility, actor.platformRoles(), draft.confirmWarnings());
        var receipt = progress.putArray("results").addObject();
        receipt.put("candidateId", "skill").put("type", "SKILL").put("namespace", draft.namespace())
                .put("slug", result.slug()).put("version", result.version().getVersion())
                .put("resourceId", result.skillId()).put("releaseId", result.version().getId())
                .put("status", result.version().getStatus().name());
        progress.put("phase", "SUBMITTED");
        return "COMPLETE";
    }
}
