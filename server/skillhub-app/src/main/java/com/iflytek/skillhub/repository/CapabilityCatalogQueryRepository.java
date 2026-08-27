package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.catalog.CapabilityCatalogItem;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface CapabilityCatalogQueryRepository {
    PageResponse<CapabilityCatalogItem> findPlugins(
            String keyword, int page, int size, String userId,
            Map<Long, NamespaceRole> namespaceRoles, Set<String> platformRoles);

    Optional<CapabilityCatalogItem> findPlugin(
            String namespace, String slug, String userId,
            Map<Long, NamespaceRole> namespaceRoles, Set<String> platformRoles);

    PageResponse<CapabilityCatalogItem> findMcpServers(
            String keyword, int page, int size, String userId,
            Map<Long, NamespaceRole> namespaceRoles, Set<String> platformRoles);

    Optional<CapabilityCatalogItem> findMcpServer(
            String namespace, String slug, String userId,
            Map<Long, NamespaceRole> namespaceRoles, Set<String> platformRoles);

    PageResponse<CapabilityCatalogItem> findAll(
            String keyword, int page, int size, String userId,
            Map<Long, NamespaceRole> namespaceRoles, Set<String> platformRoles);
}
