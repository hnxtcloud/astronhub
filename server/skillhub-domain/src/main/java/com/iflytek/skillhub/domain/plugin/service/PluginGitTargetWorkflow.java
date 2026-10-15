package com.iflytek.skillhub.domain.plugin.service;

import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.plugin.PluginGitDestinationClaim;
import com.iflytek.skillhub.domain.plugin.PluginGitDestinationClaimRepository;
import com.iflytek.skillhub.domain.plugin.PluginGitNamespaceTarget;
import com.iflytek.skillhub.domain.plugin.PluginGitNamespaceTargetRepository;
import com.iflytek.skillhub.domain.plugin.PluginGitTarget;
import com.iflytek.skillhub.domain.plugin.PluginGitTargetRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PluginGitTargetWorkflow {
    private final PluginGitTargetRepository targets;
    private final PluginGitNamespaceTargetRepository bindings;
    private final PluginGitDestinationClaimRepository claims;
    private final NamespaceRepository namespaces;

    public PluginGitTargetWorkflow(
            PluginGitTargetRepository targets,
            PluginGitNamespaceTargetRepository bindings,
            PluginGitDestinationClaimRepository claims,
            NamespaceRepository namespaces) {
        this.targets = targets;
        this.bindings = bindings;
        this.claims = claims;
        this.namespaces = namespaces;
    }

    @Transactional
    public PluginGitTarget configure(
            Long namespaceId,
            String remote,
            String fetch,
            String branch,
            String ref,
            String key,
            PluginActor actor) {
        if (!actor.reviewer()) throw new DomainForbiddenException("error.plugin.permission");
        namespaces
                .findById(namespaceId)
                .orElseThrow(() -> new DomainNotFoundException("error.plugin.namespaceNotFound"));
        var old = claims.findById(key);
        if (old.isPresent() && !old.get().getNamespaceId().equals(namespaceId))
            throw new DomainBadRequestException("error.plugin.gitNamespaceConflict");
        if (old.isEmpty()) claims.save(new PluginGitDestinationClaim(key, namespaceId));
        var saved =
                targets.save(
                        new PluginGitTarget(
                                namespaceId, remote, fetch, branch, ref, actor.userId()));
        bindings.save(new PluginGitNamespaceTarget(namespaceId, saved.getId()));
        return saved;
    }
}
