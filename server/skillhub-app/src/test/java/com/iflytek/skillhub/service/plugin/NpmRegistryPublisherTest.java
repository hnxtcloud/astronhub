package com.iflytek.skillhub.service.plugin;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

class NpmRegistryPublisherTest {
    @Test
    void readOnlyReconciliationDistinguishesAbsentAndExactRemoteBytesWithoutPublishing()
            throws Exception {
        Path archive = Files.createTempFile("npm-reconcile-", ".tgz");
        Files.writeString(archive, "approved bytes");
        var present = new java.util.concurrent.atomic.AtomicBoolean();
        var writes = new java.util.concurrent.atomic.AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/internal/";
        server.createContext(
                "/internal/",
                exchange -> {
                    if (!exchange.getRequestMethod().equals("GET")) writes.incrementAndGet();
                    if (!present.get()) {
                        exchange.sendResponseHeaders(404, -1);
                    } else {
                        byte[] data =
                                exchange.getRequestURI().getPath().endsWith(".tgz")
                                        ? "approved bytes".getBytes()
                                        : ("{\"dist\":{\"tarball\":\""
                                                        + url
                                                        + "pkg/-/pkg-1.0.0.tgz\"}}")
                                                .getBytes();
                        exchange.sendResponseHeaders(200, data.length);
                        exchange.getResponseBody().write(data);
                    }
                    exchange.close();
                });
        server.start();
        try {
            var publisher = new NpmRegistryPublisher(new ObjectMapper());
            var target = new NpmRegistryPublisher.Target(1L, url, "fixture");
            assertThat(
                            publisher.reconcile(
                                    archive,
                                    "{\"name\":\"pkg\",\"version\":\"1.0.0\"}",
                                    target,
                                    "fixture-token"))
                    .isEmpty();
            present.set(true);
            assertThat(
                            publisher
                                    .reconcile(
                                            archive,
                                            "{\"name\":\"pkg\",\"version\":\"1.0.0\"}",
                                            target,
                                            "fixture-token")
                                    .orElseThrow()
                                    .reused())
                    .isTrue();
            assertThat(writes).hasValue(0);
        } finally {
            server.stop(0);
            Files.deleteIfExists(archive);
        }
    }

    @Test
    void existingExactVersionIsReusedOnlyAfterComparingStoredBytes() throws Exception {
        Path archive = Files.createTempFile("npm-test-", ".tgz");
        Files.writeString(archive, "original tgz bytes");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/registry/";
        server.createContext(
                "/registry/",
                exchange -> {
                    byte[] bytes =
                            exchange.getRequestURI().getPath().endsWith(".tgz")
                                    ? "original tgz bytes".getBytes()
                                    : ("{\"dist\":{\"tarball\":\""
                                                    + url
                                                    + "pkg/-/pkg-1.0.0.tgz\"}}")
                                            .getBytes();
                    assertThat(exchange.getRequestMethod()).isEqualTo("GET");
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                    exchange.close();
                });
        server.start();
        try {
            var publisher = new NpmRegistryPublisher(new ObjectMapper());
            var result =
                    publisher.publish(
                            archive,
                            "{\"name\":\"@team/pkg\",\"version\":\"1.0.0\"}",
                            new NpmRegistryPublisher.Target(1L, url, "publisher"),
                            "test-token");
            assertThat(result.reused()).isTrue();
            assertThat(result.name()).isEqualTo("@team/pkg");
        } finally {
            server.stop(0);
            Files.deleteIfExists(archive);
        }
    }

    @Test
    void uncertainPutIsReconciledAndPackagePublishConfigCannotChangeDestination() throws Exception {
        Path archive = Files.createTempFile("npm-test-", ".tgz");
        Files.writeString(archive, "original tgz bytes");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/prefix/";
        var stored = new java.util.concurrent.atomic.AtomicBoolean(false);
        var writes = new java.util.concurrent.atomic.AtomicInteger();
        server.createContext(
                "/prefix/",
                exchange -> {
                    if (exchange.getRequestMethod().equals("PUT")) {
                        JsonNode payload = new ObjectMapper().readTree(exchange.getRequestBody());
                        assertThat(payload.path("versions").path("1.0.0").has("publishConfig"))
                                .isFalse();
                        assertThat(
                                        payload.path("_attachments")
                                                .elements()
                                                .next()
                                                .path("data")
                                                .asText())
                                .isEqualTo("b3JpZ2luYWwgdGd6IGJ5dGVz");
                        writes.incrementAndGet();
                        stored.set(true);
                        exchange.close();
                        return;
                    }
                    if (!stored.get()) {
                        exchange.sendResponseHeaders(404, -1);
                        exchange.close();
                        return;
                    }
                    byte[] bytes =
                            exchange.getRequestURI().getPath().endsWith(".tgz")
                                    ? "original tgz bytes".getBytes()
                                    : ("{\"dist\":{\"tarball\":\""
                                                    + url
                                                    + "pkg/-/pkg-1.0.0.tgz\"}}")
                                            .getBytes();
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                    exchange.close();
                });
        server.start();
        try {
            var result =
                    new NpmRegistryPublisher(new ObjectMapper())
                            .publish(
                                    archive,
                                    "{\"name\":\"pkg\",\"version\":\"1.0.0\",\"publishConfig\":{\"registry\":\"https://registry.npmjs.org\"}}",
                                    new NpmRegistryPublisher.Target(1L, url, "publisher"),
                                    "test-token");
            assertThat(result.reused()).isFalse();
            assertThat(writes.get()).isEqualTo(1);
        } finally {
            server.stop(0);
            Files.deleteIfExists(archive);
        }
    }

    @Test
    void registryCannotSendPublisherCredentialToAnotherOrigin() throws Exception {
        Path archive = Files.createTempFile("npm-test-", ".tgz");
        Files.writeString(archive, "original tgz bytes");
        HttpServer other = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var leaks = new java.util.concurrent.atomic.AtomicInteger();
        other.createContext(
                "/",
                exchange -> {
                    leaks.incrementAndGet();
                    exchange.sendResponseHeaders(200, -1);
                    exchange.close();
                });
        other.start();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/",
                exchange -> {
                    byte[] bytes =
                            ("{\"dist\":{\"tarball\":\"http://127.0.0.1:"
                                            + other.getAddress().getPort()
                                            + "/evil.tgz\"}}")
                                    .getBytes();
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                    exchange.close();
                });
        server.start();
        try {
            assertThatThrownBy(
                            () ->
                                    new NpmRegistryPublisher(new ObjectMapper())
                                            .publish(
                                                    archive,
                                                    "{\"name\":\"pkg\",\"version\":\"1.0.0\"}",
                                                    new NpmRegistryPublisher.Target(
                                                            1L,
                                                            "http://127.0.0.1:"
                                                                    + server.getAddress().getPort()
                                                                    + "/",
                                                            "publisher"),
                                                    "test-token"))
                    .hasMessage("REGISTRY_TARBALL_TARGET");
            assertThat(leaks.get()).isZero();
        } finally {
            server.stop(0);
            other.stop(0);
            Files.deleteIfExists(archive);
        }
    }
}
