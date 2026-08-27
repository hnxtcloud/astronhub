package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.mcp.McpConnectionProfile;
import com.iflytek.skillhub.domain.mcp.McpConnectionProfileRepository;
import com.iflytek.skillhub.domain.mcp.McpEntryRevision;
import com.iflytek.skillhub.domain.mcp.McpServerEntry;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.plugin.PluginDistribution;
import com.iflytek.skillhub.domain.plugin.PluginDistributionRepository;
import com.iflytek.skillhub.domain.plugin.PluginProject;
import com.iflytek.skillhub.domain.plugin.PluginRelease;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.catalog.CapabilityCatalogItem;
import com.iflytek.skillhub.dto.catalog.CapabilityType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/**
 * Builds catalog read models across capability, namespace, and published-version tables.
 * EntityManager is used here because these presentation queries join aggregates owned by
 * independent domains; putting the join in a domain repository would invert the module boundary.
 */
@Repository
public class JpaCapabilityCatalogQueryRepository implements CapabilityCatalogQueryRepository {
    private static final String PLUGIN_FROM = " from PluginProject p, Namespace n, PluginRelease r"
            + " where n.id = p.namespaceId"
            + " and r.id = p.latestReleaseId"
            + " and r.status = com.iflytek.skillhub.domain.plugin.PluginReleaseStatus.PUBLISHED"
            + " and p.status = com.iflytek.skillhub.domain.plugin.PluginProjectStatus.ACTIVE"
            + " and p.hidden = false"
            + " and p.latestReleaseId is not null"
            + " and (:platformAdmin = true"
            + " or p.visibility = com.iflytek.skillhub.domain.plugin.PluginVisibility.PUBLIC"
            + " or p.ownerId = :userId"
            + " or (p.visibility = com.iflytek.skillhub.domain.plugin.PluginVisibility.PRIVATE"
            + " and p.namespaceId in :adminNamespaceIds)"
            + " or (p.visibility = com.iflytek.skillhub.domain.plugin.PluginVisibility.NAMESPACE_ONLY"
            + " and p.namespaceId in :namespaceIds))"
            + " and (:keyword = '' or lower(p.displayName) like :keywordLike"
            + " or lower(p.slug) like :keywordLike or lower(coalesce(p.summary, '')) like :keywordLike)";
    private static final String MCP_FROM = " from McpServerEntry e, Namespace n, McpEntryRevision r"
            + " where n.id = e.namespaceId"
            + " and r.id = e.latestRevisionId"
            + " and r.status = com.iflytek.skillhub.domain.mcp.McpRevisionStatus.PUBLISHED"
            + " and e.status = com.iflytek.skillhub.domain.mcp.McpEntryStatus.ACTIVE"
            + " and e.hidden = false"
            + " and e.latestRevisionId is not null"
            + " and (:platformAdmin = true"
            + " or e.visibility = com.iflytek.skillhub.domain.mcp.McpVisibility.PUBLIC"
            + " or e.ownerId = :userId"
            + " or (e.visibility = com.iflytek.skillhub.domain.mcp.McpVisibility.PRIVATE"
            + " and e.namespaceId in :adminNamespaceIds)"
            + " or (e.visibility = com.iflytek.skillhub.domain.mcp.McpVisibility.NAMESPACE_ONLY"
            + " and e.namespaceId in :namespaceIds))"
            + " and (:keyword = '' or lower(e.displayName) like :keywordLike"
            + " or lower(e.slug) like :keywordLike or lower(coalesce(e.summary, '')) like :keywordLike)";

    private final EntityManager entityManager;
    private final PluginDistributionRepository pluginDistributionRepository;
    private final McpConnectionProfileRepository mcpConnectionProfileRepository;
    private final SkillCatalogQuerySource skillCatalogQuerySource;

    public JpaCapabilityCatalogQueryRepository(
            EntityManager entityManager,
            PluginDistributionRepository pluginDistributionRepository,
            McpConnectionProfileRepository mcpConnectionProfileRepository,
            SkillCatalogQuerySource skillCatalogQuerySource) {
        this.entityManager = entityManager;
        this.pluginDistributionRepository = pluginDistributionRepository;
        this.mcpConnectionProfileRepository = mcpConnectionProfileRepository;
        this.skillCatalogQuerySource = skillCatalogQuerySource;
    }

    @Override
    public PageResponse<CapabilityCatalogItem> findPlugins(
            String keyword, int page, int size, String userId,
            Map<Long, NamespaceRole> namespaceRoles, Set<String> platformRoles) {
        TypedQuery<Object[]> query = entityManager.createQuery(
                "select p, n, r" + PLUGIN_FROM + " order by p.updatedAt desc", Object[].class);
        bind(query, keyword, userId, namespaceRoles, isPluginAdmin(platformRoles));
        query.setFirstResult(page * size).setMaxResults(size);
        List<CapabilityCatalogItem> items = query.getResultList().stream()
                .map(row -> toPlugin((PluginProject) row[0], (Namespace) row[1], (PluginRelease) row[2]))
                .toList();
        TypedQuery<Long> count = entityManager.createQuery("select count(p)" + PLUGIN_FROM, Long.class);
        bind(count, keyword, userId, namespaceRoles, isPluginAdmin(platformRoles));
        return new PageResponse<>(items, count.getSingleResult(), page, size);
    }

    @Override
    public Optional<CapabilityCatalogItem> findPlugin(
            String namespace, String slug, String userId,
            Map<Long, NamespaceRole> namespaceRoles, Set<String> platformRoles) {
        TypedQuery<Object[]> query = entityManager.createQuery(
                "select p, n, r" + PLUGIN_FROM + " and n.slug = :namespace and p.slug = :slug", Object[].class);
        bind(query, "", userId, namespaceRoles, isPluginAdmin(platformRoles));
        query.setParameter("namespace", namespace).setParameter("slug", slug).setMaxResults(1);
        return query.getResultStream().findFirst()
                .map(row -> toPlugin((PluginProject) row[0], (Namespace) row[1], (PluginRelease) row[2]));
    }

    @Override
    public PageResponse<CapabilityCatalogItem> findMcpServers(
            String keyword, int page, int size, String userId,
            Map<Long, NamespaceRole> namespaceRoles, Set<String> platformRoles) {
        TypedQuery<Object[]> query = entityManager.createQuery(
                "select e, n, r" + MCP_FROM + " order by e.updatedAt desc", Object[].class);
        bind(query, keyword, userId, namespaceRoles, isMcpAdmin(platformRoles));
        query.setFirstResult(page * size).setMaxResults(size);
        List<CapabilityCatalogItem> items = query.getResultList().stream()
                .map(row -> toMcp((McpServerEntry) row[0], (Namespace) row[1], (McpEntryRevision) row[2]))
                .toList();
        TypedQuery<Long> count = entityManager.createQuery("select count(e)" + MCP_FROM, Long.class);
        bind(count, keyword, userId, namespaceRoles, isMcpAdmin(platformRoles));
        return new PageResponse<>(items, count.getSingleResult(), page, size);
    }

    @Override
    public Optional<CapabilityCatalogItem> findMcpServer(
            String namespace, String slug, String userId,
            Map<Long, NamespaceRole> namespaceRoles, Set<String> platformRoles) {
        TypedQuery<Object[]> query = entityManager.createQuery(
                "select e, n, r" + MCP_FROM + " and n.slug = :namespace and e.slug = :slug", Object[].class);
        bind(query, "", userId, namespaceRoles, isMcpAdmin(platformRoles));
        query.setParameter("namespace", namespace).setParameter("slug", slug).setMaxResults(1);
        return query.getResultStream().findFirst()
                .map(row -> toMcp((McpServerEntry) row[0], (Namespace) row[1], (McpEntryRevision) row[2]));
    }

    @Override
    public PageResponse<CapabilityCatalogItem> findAll(
            String keyword, int page, int size, String userId,
            Map<Long, NamespaceRole> namespaceRoles, Set<String> platformRoles) {
        int candidateSize = (page + 1) * size;
        PageResponse<CapabilityCatalogItem> skills = skillCatalogQuerySource.findSkillsForCatalog(
                keyword, 0, candidateSize, userId, namespaceRoles);
        PageResponse<CapabilityCatalogItem> plugins = findPlugins(
                keyword, 0, candidateSize, userId, namespaceRoles, platformRoles);
        PageResponse<CapabilityCatalogItem> mcpServers = findMcpServers(
                keyword, 0, candidateSize, userId, namespaceRoles, platformRoles);

        List<CapabilityCatalogItem> merged = new ArrayList<>();
        merged.addAll(skills.items());
        merged.addAll(plugins.items());
        merged.addAll(mcpServers.items());
        merged.sort(Comparator.comparing(
                CapabilityCatalogItem::updatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        int from = Math.min(page * size, merged.size());
        int to = Math.min(from + size, merged.size());
        return new PageResponse<>(merged.subList(from, to),
                skills.total() + plugins.total() + mcpServers.total(), page, size);
    }

    private void bind(TypedQuery<?> query, String keyword, String userId,
                      Map<Long, NamespaceRole> namespaceRoles, boolean platformAdmin) {
        String normalized = keyword == null ? "" : keyword.trim().toLowerCase(java.util.Locale.ROOT);
        query.setParameter("platformAdmin", platformAdmin);
        query.setParameter("userId", userId == null ? "" : userId);
        query.setParameter("namespaceIds", namespaceRoles == null || namespaceRoles.isEmpty()
                ? Set.of(-1L) : namespaceRoles.keySet());
        Set<Long> adminNamespaceIds = namespaceRoles == null ? Set.of() : namespaceRoles.entrySet().stream()
                .filter(entry -> entry.getValue() == NamespaceRole.OWNER || entry.getValue() == NamespaceRole.ADMIN)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        query.setParameter("adminNamespaceIds", adminNamespaceIds.isEmpty() ? Set.of(-1L) : adminNamespaceIds);
        query.setParameter("keyword", normalized);
        query.setParameter("keywordLike", "%" + normalized + "%");
    }

    private boolean isPluginAdmin(Set<String> roles) {
        return roles != null && (roles.contains("SUPER_ADMIN") || roles.contains("PLUGIN_ADMIN"));
    }

    private boolean isMcpAdmin(Set<String> roles) {
        return roles != null && (roles.contains("SUPER_ADMIN") || roles.contains("MCP_ADMIN"));
    }

    private CapabilityCatalogItem toPlugin(PluginProject project, Namespace namespace, PluginRelease release) {
        List<String> runtimes = pluginDistributionRepository.findByReleaseIdOrderByRuntimeKey(release.getId()).stream()
                .map(PluginDistribution::getRuntimeKey).toList();
        return new CapabilityCatalogItem(
                CapabilityType.PLUGIN, project.getId(), "plugin:@" + namespace.getSlug() + "/" + project.getSlug(),
                namespace.getSlug(), project.getSlug(), project.getDisplayName(), project.getSummary(),
                project.getVisibility().name(), project.getStatus().name(),
                release.getVersion(), runtimes, project.getDownloadCount(), project.getUpdatedAt());
    }

    private CapabilityCatalogItem toMcp(McpServerEntry entry, Namespace namespace, McpEntryRevision revision) {
        List<String> transports = mcpConnectionProfileRepository.findByRevisionIdOrderByProfileKey(revision.getId()).stream()
                .map(McpConnectionProfile::getTransportType).map(Enum::name).distinct().toList();
        return new CapabilityCatalogItem(
                CapabilityType.MCP, entry.getId(), "mcp:@" + namespace.getSlug() + "/" + entry.getSlug(),
                namespace.getSlug(), entry.getSlug(), entry.getDisplayName(), entry.getSummary(),
                entry.getVisibility().name(), entry.getStatus().name(),
                Integer.toString(revision.getRevisionNumber()),
                transports, entry.getProfileCopyCount(), entry.getUpdatedAt());
    }
}
