package com.iflytek.skillhub.service.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.stereotype.Component;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** npm HTTP publication of exact TGZ bytes. No process, scripts, redirects, or public fallback. */
@Component
public class NpmRegistryPublisher {
    public static final long MAX_BYTES = 104857600L;
    private static final int MAX_METADATA = 4194304;
    private static final java.util.concurrent.ScheduledExecutorService BODY_DEADLINES =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
                    task -> {
                        Thread thread = new Thread(task, "npm-body-deadline");
                        thread.setDaemon(true);
                        return thread;
                    });
    private final ObjectMapper mapper;
    private final HttpClient http =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();

    public NpmRegistryPublisher(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public record Target(Long configId, String registry, String credentialRef) {}

    public record Receipt(
            String registry,
            String name,
            String version,
            String sha256,
            String integrity,
            boolean reused) {}

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

    public static String canonical(String value) {
        try {
            URI uri = URI.create(value);
            if (!Set.of("https", "http").contains(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null
                    || value.contains("%")
                    || !uri.normalize().equals(uri)) throw new IllegalArgumentException();
            return uri.toString().endsWith("/") ? uri.toString() : uri + "/";
        } catch (Exception ex) {
            throw new IllegalArgumentException("INVALID_REGISTRY");
        }
    }

    private URI endpoint(Target target, String suffix) {
        return URI.create(canonical(target.registry()) + suffix);
    }

    private static String encoded(String name) {
        return URLEncoder.encode(name, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }

    private HttpRequest.Builder request(URI uri, String token) {
        var builder =
                HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(30))
                        .header("Accept", "application/json");
        if (token != null && !token.isBlank()) builder.header("Authorization", "Bearer " + token);
        return builder;
    }

    private HttpResponse<InputStream> send(HttpRequest request) throws IOException {
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            // HttpClient's request timeout stops at headers with an InputStream body. Bound body
            // time too.
            var deadline =
                    BODY_DEADLINES.schedule(
                            () -> {
                                try {
                                    response.body().close();
                                } catch (IOException ignored) {
                                }
                            },
                            30,
                            java.util.concurrent.TimeUnit.SECONDS);
            return new HttpResponse<InputStream>() {
                final InputStream body =
                        new FilterInputStream(response.body()) {
                            @Override
                            public void close() throws IOException {
                                deadline.cancel(false);
                                super.close();
                            }
                        };

                public int statusCode() {
                    return response.statusCode();
                }

                public HttpRequest request() {
                    return response.request();
                }

                public Optional<HttpResponse<InputStream>> previousResponse() {
                    return Optional.empty();
                }

                public HttpHeaders headers() {
                    return response.headers();
                }

                public InputStream body() {
                    return body;
                }

                public Optional<javax.net.ssl.SSLSession> sslSession() {
                    return response.sslSession();
                }

                public URI uri() {
                    return response.uri();
                }

                public HttpClient.Version version() {
                    return response.version();
                }
            };
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new Failure("REGISTRY_INTERRUPTED");
        } catch (IOException ex) {
            throw new Failure("REGISTRY_UNAVAILABLE");
        }
    }

    private static void status(int status) throws Failure {
        if (status == 401 || status == 403) throw new Failure("REGISTRY_AUTHENTICATION");
        if (status >= 300 && status < 400) throw new Failure("REGISTRY_REDIRECT_REJECTED");
        if (status != 200) throw new Failure("REGISTRY_UNAVAILABLE");
    }

    private JsonNode metadata(Target target, String suffix, String token) throws IOException {
        var response = send(request(endpoint(target, suffix), token).GET().build());
        try (var input = response.body()) {
            if (response.statusCode() == 404) return null;
            status(response.statusCode());
            byte[] bytes = input.readNBytes(MAX_METADATA + 1);
            if (bytes.length > MAX_METADATA) throw new Failure("REGISTRY_METADATA_LIMIT");
            try {
                return mapper.readTree(bytes);
            } catch (Exception ex) {
                throw new Failure("REGISTRY_INVALID_METADATA");
            }
        }
    }

    public Receipt publish(Path archive, String manifestJson, Target target, String token)
            throws IOException {
        if (Files.size(archive) > MAX_BYTES) throw new Failure("PACKAGE_SIZE_LIMIT");
        JsonNode manifest = mapper.readTree(manifestJson);
        String name = manifest.path("name").asText(), version = manifest.path("version").asText();
        if (!name.matches("(?:@[a-z0-9._-]+/)?[a-z0-9][a-z0-9._-]*")
                || name.length() > 200
                || !com.iflytek.skillhub.domain.plugin.PluginVersions.valid(version))
            throw new Failure("PACKAGE_IDENTITY");
        String sha256 = digest(archive, "SHA-256", false),
                integrity = "sha512-" + digest(archive, "SHA-512", true);
        JsonNode existing = metadata(target, encoded(name) + "/" + encoded(version), token);
        if (existing != null) {
            compare(existing, archive, target, token);
            return new Receipt(target.registry(), name, version, sha256, integrity, true);
        }
        Path payload = Files.createTempFile("astronhub-npm-publish-", ".json");
        try {
            ObjectNode clean = ((ObjectNode) manifest).deepCopy();
            // Target selection comes exclusively from the immutable plan, never package
            // configuration.
            clean.remove(List.of("publishConfig", "_attachments", "_rev", "_id", "dist"));
            String filename = name.substring(name.lastIndexOf('/') + 1) + "-" + version + ".tgz";
            String tarball =
                    canonical(target.registry()) + encoded(name) + "/-/" + encoded(filename);
            ObjectNode dist = clean.putObject("dist");
            dist.put("tarball", tarball);
            dist.put("shasum", digest(archive, "SHA-1", false));
            dist.put("integrity", integrity);
            try (var out = Files.newOutputStream(payload);
                    var json = mapper.getFactory().createGenerator(out);
                    var input = Files.newInputStream(archive)) {
                json.writeStartObject();
                json.writeStringField("_id", name);
                json.writeStringField("name", name);
                json.writeObjectFieldStart("dist-tags");
                json.writeStringField("latest", version);
                json.writeEndObject();
                json.writeObjectFieldStart("versions");
                json.writeFieldName(version);
                json.writeTree(clean);
                json.writeEndObject();
                json.writeObjectFieldStart("_attachments");
                json.writeObjectFieldStart(filename);
                json.writeStringField("content_type", "application/octet-stream");
                json.writeNumberField("length", Files.size(archive));
                json.writeFieldName("data");
                json.writeBinary(input, (int) Files.size(archive));
                json.writeEndObject();
                json.writeEndObject();
                json.writeEndObject();
            }
            String writeFailure = null;
            try {
                var response =
                        send(
                                request(endpoint(target, encoded(name)), token)
                                        .header("Content-Type", "application/json")
                                        .PUT(HttpRequest.BodyPublishers.ofFile(payload))
                                        .build());
                try (var input = response.body()) {
                    int code = response.statusCode();
                    if (code == 401 || code == 403) throw new Failure("REGISTRY_AUTHENTICATION");
                    if (code >= 300 && code < 400) throw new Failure("REGISTRY_REDIRECT_REJECTED");
                    if (code != 200 && code != 201 && code != 409)
                        writeFailure = "REGISTRY_WRITE_UNCERTAIN";
                }
            } catch (Failure ex) {
                if (ex.code().equals("REGISTRY_AUTHENTICATION")
                        || ex.code().equals("REGISTRY_REDIRECT_REJECTED")) throw ex;
                writeFailure = "REGISTRY_WRITE_UNCERTAIN";
            }
            // Even a 2xx receipt is not accepted without checking actual stored bytes.
            existing = metadata(target, encoded(name) + "/" + encoded(version), token);
            if (existing == null)
                throw new Failure(
                        writeFailure == null ? "REGISTRY_WRITE_UNCONFIRMED" : writeFailure);
            compare(existing, archive, target, token);
            return new Receipt(target.registry(), name, version, sha256, integrity, false);
        } finally {
            Files.deleteIfExists(payload);
        }
    }

    private void compare(JsonNode metadata, Path archive, Target target, String token)
            throws IOException {
        URI base = URI.create(canonical(target.registry())), remote;
        try {
            remote = URI.create(metadata.path("dist").path("tarball").asText());
        } catch (Exception ex) {
            throw new Failure("REGISTRY_TARBALL_TARGET");
        }
        if (!Objects.equals(base.getScheme(), remote.getScheme())
                || !Objects.equals(base.getHost(), remote.getHost())
                || base.getPort() != remote.getPort()
                || remote.getUserInfo() != null
                || remote.getRawQuery() != null
                || remote.getRawFragment() != null
                || !remote.normalize().equals(remote)
                || (remote.getRawPath().toLowerCase(Locale.ROOT).contains("%2e")
                        || remote.getRawPath().toLowerCase(Locale.ROOT).contains("%25")
                        || remote.getPath().contains("\\")
                        || remote.getPath().contains("/../")
                        || remote.getPath().contains("/./"))
                || !remote.getPath().startsWith(base.getPath()))
            throw new Failure("REGISTRY_TARBALL_TARGET");
        var response = send(request(remote, token).GET().build());
        try (var input = response.body();
                var original = Files.newInputStream(archive)) {
            status(response.statusCode());
            long size = 0;
            byte[] left = new byte[8192], right = new byte[8192];
            int n;
            while ((n = input.read(left)) != -1) {
                size += n;
                if (size > MAX_BYTES) throw new Failure("REGISTRY_TARBALL_LIMIT");
                if (original.readNBytes(right, 0, n) != n
                        || !Arrays.equals(left, 0, n, right, 0, n))
                    throw new Failure("REGISTRY_VERSION_CONFLICT");
            }
            if (original.read() != -1) throw new Failure("REGISTRY_VERSION_CONFLICT");
        }
    }

    public List<Dependency> dependencies(String manifestJson, Target target, String token)
            throws IOException {
        JsonNode manifest = mapper.readTree(manifestJson);
        List<Dependency> result = new ArrayList<>();
        for (String kind : List.of("dependencies", "optionalDependencies", "peerDependencies")) {
            var fields = manifest.path(kind).fields();
            while (fields.hasNext()) {
                var field = fields.next();
                if (result.size() >= 100) throw new Failure("DEPENDENCY_COUNT_LIMIT");
                String name = field.getKey(), version = field.getValue().asText();
                String availability = "UNRESOLVED_RANGE";
                if (!name.matches("(?:@[a-z0-9._-]+/)?[a-z0-9][a-z0-9._-]*"))
                    availability = "UNSUPPORTED_SOURCE";
                else if (version.matches("[0-9]+[.][0-9]+[.][0-9]+(?:-[a-zA-Z0-9.-]+)?"))
                    availability =
                            metadata(target, encoded(name) + "/" + encoded(version), token) == null
                                    ? "MISSING"
                                    : "AVAILABLE";
                else if (version.contains(":")
                        || version.startsWith(".")
                        || version.startsWith("/")) availability = "UNSUPPORTED_SOURCE";
                result.add(new Dependency(name, version, kind, availability));
            }
        }
        return result;
    }

    public record Dependency(String name, String range, String kind, String availability) {}

    private static String digest(Path path, String algorithm, boolean base64) throws IOException {
        try {
            MessageDigest sha = MessageDigest.getInstance(algorithm);
            try (var input = Files.newInputStream(path)) {
                byte[] b = new byte[8192];
                int n;
                while ((n = input.read(b)) != -1) sha.update(b, 0, n);
            }
            return base64
                    ? Base64.getEncoder().encodeToString(sha.digest())
                    : HexFormat.of().formatHex(sha.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
