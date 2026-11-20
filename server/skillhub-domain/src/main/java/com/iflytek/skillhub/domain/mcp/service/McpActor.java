package com.iflytek.skillhub.domain.mcp.service;

import com.iflytek.skillhub.domain.mcp.McpServerEntry;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import java.util.Map;
import java.util.Set;

/** Trusted authentication context supplied by the server, never request JSON. */
public record McpActor(String userId, Map<Long, NamespaceRole> namespaceRoles, Set<String> platformRoles) {
    public McpActor {
        namespaceRoles = namespaceRoles == null ? Map.of() : Map.copyOf(namespaceRoles);
        platformRoles = platformRoles == null ? Set.of() : Set.copyOf(platformRoles);
    }
    public boolean superAdmin() { return platformRoles.contains("SUPER_ADMIN"); }
    /** MCP_ADMIN reviews global submissions; namespace ADMIN/OWNER review their own team space. */
    public boolean reviewer() { return superAdmin() || platformRoles.contains("MCP_ADMIN"); }
    public boolean namespaceAdmin(Long namespaceId) {
        return namespaceRoles.get(namespaceId) == NamespaceRole.OWNER
                || namespaceRoles.get(namespaceId) == NamespaceRole.ADMIN;
    }
    public boolean namespaceMember(Long namespaceId) { return namespaceRoles.containsKey(namespaceId); }
    public boolean canMaintain(McpServerEntry entry) {
        return userId != null
                && (superAdmin()
                        || namespaceAdmin(entry.getNamespaceId())
                        || (userId.equals(entry.getOwnerId()) && namespaceMember(entry.getNamespaceId())));
    }
    public boolean canReview(McpServerEntry entry) {
        return userId != null && (reviewer() || namespaceAdmin(entry.getNamespaceId()));
    }
}
