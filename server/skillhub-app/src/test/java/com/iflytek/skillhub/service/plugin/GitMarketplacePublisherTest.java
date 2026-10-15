package com.iflytek.skillhub.service.plugin;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class GitMarketplacePublisherTest {
    @Test
    void actualBareGitReceivesBothNativeIndexesAndExactResourceBytes() throws Exception {
        Path root = Files.createTempDirectory("git-marketplace-test-");
        Path remote = root.resolve("remote.git");
        new ProcessBuilder("git", "init", "--bare", remote.toString())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
                .waitFor();
        Path claude = root.resolve("claude.json"),
                codex = root.resolve("codex.json"),
                resource = root.resolve("hello.md");
        Files.writeString(claude, "{\"name\":\"sample\",\"version\":\"1.0.0\"}");
        Files.writeString(
                codex,
                "{\"$schema\":\"https://agent-plugins.org/schemas/1.0.0/plugin.schema.json\",\"name\":\"sample\",\"version\":\"1.0.0\"}");
        Files.write(
                resource, new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf, 65, 13, 10, 66, 10});
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
                                "sample",
                                "1.0.0",
                                "a".repeat(64),
                                Map.of(
                                        ".claude-plugin/plugin.json",
                                        new GitMarketplacePublisher.Blob(claude, false),
                                        "commands/hello.md",
                                        new GitMarketplacePublisher.Blob(resource, false))),
                        new GitMarketplacePublisher.Artifact(
                                "codex",
                                "sample",
                                "1.0.0",
                                "b".repeat(64),
                                Map.of(
                                        "plugin.json",
                                        new GitMarketplacePublisher.Blob(codex, false),
                                        "skills/hello/SKILL.md",
                                        new GitMarketplacePublisher.Blob(resource, false))));
        var intents = new ArrayList<GitMarketplacePublisher.Intent>();
        var store =
                new GitMarketplacePublisher.IntentStore() {
                    public List<GitMarketplacePublisher.Intent> previous() {
                        return List.copyOf(intents);
                    }

                    public void record(GitMarketplacePublisher.Intent intent) {
                        intents.add(intent);
                    }
                };
        try {
            var publisher = new GitMarketplacePublisher(new ObjectMapper());
            var receipt =
                    publisher.publish(
                            new GitMarketplacePublisher.Plan(
                                    target, "team", 1L, 1L, 10L, 1700000000L, artifacts),
                            null,
                            store);
            assertThat(receipt.commit()).matches("[a-f0-9]{40}");
            assertThat(intents).hasSize(1);
            assertThat(show(remote, "main:plugins/claude-code/sample/1.0.0/commands/hello.md"))
                    .isEqualTo(Files.readAllBytes(resource));
            var mapper = new ObjectMapper();
            var claudeIndex = mapper.readTree(show(remote, "main:.claude-plugin/marketplace.json"));
            assertThat(claudeIndex.path("name").asText()).isEqualTo("astronhub-team");
            assertThat(claudeIndex.path("plugins").get(0).path("source").asText())
                    .isEqualTo("./plugins/claude-code/sample/1.0.0");
            var codexIndex = mapper.readTree(show(remote, "main:.agents/plugins/marketplace.json"));
            assertThat(codexIndex.path("plugins").get(0).path("source").path("source").asText())
                    .isEqualTo("local");
        } finally {
            GitMarketplacePublisher.cleanup(root);
        }
    }

    @Test
    void concurrentNewerApprovalWinsIndexWhileLateOlderVersionRemainsInHistory() throws Exception {
        Path root = Files.createTempDirectory("git-marketplace-test-");
        Path remote = root.resolve("remote.git");
        init(remote);
        try {
            var target =
                    new GitMarketplacePublisher.Target(
                            1L,
                            remote.toUri().toString(),
                            remote.toUri().toString(),
                            "main",
                            "fixture");
            var oldPlan = fixturePlan(root, target, "1.0.0", 10L, 1L);
            var newer = fixturePlan(root, target, "2.0.0", 20L, 1L);
            var newerStore = new MemoryIntents();
            var raced = new java.util.concurrent.atomic.AtomicBoolean();
            var oldStore =
                    new MemoryIntents() {
                        public void record(GitMarketplacePublisher.Intent intent) {
                            super.record(intent);
                            if (raced.compareAndSet(false, true))
                                try {
                                    new GitMarketplacePublisher(new ObjectMapper())
                                            .publish(newer, null, newerStore);
                                } catch (Exception ex) {
                                    throw new RuntimeException(ex);
                                }
                        }
                    };
            var olderReceipt =
                    new GitMarketplacePublisher(new ObjectMapper())
                            .publish(oldPlan, null, oldStore);
            assertThat(oldStore.previous()).hasSize(2);
            assertThat(olderReceipt.artifacts()).allMatch(a -> !a.indexed());
            assertThat(olderReceipt.installInstructions())
                    .contains("fixed-version AstronHub archive")
                    .doesNotContain("plugin install sample@");
            var catalog = new ObjectMapper().readTree(show(remote, "main:.astronhub/catalog.json"));
            assertThat(catalog.path("entries").path("claude-code:sample").path("version").asText())
                    .isEqualTo("2.0.0");
            assertThat(
                            show(
                                    remote,
                                    "main:plugins/claude-code/sample/1.0.0/.claude-plugin/plugin.json"))
                    .isEqualTo(
                            Files.readAllBytes(
                                    oldPlan.artifacts()
                                            .getFirst()
                                            .files()
                                            .get(".claude-plugin/plugin.json")
                                            .file()));
            assertThat(
                            show(
                                    remote,
                                    "main:plugins/claude-code/sample/2.0.0/.claude-plugin/plugin.json"))
                    .isNotEmpty();
            assertThat(show(remote, olderReceipt.commit() + ":.claude-plugin/marketplace.json"))
                    .asString()
                    .contains("2.0.0");
        } finally {
            GitMarketplacePublisher.cleanup(root);
        }
    }

    @Test
    void restartAfterLostSuccessReceiptReusesActualCommitAndRejectsIdentityConflicts()
            throws Exception {
        Path root = Files.createTempDirectory("git-marketplace-test-");
        Path remote = root.resolve("remote.git");
        init(remote);
        try {
            var target =
                    new GitMarketplacePublisher.Target(
                            1L,
                            remote.toUri().toString(),
                            remote.toUri().toString(),
                            "main",
                            "fixture");
            var plan = fixturePlan(root, target, "1.0.0", 10L, 1L);
            var store = new MemoryIntents();
            var first = new GitMarketplacePublisher(new ObjectMapper()).publish(plan, null, store);
            // Simulated crash: the remote succeeded, but the caller never durably records its
            // channel receipt.
            var recovered =
                    new GitMarketplacePublisher(new ObjectMapper()).publish(plan, null, store);
            assertThat(recovered.reused()).isTrue();
            assertThat(recovered.commit()).isEqualTo(first.commit());
            assertThat(store.previous()).hasSize(1);
            var renamedProject =
                    new GitMarketplacePublisher.Plan(
                            target, "team", 2L, 2L, 20L, 1700000000L, plan.artifacts());
            assertThatThrownBy(
                            () ->
                                    new GitMarketplacePublisher(new ObjectMapper())
                                            .publish(renamedProject, null, new MemoryIntents()))
                    .hasMessage("GIT_VERSION_CONFLICT");
            var different = fixturePlan(root, target, "1.0.0", 30L, 1L);
            var artifact = different.artifacts().getFirst();
            var conflict =
                    new GitMarketplacePublisher.Artifact(
                            artifact.runtime(),
                            artifact.name(),
                            artifact.version(),
                            "c".repeat(64),
                            artifact.files());
            var conflicted =
                    new GitMarketplacePublisher.Plan(
                            target, "team", 1L, 3L, 30L, 1700000000L, List.of(conflict));
            assertThatThrownBy(
                            () ->
                                    new GitMarketplacePublisher(new ObjectMapper())
                                            .publish(conflicted, null, new MemoryIntents()))
                    .hasMessage("GIT_VERSION_CONFLICT");
            assertThat(new String(show(remote, "main:.astronhub/catalog.json"))).contains("1.0.0");
        } finally {
            GitMarketplacePublisher.cleanup(root);
        }
    }

    @Test
    void gitControlFilesAreRejectedBeforeRemoteMutation() throws Exception {
        Path root = Files.createTempDirectory("git-marketplace-test-");
        Path remote = root.resolve("remote.git");
        init(remote);
        try {
            var target =
                    new GitMarketplacePublisher.Target(
                            1L,
                            remote.toUri().toString(),
                            remote.toUri().toString(),
                            "main",
                            "fixture");
            var plan = fixturePlan(root, target, "1.0.0", 10L, 1L);
            var files = new HashMap<>(plan.artifacts().getFirst().files());
            Path hook = root.resolve("dangerous.sh");
            Files.writeString(hook, "exit 87");
            files.put(".git/hooks/pre-push", new GitMarketplacePublisher.Blob(hook, true));
            var artifact =
                    new GitMarketplacePublisher.Artifact(
                            "claude-code", "sample", "1.0.0", "a".repeat(64), files);
            var unsafe =
                    new GitMarketplacePublisher.Plan(
                            target, "team", 1L, 1L, 10L, 1700000000L, List.of(artifact));
            var store = new MemoryIntents();
            assertThatThrownBy(
                            () ->
                                    new GitMarketplacePublisher(new ObjectMapper())
                                            .publish(unsafe, null, store))
                    .hasMessage("GIT_METADATA_REJECTED");
            assertThat(store.previous()).isEmpty();
            var process = new ProcessBuilder("git", "--git-dir=" + remote, "show-ref").start();
            assertThat(process.waitFor()).isEqualTo(1);
        } finally {
            GitMarketplacePublisher.cleanup(root);
        }
    }

    @Test
    void unownedFilesAndExternallyChangedIndexesAreNeverOverwritten() throws Exception {
        Path root = Files.createTempDirectory("git-marketplace-test-");
        Path remote = root.resolve("remote.git");
        init(remote);
        try {
            var target =
                    new GitMarketplacePublisher.Target(
                            1L,
                            remote.toUri().toString(),
                            remote.toUri().toString(),
                            "main",
                            "fixture");
            seed(
                    remote,
                    null,
                    "plugins/claude-code/sample/1.0.0/.claude-plugin/plugin.json",
                    "unmanaged");
            var store = new MemoryIntents();
            assertThatThrownBy(
                            () ->
                                    new GitMarketplacePublisher(new ObjectMapper())
                                            .publish(
                                                    fixturePlan(root, target, "1.0.0", 10L, 1L),
                                                    null,
                                                    store))
                    .hasMessage("GIT_UNMANAGED_CONTENT");
            assertThat(store.previous()).isEmpty();
            assertThat(
                            show(
                                    remote,
                                    "main:plugins/claude-code/sample/1.0.0/.claude-plugin/plugin.json"))
                    .asString()
                    .isEqualTo("unmanaged");
            var first =
                    new GitMarketplacePublisher(new ObjectMapper())
                            .publish(
                                    fixturePlan(root, target, "2.0.0", 20L, 1L),
                                    null,
                                    new MemoryIntents());
            seed(remote, first.commit(), ".claude-plugin/marketplace.json", "{}");
            assertThatThrownBy(
                            () ->
                                    new GitMarketplacePublisher(new ObjectMapper())
                                            .publish(
                                                    fixturePlan(root, target, "3.0.0", 30L, 1L),
                                                    null,
                                                    store))
                    .hasMessage("GIT_REMOTE_INDEX_CONFLICT");
            assertThat(store.previous()).isEmpty();
            assertThat(show(remote, "main:.claude-plugin/marketplace.json"))
                    .asString()
                    .isEqualTo("{}");
        } finally {
            GitMarketplacePublisher.cleanup(root);
        }
    }

    static void seed(Path remote, String parent, String path, String content) throws Exception {
        String script =
                "commit refs/heads/main\n"
                    + "committer Fixture <fixture@example.invalid> 1700000000 +0000\n"
                    + "data 4\n"
                    + "seed\n"
                        + (parent == null ? "" : "from " + parent + "\n")
                        + "M 100644 inline "
                        + path
                        + "\ndata "
                        + content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                        + "\n"
                        + content
                        + "\n\ndone\n";
        var process =
                new ProcessBuilder("git", "--git-dir=" + remote, "fast-import", "--quiet", "--done")
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
        try (var output = process.getOutputStream()) {
            output.write(script.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        assertThat(process.waitFor()).isZero();
    }

    static class MemoryIntents implements GitMarketplacePublisher.IntentStore {
        final List<GitMarketplacePublisher.Intent> intents = new ArrayList<>();

        public List<GitMarketplacePublisher.Intent> previous() {
            return List.copyOf(intents);
        }

        public void record(GitMarketplacePublisher.Intent intent) {
            intents.add(intent);
        }
    }

    static GitMarketplacePublisher.Plan fixturePlan(
            Path root,
            GitMarketplacePublisher.Target target,
            String version,
            Long task,
            Long project)
            throws Exception {
        Path manifest = root.resolve("manifest-" + task + ".json");
        Files.writeString(manifest, "{\"name\":\"sample\",\"version\":\"" + version + "\"}");
        var artifact =
                new GitMarketplacePublisher.Artifact(
                        "claude-code",
                        "sample",
                        version,
                        "a".repeat(64),
                        Map.of(
                                ".claude-plugin/plugin.json",
                                new GitMarketplacePublisher.Blob(manifest, false)));
        return new GitMarketplacePublisher.Plan(
                target, "team", project, task, task, 1700000000L, List.of(artifact));
    }

    static void init(Path remote) throws Exception {
        var process =
                new ProcessBuilder("git", "init", "--bare", remote.toString())
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
        assertThat(process.waitFor()).isZero();
    }

    static byte[] show(Path remote, String object) throws Exception {
        var process = new ProcessBuilder("git", "--git-dir=" + remote, "show", object).start();
        byte[] bytes = process.getInputStream().readAllBytes();
        if (process.waitFor() != 0) throw new IllegalStateException("fixture read failed");
        return bytes;
    }
}
