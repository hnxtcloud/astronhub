package com.iflytek.skillhub.service.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Opt-in publisher output fixture for isolated actual native-client acceptance. */
@EnabledIfSystemProperty(named = "astronhub.git.native-fixture", matches = "true")
class GitNativeFixtureIntegrationTest {
    @Test
    void exportsActualPublishedCommitForNativeClients() throws Exception {
        Path base = Path.of(System.getProperty("astronhub.git.fixture-directory"));
        Files.createDirectories(base);
        Path root = Files.createTempDirectory(base, "run-");
        Path remote = root.resolve("market.git");
        assertThat(
                        new ProcessBuilder("git", "init", "--bare", remote.toString())
                                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                                .redirectError(ProcessBuilder.Redirect.DISCARD)
                                .start()
                                .waitFor())
                .isZero();
        Path claude = root.resolve("claude.json"),
                codex = root.resolve("codex.json"),
                command = root.resolve("command.md"),
                skill = root.resolve("SKILL.md");
        Files.writeString(
                claude,
                "{\"name\":\"native-fixture\",\"version\":\"1.0.0\",\"description\":\"Synthetic"
                        + " offline fixture\",\"commands\":[\"./commands/greeting.md\"]}");
        Files.writeString(
                codex,
                "{\"$schema\":\"https://agent-plugins.org/schemas/1.0.0/plugin.schema.json\",\"name\":\"native-fixture\",\"version\":\"1.0.0\",\"description\":\"Synthetic"
                    + " offline fixture\"}");
        Files.writeString(
                command, "---\ndescription: Synthetic greeting\n---\nReply with hello.\n");
        Files.writeString(
                skill,
                "---\n"
                        + "name: native-fixture-greeting\n"
                        + "description: Synthetic greeting\n"
                        + "---\n"
                        + "Reply with hello.\n");
        var target =
                new GitMarketplacePublisher.Target(
                        1L,
                        remote.toUri().toString(),
                        remote.toUri().toString(),
                        "main",
                        "fixture");
        var artifacts =
                List.of(
                        new GitMarketplacePublisher.Artifact(
                                "claude-code",
                                "native-fixture",
                                "1.0.0",
                                "a".repeat(64),
                                Map.of(
                                        ".claude-plugin/plugin.json",
                                        new GitMarketplacePublisher.Blob(claude, false),
                                        "commands/greeting.md",
                                        new GitMarketplacePublisher.Blob(command, false))),
                        new GitMarketplacePublisher.Artifact(
                                "codex",
                                "native-fixture",
                                "1.0.0",
                                "b".repeat(64),
                                Map.of(
                                        "plugin.json",
                                        new GitMarketplacePublisher.Blob(codex, false),
                                        "skills/greeting/SKILL.md",
                                        new GitMarketplacePublisher.Blob(skill, false))));
        var mapper = new ObjectMapper();
        var intents = new ArrayList<GitMarketplacePublisher.Intent>();
        var store =
                new GitMarketplacePublisher.IntentStore() {
                    public List<GitMarketplacePublisher.Intent> previous() {
                        return List.copyOf(intents);
                    }

                    public void record(GitMarketplacePublisher.Intent value) {
                        intents.add(value);
                        try {
                            Files.writeString(
                                    root.resolve("intents.json"),
                                    mapper.writerWithDefaultPrettyPrinter()
                                            .writeValueAsString(intents));
                        } catch (Exception ex) {
                            throw new IllegalStateException(ex);
                        }
                    }
                };
        var receipt =
                new GitMarketplacePublisher(mapper)
                        .publish(
                                new GitMarketplacePublisher.Plan(
                                        target,
                                        "native-test",
                                        55L,
                                        99L,
                                        99L,
                                        1700000000L,
                                        artifacts),
                                null,
                                store);
        Files.writeString(
                root.resolve("receipt.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(receipt));
        Files.writeString(base.resolve("latest-run.txt"), root.toString());
        assertThat(receipt.commit()).matches("[a-f0-9]{40}");
    }
}
