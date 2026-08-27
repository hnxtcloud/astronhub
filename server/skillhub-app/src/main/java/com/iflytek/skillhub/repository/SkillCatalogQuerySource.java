package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.catalog.CapabilityCatalogItem;
import java.util.Map;

/** Provides the existing Skill discovery read model to the unified catalog query. */
public interface SkillCatalogQuerySource {
    PageResponse<CapabilityCatalogItem> findSkillsForCatalog(
            String keyword, int page, int size, String userId,
            Map<Long, NamespaceRole> namespaceRoles);
}
