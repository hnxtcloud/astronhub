package com.iflytek.skillhub.domain.plugin.service;

import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.plugin.*;
import com.iflytek.skillhub.domain.shared.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PluginLifecycleService {
    private final NamespaceRepository namespaces;
    private final PluginProjectRepository projects;

    public PluginLifecycleService(NamespaceRepository namespaces, PluginProjectRepository projects) {
        this.namespaces = namespaces;
        this.projects = projects;
    }

    @Transactional
    public PluginProject createProject(Long namespaceId, String slug, String displayName, String summary,
                                       PluginVisibility visibility, PluginActor actor) {
        Namespace namespace = namespaces.findById(namespaceId)
                .orElseThrow(() -> new DomainNotFoundException("error.plugin.namespaceNotFound"));
        if (actor.userId() == null || (!actor.superAdmin() && !actor.namespaceRoles().containsKey(namespaceId))) {
            throw new DomainForbiddenException("error.plugin.permission");
        }
        if (namespace.getStatus() != NamespaceStatus.ACTIVE) {
            throw new DomainBadRequestException("error.plugin.namespaceInactive");
        }
        SlugValidator.validate(slug);
        if (displayName == null || displayName.isBlank() || displayName.length() > 200 || visibility == null) {
            throw new DomainBadRequestException("error.plugin.metadata");
        }
        if (projects.findByNamespaceIdAndSlug(namespaceId, slug).isPresent()) {
            throw new DomainBadRequestException("error.plugin.projectExists");
        }
        PluginProject project = new PluginProject(namespaceId, slug, displayName.trim(), actor.userId(), visibility);
        project.updateProfile(displayName, summary, actor.userId());
        project.setCreatedBy(actor.userId());
        project.setUpdatedBy(actor.userId());
        return projects.save(project);
    }
}
