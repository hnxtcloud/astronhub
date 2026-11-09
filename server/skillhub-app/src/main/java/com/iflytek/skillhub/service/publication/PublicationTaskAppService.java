package com.iflytek.skillhub.service.publication;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.plugin.service.PluginActor;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.LocalizedDomainException;
import com.iflytek.skillhub.domain.publication.*;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.publication.PublicationTaskView;
import com.iflytek.skillhub.dto.publication.PublicationSaveRequest;
import com.iflytek.skillhub.dto.publication.PublicationDraft;
import com.iflytek.skillhub.dto.publication.PublicationProgressView;
import com.iflytek.skillhub.dto.publication.PublicationProgressView.PublicationResultView;
import com.iflytek.skillhub.dto.plugin.PluginImportDtos.ImportPreviewView;
import com.iflytek.skillhub.service.AuditRequestContext;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicationTaskAppService {
    private final PublicationTaskRepository tasks;
    private final ObjectMapper mapper;
    private final PublicationMaterialService materials;
    private final Map<String, PublicationWorkflow> workflows;
    private final NamespaceMemberRepository members;
    private final TransactionTemplate transactions;
    private final MessageSource messages;
    private final com.iflytek.skillhub.repository.PublicationContentQueryRepository contents;
    private final com.iflytek.skillhub.repository.PublicationTaskQueryRepository taskQuery;
    private final PublicationUploadAppService uploads;
    private final com.iflytek.skillhub.auth.rbac.RbacService rbac;
    public PublicationTaskAppService(PublicationTaskRepository tasks, ObjectMapper mapper,
            PublicationMaterialService materials, List<PublicationWorkflow> workflows,
            NamespaceMemberRepository members, PlatformTransactionManager manager, MessageSource messages,
            com.iflytek.skillhub.repository.PublicationContentQueryRepository contents,
            com.iflytek.skillhub.repository.PublicationTaskQueryRepository taskQuery, PublicationUploadAppService uploads,
            com.iflytek.skillhub.auth.rbac.RbacService rbac) {
        this.tasks = tasks; this.mapper = mapper; this.materials = materials;
        this.workflows = workflows.stream().collect(Collectors.toMap(PublicationWorkflow::type, value -> value));
        this.members = members; this.transactions = new TransactionTemplate(manager); this.messages = messages;
        this.contents = contents;
        this.taskQuery = taskQuery;
        this.uploads = uploads;
        this.rbac = rbac;
    }
    @Transactional
    public PublicationTaskView create(String owner) { return view(tasks.save(new PublicationTask(owner, "{}"))); }
    @Transactional(readOnly = true)
    public PublicationTaskView get(String id, String owner) { return view(owned(id, owner)); }
    @Transactional(readOnly = true)
    public PageResponse<PublicationTaskView> list(String owner, int page) {
        var result = tasks.findByOwnerIdOrderByUpdatedAtDesc(owner, PageRequest.of(Math.max(0, page), 20));
        return new PageResponse<>(result.map(this::view).getContent(), result.getTotalElements(), result.getNumber(), 20);
    }
    public PageResponse<com.iflytek.skillhub.dto.publication.PublicationContentView> content(PlatformPrincipal principal, String type, int page) {
        if (type != null && !Set.of("ALL", "SKILL", "PLUGIN", "SOFTWARE").contains(type)) throw new DomainBadRequestException("error.publication.type");
        return contents.list(actor(principal), type, page);
    }
    public com.iflytek.skillhub.dto.publication.PublicationContentView skillTarget(String owner, String namespace, String slug) {
        return contents.skillTarget(owner, namespace, slug);
    }
    @Transactional
    public PublicationTaskView save(String id, String owner, PublicationSaveRequest request) {
        var task = locked(id, owner, request.revision());
        if (!task.getStatus().equals("DRAFT") && !task.getStatus().equals("FAILED") && !task.getStatus().equals("WAITING")) {
            throw new DomainConflictException("error.publication.state");
        }
        var progress = object(task.getProgressJson());
        if (progress.has("materialUpload")) throw new DomainConflictException("error.publication.state");
        var next = mapper.valueToTree(request.draft());
        String detected = progress.path("detectedType").asText("UNKNOWN");
        if (!detected.equals("UNKNOWN") && !detected.equals(request.draft().type())) {
            throw new DomainBadRequestException("error.publication.type");
        }
        var previous = object(task.getDraftJson());
        for (var receipt : progress.path("results")) {
            if (!receipt.hasNonNull("resourceId") || "FAILED".equals(receipt.path("status").asText()) || !"PLUGIN".equals(receipt.path("type").asText())) continue;
            String candidate = receipt.path("candidateId").asText();
            var oldSelection = java.util.stream.StreamSupport.stream(previous.path("selections").spliterator(), false)
                    .filter(s -> s.path("candidateId").asText().equals(candidate)).findFirst().orElse(null);
            var newSelection = java.util.stream.StreamSupport.stream(next.path("selections").spliterator(), false)
                    .filter(s -> s.path("candidateId").asText().equals(candidate)).findFirst().orElse(null);
            if (!Objects.equals(oldSelection, newSelection)) throw new DomainConflictException("error.publication.bound");
        }
        if (progress.has("appId") || progress.path("results").size() > 0) {
            var pinned = new ArrayList<>(List.of("type", "namespace"));
            if (progress.has("appId")) pinned.addAll(List.of("slug", "appId"));
            if (progress.has("uploadId")) pinned.addAll(List.of("version", "format", "targetOs", "targetArch", "targetDistro"));
            for (String field : pinned) {
                if (!previous.path(field).equals(next.path(field))) throw new DomainConflictException("error.publication.bound");
            }
            if (progress.has("appId") && !progress.has("uploadId") && !previous.path("version").equals(next.path("version"))) {
                progress.remove(List.of("releaseId", "activeResultIndex"));
                task.updateProgress(json(progress), task.getStatus());
            }
        }
        task.updateDraft(json(request.draft()));
        return view(task);
    }
    public PublicationTaskView material(String id, String owner, long revision, MultipartFile file) throws java.io.IOException {
        owned(id, owner);
        uploads.validateSize(owner, file.getSize());
        ObjectNode detected = materials.detect(file);
        var extractionWarnings = detected.remove("warnings");
        String key = "publications/" + id + "/" + UUID.randomUUID();
        materials.store(key, file);
        String[] oldKey = {null};
        try {
            var result = transactions.execute(tx -> {
                var task = locked(id, owner, revision);
                if (!task.getStatus().equals("DRAFT") && !task.getStatus().equals("FAILED")) {
                    throw new DomainConflictException("error.publication.state");
                }
                var progress = object(task.getProgressJson());
                if (progress.has("appId") || progress.path("results").size() > 0) throw new DomainConflictException("error.publication.bound");
                oldKey[0] = task.getMaterialKey();
                task.attach(key, materials.fileName(file.getOriginalFilename()), file.getSize());
                var draft = object(task.getDraftJson());
                var visibility = draft.get("visibility");
                draft.setAll(detected);
                if (visibility != null && !visibility.isNull()) draft.set("visibility", visibility);
                task.updateDraft(json(draft));
                var initial = mapper.createObjectNode().put("detectedType", detected.path("type").asText());
                if (extractionWarnings != null) initial.set("warnings", extractionWarnings);
                task.updateProgress(json(initial), "DRAFT");
                return view(task);
            });
            try { materials.delete(oldKey[0]); } catch (RuntimeException ignored) { /* Isolated obsolete material can be cleaned by operators. */ }
            return result;
        } catch (RuntimeException e) {
            try { materials.delete(key); } catch (RuntimeException cleanup) { e.addSuppressed(cleanup); }
            throw e;
        }
    }
    public PublicationTaskView execute(String id, long revision, PlatformPrincipal principal, AuditRequestContext audit) {
        try {
            return transactions.execute(tx -> {
                var task = locked(id, principal.userId(), revision);
                if (task.getStatus().equals("COMPLETE")) return view(task);
                if (task.getStatus().equals("CANCELLED")) throw new DomainConflictException("error.publication.state");
                if (task.getMaterialKey() == null) throw new DomainBadRequestException("error.publication.material");
                var draft = draft(task);
                var normalized = mapper.valueToTree(draft);
                for (String field : List.of("namespace", "slug", "name", "version", "format", "targetOs", "targetArch", "targetDistro")) {
                    if (normalized.path(field).isTextual()) ((ObjectNode) normalized).put(field, normalized.path(field).asText().trim());
                }
                if (!json(normalized).equals(task.getDraftJson())) { task.updateDraft(json(normalized)); draft = draft(task); }
                var workflow = workflows.get(draft.type());
                if (workflow == null) throw new DomainBadRequestException("error.publication.type");
                var progress = object(task.getProgressJson());
                if (task.getStatus().equals("FAILED") && progress.path("results").isArray()) {
                    var remaining = mapper.createArrayNode();
                    for (var row : progress.path("results")) if (!row.path("status").asText().equals("FAILED")) remaining.add(row);
                    progress.set("results", remaining);
                }
                progress.remove("error");
                try {
                    String state = workflow.advance(task, draft, progress, actor(principal), audit);
                    if (state.equals("COMPLETE")) discardMaterial(task, progress);
                    task.updateProgress(json(progress), state);
                    return view(task);
                } catch (java.io.IOException e) { throw new IllegalStateException("Publication material unavailable", e); }
            });
        } catch (LocalizedDomainException failure) {
            if (failure.statusCode() != 404 && !failure.messageCode().equals("error.publication.conflict")) {
                transactions.executeWithoutResult(tx -> {
                    var task = tasks.lockById(id).filter(t -> t.getOwnerId().equals(principal.userId())).orElse(null);
                    if (task != null && task.getRevision() == revision && !task.getStatus().equals("CANCELLED")) {
                        var progress = object(task.getProgressJson());
                        progress.put("error", messages.getMessage(failure.messageCode(), failure.messageArgs(), failure.messageCode(), LocaleContextHolder.getLocale()));
                        task.updateProgress(json(progress), "FAILED");
                    }
                });
            }
            throw failure;
        }
    }
    public PublicationTaskView preview(String id, long revision, PlatformPrincipal principal, AuditRequestContext audit) {
        return transactions.execute(tx -> {
            var task = locked(id, principal.userId(), revision);
            if (task.getMaterialKey() == null) throw new DomainBadRequestException("error.publication.material");
            if (!task.getStatus().equals("DRAFT") && !task.getStatus().equals("FAILED")) throw new DomainConflictException("error.publication.state");
            var draft = draft(task);
            var workflow = workflows.get(draft.type());
            if (workflow == null) throw new DomainBadRequestException("error.publication.type");
            var progress = object(task.getProgressJson());
            try { workflow.preview(task, draft, progress, actor(principal), audit); }
            catch (java.io.IOException e) { throw new IllegalStateException("Publication preview unavailable", e); }
            progress.remove("error");
            task.updateProgress(json(progress), "DRAFT");
            return view(task);
        });
    }
    private PluginActor actor(PlatformPrincipal principal) {
        var roles = members.findByUserId(principal.userId()).stream().collect(Collectors.toMap(
                com.iflytek.skillhub.domain.namespace.NamespaceMember::getNamespaceId,
                com.iflytek.skillhub.domain.namespace.NamespaceMember::getRole));
        return new PluginActor(principal.userId(), roles, rbac.getUserRoleCodes(principal.userId()));
    }
    private ObjectNode object(String json) {
        try { return (ObjectNode) mapper.readTree(json); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Invalid publication data", e); }
    }
    private PublicationDraft draft(PublicationTask task) {
        try { return mapper.readValue(task.getDraftJson(), PublicationDraft.class); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Invalid publication draft", e); }
    }
    @Transactional
    public PublicationTaskView cancel(String id, String owner, long revision) {
        var task = locked(id, owner, revision);
        if (!task.getStatus().equals("CANCELLED")) {
            var progress = object(task.getProgressJson());
            var workflow = workflows.get(draft(task).type());
            if (workflow != null) workflow.cancel(task, progress, owner);
            discardMaterial(task, progress);
            task.updateProgress(json(progress), "CANCELLED");
        }
        return view(task);
    }
    private void discardMaterial(PublicationTask task, ObjectNode progress) {
        var keys = new ArrayList<String>();
        if (task.getMaterialKey() != null) keys.add(task.getMaterialKey());
        var staged = progress.path("materialUpload");
        if (staged.isObject()) {
            var digests = staged.path("chunkDigests");
            for (int i = 0; i < digests.size(); i++) keys.add(staged.path("prefix").asText() + "/" + (i + 1) + "-" + digests.get(i).asText());
            if (staged.hasNonNull("previousMaterial")) keys.add(staged.path("previousMaterial").asText());
            progress.remove("materialUpload");
        }
        if (task.getMaterialKey() != null) task.attach(null, task.getFileName(), task.getByteSize());
        materials.discardAfterCommit(keys);
    }
    private PublicationTask locked(String id, String owner, long revision) {
        var task = tasks.lockById(id).filter(t -> t.getOwnerId().equals(owner))
                .orElseThrow(() -> new DomainNotFoundException("error.publication.notFound"));
        if (task.getRevision() != revision) throw new DomainConflictException("error.publication.conflict");
        return task;
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Invalid publication data", e); }
    }
    private PublicationTask owned(String id, String owner) {
        return tasks.findById(id).filter(t -> t.getOwnerId().equals(owner))
                .orElseThrow(() -> new DomainNotFoundException("error.publication.notFound"));
    }
    private PublicationTaskView view(PublicationTask task) {
        return taskQuery.project(task);
    }
}
