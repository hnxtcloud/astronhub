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
                SoftwareReleaseAsset blockmap = assets.get(target.getRelativePath() + ".blockmap");
                if (blockmap == null || !"BLOCKMAP".equals(blockmap.getRole())
                        || blockmap.getByteSize() != file.path("blockMapSize").asLong()) fail("blockmap");
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
        SoftwareUpdateRules.path(reference);
        String parent = metadata.getRelativePath().contains("/")
                ? metadata.getRelativePath().substring(0, metadata.getRelativePath().lastIndexOf('/') + 1) : "";
        var target = assets.get(parent + reference);
        if (target == null) fail("missing referenced asset: " + parent + reference);
        return target;
    }
    private static void fail(String detail) { throw new DomainBadRequestException("error.software.update.metadata", detail); }
}
