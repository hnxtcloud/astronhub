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
        Map<String, SoftwareReleaseAsset> byPath = assets.stream().collect(Collectors.toMap(SoftwareReleaseAsset::getRelativePath, Function.identity()));
        List<SoftwareReleaseAsset> metadata = assets.stream().filter(a -> "METADATA".equals(a.getRole())).toList();
        if (metadata.isEmpty() || assets.stream().noneMatch(a -> "INSTALLER".equals(a.getRole()))) fail("missing installer or metadata");
        for (var asset : metadata) {
            JsonNode document = parse(asset);
            if (!release.getMachineVersion().equals(document.path("version").asText())) fail("version");
            if ("ELECTRON".equals(release.getProtocol())) electron(document, asset, byPath);
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
    private JsonNode parse(SoftwareReleaseAsset asset) {
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
    private void electron(JsonNode doc, SoftwareReleaseAsset metadata, Map<String, SoftwareReleaseAsset> assets) {
        JsonNode files = doc.path("files");
        if (!files.isArray() || files.isEmpty()) fail("files");
        for (JsonNode file : files) {
            SoftwareReleaseAsset target = referenced(metadata, file.path("url").asText(), assets);
            if (!"INSTALLER".equals(target.getRole()) || !file.path("size").isIntegralNumber()
                    || target.getByteSize() != file.path("size").asLong()
                    || !target.getSha512Base64().equals(file.path("sha512").asText())) fail("files checksum/size");
            if (file.has("blockMapSize")) {
                // electron-builder uses this field for a blockmap embedded at the end of the
                // package, followed by a four-byte size word. It is not an external .blockmap size.
                JsonNode embedded = file.path("blockMapSize");
                if (!embedded.isIntegralNumber() || !embedded.canConvertToLong() || embedded.asLong() < 0
                        || embedded.asLong() > target.getByteSize() - 4) fail("embedded blockmap size");
            }
        }
        if (doc.has("path")) {
            var target = referenced(metadata, doc.path("path").asText(), assets);
            if (doc.has("sha512") && !target.getSha512Base64().equals(doc.path("sha512").asText())) fail("path checksum");
        }
    }
    private void baize(JsonNode doc, SoftwareReleaseAsset metadata, Map<String, SoftwareReleaseAsset> assets,
            SoftwareUpdateRelease release) {
        var target = referenced(metadata, doc.path("package").asText(), assets);
        if (doc.path("schemaVersion").asInt() != 1 || !"win32".equals(doc.path("platform").asText())
                || !"x64".equals(doc.path("arch").asText()) || !release.getChannel().equals(doc.path("channel").asText())
                || !release.getPiVersion().equals(doc.path("piVersion").asText())
                || !"INSTALLER".equals(target.getRole()) || !target.getRelativePath().endsWith(".zip")
                || !target.getSha256().equals(doc.path("sha256").asText())
                || !doc.path("bytes").isIntegralNumber() || target.getByteSize() != doc.path("bytes").asLong()) fail("manifest identity/checksum/bytes");
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
}
