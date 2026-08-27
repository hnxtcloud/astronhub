package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.catalog.CapabilityCatalogItem;
import com.iflytek.skillhub.dto.catalog.CapabilityType;
import com.iflytek.skillhub.service.SkillSearchAppService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/** Adapts the unchanged Skill discovery service to the unified catalog read model. */
@Repository
public class SkillCatalogQueryAdapter implements SkillCatalogQuerySource {
    private final SkillSearchAppService skillSearchAppService;

    public SkillCatalogQueryAdapter(SkillSearchAppService skillSearchAppService) {
        this.skillSearchAppService = skillSearchAppService;
    }

    @Override
    public PageResponse<CapabilityCatalogItem> findSkillsForCatalog(
            String keyword, int page, int size, String userId,
            Map<Long, NamespaceRole> namespaceRoles) {
        SkillSearchAppService.SearchResponse response = skillSearchAppService.search(
                keyword, null, "newest", page, size, List.of(), userId,
                namespaceRoles != null ? namespaceRoles : Map.of());
        List<CapabilityCatalogItem> items = response.items().stream()
                .map(skill -> new CapabilityCatalogItem(
                        CapabilityType.SKILL, skill.id(),
                        "skill:@" + skill.namespace() + "/" + skill.slug(),
                        skill.namespace(), skill.slug(), skill.displayName(), skill.summary(), skill.visibility(),
                        skill.status(), skill.headlineVersion() != null ? skill.headlineVersion().version() : null,
                        List.of(), skill.downloadCount() != null ? skill.downloadCount() : 0L, skill.updatedAt()))
                .toList();
        return new PageResponse<>(items, response.total(), response.page(), response.size());
    }
}
