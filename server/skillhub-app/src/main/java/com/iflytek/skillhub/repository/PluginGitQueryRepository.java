package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.config.PluginPublicationProperties;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.plugin.PluginGitNamespaceTargetRepository;
import com.iflytek.skillhub.domain.plugin.PluginGitPublicationIntentRepository;
import com.iflytek.skillhub.domain.plugin.PluginGitTarget;
import com.iflytek.skillhub.domain.plugin.PluginGitTargetRepository;
import com.iflytek.skillhub.dto.plugin.PluginGitDtos.GitIntentView;
import com.iflytek.skillhub.dto.plugin.PluginGitDtos.GitNamespaceView;
import com.iflytek.skillhub.dto.plugin.PluginGitDtos.GitSettingsView;
import com.iflytek.skillhub.dto.plugin.PluginGitDtos.GitTargetView;

import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PluginGitQueryRepository {
    private final PluginGitTargetRepository targets;
    private final PluginGitNamespaceTargetRepository bindings;
    private final PluginGitPublicationIntentRepository intents;
    private final PluginPublicationProperties properties;
    private final NamespaceRepository namespaces;

    public PluginGitQueryRepository(
            PluginGitTargetRepository targets,
            PluginGitNamespaceTargetRepository bindings,
            PluginGitPublicationIntentRepository intents,
            PluginPublicationProperties properties,
            NamespaceRepository namespaces) {
        this.targets = targets;
        this.bindings = bindings;
        this.intents = intents;
        this.properties = properties;
        this.namespaces = namespaces;
    }

    public GitTargetView target(PluginGitTarget value) {
        var namespace = namespaces.findById(value.getNamespaceId()).orElseThrow();
        return new GitTargetView(
                value.getId(),
                value.getNamespaceId(),
                namespace.getSlug(),
                value.getRemote(),
                value.getFetchUrl(),
                value.getBranch(),
                value.getCredentialRef(),
                properties.available(value.getCredentialRef()),
                bindings.findById(value.getNamespaceId())
                        .map(b -> b.getTargetId().equals(value.getId()))
                        .orElse(false),
                value.getCreatedAt());
    }

    public GitSettingsView settings() {
        return new GitSettingsView(
                List.copyOf(properties.getAllowedGitRemotes()),
                properties.getCredentials().keySet().stream().sorted().toList(),
                namespaces
                        .findByStatus(
                                NamespaceStatus.ACTIVE,
                                org.springframework.data.domain.PageRequest.of(
                                        0, 1000, org.springframework.data.domain.Sort.by("slug")))
                        .getContent()
                        .stream()
                        .map(n -> new GitNamespaceView(n.getId(), n.getSlug(), n.getDisplayName()))
                        .toList(),
                targets.findTop100ByOrderByIdDesc().stream().map(this::target).toList());
    }

    public List<GitIntentView> intents(Long taskId) {
        return intents.findByTaskIdOrderByIdDesc(taskId).stream()
                .map(
                        value ->
                                new GitIntentView(
                                        value.getId(),
                                        value.getParentSha(),
                                        value.getTreeSha(),
                                        value.getCommitSha(),
                                        value.getPlanDigest(),
                                        value.getCreatedAt()))
                .toList();
    }
}
