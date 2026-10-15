package com.iflytek.skillhub.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iflytek.skillhub.domain.software.SoftwareUpdateRules;
import com.iflytek.skillhub.repository.SoftwareUpdateReadQueryRepository.AssetTarget;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

/** Projects only URL coordinates; stored native metadata and artifact digests remain unchanged. */
@Service
public class SoftwareNativeUpdateFeedService {
    private final SoftwareUpdateMetadataValidator metadata;
    private final ObjectMapper mapper;

    public SoftwareNativeUpdateFeedService(SoftwareUpdateMetadataValidator metadata, ObjectMapper mapper) {
        this.metadata = metadata;
        this.mapper = mapper;
    }

    public byte[] project(AssetTarget target, String application, String prefix) throws java.io.IOException {
        ObjectNode document = (ObjectNode) metadata.readDocument(target.asset()).deepCopy();
        String base = prefix + "/releases/" + encode(application) + "/" + target.release().getId() + "/assets/";
        if ("BAIZE".equals(target.release().getProtocol())) {
            document.put("package", reference(base, target, document.path("package").asText()));
            if (document.has("releasesUrl")) document.put("releasesUrl", reference(base, target, document.path("releasesUrl").asText()));
            return mapper.writeValueAsBytes(document);
        }
        for (var item : document.path("files")) {
            ((ObjectNode) item).put("url", reference(base, target, item.path("url").asText()));
        }
        if (document.has("path")) document.put("path", reference(base, target, document.path("path").asText()));
        Map<String, Object> data = mapper.convertValue(document, new TypeReference<>() { });
        return new Yaml().dump(data).getBytes(StandardCharsets.UTF_8);
    }

    private static String reference(String base, AssetTarget target, String reference) {
        // Publication validation has already restricted native links to local relative paths.
        String path = URI.create(reference.replace(" ", "%20")).getPath();
        String source = target.asset().getRelativePath();
        String parent = source.contains("/") ? source.substring(0, source.lastIndexOf('/') + 1) : "";
        SoftwareUpdateRules.path(parent + path);
        return base + encode(parent + path);
    }

    private static String encode(String path) {
        return Arrays.stream(path.split("/", -1)).map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8)
                .replace("+", "%20")).collect(Collectors.joining("/"));
    }
}
