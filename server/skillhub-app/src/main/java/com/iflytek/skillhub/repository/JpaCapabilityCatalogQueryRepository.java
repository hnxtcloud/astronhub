package com.iflytek.skillhub.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.iflytek.skillhub.domain.software.SoftwareApplication;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.catalog.CapabilityCatalogItem;
import com.iflytek.skillhub.dto.catalog.CapabilityType;
import com.iflytek.skillhub.dto.catalog.McpConnectionProfilesResponse;
import com.iflytek.skillhub.dto.catalog.McpConnectionProfilesResponse.McpConnectionProfileResponse;
import com.iflytek.skillhub.dto.catalog.McpConnectionProfilesResponse.McpVariableResponse;
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
import org.springframework.transaction.annotation.Transactional;

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
            + " or (p.ownerId = :userId and p.namespaceId in :namespaceIds)"
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
    private final ObjectMapper objectMapper;

    public JpaCapabilityCatalogQueryRepository(
            EntityManager entityManager,
            PluginDistributionRepository pluginDistributionRepository,
            McpConnectionProfileRepository mcpConnectionProfileRepository,
            SkillCatalogQuerySource skillCatalogQuerySource,
            ObjectMapper objectMapper) {
        this.entityManager = entityManager;
        this.pluginDistributionRepository = pluginDistributionRepository;
        this.mcpConnectionProfileRepository = mcpConnectionProfileRepository;
        this.skillCatalogQuerySource = skillCatalogQuerySource;
        this.objectMapper = objectMapper;
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
        return query.getResultList().stream().findFirst()
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
        return findMcpRow(namespace, slug, userId, namespaceRoles, platformRoles)
                .map(row -> toMcp((McpServerEntry) row[0], (Namespace) row[1], (McpEntryRevision) row[2]));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<McpConnectionProfilesResponse> findMcpConnectionProfiles(
            String namespace, String slug, String userId,
            Map<Long, NamespaceRole> namespaceRoles, Set<String> platformRoles) {
        return findMcpRow(namespace, slug, userId, namespaceRoles, platformRoles).map(row -> {
            McpEntryRevision revision = (McpEntryRevision) row[2];
            return new McpConnectionProfilesResponse(
                    revision.getRevisionNumber(), revision.getDescription(), revision.getPublishedAt(),
                    mcpConnectionProfileRepository.findByRevisionIdOrderByProfileKey(revision.getId()).stream()
                            .map(this::toMcpProfile).toList());
        });
    }

    private Optional<Object[]> findMcpRow(
            String namespace, String slug, String userId,
            Map<Long, NamespaceRole> namespaceRoles, Set<String> platformRoles) {
        TypedQuery<Object[]> query = entityManager.createQuery(
                "select e, n, r" + MCP_FROM + " and n.slug = :namespace and e.slug = :slug", Object[].class);
        bind(query, "", userId, namespaceRoles, isMcpAdmin(platformRoles));
        query.setParameter("namespace", namespace).setParameter("slug", slug).setMaxResults(1);
        return query.getResultList().stream().findFirst();
    }

    private McpConnectionProfileResponse toMcpProfile(McpConnectionProfile profile) {
        List<String> arguments = new ArrayList<>();
        List<McpVariableResponse> variables = new ArrayList<>();
        boolean valid = true;
        try {
            for (JsonNode argument : profileArray(profile.getArgumentsJson())) {
                if (!argument.isTextual()) throw new IllegalArgumentException("Invalid argument");
                arguments.add(argument.asText());
            }
            for (JsonNode variable : profileArray(profile.getVariablesJson())) {
                String name = variable.path("name").asText("");
                if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("Invalid variable name");
                boolean secret = variable.path("secret").asBoolean(false);
                JsonNode defaultValue = variable.has("defaultValue") ? variable.get("defaultValue") : variable.get("default");
                variables.add(new McpVariableResponse(name, variable.path("description").asText(null),
                        variable.path("required").asBoolean(false), secret,
                        secret || defaultValue == null || defaultValue.isNull() ? null : defaultValue.asText(),
                        variable.path("validationHint").asText(null)));
            }
        } catch (java.io.IOException | IllegalArgumentException exception) {
            // Malformed stored templates remain visible, but cannot be copied as a working configuration.
            valid = false;
            arguments = List.of();
            variables = List.of();
        }
        valid = valid && switch (profile.getTransportType()) {
            case STDIO -> profile.getCommandTemplate() != null && !profile.getCommandTemplate().isBlank();
            case STREAMABLE_HTTP, LEGACY_HTTP_SSE -> profile.getEndpointTemplate() != null && !profile.getEndpointTemplate().isBlank();
            case CUSTOM -> false;
        };
        return new McpConnectionProfileResponse(profile.getProfileKey(), profile.getDisplayName(),
                profile.getTransportType(), profile.getEndpointTemplate(), profile.getCommandTemplate(),
                arguments, variables, valid);
    }

    private JsonNode profileArray(String value) throws java.io.IOException {
        if (value == null || value.isBlank()) return objectMapper.createArrayNode();
        JsonNode parsed = objectMapper.readTree(value);
        if (parsed == null || !parsed.isArray()) throw new IllegalArgumentException("Expected template array");
        return parsed;
    }

    @Override
    public PageResponse<CapabilityCatalogItem> findSoftware(String keyword, int page, int size, String userId) {
        // The software portal is session-only; unified anonymous search must preserve this boundary.
        if (userId == null || userId.isBlank()) return new PageResponse<>(List.of(), 0, page, size);
        String query = keyword == null ? "" : keyword.trim().toLowerCase(java.util.Locale.ROOT);
        String from = " from SoftwareApplication a where a.status = com.iflytek.skillhub.domain.software.SoftwareApplicationStatus.PUBLISHED"
                + " and (:keyword = '' or lower(a.name) like :pattern or lower(a.slug) like :pattern or lower(coalesce(a.description, '')) like :pattern)";
        var rows = entityManager.createQuery("select a" + from + " order by a.updatedAt desc, a.id desc", SoftwareApplication.class)
                .setParameter("keyword", query).setParameter("pattern", "%" + query + "%")
                .setFirstResult(page * size).setMaxResults(size).getResultList();
        long total = entityManager.createQuery("select count(a)" + from, Long.class)
                .setParameter("keyword", query).setParameter("pattern", "%" + query + "%").getSingleResult();
        var items = rows.stream().map(app -> new CapabilityCatalogItem(CapabilityType.SOFTWARE, app.getId(),
                "software:" + app.getSlug(), null, app.getSlug(), app.getName(), app.getDescription(),
                "AUTHENTICATED", app.getStatus().name(), null, List.of(), app.getDownloadCount(), app.getUpdatedAt())).toList();
        return new PageResponse<>(items, total, page, size);
    }

    @Override
    public PageResponse<CapabilityCatalogItem> findAll(
            String keyword, int page, int size, String userId,
            Map<Long, NamespaceRole> namespaceRoles, Set<String> platformRoles) {
        return findAll(keyword, page, size, userId, namespaceRoles, platformRoles, true);
    }

    @Override
    public PageResponse<CapabilityCatalogItem> findAll(
            String keyword, int page, int size, String userId,
            Map<Long, NamespaceRole> namespaceRoles, Set<String> platformRoles, boolean includeSoftware) {
        int candidateSize = (page + 1) * size;
        PageResponse<CapabilityCatalogItem> skills = skillCatalogQuerySource.findSkillsForCatalog(
                keyword, 0, candidateSize, userId, namespaceRoles);
        PageResponse<CapabilityCatalogItem> plugins = findPlugins(
                keyword, 0, candidateSize, userId, namespaceRoles, platformRoles);
        PageResponse<CapabilityCatalogItem> mcpServers = findMcpServers(
                keyword, 0, candidateSize, userId, namespaceRoles, platformRoles);
        PageResponse<CapabilityCatalogItem> software = findSoftware(keyword, 0, candidateSize, includeSoftware ? userId : null);

        List<CapabilityCatalogItem> merged = new ArrayList<>();
        merged.addAll(skills.items());
        merged.addAll(plugins.items());
        merged.addAll(mcpServers.items());
        merged.addAll(software.items());
        merged.sort(Comparator.comparing(
                CapabilityCatalogItem::updatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(CapabilityCatalogItem::coordinate));
        int from = Math.min(page * size, merged.size());
        int to = Math.min(from + size, merged.size());
        return new PageResponse<>(merged.subList(from, to),
                skills.total() + plugins.total() + mcpServers.total() + software.total(), page, size);
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
