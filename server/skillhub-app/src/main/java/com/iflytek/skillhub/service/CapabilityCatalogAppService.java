package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.catalog.CapabilityCatalogItem;
import com.iflytek.skillhub.dto.catalog.CapabilityTypeFilter;
import com.iflytek.skillhub.repository.CapabilityCatalogQueryRepository;
import com.iflytek.skillhub.repository.SkillCatalogQuerySource;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class CapabilityCatalogAppService {
    private final CapabilityCatalogQueryRepository catalogQueryRepository;
    private final SkillCatalogQuerySource skillCatalogQuerySource;

    public CapabilityCatalogAppService(CapabilityCatalogQueryRepository catalogQueryRepository,
                                       SkillCatalogQuerySource skillCatalogQuerySource) {
        this.catalogQueryRepository = catalogQueryRepository;
        this.skillCatalogQuerySource = skillCatalogQuerySource;
    }

    public PageResponse<CapabilityCatalogItem> listPlugins(
            String keyword, int page, int size, String userId,
            Map<Long, NamespaceRole> namespaceRoles, Set<String> platformRoles) {
        return catalogQueryRepository.findPlugins(
                keyword, boundedPage(page), boundedSize(size), userId,
                safeNamespaceRoles(namespaceRoles), safeRoles(platformRoles));
    }

    public CapabilityCatalogItem getPlugin(
            String namespace, String slug, String userId,
            Map<Long, NamespaceRole> namespaceRoles, Set<String> platformRoles) {
        return catalogQueryRepository.findPlugin(
                        namespace, slug, userId, safeNamespaceRoles(namespaceRoles), safeRoles(platformRoles))
                .orElseThrow(() -> new DomainNotFoundException("plugin.not_found", namespace, slug));
    }

    public PageResponse<CapabilityCatalogItem> listMcpServers(
            String keyword, int page, int size, String userId,
            Map<Long, NamespaceRole> namespaceRoles, Set<String> platformRoles) {
        return catalogQueryRepository.findMcpServers(
                keyword, boundedPage(page), boundedSize(size), userId,
                safeNamespaceRoles(namespaceRoles), safeRoles(platformRoles));
    }

    public CapabilityCatalogItem getMcpServer(
            String namespace, String slug, String userId,
            Map<Long, NamespaceRole> namespaceRoles, Set<String> platformRoles) {
        return catalogQueryRepository.findMcpServer(
                        namespace, slug, userId, safeNamespaceRoles(namespaceRoles), safeRoles(platformRoles))
                .orElseThrow(() -> new DomainNotFoundException("mcp.not_found", namespace, slug));
    }

    public PageResponse<CapabilityCatalogItem> search(
            String keyword, CapabilityTypeFilter type, int page, int size, String userId,
            Map<Long, NamespaceRole> namespaceRoles, Set<String> platformRoles) {
        int safePage = boundedPage(page);
        int safeSize = boundedSize(size);
        CapabilityTypeFilter normalizedType = type == null ? CapabilityTypeFilter.ALL : type;
        if (normalizedType == CapabilityTypeFilter.PLUGIN) {
            return listPlugins(keyword, safePage, safeSize, userId, namespaceRoles, platformRoles);
        }
        if (normalizedType == CapabilityTypeFilter.MCP) {
            return listMcpServers(keyword, safePage, safeSize, userId, namespaceRoles, platformRoles);
        }
        if (normalizedType == CapabilityTypeFilter.SKILL) {
            return skillPage(keyword, safePage, safeSize, userId, namespaceRoles);
        }
        return catalogQueryRepository.findAll(
                keyword, safePage, safeSize, userId,
                safeNamespaceRoles(namespaceRoles), safeRoles(platformRoles));
    }

    private PageResponse<CapabilityCatalogItem> skillPage(
            String keyword, int page, int size, String userId, Map<Long, NamespaceRole> namespaceRoles) {
        return skillCatalogQuerySource.findSkillsForCatalog(
                keyword, page, size, userId, safeNamespaceRoles(namespaceRoles));
    }

    private int boundedPage(int page) { return Math.min(99, Math.max(0, page)); }
    private int boundedSize(int size) { return Math.max(1, Math.min(100, size)); }
    private Map<Long, NamespaceRole> safeNamespaceRoles(Map<Long, NamespaceRole> roles) {
        return roles == null ? Map.of() : roles;
    }
    private Set<String> safeRoles(Set<String> roles) { return roles == null ? Set.of() : roles; }
}
