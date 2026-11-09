package com.iflytek.skillhub.service.publication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.plugin.PluginReleaseStatus;
import com.iflytek.skillhub.domain.plugin.service.PluginActor;
import com.iflytek.skillhub.domain.publication.PublicationTask;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.dto.publication.PublicationDraft;
import com.iflytek.skillhub.repository.PluginManagementQueryRepository;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.plugin.PluginManagementAppService;
import com.iflytek.skillhub.service.plugin.PluginOfflineImportAppService;
import java.util.HashSet;
import org.springframework.stereotype.Service;

@Service
public class PluginPublicationWorkflow implements PublicationWorkflow {
    private final PublicationMaterialService materials;
    private final PluginOfflineImportAppService imports;
    private final PluginPublicationImportService importer;
    private final PluginManagementAppService publisher;
    private final PluginManagementQueryRepository queries;
    private final NamespaceRepository namespaces;
    private final ObjectMapper mapper;
    private final com.iflytek.skillhub.service.plugin.PluginNpmManagementAppService npm;
    public PluginPublicationWorkflow(PublicationMaterialService materials, PluginOfflineImportAppService imports,
            PluginPublicationImportService importer, PluginManagementAppService publisher,
            PluginManagementQueryRepository queries, NamespaceRepository namespaces, ObjectMapper mapper,
            com.iflytek.skillhub.service.plugin.PluginNpmManagementAppService npm) {
        this.materials = materials; this.imports = imports; this.importer = importer;
        this.publisher = publisher; this.queries = queries; this.namespaces = namespaces; this.mapper = mapper; this.npm = npm;
    }
    public String type() { return "PLUGIN"; }
    public void preview(PublicationTask task, PublicationDraft draft, ObjectNode progress,
            PluginActor actor, AuditRequestContext audit) throws java.io.IOException {
        var namespace = namespaces.findBySlug(draft.namespace()).orElseThrow(() -> new DomainBadRequestException("error.plugin.namespaceNotFound"));
        String lower = task.getFileName().toLowerCase(java.util.Locale.ROOT);
        try (var input = materials.stored(task.getMaterialKey(), task.getFileName(), task.getByteSize()).getInputStream()) {
            var preview = imports.preview(namespace.getId(), draft.repository(), lower.endsWith(".zip") ? "ZIP" : "TGZ", input, actor, audit);
            if (progress.path("revalidateMaterial").asBoolean()) {
                var oldPreview = progress.path("priorPreview");
                var retained = mapper.createArrayNode();
                var selections = mapper.createArrayNode();
                for (var row : progress.path("results")) {
                    if (!row.hasNonNull("resourceId") || row.path("status").asText().equals("FAILED")) continue;
                    var old = java.util.stream.StreamSupport.stream(oldPreview.path("candidates").spliterator(), false)
                            .filter(candidate -> candidate.path("id").asText().equals(row.path("candidateId").asText())).findFirst()
                            .orElseThrow(() -> new com.iflytek.skillhub.domain.shared.exception.DomainConflictException("error.publication.bound"));
                    var replacement = preview.candidates().stream().filter(candidate ->
                            java.util.Objects.equals(candidate.sha256(), old.path("sha256").asText())
                            && java.util.Objects.equals(candidate.runtime(), old.path("runtime").asText())
                            && java.util.Objects.equals(candidate.version(), old.path("version").asText())).findFirst()
                            .orElseThrow(() -> new com.iflytek.skillhub.domain.shared.exception.DomainConflictException("error.publication.bound"));
                    var receipt = (ObjectNode) row.deepCopy(); receipt.put("candidateId", replacement.id()); retained.add(receipt);
                    var selected = draft.selections() == null ? null : draft.selections().stream().filter(s -> s.candidateId().equals(row.path("candidateId").asText())).findFirst().orElse(null);
                    if (selected == null) throw new com.iflytek.skillhub.domain.shared.exception.DomainConflictException("error.publication.bound");
                    var choice = (ObjectNode) mapper.valueToTree(selected); choice.put("candidateId", replacement.id()); selections.add(choice);
                }
                var metadata = (ObjectNode) mapper.valueToTree(draft); metadata.set("selections", selections);
                task.updateDraft(mapper.writeValueAsString(metadata)); progress.set("results", retained);
                progress.remove(java.util.List.of("priorPreview", "revalidateMaterial"));
            }
            progress.set("preview", mapper.valueToTree(preview));
            progress.put("phase", "REVIEW");
        }
    }
    public String advance(PublicationTask task, PublicationDraft draft, ObjectNode progress,
            PluginActor actor, AuditRequestContext audit) {
        if (!progress.has("preview") || draft.selections() == null || draft.selections().isEmpty()) {
            throw new DomainBadRequestException("error.publication.selection");
        }
        var results = progress.withArray("results");
        var seen = new HashSet<String>();
        for (var selection : draft.selections()) {
            if (!seen.add(selection.candidateId())) throw new DomainBadRequestException("error.publication.selection");
            boolean imported = java.util.stream.StreamSupport.stream(results.spliterator(), false)
                    .anyMatch(r -> r.path("candidateId").asText().equals(selection.candidateId()));
            if (imported) continue;
            var result = importer.apply(progress.path("preview").path("id").asText(), selection, actor, audit);
            var receipt = results.addObject().put("candidateId", result.candidateId()).put("type", "PLUGIN")
                    .put("namespace", draft.namespace()).put("slug", selection.slug()).put("error", result.error());
            if (result.releaseId() == null || !(result.status().equals("IMPORTED") || result.status().equals("IDENTICAL"))) {
                receipt.put("status", "FAILED");
                if (result.error() == null) receipt.put("error", result.status());
            } else {
                var release = queries.release(result.releaseId(), actor);
                receipt.put("resourceId", result.projectId()).put("releaseId", result.releaseId())
                        .put("version", release.version()).put("status", release.status().name());
            }
            progress.put("phase", "IMPORTING");
            return "RUNNING";
        }
        for (var row : results) {
            if (!row.path("status").asText().equals("DRAFT")) continue;
            long releaseId = row.path("releaseId").asLong();
            var release = queries.release(releaseId, actor);
            if (release.status() == PluginReleaseStatus.DRAFT) {
                if (draft.npmRegistryId() != null && progress.path("preview").path("candidates").findValuesAsText("runtime").contains("pi")) {
                    npm.select(releaseId, draft.npmRegistryId(), actor, audit);
                }
                release = publisher.submit(releaseId, actor, audit);
            }
            for (var other : results) if (other.path("releaseId").asLong() == releaseId) ((ObjectNode) other).put("status", release.status().name());
            progress.put("phase", "SUBMITTING");
            return "RUNNING";
        }
        boolean failed = java.util.stream.StreamSupport.stream(results.spliterator(), false).anyMatch(r -> r.path("status").asText().equals("FAILED"));
        progress.put("phase", "SUBMITTED");
        return failed ? "FAILED" : "COMPLETE";
    }
}
