package com.iflytek.skillhub.domain.software;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import java.util.Iterator;
import java.util.Map;

/**
 * JSON (de)serialization bridge between {@link SoftwarePolicyConfig} and the
 * stored revision JSON. Only typed keys of the current schema are accepted;
 * unknown keys are rejected instead of silently dropped.
 */
public final class SoftwarePolicyConfigJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SoftwarePolicyConfigJson() {
    }

    public static String toJson(SoftwarePolicyConfig config) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("schemaVersion", SoftwarePolicyConfig.SCHEMA_VERSION);
            node.put("maxPackageBytes", config.maxPackageBytes());
            node.put("totalQuotaBytes", config.totalQuotaBytes());
            node.put("maxActiveUploadsPerAdmin", config.maxActiveUploadsPerAdmin());
            node.put("chunkSizeBytes", config.chunkSizeBytes());
            node.put("uploadIdleTimeoutMinutes", config.uploadIdleTimeoutMinutes());
            node.put("failedTempRetentionHours", config.failedTempRetentionHours());
            node.put("cleanupScanIntervalMinutes", config.cleanupScanIntervalMinutes());
            node.put("maxConcurrentInspectionsPerWorker", config.maxConcurrentInspectionsPerWorker());
            node.put("inspectionTotalTimeoutSeconds", config.inspectionTotalTimeoutSeconds());
            node.put("inspectionSubprocessTimeoutSeconds", config.inspectionSubprocessTimeoutSeconds());
            node.put("inspectionSoftMemoryLimitBytes", config.inspectionSoftMemoryLimitBytes());
            node.put("inspectionMaxExtractedBytes", config.inspectionMaxExtractedBytes());
            node.put("inspectionMaxExtractedEntries", config.inspectionMaxExtractedEntries());
            node.put("maxAutoInspectionAttempts", config.maxAutoInspectionAttempts());
            node.put("downloadSessionIdleTimeoutMinutes", config.downloadSessionIdleTimeoutMinutes());
            return MAPPER.writeValueAsString(node);
        } catch (Exception ex) {
            throw new DomainBadRequestException("error.software.settings.corrupt");
        }
    }

    public static SoftwarePolicyConfig fromJson(String json) {
        JsonNode node = parse(json);
        requireSupportedSchema(node);
        try {
            return new SoftwarePolicyConfig(
                    requireLong(node, "maxPackageBytes"),
                    requireLong(node, "totalQuotaBytes"),
                    requireInt(node, "maxActiveUploadsPerAdmin"),
                    requireLong(node, "chunkSizeBytes"),
                    requireLong(node, "uploadIdleTimeoutMinutes"),
                    requireLong(node, "failedTempRetentionHours"),
                    requireLong(node, "cleanupScanIntervalMinutes"),
                    requireInt(node, "maxConcurrentInspectionsPerWorker"),
                    requireLong(node, "inspectionTotalTimeoutSeconds"),
                    requireLong(node, "inspectionSubprocessTimeoutSeconds"),
                    requireLong(node, "inspectionSoftMemoryLimitBytes"),
                    requireLong(node, "inspectionMaxExtractedBytes"),
                    requireInt(node, "inspectionMaxExtractedEntries"),
                    requireInt(node, "maxAutoInspectionAttempts"),
                    requireLong(node, "downloadSessionIdleTimeoutMinutes")
            );
        } catch (DomainBadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new DomainBadRequestException("error.software.settings.corrupt");
        }
    }

    /** Flattens a config JSON object into a field→value map for audit diffs. */
    public static Map<String, Object> toFieldMap(String json) {
        JsonNode node = parse(json);
        Map<String, Object> fields = new java.util.LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = iterator.next();
            JsonNode value = entry.getValue();
            if (value.isNumber()) {
                fields.put(entry.getKey(), value.numberValue());
            } else {
                fields.put(entry.getKey(), value.asText());
            }
        }
        return fields;
    }

    private static JsonNode parse(String json) {
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node == null || !node.isObject()) {
                throw new DomainBadRequestException("error.software.settings.corrupt");
            }
            return node;
        } catch (DomainBadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new DomainBadRequestException("error.software.settings.corrupt");
        }
    }

    private static void requireSupportedSchema(JsonNode node) {
        int schemaVersion = node.path("schemaVersion").asInt(0);
        if (schemaVersion != SoftwarePolicyConfig.SCHEMA_VERSION) {
            throw new DomainBadRequestException("error.software.settings.schemaUnsupported", schemaVersion);
        }
    }

    private static long requireLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToLong()) {
            throw new DomainBadRequestException("error.software.settings.fieldMissing", field);
        }
        return value.asLong();
    }

    private static int requireInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToInt()) {
            throw new DomainBadRequestException("error.software.settings.fieldMissing", field);
        }
        return value.asInt();
    }
}
