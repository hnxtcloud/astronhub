package com.iflytek.skillhub.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.plugin.*;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Reads completed native publication evidence without returning credential references or plans. */
@Repository
@Transactional(readOnly = true)
public class PluginInstallationQueryRepository {
    private final PluginPublicationTaskRepository tasks;
    private final PluginPublicationChannelRepository channels;
    private final ObjectMapper mapper;

    public PluginInstallationQueryRepository(
            PluginPublicationTaskRepository tasks,
            PluginPublicationChannelRepository channels,
            ObjectMapper mapper) {
        this.tasks = tasks;
        this.channels = channels;
        this.mapper = mapper;
    }

    public Optional<NpmSource> npmSource(
            PluginProject project, PluginRelease release, PluginDistribution distribution) {
        if (project.getVisibility() != PluginVisibility.PUBLIC) return Optional.empty();
        return tasks.findByReleaseId(release.getId()).stream()
                .flatMap(task -> channels.findByTaskIdOrderByChannel(task.getId()).stream())
                .filter(
                        channel ->
                                channel.getChannel().equals("NPM")
                                        && channel.getState().equals("SUCCEEDED")
                                        && channel.getReceipt() != null)
                .map(
                        channel -> {
                            try {
                                var receipt = mapper.readTree(channel.getReceipt());
                                if (receipt == null || !receipt.isObject()) return null;
                                String registry = receipt.path("registry").asText();
                                var uri = java.net.URI.create(registry);
                                if (!distribution
                                                .getNativeName()
                                                .equals(receipt.path("name").asText())
                                        || !release.getVersion()
                                                .equals(receipt.path("version").asText())
                                        || !distribution
                                                .getIntegrityDigest()
                                                .equals(receipt.path("sha256").asText())
                                        || !("http".equals(uri.getScheme())
                                                || "https".equals(uri.getScheme()))
                                        || uri.getHost() == null
                                        || uri.getUserInfo() != null
                                        || uri.getQuery() != null
                                        || uri.getFragment() != null
                                        || registry.chars().anyMatch(c -> c < 32 || c == 127))
                                    return null;
                                return new NpmSource(
                                        registry,
                                        distribution.getNativeName(),
                                        release.getVersion());
                            } catch (java.io.IOException | IllegalArgumentException ex) {
                                return null;
                            }
                        })
                .filter(java.util.Objects::nonNull)
                .findFirst();
    }

    public Optional<GitSource> gitSource(
            PluginProject project, PluginRelease release, PluginDistribution distribution) {
        if (project.getVisibility() != PluginVisibility.PUBLIC
                || !java.util.Set.of("claude-code", "codex").contains(distribution.getRuntimeKey()))
            return Optional.empty();
        return tasks.findByReleaseId(release.getId()).stream()
                .flatMap(task -> channels.findByTaskIdOrderByChannel(task.getId()).stream())
                .filter(
                        channel ->
                                channel.getChannel().equals("GIT")
                                        && channel.getState().equals("SUCCEEDED")
                                        && channel.getReceipt() != null)
                .map(
                        channel -> {
                            try {
                                var receipt = mapper.readTree(channel.getReceipt());
                                String remote = receipt.path("remote").asText();
                                var uri = java.net.URI.create(remote);
                                String commit = receipt.path("commit").asText();
                                String market = receipt.path("marketplace").asText();
                                if (!commit.matches("[a-f0-9]{40}")
                                        || !market.matches("[a-z0-9][a-z0-9-]{0,99}")
                                        || !remote.matches("[A-Za-z0-9:/._~+@-]+")
                                        || uri.getUserInfo() != null
                                        || uri.getQuery() != null
                                        || uri.getFragment() != null
                                        || !("https".equals(uri.getScheme())
                                                        && uri.getHost() != null
                                                || "file".equals(uri.getScheme())
                                                        && uri.getHost() == null)) return null;
                                for (var artifact : receipt.path("artifacts")) {
                                    if (distribution
                                                    .getRuntimeKey()
                                                    .equals(artifact.path("runtime").asText())
                                            && distribution
                                                    .getNativeName()
                                                    .equals(artifact.path("name").asText())
                                            && distribution
                                                    .getNativeVersion()
                                                    .equals(artifact.path("version").asText())
                                            && distribution
                                                    .getIntegrityDigest()
                                                    .equals(artifact.path("sha256").asText()))
                                        return new GitSource(remote, commit, market);
                                }
                                return null;
                            } catch (java.io.IOException | IllegalArgumentException ex) {
                                return null;
                            }
                        })
                .filter(java.util.Objects::nonNull)
                .findFirst();
    }

    public record GitSource(String remote, String commit, String marketplace) {}

    public record NpmSource(String registry, String name, String version) {}
}
