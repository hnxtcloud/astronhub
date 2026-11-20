package com.iflytek.skillhub.domain.mcp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.mcp.McpConnectionProfile;
import com.iflytek.skillhub.domain.mcp.McpConnectionProfileRepository;
import com.iflytek.skillhub.domain.mcp.McpEntryRevision;
import com.iflytek.skillhub.domain.mcp.McpEntryRevisionRepository;
import com.iflytek.skillhub.domain.mcp.McpEntryStatus;
import com.iflytek.skillhub.domain.mcp.McpProfileTemplate;
import com.iflytek.skillhub.domain.mcp.McpReviewDecisionRepository;
import com.iflytek.skillhub.domain.mcp.McpRevisionStatus;
import com.iflytek.skillhub.domain.mcp.McpServerEntry;
import com.iflytek.skillhub.domain.mcp.McpServerEntryRepository;
import com.iflytek.skillhub.domain.mcp.McpTransportType;
import com.iflytek.skillhub.domain.mcp.McpVariableTemplate;
import com.iflytek.skillhub.domain.mcp.McpVisibility;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class McpCatalogWorkflowTest {
    private final McpServerEntryRepository entries = mock(McpServerEntryRepository.class);
    private final McpEntryRevisionRepository revisions = mock(McpEntryRevisionRepository.class);
    private final McpConnectionProfileRepository profiles = mock(McpConnectionProfileRepository.class);
    private final McpReviewDecisionRepository decisions = mock(McpReviewDecisionRepository.class);
    private final NamespaceRepository namespaces = mock(NamespaceRepository.class);
    private final McpCatalogWorkflow workflow = new McpCatalogWorkflow(
            entries, revisions, profiles, decisions, namespaces, new ObjectMapper());

    private final Namespace namespace = new Namespace("demo", "Demo", "owner");

    @BeforeEach
    void activeNamespace() {
        given(namespaces.findById(10L)).willReturn(Optional.of(namespace));
        namespace.setStatus(NamespaceStatus.ACTIVE);
    }

    private McpActor actor(String userId, NamespaceRole role, String... platformRoles) {
        return new McpActor(userId, role == null ? Map.of() : Map.of(10L, role), Set.of(platformRoles));
    }

    private McpProfileTemplate httpProfile() {
        return new McpProfileTemplate("remote", "Remote", McpTransportType.STREAMABLE_HTTP,
                "https://example.test/mcp?key=${API_KEY}", null, List.of(),
                List.of(new McpVariableTemplate("API_KEY", null, true, true, null, null)));
    }

    @Test
    void createEntryRejectsOutsidersAndDuplicateSlugs() {
        given(entries.findByNamespaceIdAndSlug(10L, "demo")).willReturn(Optional.empty());
        assertThatThrownBy(() -> workflow.createEntry(10L, "demo", "Demo", null, McpVisibility.PUBLIC,
                List.of(), McpCatalogWorkflow.SOURCE_MANUAL, null, actor("stranger", null)))
                .isInstanceOf(DomainForbiddenException.class);
        given(entries.findByNamespaceIdAndSlug(10L, "demo")).willReturn(Optional.of(newEntry()));
        assertThatThrownBy(() -> workflow.createEntry(10L, "demo", "Demo", null, McpVisibility.PUBLIC,
                List.of(), McpCatalogWorkflow.SOURCE_MANUAL, null, actor("owner", NamespaceRole.MEMBER)))
                .isInstanceOf(DomainConflictException.class);
    }

    @Test
    void profileValidationRejectsSecretDefaultsAndMissingTemplates() {
        given(entries.findByNamespaceIdAndSlug(10L, "demo")).willReturn(Optional.empty());
        var secretDefault = new McpProfileTemplate("remote", "Remote", McpTransportType.STREAMABLE_HTTP,
                "https://example.test/mcp", null, List.of(),
                List.of(new McpVariableTemplate("API_KEY", null, true, true, "leaky", null)));
        assertThatThrownBy(() -> workflow.createEntry(10L, "demo", "Demo", null, McpVisibility.PUBLIC,
                List.of(secretDefault), McpCatalogWorkflow.SOURCE_MANUAL, null, actor("owner", NamespaceRole.MEMBER)))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("error.mcp.secretDefault");
        var missingCommand = new McpProfileTemplate("stdio", "Stdio", McpTransportType.STDIO,
                null, "  ", List.of(), List.of());
        assertThatThrownBy(() -> workflow.createEntry(10L, "demo", "Demo", null, McpVisibility.PUBLIC,
                List.of(missingCommand), McpCatalogWorkflow.SOURCE_MANUAL, null, actor("owner", NamespaceRole.MEMBER)))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("error.mcp.profileInvalid");
        var httpSecretWithoutPlaceholder = new McpProfileTemplate("remote", "Remote", McpTransportType.STREAMABLE_HTTP,
                "https://example.test/mcp", null, List.of(),
                List.of(new McpVariableTemplate("API_KEY", null, true, true, null, null)));
        assertThatThrownBy(() -> workflow.createEntry(10L, "demo", "Demo", null, McpVisibility.PUBLIC,
                List.of(httpSecretWithoutPlaceholder), McpCatalogWorkflow.SOURCE_MANUAL, null,
                actor("owner", NamespaceRole.MEMBER)))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("error.mcp.secretPlaceholder");
    }

    @Test
    void submissionRequiresProfilesAndApprovalRepointsTheEntry() {
        McpServerEntry entry = newEntry();
        McpEntryRevision revision = newRevision(entry, 1, McpCatalogWorkflow.SOURCE_MANUAL, "owner");
        given(entries.findByIdForUpdate(entry.getId())).willReturn(Optional.of(entry));
        given(entries.findById(entry.getId())).willReturn(Optional.of(entry));
        given(revisions.findByIdForUpdate(revision.getId())).willReturn(Optional.of(revision));
        given(revisions.save(revision)).willReturn(revision);
        given(entries.save(entry)).willReturn(entry);
        given(profiles.findByRevisionIdOrderByProfileKey(revision.getId())).willReturn(List.of());
        given(decisions.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> workflow.submitRevision(revision.getId(), actor("owner", NamespaceRole.MEMBER)))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("error.mcp.validationRequired");

        given(profiles.findByRevisionIdOrderByProfileKey(revision.getId()))
                .willReturn(List.of(new McpConnectionProfile(
                        revision.getId(), "remote", "Remote", McpTransportType.STREAMABLE_HTTP)));

        assertThatThrownBy(() -> workflow.approveRevision(revision.getId(), null, actor("owner", NamespaceRole.MEMBER)))
                .isInstanceOf(DomainForbiddenException.class);
        assertThatThrownBy(() -> workflow.approveRevision(revision.getId(), null,
                actor("mcp-admin", null, "MCP_ADMIN")))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("error.mcp.reviewState");

        revision.submit();
        McpEntryRevision approved = workflow.approveRevision(
                revision.getId(), null, actor("ns-admin", NamespaceRole.ADMIN));
        assertThat(approved.getStatus()).isEqualTo(McpRevisionStatus.PUBLISHED);
        assertThat(entry.getLatestRevisionId()).isEqualTo(revision.getId());
    }

    @Test
    void onlyOneLiveDraftExistsPerEntryAndEditingPublishedStaysImpossible() {
        McpServerEntry entry = newEntry();
        McpEntryRevision published = newRevision(entry, 1, McpCatalogWorkflow.SOURCE_MANUAL, "owner");
        McpEntryRevision draft = newRevision(entry, 2, McpCatalogWorkflow.SOURCE_SERVER_JSON, "owner");
        given(entries.findByIdForUpdate(entry.getId())).willReturn(Optional.of(entry));
        given(entries.findById(entry.getId())).willReturn(Optional.of(entry));
        given(revisions.findFirstByEntryIdOrderByRevisionNumberDesc(entry.getId()))
                .willReturn(Optional.of(draft));

        assertThatThrownBy(() -> workflow.createDraftRevision(entry.getId(), "desc", List.of(httpProfile()),
                McpCatalogWorkflow.SOURCE_MANUAL, null, null, actor("owner", NamespaceRole.MEMBER)))
                .isInstanceOf(DomainConflictException.class)
                .hasMessageContaining("error.mcp.draftExists");

        given(revisions.findByIdForUpdate(published.getId())).willReturn(Optional.of(published));
        setField(published, "status", McpRevisionStatus.PUBLISHED);
        assertThatThrownBy(() -> workflow.updateDraftRevision(published.getId(), "desc", List.of(httpProfile()),
                null, actor("owner", NamespaceRole.MEMBER)))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("error.mcp.notEditable");
    }

    @Test
    void directPublishIsSuperAdminOnlyAndGovernanceRequiresReason() {
        McpServerEntry entry = newEntry();
        McpEntryRevision draft = newRevision(entry, 1, McpCatalogWorkflow.SOURCE_MANUAL, "owner");
        given(entries.findByIdForUpdate(entry.getId())).willReturn(Optional.of(entry));
        given(entries.findById(entry.getId())).willReturn(Optional.of(entry));
        given(revisions.findByIdForUpdate(draft.getId())).willReturn(Optional.of(draft));
        given(revisions.save(draft)).willReturn(draft);
        given(entries.save(entry)).willReturn(entry);
        given(profiles.findByRevisionIdOrderByProfileKey(draft.getId()))
                .willReturn(List.of(new McpConnectionProfile(
                        draft.getId(), "remote", "Remote", McpTransportType.STREAMABLE_HTTP)));

        assertThatThrownBy(() -> workflow.directPublishRevision(draft.getId(), null, actor("owner", NamespaceRole.MEMBER)))
                .isInstanceOf(DomainForbiddenException.class);

        McpEntryRevision published = workflow.directPublishRevision(
                draft.getId(), null, actor("root", null, "SUPER_ADMIN"));
        assertThat(published.getStatus()).isEqualTo(McpRevisionStatus.PUBLISHED);
        assertThat(entry.getLatestRevisionId()).isEqualTo(draft.getId());

        assertThatThrownBy(() -> workflow.govern(entry.getId(), "HIDE", null, "  ", actor("owner", NamespaceRole.MEMBER)))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("error.mcp.governanceReason");
        McpServerEntry hidden = workflow.govern(
                entry.getId(), "HIDE", null, "duplicate entry", actor("mcp-admin", null, "MCP_ADMIN"));
        assertThat(hidden.isHidden()).isTrue();
        assertThatThrownBy(() -> workflow.govern(entry.getId(), "VISIBILITY", McpVisibility.NAMESPACE_ONLY,
                "shrink scope", actor("mcp-admin", null, "MCP_ADMIN")))
                .isInstanceOf(DomainForbiddenException.class);
    }

    @Test
    void stdioSecretVariablesBindThroughEnvWithoutCommandPlaceholders() {
        given(entries.findByNamespaceIdAndSlug(10L, "demo")).willReturn(Optional.empty());
        given(entries.save(any(McpServerEntry.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(revisions.save(any(McpEntryRevision.class)))
                .willAnswer(invocation -> {
                    McpEntryRevision revision = invocation.getArgument(0);
                    given(profiles.findByRevisionIdOrderByProfileKey(revision.getId()))
                            .willReturn(List.of(new McpConnectionProfile(
                                    revision.getId(), "stdio", "Stdio", McpTransportType.STDIO)));
                    return revision;
                });

        McpServerEntry saved = workflow.createEntry(10L, "demo", "Demo", null, McpVisibility.PUBLIC,
                List.of(new McpProfileTemplate("stdio", "Stdio", McpTransportType.STDIO,
                        null, "npx -y demo", List.of(),
                        List.of(new McpVariableTemplate("DEMO_TOKEN", null, true, true, null, null)))),
                McpCatalogWorkflow.SOURCE_MANUAL, null, actor("owner", NamespaceRole.MEMBER));

        assertThat(saved.getStatus()).isEqualTo(McpEntryStatus.ACTIVE);
    }

    /** Point-to-published guards require a persisted identity; tests inject one via reflection. */
    private McpServerEntry newEntry() {
        McpServerEntry entry = new McpServerEntry(10L, "demo", "Demo", "owner", McpVisibility.PUBLIC);
        setField(entry, "id", 77L);
        return entry;
    }

    private McpEntryRevision newRevision(McpServerEntry entry, int number, String sourceKind, String userId) {
        McpEntryRevision revision = new McpEntryRevision(entry.getId(), number, sourceKind, userId);
        setField(revision, "id", (long) (100 + number));
        return revision;
    }

    private void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
