package com.iflytek.skillhub.service.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.stereotype.Component;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Exact-blob Git plumbing. Never checks out imported files or invokes package code. */
@Component
public class GitMarketplacePublisher {
    private static final long MAX_SCRATCH_BYTES = 4L * 1024 * 1024 * 1024;
    private static final int MAX_JSON_BYTES = 4194304;
    private final ObjectMapper mapper;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public GitMarketplacePublisher(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public record Target(
            Long configId, String remote, String fetchUrl, String branch, String credentialRef) {}

    public record Blob(Path file, boolean executable) {}

    public record Artifact(
            String runtime, String name, String version, String sha256, Map<String, Blob> files) {}

    public record Plan(
            Target target,
            String namespace,
            Long projectId,
            Long releaseId,
            Long taskId,
            long timestamp,
            List<Artifact> artifacts,
            Withdrawal withdrawal) {
        public Plan(
                Target target,
                String namespace,
                Long projectId,
                Long releaseId,
                Long taskId,
                long timestamp,
                List<Artifact> artifacts) {
            this(target, namespace, projectId, releaseId, taskId, timestamp, artifacts, null);
        }
    }

    public record Withdrawal(Long operationId, Long throughTaskId, Long throughReleaseId) {}

    public Receipt withdraw(
            Plan approved,
            Long operationId,
            Long throughTask,
            Long throughRelease,
            String credential,
            IntentStore store)
            throws Exception {
        return publish(
                new Plan(
                        approved.target,
                        approved.namespace,
                        approved.projectId,
                        approved.releaseId,
                        approved.taskId,
                        approved.timestamp,
                        approved.artifacts,
                        new Withdrawal(operationId, throughTask, throughRelease)),
                credential,
                store);
    }

    public record Intent(String parent, String tree, String commit, String planDigest) {}

    public record PublishedArtifact(
            String runtime, String name, String version, String sha256, boolean indexed) {}

    public record Receipt(
            String remote,
            String branch,
            String marketplace,
            String commit,
            String tree,
            String planDigest,
            boolean reused,
            List<String> indexes,
            List<PublishedArtifact> artifacts,
            String installInstructions) {}

    public interface IntentStore {
        List<Intent> previous();

        void record(Intent intent);
    }

    public static class Failure extends IOException {
        private final String code;

        public Failure(String code) {
            super(code);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    public static String canonicalRemote(String value) {
        try {
            URI uri = URI.create(value);
            if (!value.matches("[A-Za-z0-9:/._~+@-]+")
                    || !Set.of("https", "file").contains(uri.getScheme())
                    || uri.getUserInfo() != null
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null
                    || !uri.normalize().equals(uri)
                    || value.contains("\\")
                    || value.contains("%")
                    || value.chars().anyMatch(c -> c < 32)
                    || ("https".equals(uri.getScheme()) && uri.getHost() == null)
                    || ("file".equals(uri.getScheme())
                            && (uri.getAuthority() != null && !uri.getAuthority().isEmpty())))
                throw new IllegalArgumentException();
            return uri.toString();
        } catch (Exception ex) {
            throw new IllegalArgumentException("INVALID_GIT_REMOTE");
        }
    }

    public static boolean validBranch(String branch) {
        return branch != null
                && branch.matches("[A-Za-z0-9][A-Za-z0-9._/-]{0,199}")
                && !branch.contains("..")
                && !branch.contains("//")
                && !branch.endsWith("/")
                && !branch.endsWith(".")
                && Arrays.stream(branch.split("/"))
                        .noneMatch(p -> p.startsWith(".") || p.endsWith(".lock"));
    }

    public Receipt publish(Plan plan, String credential, IntentStore store) throws Exception {
        String remote = canonicalRemote(plan.target.remote());
        if (!canonicalRemote(plan.target.fetchUrl()).equals(remote)
                || !validBranch(plan.target.branch())
                || !plan.namespace.matches("[a-z0-9]+(?:-[a-z0-9]+)*")
                || plan.artifacts.isEmpty()) throw new Failure("GIT_INVALID_PLAN");
        String key = remote + "\n" + plan.target.branch();
        synchronized (locks.computeIfAbsent(key, k -> new Object())) {
            return publishLocked(plan, credential, store);
        }
    }

    /** Reconciles only against the immutable original target; never imports or pushes a tree. */
    public java.util.Optional<Receipt> reconcile(Plan plan, String credential, IntentStore store)
            throws Exception {
        String remote = canonicalRemote(plan.target.remote());
        if (!remote.equals(canonicalRemote(plan.target.fetchUrl()))
                || !validBranch(plan.target.branch())) throw new Failure("GIT_INVALID_PLAN");
        Path temp = Files.createTempDirectory("astronhub-git-reconcile-");
        try {
            Path repo = temp.resolve("objects.git");
            Files.createDirectory(temp.resolve("hooks"));
            run(temp, null, null, null, List.of("init", "--bare", repo.toString()), null, 1048576);
            String head = fetch(temp, repo, plan.target, credential);
            if (head == null) return java.util.Optional.empty();
            String digest = planDigest(plan),
                    market =
                            plan.namespace.equals("global")
                                    ? "astronhub"
                                    : "astronhub-" + plan.namespace;
            for (Intent intent : store.previous()) {
                if (!digest.equals(intent.planDigest)) continue;
                if (command(
                                        temp,
                                        repo,
                                        plan.target,
                                        credential,
                                        List.of("merge-base", "--is-ancestor", intent.commit, head),
                                        null,
                                        64)
                                .code
                        == 0) {
                    String tree =
                            text(
                                    command(
                                            temp,
                                            repo,
                                            plan.target,
                                            credential,
                                            List.of("rev-parse", intent.commit + "^{tree}"),
                                            null,
                                            128));
                    if (tree.equals(intent.tree)) {
                        var catalog =
                                readCatalog(
                                        temp,
                                        repo,
                                        plan.target,
                                        credential,
                                        intent.commit,
                                        plan.namespace,
                                        market);
                        if (plan.withdrawal == null)
                            verifyVersions(temp, repo, plan, credential, intent.commit, catalog);
                        return java.util.Optional.of(receipt(plan, market, intent, true, catalog));
                    }
                }
            }
            var catalog =
                    readCatalog(temp, repo, plan.target, credential, head, plan.namespace, market);
            String recorded = catalog.path("publications").path(publicationKey(plan)).asText();
            if (recorded.isEmpty()) return java.util.Optional.empty();
            if (!recorded.equals(digest)) throw new Failure("GIT_PUBLICATION_CONFLICT");
            if (plan.withdrawal == null)
                verifyVersions(temp, repo, plan, credential, head, catalog);
            String tree =
                    text(
                            command(
                                    temp,
                                    repo,
                                    plan.target,
                                    credential,
                                    List.of("rev-parse", head + "^{tree}"),
                                    null,
                                    128));
            return java.util.Optional.of(
                    receipt(plan, market, new Intent(head, tree, head, digest), true, catalog));
        } finally {
            cleanup(temp);
        }
    }

    private Receipt publishLocked(Plan plan, String credential, IntentStore store)
            throws Exception {
        Path temp = Files.createTempDirectory("astronhub-git-");
        try {
            Path repo = temp.resolve("objects.git");
            Files.createDirectory(temp.resolve("hooks"));
            run(temp, null, null, null, List.of("init", "--bare", repo.toString()), null, 1048576);
            String digest = planDigest(plan),
                    market =
                            plan.namespace.equals("global")
                                    ? "astronhub"
                                    : "astronhub-" + plan.namespace;
            for (int attempt = 0; attempt < 3; attempt++) {
                String head = fetch(temp, repo, plan.target, credential);
                if (head != null)
                    for (Intent intent : store.previous()) {
                        if (!intent.planDigest.equals(digest)) continue;
                        if (command(
                                                temp,
                                                repo,
                                                plan.target,
                                                credential,
                                                List.of(
                                                        "merge-base",
                                                        "--is-ancestor",
                                                        intent.commit,
                                                        head),
                                                null,
                                                64)
                                        .code
                                == 0) {
                            String tree =
                                    text(
                                            command(
                                                    temp,
                                                    repo,
                                                    plan.target,
                                                    credential,
                                                    List.of("rev-parse", intent.commit + "^{tree}"),
                                                    null,
                                                    128));
                            if (tree.equals(intent.tree))
                                return receipt(
                                        plan,
                                        market,
                                        intent,
                                        true,
                                        readCatalog(
                                                temp,
                                                repo,
                                                plan.target,
                                                credential,
                                                intent.commit,
                                                plan.namespace,
                                                market));
                        }
                    }
                ObjectNode catalog =
                        readCatalog(
                                temp, repo, plan.target, credential, head, plan.namespace, market);
                ObjectNode publications = (ObjectNode) catalog.withObject("/publications");
                String task = publicationKey(plan);
                if (publications.has(task)) {
                    if (!publications.path(task).asText().equals(digest))
                        throw new Failure("GIT_PUBLICATION_CONFLICT");
                    // Exact task content can be reconciled after a lost local intent receipt.
                    if (plan.withdrawal == null)
                        verifyVersions(temp, repo, plan, credential, head, catalog);
                    return receipt(
                            plan,
                            market,
                            new Intent(
                                    head,
                                    text(
                                            command(
                                                    temp,
                                                    repo,
                                                    plan.target,
                                                    credential,
                                                    List.of("rev-parse", head + "^{tree}"),
                                                    null,
                                                    128)),
                                    head,
                                    digest),
                            true,
                            catalog);
                }
                if (plan.withdrawal == null)
                    verifyVersions(temp, repo, plan, credential, head, catalog);
                ObjectNode entries = (ObjectNode) catalog.withObject("/entries"),
                        versions = (ObjectNode) catalog.withObject("/versions");
                for (Artifact artifact : plan.artifacts) {
                    String identity = artifact.runtime + ":" + artifact.name,
                            versionIdentity = identity + "@" + artifact.version,
                            path = artifactPath(artifact);
                    JsonNode old = entries.get(identity);
                    if (old != null && old.path("projectId").asLong() != plan.projectId)
                        throw new Failure("GIT_NATIVE_NAME_CONFLICT");
                    ObjectNode barriers = catalog.withObject("/tombstones");
                    JsonNode barrier = barriers.get(identity);
                    if (barrier != null && barrier.path("projectId").asLong() != plan.projectId)
                        throw new Failure("GIT_NATIVE_NAME_CONFLICT");
                    if (plan.withdrawal != null) {
                        if (old != null
                                && old.path("releaseId").asLong() == plan.releaseId
                                && com.iflytek.skillhub.domain.plugin.PluginPublicationOrder
                                                .compare(
                                                        old.path("taskId").asLong(-1),
                                                        old.path("releaseId").asLong(-1),
                                                        plan.withdrawal.throughTaskId,
                                                        plan.withdrawal.throughReleaseId)
                                        <= 0) entries.remove(identity);
                        if (barrier == null
                                || com.iflytek.skillhub.domain.plugin.PluginPublicationOrder
                                                .compare(
                                                        plan.withdrawal.throughTaskId,
                                                        plan.withdrawal.throughReleaseId,
                                                        barrier.path("taskId").asLong(-1),
                                                        barrier.path("releaseId").asLong(-1))
                                        > 0) {
                            var tombstone = barriers.putObject(identity);
                            tombstone
                                    .put("projectId", plan.projectId)
                                    .put("taskId", plan.withdrawal.throughTaskId)
                                    .put("releaseId", plan.withdrawal.throughReleaseId)
                                    .put("operationId", plan.withdrawal.operationId);
                        }
                        continue;
                    }
                    if (barrier != null
                            && com.iflytek.skillhub.domain.plugin.PluginPublicationOrder.compare(
                                            plan.taskId,
                                            plan.releaseId,
                                            barrier.path("taskId").asLong(-1),
                                            barrier.path("releaseId").asLong(-1))
                                    <= 0) throw new Failure("GIT_PUBLICATION_WITHDRAWN");
                    if (old == null
                            || com.iflytek.skillhub.domain.plugin.PluginPublicationOrder.compare(
                                            plan.taskId,
                                            plan.releaseId,
                                            old.path("taskId").asLong(-1),
                                            old.path("releaseId").asLong(-1))
                                    >= 0) {
                        var selected = entries.putObject(identity);
                        selected.put("runtime", artifact.runtime);
                        selected.put("name", artifact.name);
                        selected.put("version", artifact.version);
                        selected.put("source", path);
                        selected.put("projectId", plan.projectId);
                        selected.put("taskId", plan.taskId);
                        selected.put("releaseId", plan.releaseId);
                    }
                    var version = versions.putObject(versionIdentity);
                    version.put("projectId", plan.projectId);
                    version.put("sha256", artifact.sha256);
                    version.put("source", path);
                }
                publications.put(task, digest);
                byte[] catalogBytes = mapper.writeValueAsBytes(catalog),
                        claude = index(entries, market, false),
                        codex = index(entries, market, true);
                if (catalogBytes.length > MAX_JSON_BYTES
                        || claude.length > MAX_JSON_BYTES
                        || codex.length > MAX_JSON_BYTES) throw new Failure("GIT_CATALOG_LIMIT");
                Path script = temp.resolve("import.stream");
                try (var output = new BufferedOutputStream(Files.newOutputStream(script))) {
                    line(output, "commit refs/heads/astronhub-stage");
                    line(
                            output,
                            "committer AstronHub <plugins@astronhub.invalid> "
                                    + plan.timestamp
                                    + " +0000");
                    data(
                            output,
                            ("AstronHub approved publication " + plan.taskId + "\n")
                                    .getBytes(StandardCharsets.UTF_8));
                    if (head != null) line(output, "from " + head);
                    long contentBytes = 0;
                    for (Artifact artifact :
                            plan.withdrawal == null ? plan.artifacts : List.<Artifact>of())
                        for (var file : new TreeMap<>(artifact.files).entrySet()) {
                            safeFile(file.getKey());
                            line(
                                    output,
                                    "M "
                                            + (file.getValue().executable ? "100755" : "100644")
                                            + " inline "
                                            + quoted(artifactPath(artifact) + "/" + file.getKey()));
                            long size = Files.size(file.getValue().file);
                            contentBytes += size;
                            if (contentBytes > 2147483648L) throw new Failure("GIT_CONTENT_LIMIT");
                            line(output, "data " + size);
                            Files.copy(file.getValue().file, output);
                            output.write('\n');
                        }
                    inline(output, ".astronhub/catalog.json", catalogBytes);
                    inline(output, ".claude-plugin/marketplace.json", claude);
                    inline(output, ".agents/plugins/marketplace.json", codex);
                    line(output, "");
                    line(output, "done");
                }
                if (Files.size(script) > 2147483648L + 3L * MAX_JSON_BYTES)
                    throw new Failure("GIT_CONTENT_LIMIT");
                command(
                        temp,
                        repo,
                        plan.target,
                        credential,
                        List.of("update-ref", "-d", "refs/heads/astronhub-stage"),
                        null,
                        256);
                text(
                        command(
                                temp,
                                repo,
                                plan.target,
                                credential,
                                List.of("fast-import", "--quiet", "--done"),
                                script,
                                1048576));
                String commit =
                        text(
                                command(
                                        temp,
                                        repo,
                                        plan.target,
                                        credential,
                                        List.of("rev-parse", "refs/heads/astronhub-stage"),
                                        null,
                                        128));
                String tree =
                        text(
                                command(
                                        temp,
                                        repo,
                                        plan.target,
                                        credential,
                                        List.of("rev-parse", commit + "^{tree}"),
                                        null,
                                        128));
                Intent intent = new Intent(head, tree, commit, digest);
                store.record(intent); // Durable before remote mutation.
                PluginPublicationWriteGuard.check();
                Result pushed =
                        command(
                                temp,
                                repo,
                                plan.target,
                                credential,
                                List.of(
                                        "push",
                                        "--porcelain",
                                        plan.target.remote,
                                        commit + ":refs/heads/" + plan.target.branch),
                                null,
                                1048576);
                String actual = fetch(temp, repo, plan.target, credential);
                if (actual != null
                        && command(
                                                temp,
                                                repo,
                                                plan.target,
                                                credential,
                                                List.of(
                                                        "merge-base",
                                                        "--is-ancestor",
                                                        commit,
                                                        actual),
                                                null,
                                                64)
                                        .code
                                == 0) return receipt(plan, market, intent, false, catalog);
                if (pushed.code == 0) throw new Failure("GIT_PUSH_UNCONFIRMED");
            }
            throw new Failure("GIT_CONCURRENT_UPDATE");
        } finally {
            cleanup(temp);
        }
    }

    private String artifactPath(Artifact artifact) throws Failure {
        if (!Set.of("claude-code", "codex").contains(artifact.runtime)
                || !artifact.name.matches("[a-z0-9]+(?:-[a-z0-9]+)*")
                || !com.iflytek.skillhub.domain.plugin.PluginVersions.valid(artifact.version))
            throw new Failure("GIT_PACKAGE_IDENTITY");
        return "plugins/" + artifact.runtime + "/" + artifact.name + "/" + artifact.version;
    }

    private void safeFile(String path) throws Failure {
        OfflinePluginScanner.safePath(path);
        for (String part : path.split("/"))
            if (part.equalsIgnoreCase(".git") || part.equalsIgnoreCase(".gitmodules"))
                throw new Failure("GIT_METADATA_REJECTED");
    }

    private void verifyVersions(
            Path temp, Path repo, Plan plan, String credential, String head, ObjectNode catalog)
            throws Exception {
        for (Artifact artifact : plan.artifacts) {
            String identity = artifact.runtime + ":" + artifact.name;
            String root = artifactPath(artifact);
            var existing = catalog.path("versions").get(identity + "@" + artifact.version);
            if (existing == null) {
                if (head != null
                        && command(
                                                temp,
                                                repo,
                                                plan.target,
                                                credential,
                                                List.of("cat-file", "-e", head + ":" + root),
                                                null,
                                                128)
                                        .code
                                == 0) throw new Failure("GIT_UNMANAGED_CONTENT");
                continue;
            }
            if (existing.path("projectId").asLong() != plan.projectId
                    || !existing.path("sha256").asText().equals(artifact.sha256))
                throw new Failure("GIT_VERSION_CONFLICT");
            Result list =
                    command(
                            temp,
                            repo,
                            plan.target,
                            credential,
                            List.of("ls-tree", "-r", "-z", head + ":" + root),
                            null,
                            16 * MAX_JSON_BYTES);
            String listing = text(list);
            var observed = new TreeMap<String, String>();
            for (String entry : listing.split("\u0000"))
                if (!entry.isEmpty()) {
                    int tab = entry.indexOf('\t');
                    if (tab < 0) throw new Failure("GIT_REMOTE_CONTENT_CONFLICT");
                    observed.put(entry.substring(tab + 1), entry.substring(0, tab));
                }
            if (observed.size() != artifact.files.size())
                throw new Failure("GIT_REMOTE_CONTENT_CONFLICT");
            for (var file : artifact.files.entrySet()) {
                String hash =
                        text(
                                command(
                                        temp,
                                        repo,
                                        plan.target,
                                        credential,
                                        List.of(
                                                "hash-object",
                                                "--no-filters",
                                                file.getValue().file.toString()),
                                        null,
                                        128));
                String expected =
                        (file.getValue().executable ? "100755" : "100644") + " blob " + hash;
                if (!expected.equals(observed.get(file.getKey())))
                    throw new Failure("GIT_REMOTE_CONTENT_CONFLICT");
            }
        }
    }

    private ObjectNode readCatalog(
            Path temp,
            Path repo,
            Target target,
            String credential,
            String head,
            String namespace,
            String market)
            throws Exception {
        if (head == null)
            return mapper.createObjectNode().put("namespace", namespace).put("marketplace", market);
        Result exists =
                command(
                        temp,
                        repo,
                        target,
                        credential,
                        List.of("cat-file", "-e", head + ":.astronhub/catalog.json"),
                        null,
                        128);
        if (exists.code != 0) {
            for (String index :
                    List.of(".claude-plugin/marketplace.json", ".agents/plugins/marketplace.json"))
                if (command(
                                        temp,
                                        repo,
                                        target,
                                        credential,
                                        List.of("cat-file", "-e", head + ":" + index),
                                        null,
                                        128)
                                .code
                        == 0) throw new Failure("GIT_UNMANAGED_MARKETPLACE");
            return mapper.createObjectNode().put("namespace", namespace).put("marketplace", market);
        }
        long size =
                Long.parseLong(
                        text(
                                command(
                                        temp,
                                        repo,
                                        target,
                                        credential,
                                        List.of(
                                                "cat-file",
                                                "-s",
                                                head + ":.astronhub/catalog.json"),
                                        null,
                                        128)));
        if (size > MAX_JSON_BYTES) throw new Failure("GIT_CATALOG_LIMIT");
        JsonNode json =
                mapper.readTree(
                        command(
                                        temp,
                                        repo,
                                        target,
                                        credential,
                                        List.of("show", head + ":.astronhub/catalog.json"),
                                        null,
                                        MAX_JSON_BYTES)
                                .bytes);
        if (!json.isObject()
                || !json.path("namespace").asText().equals(namespace)
                || !json.path("marketplace").asText().equals(market))
            throw new Failure("GIT_NAMESPACE_CONFLICT");
        ObjectNode entries = (ObjectNode) json.path("entries");
        for (boolean codex : List.of(false, true)) {
            String path =
                    codex ? ".agents/plugins/marketplace.json" : ".claude-plugin/marketplace.json";
            Result nativeIndex =
                    command(
                            temp,
                            repo,
                            target,
                            credential,
                            List.of("show", head + ":" + path),
                            null,
                            MAX_JSON_BYTES);
            if (nativeIndex.code != 0
                    || !mapper.readTree(index(entries, market, codex))
                            .equals(mapper.readTree(nativeIndex.bytes)))
                throw new Failure("GIT_REMOTE_INDEX_CONFLICT");
        }
        return (ObjectNode) json;
    }

    private byte[] index(ObjectNode entries, String market, boolean codex) throws Exception {
        ObjectNode root = mapper.createObjectNode().put("name", market);
        if (codex) root.putObject("interface").put("displayName", market);
        else root.putObject("owner").put("name", "AstronHub");
        var plugins = root.putArray("plugins");
        var names = new TreeMap<String, JsonNode>();
        entries.fields().forEachRemaining(e -> names.put(e.getKey(), e.getValue()));
        for (JsonNode entry : names.values())
            if (entry.path("runtime").asText().equals(codex ? "codex" : "claude-code")) {
                var plugin = plugins.addObject().put("name", entry.path("name").asText());
                if (codex) {
                    plugin.putObject("source")
                            .put("source", "local")
                            .put("path", "./" + entry.path("source").asText());
                    plugin.putObject("policy")
                            .put("installation", "AVAILABLE")
                            .put("authentication", "ON_INSTALL");
                    plugin.put("category", "Productivity");
                } else
                    plugin.put("source", "./" + entry.path("source").asText())
                            .put("version", entry.path("version").asText());
            }
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);
    }

    private boolean indexed(Plan plan, Artifact artifact, ObjectNode catalog) {
        JsonNode entry = catalog.path("entries").path(artifact.runtime + ":" + artifact.name);
        return entry.path("projectId").asLong(-1) == plan.projectId
                && entry.path("version").asText().equals(artifact.version)
                && entry.path("source")
                        .asText()
                        .equals(
                                "plugins/"
                                        + artifact.runtime
                                        + "/"
                                        + artifact.name
                                        + "/"
                                        + artifact.version);
    }

    private Receipt receipt(
            Plan plan, String market, Intent intent, boolean reused, ObjectNode catalog) {
        StringBuilder instructions =
                new StringBuilder(
                        "git clone -- "
                                + plan.target.fetchUrl
                                + " <local-directory>\ngit -C <local-directory> checkout --detach "
                                + intent.commit
                                + "\n");
        for (Artifact artifact : plan.artifacts) {
            if (!indexed(plan, artifact, catalog)) {
                instructions
                        .append(artifact.runtime)
                        .append(
                                ": this commit retains the requested files but its marketplace"
                                    + " selects a newer version. Use the fixed-version AstronHub"
                                    + " archive and local installation recipe.\n");
                continue;
            }
            if (artifact.runtime.equals("claude-code"))
                instructions
                        .append(
                                "Claude Code 2.1.273:\n"
                                        + "claude plugin marketplace add <local-directory>\n"
                                        + "claude plugin install ")
                        .append(artifact.name)
                        .append("@")
                        .append(market)
                        .append("\n");
            else {
                instructions.append("Codex CLI 0.162.1:\ncodex plugin marketplace add ");
                if (plan.target.fetchUrl.startsWith("file:"))
                    instructions.append("<local-directory>");
                else
                    instructions
                            .append(plan.target.fetchUrl)
                            .append(" --ref ")
                            .append(intent.commit);
                instructions
                        .append("\ncodex plugin add ")
                        .append(artifact.name)
                        .append("@")
                        .append(market)
                        .append("\n");
            }
        }
        return new Receipt(
                plan.target.fetchUrl,
                plan.target.branch,
                market,
                intent.commit,
                intent.tree,
                intent.planDigest,
                reused,
                List.of(".claude-plugin/marketplace.json", ".agents/plugins/marketplace.json"),
                plan.artifacts.stream()
                        .map(
                                a ->
                                        new PublishedArtifact(
                                                a.runtime,
                                                a.name,
                                                a.version,
                                                a.sha256,
                                                indexed(plan, a, catalog)))
                        .toList(),
                instructions.toString());
    }

    private String planDigest(Plan plan) throws Exception {
        var value =
                mapper.createObjectNode()
                        .put("operation", plan.withdrawal == null ? "PUBLISH" : "WITHDRAW_CURRENT")
                        .put("configId", plan.target.configId)
                        .put("remote", plan.target.remote)
                        .put("fetchUrl", plan.target.fetchUrl)
                        .put("branch", plan.target.branch)
                        .put("credentialRef", plan.target.credentialRef)
                        .put("namespace", plan.namespace)
                        .put("projectId", plan.projectId)
                        .put("releaseId", plan.releaseId)
                        .put("taskId", plan.taskId);
        if (plan.withdrawal != null)
            value.put("governanceOperationId", plan.withdrawal.operationId)
                    .put("throughTaskId", plan.withdrawal.throughTaskId)
                    .put("throughReleaseId", plan.withdrawal.throughReleaseId);
        var a = value.putArray("artifacts");
        for (Artifact artifact :
                plan.artifacts.stream().sorted(Comparator.comparing(Artifact::runtime)).toList())
            a.addObject()
                    .put("runtime", artifact.runtime)
                    .put("name", artifact.name)
                    .put("version", artifact.version)
                    .put("sha256", artifact.sha256);
        return HexFormat.of()
                .formatHex(
                        MessageDigest.getInstance("SHA-256")
                                .digest(mapper.writeValueAsBytes(value)));
    }

    private static String publicationKey(Plan plan) {
        return plan.withdrawal == null
                ? plan.taskId.toString()
                : "withdraw:" + plan.withdrawal.operationId;
    }

    private void inline(OutputStream out, String path, byte[] bytes) throws Exception {
        line(out, "M 100644 inline " + quoted(path));
        data(out, bytes);
    }

    private String quoted(String path) throws Exception {
        return mapper.writeValueAsString(path);
    }

    private static void line(OutputStream out, String value) throws IOException {
        out.write((value + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private static void data(OutputStream out, byte[] bytes) throws IOException {
        line(out, "data " + bytes.length);
        out.write(bytes);
        out.write('\n');
    }

    private String fetch(Path temp, Path repo, Target target, String credential) throws Exception {
        Result refs =
                command(
                        temp,
                        repo,
                        target,
                        credential,
                        List.of(
                                "ls-remote",
                                "--exit-code",
                                "--heads",
                                target.remote,
                                "refs/heads/" + target.branch),
                        null,
                        1048576);
        if (refs.code == 2) return null;
        if (refs.code != 0) throw new Failure("GIT_REMOTE_UNAVAILABLE");
        text(
                command(
                        temp,
                        repo,
                        target,
                        credential,
                        List.of(
                                "fetch",
                                "--no-tags",
                                "--no-recurse-submodules",
                                "--filter=blob:none",
                                "--depth=256",
                                target.remote,
                                "+refs/heads/" + target.branch + ":refs/remotes/target/head"),
                        null,
                        1048576));
        return text(
                command(
                        temp,
                        repo,
                        target,
                        credential,
                        List.of("rev-parse", "refs/remotes/target/head"),
                        null,
                        128));
    }

    private Result command(
            Path temp,
            Path repo,
            Target target,
            String credential,
            List<String> args,
            Path input,
            int max)
            throws Exception {
        return run(temp, repo, target, credential, args, input, max);
    }

    private record Result(int code, byte[] bytes) {}

    private String text(Result result) throws Failure {
        if (result.code != 0) throw new Failure("GIT_COMMAND_FAILED");
        return new String(result.bytes, StandardCharsets.UTF_8).strip();
    }

    private Result run(
            Path temp,
            Path repo,
            Target target,
            String credential,
            List<String> args,
            Path input,
            int max)
            throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        if (repo != null) command.add("--git-dir=" + repo);
        command.addAll(
                List.of(
                        "-c",
                        "core.hooksPath=" + temp.resolve("hooks"),
                        "-c",
                        "core.fsmonitor=false",
                        "-c",
                        "gc.auto=0",
                        "-c",
                        "maintenance.auto=false",
                        "-c",
                        "protocol.allow=never",
                        "-c",
                        "protocol.https.allow=always",
                        "-c",
                        "protocol.file.allow=always",
                        "-c",
                        "http.followRedirects=false",
                        "-c",
                        "http.sslVerify=true",
                        "-c",
                        "fetch.recurseSubmodules=false"));
        command.addAll(args);
        ProcessBuilder builder =
                new ProcessBuilder(command)
                        .directory(temp.toFile())
                        .redirectError(ProcessBuilder.Redirect.DISCARD);
        var environment = builder.environment();
        environment
                .keySet()
                .removeIf(
                        key -> {
                            String k = key.toUpperCase(Locale.ROOT);
                            return k.startsWith("GIT_")
                                    || k.equals("SSH_ASKPASS")
                                    || k.equals("BASH_ENV")
                                    || k.equals("ENV");
                        });
        environment.put("GIT_CONFIG_NOSYSTEM", "1");
        environment.put(
                "GIT_CONFIG_GLOBAL",
                System.getProperty("os.name").startsWith("Windows") ? "NUL" : "/dev/null");
        environment.put("GIT_TERMINAL_PROMPT", "0");
        environment.put("GIT_LFS_SKIP_SMUDGE", "1");
        if (credential != null && target != null && target.remote.startsWith("https://")) {
            if (credential.contains("\n") || credential.contains("\r"))
                throw new Failure("GIT_CREDENTIAL_UNAVAILABLE");
            environment.put("GIT_CONFIG_COUNT", "1");
            environment.put("GIT_CONFIG_KEY_0", "http." + target.remote + ".extraHeader");
            environment.put(
                    "GIT_CONFIG_VALUE_0",
                    "Authorization: "
                            + (credential.startsWith("Basic ") || credential.startsWith("Bearer ")
                                    ? credential
                                    : "Bearer " + credential));
        }
        if (input != null) builder.redirectInput(input.toFile());
        else builder.redirectInput(ProcessBuilder.Redirect.PIPE);
        Process process = builder.start();
        if (input == null) process.getOutputStream().close();
        var output =
                java.util.concurrent.CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return process.getInputStream().readNBytes(max + 1);
                            } catch (IOException ex) {
                                return new byte[max + 1];
                            }
                        });
        java.util.Set<ProcessHandle> owned = new java.util.HashSet<>();
        long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        try {
            while (true) {
                process.descendants().forEach(owned::add);
                if (process.waitFor(100, TimeUnit.MILLISECONDS)) break;
                if (System.nanoTime() > deadline) throw new Failure("GIT_COMMAND_TIMEOUT");
                if (output.isDone() && output.get().length > max)
                    throw new Failure("GIT_OUTPUT_LIMIT");
                try (var paths = Files.walk(temp)) {
                    long bytes = 0;
                    var iterator = paths.filter(Files::isRegularFile).iterator();
                    while (iterator.hasNext()) {
                        bytes += Files.size(iterator.next());
                        if (bytes > MAX_SCRATCH_BYTES) throw new Failure("GIT_SCRATCH_LIMIT");
                    }
                }
            }
            byte[] bytes = output.get(5, TimeUnit.SECONDS);
            if (bytes.length > max) throw new Failure("GIT_OUTPUT_LIMIT");
            return new Result(process.exitValue(), bytes);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new Failure("GIT_COMMAND_INTERRUPTED");
        } finally {
            boolean interrupted = Thread.interrupted();
            process.descendants().forEach(owned::add);
            // Git transport helpers can retain pipes and credential-bearing environment after Git
            // exits.
            for (ProcessHandle child : owned) if (child.isAlive()) child.destroyForcibly();
            if (process.isAlive()) process.destroyForcibly();
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            for (ProcessHandle child : owned)
                if (child.isAlive())
                    try {
                        child.onExit()
                                .get(Math.max(1, until - System.nanoTime()), TimeUnit.NANOSECONDS);
                    } catch (InterruptedException ex) {
                        interrupted = true;
                    } catch (Exception ignored) {
                    }
            try {
                process.waitFor(Math.max(1, until - System.nanoTime()), TimeUnit.NANOSECONDS);
            } catch (InterruptedException ex) {
                interrupted = true;
            }
            output.cancel(true);
            try {
                process.getInputStream().close();
                process.getOutputStream().close();
            } catch (IOException ignored) {
            }
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    static void cleanup(Path root) throws IOException {
        Path resolved = root.toAbsolutePath().normalize();
        Path temp = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        if (!resolved.getParent().equals(temp)
                || !(resolved.getFileName().toString().startsWith("astronhub-git-")
                        || resolved.getFileName().toString().startsWith("git-marketplace-test-")))
            throw new IOException("GIT_CLEANUP_TARGET");
        try (var paths = Files.walk(resolved)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (Files.getFileAttributeView(
                                path,
                                java.nio.file.attribute.DosFileAttributeView.class,
                                LinkOption.NOFOLLOW_LINKS)
                        != null)
                    Files.setAttribute(path, "dos:readonly", false, LinkOption.NOFOLLOW_LINKS);
                Files.deleteIfExists(path);
            }
        }
    }
}
