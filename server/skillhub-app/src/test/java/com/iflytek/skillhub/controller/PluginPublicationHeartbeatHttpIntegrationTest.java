package com.iflytek.skillhub.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.entity.Role;
import com.iflytek.skillhub.auth.entity.UserRoleBinding;
import com.iflytek.skillhub.auth.local.LocalCredential;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.plugin.*;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.service.plugin.NpmRegistryPublisher;
import com.iflytek.skillhub.storage.ObjectStorageService;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * One nontransactional, real TCP/Tomcat + JPA contract: an outbound publication outlives its
 * initial lease, independently committed heartbeats retain ownership, and a concurrent HTTP retry
 * cannot claim or publish the same channel. Authentication uses a real local session/CSRF. Only the
 * external storage/publisher boundary is mocked; no product clock/token/lease is altered.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "skillhub.builtin-skills.enabled=false",
            "skillhub.plugin.hosted-worker-enabled=false",
            "skillhub.plugin.publication.lease-seconds=1",
            "skillhub.plugin.publication.heartbeat-millis=100",
            "skillhub.plugin.publication.allowed-registries[0]=http://127.0.0.1:19876/internal/",
            "skillhub.plugin.publication.credentials.fixture=heartbeat-fixture-only-secret"
        })
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PluginPublicationHeartbeatHttpIntegrationTest {
    private static final String REGISTRY = "http://127.0.0.1:19876/internal/";
    private static final String PASSWORD = "HeartbeatFixture!2026";
    private static final byte[] ARCHIVE = "approved native bytes".getBytes(StandardCharsets.UTF_8);

    @Autowired EntityManager entities;
    @Autowired ObjectMapper mapper;
    @Autowired PlatformTransactionManager transactions;
    @Autowired PasswordEncoder passwords;
    @MockBean ObjectStorageService storage;
    @MockBean NpmRegistryPublisher publisher;
    @Autowired com.iflytek.skillhub.auth.settings.LocalAuthSettingsService localSettings;
    @LocalServerPort int port;

    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient http =
            HttpClient.newBuilder()
                    .cookieHandler(cookies)
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
    private Fixture fixture;

    @Test
    void realHeartbeatExtendsLeaseAndFencesConcurrentHttpRetryUntilOwnedRevisionFinishes()
            throws Exception {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(ARCHIVE));
        fixture = seedCommittedFixture(sha);
        assertThat(localSettings.current().passwordLoginEnabled()).isTrue();
        when(storage.getObject(fixture.objectKey()))
                .thenAnswer(ignored -> new ByteArrayInputStream(ARCHIVE));
        var entered = new CountDownLatch(1);
        var finishExternalPublication = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var enteredNanos = new AtomicLong();
        var receipt =
                new NpmRegistryPublisher.Receipt(
                        REGISTRY, "sample", "1.0.0", sha, "sha512-fixture-proof", false);
        when(publisher.publish(any(), anyString(), any(), anyString()))
                .thenAnswer(
                        invocation -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .as("Outbound operation must not retain the claim transaction")
                                    .isFalse();
                            calls.incrementAndGet();
                            enteredNanos.compareAndSet(0L, System.nanoTime());
                            entered.countDown();
                            if (!finishExternalPublication.await(30, TimeUnit.SECONDS))
                                throw new IllegalStateException("FIXTURE_PUBLISH_RELEASE_TIMEOUT");
                            return receipt;
                        });
        when(publisher.reconcile(any(), anyString(), any(), anyString()))
                .thenReturn(Optional.empty());

        loginOverRealHttp();
        // Obtain a real non-zero revision through production preview/apply HTTP, not reflection.
        var request =
                mapper.createObjectNode()
                        .put("channel", "NPM")
                        .put("targetRevisionId", fixture.targetId())
                        .put("expectedRevision", 0)
                        .put("reason", "Select explicit target for heartbeat contract");
        var preview = post("/publication-replans/preview", request.toString());
        request.put("previewDigest", preview.path("digest").asText());
        post("/publication-replans", request.toString());
        assertThat(observe().revision()).isEqualTo(1);
        assertThat(observe().attempts()).isEmpty();

        CompletableFuture<HttpResponse<String>> first = null;
        CompletableFuture<HttpResponse<String>> second = null;
        try {
            first =
                    http.sendAsync(
                            writeRequest("/publish-channels", "{}"),
                            HttpResponse.BodyHandlers.ofString());
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            Observation initial =
                    awaitObservation(
                            "initial committed RUNNING claim",
                            value ->
                                    value.state().equals("RUNNING")
                                            && value.attempts().size() == 1);
            Attempt owned = initial.attempts().getFirst();
            assertThat(initial.token()).isNotBlank();
            assertThat(initial.revision()).isEqualTo(1);
            assertThat(initial.count()).isEqualTo(1);
            assertThat(owned.token()).isEqualTo(initial.token());
            assertThat(owned.revision()).isEqualTo(1);
            Instant originalExpiry = owned.startedAt().plusSeconds(1);

            // Poll committed JPA state, not Mockito calls or a sleep-only timing assumption.
            Observation extended =
                    awaitObservation(
                            "heartbeat is persisted after original expiry while publisher is"
                                    + " blocked",
                            value ->
                                    System.nanoTime() - enteredNanos.get()
                                                    >= Duration.ofMillis(1300).toNanos()
                                            && Instant.now().isAfter(originalExpiry.plusMillis(300))
                                            && value.state().equals("RUNNING")
                                            && value.attempts().size() == 1
                                            && initial.token().equals(value.token())
                                            && value.revision() == 1
                                            && value.leaseUntil() != null
                                            && value.leaseUntil().isAfter(Instant.now())
                                            && value.leaseUntil().isAfter(originalExpiry)
                                            && value.attempts()
                                                    .getFirst()
                                                    .heartbeatAt()
                                                    .isAfter(owned.startedAt())
                                            && value.attempts()
                                                    .getFirst()
                                                    .leaseUntil()
                                                    .isAfter(originalExpiry));
            assertThat(first.isDone()).isFalse();
            assertThat(extended.attempts().getFirst().outcome()).isEqualTo("RUNNING");
            var recovery = get("/publication-recovery").get(0);
            assertThat(recovery.path("state").asText()).isEqualTo("RUNNING");
            assertThat(recovery.path("planRevision").asInt()).isEqualTo(1);
            assertThat(recovery.path("attempts").size()).isEqualTo(1);
            assertThat(Instant.parse(recovery.path("attempts").get(0).path("heartbeatAt").asText()))
                    .isAfter(owned.startedAt());
            assertThat(Instant.parse(recovery.path("leaseUntil").asText())).isAfter(originalExpiry);

            // This request is issued over a second real TCP request after the initial lease
            // expired.
            // Without heartbeat fencing it either starts a second write or expires/replaces the
            // owner.
            second =
                    http.sendAsync(
                            writeRequest("/publish-channels", "{}"),
                            HttpResponse.BodyHandlers.ofString());
            JsonNode competing = data(second.get(5, TimeUnit.SECONDS));
            assertThat(competing.get(0).path("state").asText()).isEqualTo("RUNNING");
            Observation afterCompetingRequest = observe();
            assertThat(afterCompetingRequest.token()).isEqualTo(initial.token());
            assertThat(afterCompetingRequest.revision()).isEqualTo(1);
            assertThat(afterCompetingRequest.count()).isEqualTo(1);
            assertThat(afterCompetingRequest.attempts()).hasSize(1);
            assertThat(afterCompetingRequest.attempts().getFirst().token())
                    .isEqualTo(owned.token());
            assertThat(afterCompetingRequest.attempts().getFirst().outcome()).isEqualTo("RUNNING");
            assertThat(calls.get())
                    .isEqualTo(1); // Supplementary; committed lease/attempts above are primary.
            verify(publisher, never()).reconcile(any(), anyString(), any(), anyString());

            finishExternalPublication.countDown();
            assertThat(data(first.get(10, TimeUnit.SECONDS)).get(0).path("state").asText())
                    .isEqualTo("SUCCEEDED");
            Observation done =
                    awaitObservation(
                            "owned result committed", value -> value.state().equals("SUCCEEDED"));
            assertThat(done.token()).isEqualTo(initial.token());
            assertThat(done.revision()).isEqualTo(1);
            assertThat(done.leaseUntil()).isNull();
            assertThat(done.count()).isEqualTo(1);
            assertThat(done.attempts()).hasSize(1);
            Attempt completed = done.attempts().getFirst();
            assertThat(completed.token()).isEqualTo(owned.token());
            assertThat(completed.revision()).isEqualTo(1);
            assertThat(completed.outcome()).isEqualTo("SUCCEEDED");
            assertThat(completed.action()).isEqualTo("PUBLISH");
            assertThat(completed.finishedAt()).isNotNull();
            assertThat(completed.error()).isNull();
            assertThat(completed.lateReceipt()).isNull();
            assertThat(mapper.readTree(completed.receipt())).isEqualTo(mapper.valueToTree(receipt));
            assertThat(completed.receipt()).isEqualTo(done.receipt());
            assertThat(done.releaseStatus()).isEqualTo(PluginReleaseStatus.PUBLISHED);
            assertThat(done.taskStatus()).isEqualTo("SUCCEEDED");
            assertThat(done.latestRelease()).isEqualTo(fixture.releaseId());
            var finishedRecovery = get("/publication-recovery").get(0);
            assertThat(finishedRecovery.path("attempts").size()).isEqualTo(1);
            assertThat(finishedRecovery.path("attempts").get(0).path("planRevision").asInt())
                    .isEqualTo(1);
            assertThat(finishedRecovery.path("attempts").get(0).path("outcome").asText())
                    .isEqualTo("SUCCEEDED");
            assertThat(calls.get()).isEqualTo(1);
        } finally {
            finishExternalPublication.countDown();
            settle(first);
            settle(second);
        }
    }

    private Fixture seedCommittedFixture(String sha) {
        return new TransactionTemplate(transactions)
                .execute(
                        status -> {
                            String user = "pulse-" + UUID.randomUUID().toString().substring(0, 8);
                            entities.persist(
                                    new UserAccount(
                                            user,
                                            "Heartbeat Fixture",
                                            user + "@test.invalid",
                                            null));
                            entities.persist(
                                    new LocalCredential(user, user, passwords.encode(PASSWORD)));
                            // H2's JSONB domain requires FORMAT JSON for an object document;
                            // a plain SQL string/CAST produces a JSON string instead.
                            entities.createNativeQuery(
                                            "delete from system_setting where"
                                                + " setting_key='auth.local'")
                                    .executeUpdate();
                            entities.createNativeQuery(
                                            "insert into"
                                                + " system_setting(setting_key,value_json,version,created_at,updated_at)"
                                                + " values"
                                                + " ('auth.local','{\"passwordLoginEnabled\":true,\"selfRegistrationEnabled\":false}'"
                                                + " FORMAT"
                                                + " JSON,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
                                    .executeUpdate();
                            var roles =
                                    entities.createQuery(
                                                    "from Role r where r.code='PLUGIN_ADMIN'",
                                                    Role.class)
                                            .getResultList();
                            if (roles.isEmpty()) {
                                entities.createNativeQuery(
                                                "insert into role(code,name,is_system,created_at)"
                                                        + " values ('PLUGIN_ADMIN','Plugin"
                                                        + " Admin',true,CURRENT_TIMESTAMP)")
                                        .executeUpdate();
                                roles =
                                        entities.createQuery(
                                                        "from Role r where r.code='PLUGIN_ADMIN'",
                                                        Role.class)
                                                .getResultList();
                            }
                            entities.persist(new UserRoleBinding(user, roles.getFirst()));
                            var namespace = new Namespace(user, "Heartbeat Namespace", user);
                            entities.persist(namespace);
                            entities.flush();
                            entities.persist(
                                    new NamespaceMember(
                                            namespace.getId(), user, NamespaceRole.OWNER));
                            var project =
                                    new PluginProject(
                                            namespace.getId(),
                                            "sample",
                                            "Heartbeat Sample",
                                            user,
                                            PluginVisibility.PUBLIC);
                            entities.persist(project);
                            entities.flush();
                            var release = new PluginRelease(project.getId(), "1.0.0", user);
                            release.submit();
                            entities.persist(release);
                            entities.flush();
                            String key = "plugins/heartbeat/" + user + ".tgz";
                            entities.persist(
                                    PluginDistribution.hostedArchive(
                                            release.getId(),
                                            "pi",
                                            key,
                                            sha,
                                            "sample",
                                            "1.0.0",
                                            "{\"packaging\":\"ORIGINAL_NPM_TGZ\",\"files\":[]}",
                                            "{\"name\":\"sample\",\"version\":\"1.0.0\"}"));
                            var task =
                                    new PluginPublicationTask(
                                            release.getId(),
                                            user,
                                            "HOSTED,NPM",
                                            "PUBLIC\n1.0.0\npi:" + sha);
                            task.completeHosted();
                            entities.persist(task);
                            entities.flush();
                            var channel = new PluginPublicationChannel(task.getId(), "NPM", null);
                            entities.persist(channel);
                            var target =
                                    new PluginNpmRegistry(
                                            "Heartbeat Target", REGISTRY, "fixture", true, user);
                            entities.persist(target);
                            entities.flush();
                            return new Fixture(
                                    user,
                                    project.getId(),
                                    release.getId(),
                                    task.getId(),
                                    channel.getId(),
                                    target.getId(),
                                    key);
                        });
    }

    private Observation observe() {
        return new TransactionTemplate(transactions)
                .execute(
                        status -> {
                            entities.clear();
                            var channel =
                                    entities.find(
                                            PluginPublicationChannel.class, fixture.channelId());
                            var attempts =
                                    entities
                                            .createQuery(
                                                    "from PluginChannelAttempt a where"
                                                            + " a.channelId=:id order by a.id",
                                                    PluginChannelAttempt.class)
                                            .setParameter("id", fixture.channelId())
                                            .getResultList()
                                            .stream()
                                            .map(
                                                    value ->
                                                            new Attempt(
                                                                    value.getAttemptToken(),
                                                                    value.getPlanRevision(),
                                                                    value.getAction(),
                                                                    value.getOutcome(),
                                                                    value.getStartedAt(),
                                                                    value.getHeartbeatAt(),
                                                                    value.getLeaseUntil(),
                                                                    value.getFinishedAt(),
                                                                    value.getReceipt(),
                                                                    value.getErrorCode(),
                                                                    value.getLateReceipt()))
                                            .toList();
                            return new Observation(
                                    channel.getState(),
                                    channel.getAttemptToken(),
                                    channel.getPlanRevision(),
                                    channel.getAttemptCount(),
                                    channel.getLeaseUntil(),
                                    channel.getReceipt(),
                                    attempts,
                                    entities.find(PluginRelease.class, fixture.releaseId())
                                            .getStatus(),
                                    entities.find(PluginPublicationTask.class, fixture.taskId())
                                            .getStatus(),
                                    entities.find(PluginProject.class, fixture.projectId())
                                            .getLatestReleaseId());
                        });
    }

    private Observation awaitObservation(String expectation, Predicate<Observation> condition)
            throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        Observation last;
        do {
            last = observe();
            if (condition.test(last)) return last;
            Thread.sleep(25);
        } while (System.nanoTime() < deadline);
        throw new AssertionError(
                expectation
                        + "; last state="
                        + last.state()
                        + ", revision="
                        + last.revision()
                        + ", attemptCount="
                        + last.count());
    }

    private void loginOverRealHttp() throws Exception {
        var capabilities =
                http.send(
                        HttpRequest.newBuilder(uri("/api/v1/auth/local/capabilities"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(capabilities.statusCode()).isEqualTo(200);
        var login =
                mapper.writeValueAsString(Map.of("username", fixture.user(), "password", PASSWORD));
        var response =
                http.send(
                        HttpRequest.newBuilder(uri("/api/v1/auth/local/login"))
                                .header("Content-Type", "application/json")
                                .header("X-XSRF-TOKEN", csrf())
                                .POST(HttpRequest.BodyPublishers.ofString(login))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        http.send(
                HttpRequest.newBuilder(uri("/api/v1/auth/local/capabilities")).GET().build(),
                HttpResponse.BodyHandlers.discarding());
        var me =
                http.send(
                        HttpRequest.newBuilder(uri("/api/v1/auth/me")).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(me.statusCode()).isEqualTo(200);
    }

    private JsonNode post(String suffix, String body) throws Exception {
        return data(http.send(writeRequest(suffix, body), HttpResponse.BodyHandlers.ofString()));
    }

    private JsonNode get(String suffix) throws Exception {
        return data(
                http.send(
                        HttpRequest.newBuilder(uri(base() + suffix)).GET().build(),
                        HttpResponse.BodyHandlers.ofString()));
    }

    private HttpRequest writeRequest(String suffix, String body) {
        return HttpRequest.newBuilder(uri(base() + suffix))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", csrf())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private JsonNode data(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readTree(response.body()).path("data");
    }

    private String csrf() {
        return cookies.getCookieStore().getCookies().stream()
                .filter(
                        value ->
                                value.getName().equals("XSRF-TOKEN")
                                        && !value.hasExpired()
                                        && !value.getValue().isBlank())
                .reduce((left, right) -> right)
                .map(value -> URLDecoder.decode(value.getValue(), StandardCharsets.UTF_8))
                .orElseThrow(() -> new AssertionError("Expected real CSRF cookie"));
    }

    private String base() {
        return "/api/v1/plugin-management/releases/" + fixture.releaseId();
    }

    private URI uri(String value) {
        return URI.create("http://127.0.0.1:" + port + value);
    }

    private void settle(CompletableFuture<?> request) {
        if (request != null)
            try {
                request.get(5, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                request.cancel(true);
            }
    }

    private record Fixture(
            String user,
            Long projectId,
            Long releaseId,
            Long taskId,
            Long channelId,
            Long targetId,
            String objectKey) {}

    private record Attempt(
            String token,
            int revision,
            String action,
            String outcome,
            Instant startedAt,
            Instant heartbeatAt,
            Instant leaseUntil,
            Instant finishedAt,
            String receipt,
            String error,
            String lateReceipt) {}

    private record Observation(
            String state,
            String token,
            int revision,
            int count,
            Instant leaseUntil,
            String receipt,
            List<Attempt> attempts,
            PluginReleaseStatus releaseStatus,
            String taskStatus,
            Long latestRelease) {}
}
