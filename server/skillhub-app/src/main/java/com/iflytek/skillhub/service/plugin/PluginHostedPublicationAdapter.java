package com.iflytek.skillhub.service.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.plugin.*;
import com.iflytek.skillhub.storage.ObjectStorageService;

import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.util.*;

/** Verifies fixed hosted archive evidence outside any transaction or database lock. */
@Component
public class PluginHostedPublicationAdapter implements PluginPublicationChannelAdapter {
    private final ObjectStorageService storage;
    private final ObjectMapper mapper;

    public PluginHostedPublicationAdapter(ObjectStorageService storage, ObjectMapper mapper) {
        this.storage = storage;
        this.mapper = mapper;
    }

    public record Archive(String key, String sha256) {}

    public record Snapshot(List<Archive> archives) {}

    public String channel() {
        return "HOSTED";
    }

    public String snapshot(
            PluginProject p, PluginRelease r, List<PluginDistribution> distributions) {
        try {
            return mapper.writeValueAsString(
                    new Snapshot(
                            distributions.stream()
                                    .map(
                                            d ->
                                                    new Archive(
                                                            d.getSourceLocator(),
                                                            d.getIntegrityDigest()))
                                    .toList()));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    public String publish(PluginChannelContext context) throws Exception {
        var plan = mapper.readValue(context.snapshot(), Snapshot.class);
        if (plan.archives().isEmpty()) throw new IllegalStateException("HOSTED_ARCHIVE_MISSING");
        for (var archive : plan.archives()) {
            var sha = MessageDigest.getInstance("SHA-256");
            long total = 0;
            try (var input = storage.getObject(archive.key())) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > 2147483648L) throw new IllegalStateException("HOSTED_ARCHIVE_SIZE");
                    sha.update(buffer, 0, read);
                }
            }
            if (!HexFormat.of().formatHex(sha.digest()).equals(archive.sha256()))
                throw new IllegalStateException("HOSTED_ARCHIVE_DIGEST");
        }
        return "{\"verified\":true}";
    }

    public Inspection reconcile(PluginChannelContext context) throws Exception {
        return new Inspection("PRESENT", publish(context), null);
    }
}
