package com.iflytek.skillhub.domain.plugin.service;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import java.util.Map;
import java.util.Set;

/** Trusted authentication context supplied by the server, never request JSON. */
public record PluginActor(String userId, Map<Long, NamespaceRole> namespaceRoles, Set<String> platformRoles) {
    public PluginActor {
        namespaceRoles = namespaceRoles == null ? Map.of() : Map.copyOf(namespaceRoles);
        platformRoles = platformRoles == null ? Set.of() : Set.copyOf(platformRoles);
    }
    public boolean superAdmin() { return platformRoles.contains("SUPER_ADMIN"); }
    public boolean reviewer() { return superAdmin() || platformRoles.contains("PLUGIN_ADMIN"); }
    public boolean namespaceAdmin(Long namespaceId) {
        return namespaceRoles.get(namespaceId) == NamespaceRole.OWNER
                || namespaceRoles.get(namespaceId) == NamespaceRole.ADMIN;
    }
}
