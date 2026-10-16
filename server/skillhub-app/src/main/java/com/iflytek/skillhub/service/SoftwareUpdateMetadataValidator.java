package com.iflytek.skillhub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.software.SoftwareReleaseAsset;
import com.iflytek.skillhub.domain.software.SoftwareUpdateRelease;
import com.iflytek.skillhub.domain.software.SoftwareUpdateRules;
import com.iflytek.skillhub.storage.software.SoftwareTransferStorage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Safe bounded native-metadata parsing; original bytes remain unchanged. */
@Service
public class SoftwareUpdateMetadataValidator {
    private static final long MAX_METADATA = 2L * 1024 * 1024;
    private final SoftwareTransferStorage storage;
    private final ObjectMapper mapper;
    public SoftwareUpdateMetadataValidator(SoftwareTransferStorage storage, ObjectMapper mapper) {
        this.storage = storage; this.mapper = mapper;
    }
    public void validate(SoftwareUpdateRelease release, List<SoftwareReleaseAsset> assets) {
        int versionLimit = "BAIZE".equals(release.getProtocol()) ? 100 : 255;
        comparableVersion(release.getMachineVersion(), versionLimit);
        if ("BAIZE".equals(release.getProtocol())) comparableVersion(release.getPiVersion(), versionLimit);
        Map<String, SoftwareReleaseAsset> byPath = assets.stream().collect(Collectors.toMap(SoftwareReleaseAsset::getRelativePath, Function.identity()));
        List<SoftwareReleaseAsset> metadata = assets.stream().filter(a -> "METADATA".equals(a.getRole())).toList();
        if (metadata.isEmpty() || assets.stream().noneMatch(a -> "INSTALLER".equals(a.getRole()))) fail("missing installer or metadata");
        for (var asset : metadata) {
            JsonNode document = readDocument(asset);
            if (document == null || !document.isObject()) fail("metadata must be an object");
            if (!release.getMachineVersion().equals(document.path("version").asText())) fail("version");
            if ("ELECTRON".equals(release.getProtocol())) electron(document, asset, byPath, release);
            else baize(document, asset, byPath, release);
        }
        for (var asset : assets) if ("CHECKSUM".equals(asset.getRole())) checksum(asset, byPath);
    }
    private void checksum(SoftwareReleaseAsset checksum, Map<String, SoftwareReleaseAsset> assets) {
        String text = new String(content(checksum), StandardCharsets.UTF_8).strip();
        if (text.matches("[A-Fa-f0-9]{64}|[A-Fa-f0-9]{128}")) {
            String path = checksum.getRelativePath();
            String suffix = text.length() == 64 ? ".sha256" : ".sha512";
            if (!path.endsWith(suffix)) fail("bare checksum requires companion .sha256/.sha512 file");
            var target = assets.get(path.substring(0, path.length() - suffix.length()));
            if (target == null) fail("missing checksum target");
            requireChecksum(text, target);
            return;
        }
        if (text.isEmpty()) fail("empty checksum file");
        for (String line : text.lines().toList()) {
            if (line.isBlank()) continue;
            var match = java.util.regex.Pattern.compile("^([A-Fa-f0-9]{64}|[A-Fa-f0-9]{128})[ \\t]+\\*?(.+)$").matcher(line);
            if (!match.matches()) fail("unsupported checksum format");
            requireChecksum(match.group(1), referenced(checksum, match.group(2), assets));
        }
    }
    private void requireChecksum(String declared, SoftwareReleaseAsset target) {
        String actual = declared.length() == 64 ? target.getSha256()
                : java.util.HexFormat.of().formatHex(java.util.Base64.getDecoder().decode(target.getSha512Base64()));
        if (!actual.equalsIgnoreCase(declared)) fail("checksum file does not match stored bytes");
    }
    private byte[] content(SoftwareReleaseAsset asset) {
        if (asset.getByteSize() > MAX_METADATA) fail("metadata/checksum exceeds 2 MiB");
        try (var input = storage.readRange(asset.getObjectKey(), 0, asset.getByteSize())) {
            byte[] result = input.readNBytes(Math.toIntExact(asset.getByteSize()) + 1);
            if (result.length != asset.getByteSize()) fail("metadata/checksum length");
            return result;
        } catch (IOException ex) { throw new DomainBadRequestException("error.software.update.metadata", "metadata/checksum unavailable"); }
    }
    public JsonNode readDocument(SoftwareReleaseAsset asset) {
        if (asset.getByteSize() > MAX_METADATA) fail("metadata exceeds 2 MiB");
        try (var input = storage.readRange(asset.getObjectKey(), 0, asset.getByteSize())) {
            byte[] content = input.readNBytes(Math.toIntExact(asset.getByteSize()) + 1);
            if (content.length != asset.getByteSize()) fail("metadata length");
            if (asset.getRelativePath().endsWith(".json")) return mapper.readTree(content);
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            options.setMaxAliasesForCollections(0);
            options.setNestingDepthLimit(32);
            options.setCodePointLimit((int) MAX_METADATA);
            return mapper.valueToTree(new Yaml(new SafeConstructor(options)).load(new String(content, StandardCharsets.UTF_8)));
        } catch (IOException | org.yaml.snakeyaml.error.YAMLException ex) {
            throw new DomainBadRequestException("error.software.update.metadata", "malformed metadata");
        }
    }
    private void electron(JsonNode doc, SoftwareReleaseAsset metadata, Map<String, SoftwareReleaseAsset> assets,
            SoftwareUpdateRelease release) {
        // NSIS-web packages use an additional installer graph. Do not publish references
        // until that protocol has its own complete validator and supported package format.
        if (doc.has("packages")) fail("web-installer packages are not supported; upload a full installer");
        JsonNode files = doc.path("files");
        if (!files.isArray() || files.isEmpty()) fail("files");
        java.util.Set<String> installerPaths = new java.util.HashSet<>();
        for (JsonNode file : files) {
            SoftwareReleaseAsset target = referenced(metadata, file.path("url").asText(), assets);
            if (!"INSTALLER".equals(target.getRole()) || !file.path("size").isIntegralNumber()
                    || !file.path("size").canConvertToLong()
                    || target.getByteSize() != file.path("size").asLong()
                    || !target.getSha512Base64().equals(file.path("sha512").asText())) fail("files checksum/size");
            installerPaths.add(target.getRelativePath());
            if (file.has("blockMapSize")) {
                // electron-builder uses this field for a blockmap embedded at the end of the
                // package, followed by a four-byte size word. It is not an external .blockmap size.
                JsonNode embedded = file.path("blockMapSize");
                if (!embedded.isIntegralNumber() || !embedded.canConvertToLong() || embedded.asLong() < 0
                        || embedded.asLong() > target.getByteSize() - 4) fail("embedded blockmap size");
            }
        }
        String requiredExtension = switch (release.getTargetOs()) {
            case "MACOS" -> ".zip";
            case "WINDOWS" -> ".exe";
            default -> throw new DomainBadRequestException("error.software.update.metadata", "unsupported Electron target; Linux native updater formats are not supported");
        };
        if (installerPaths.stream().noneMatch(path -> path.toLowerCase(java.util.Locale.ROOT).endsWith(requiredExtension)))
            fail("native updater requires a referenced " + requiredExtension + " installer");
        if (doc.has("path")) {
            var target = referenced(metadata, doc.path("path").asText(), assets);
            if (!"INSTALLER".equals(target.getRole()) || !installerPaths.contains(target.getRelativePath())) fail("path must match a files installer");
            if (doc.has("sha512") && !target.getSha512Base64().equals(doc.path("sha512").asText())) fail("path checksum");
        }
    }
    private void baize(JsonNode doc, SoftwareReleaseAsset metadata, Map<String, SoftwareReleaseAsset> assets,
            SoftwareUpdateRelease release) {
        var target = referenced(metadata, doc.path("package").asText(), assets);
        if (!doc.path("schemaVersion").isIntegralNumber() || doc.path("schemaVersion").asInt() != 1 || !"win32".equals(doc.path("platform").asText())
                || !"x64".equals(doc.path("arch").asText()) || !release.getChannel().equals(doc.path("channel").asText())
                || !release.getPiVersion().equals(doc.path("piVersion").asText())
                || !"INSTALLER".equals(target.getRole()) || !target.getRelativePath().endsWith(".zip")
                || !target.getSha256().equals(doc.path("sha256").asText())
                || !doc.path("bytes").isIntegralNumber() || !doc.path("bytes").canConvertToLong()
                || target.getByteSize() != doc.path("bytes").asLong()) fail("manifest identity/checksum/bytes");
        try { java.time.Instant.parse(doc.path("publishedAt").asText()); }
        catch (java.time.format.DateTimeParseException ex) { fail("publishedAt must be an ISO instant"); }
        if (doc.has("releasesUrl")) {
            referenced(metadata, doc.path("releasesUrl").asText(), assets);
        }
    }
    private SoftwareReleaseAsset referenced(SoftwareReleaseAsset metadata, String reference, Map<String, SoftwareReleaseAsset> assets) {
        reference = localReference(reference);
        SoftwareUpdateRules.path(reference);
        String parent = metadata.getRelativePath().contains("/")
                ? metadata.getRelativePath().substring(0, metadata.getRelativePath().lastIndexOf('/') + 1) : "";
        var target = assets.get(parent + reference);
        if (target == null) fail("missing referenced asset: " + parent + reference);
        return target;
    }
    /** electron-updater resolves files[].url as a URL, so encoded spaces represent actual file names. */
    private String localReference(String reference) {
        try {
            var uri = java.net.URI.create(reference.replace(" ", "%20"));
            if (uri.isAbsolute() || uri.getRawAuthority() != null || uri.getRawQuery() != null || uri.getRawFragment() != null)
                fail("local asset reference required");
            String raw = uri.getRawPath();
            var bytes = new java.io.ByteArrayOutputStream();
            for (int index = 0; index < raw.length();) {
                if (raw.charAt(index) == '%') {
                    int value = Integer.parseInt(raw.substring(index + 1, index + 3), 16);
                    // Encoded separators create aliases; only encode individual file name characters.
                    if (value == '/' || value == '\\') fail("encoded path separator");
                    bytes.write(value);
                    index += 3;
                } else {
                    int codePoint = raw.codePointAt(index);
                    bytes.writeBytes(new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8));
                    index += Character.charCount(codePoint);
                }
            }
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes.toByteArray())).toString();
        } catch (IllegalArgumentException | java.nio.charset.CharacterCodingException ex) {
            throw new DomainBadRequestException("error.software.update.metadata", "invalid local asset reference");
        }
    }
    private static void fail(String detail) { throw new DomainBadRequestException("error.software.update.metadata", detail); }

    private static void comparableVersion(String version, int lengthLimit) {
        SoftwareUpdateRules.version(version);
        if (version.length() > lengthLimit) fail("native client version length exceeds " + lengthLimit);
        String[] identity = version.split("\\+", 2)[0].split("-", 2);
        var numbers = new java.util.ArrayList<>(List.of(identity[0].split("\\.")));
        if (identity.length == 2) {
            for (String part : identity[1].split("\\.")) if (part.matches("[0-9]+")) numbers.add(part);
        }
        var maximum = java.math.BigInteger.valueOf(9_007_199_254_740_991L);
        for (String component : numbers) {
            if (new java.math.BigInteger(component).compareTo(maximum) > 0)
                fail("version component exceeds the native JavaScript client's safe integer range");
        }
    }
}
