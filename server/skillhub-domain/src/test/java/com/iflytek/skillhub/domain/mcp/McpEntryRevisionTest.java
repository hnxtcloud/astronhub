package com.iflytek.skillhub.domain.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class McpEntryRevisionTest {
    @Test
    void draftLifecycleTransitionsFollowTheCatalogStateMachine() {
        McpEntryRevision revision = new McpEntryRevision(1L, 1, "MANUAL", "user-1");
        assertThat(revision.getStatus()).isEqualTo(McpRevisionStatus.DRAFT);
        assertThat(revision.getPublishedAt()).isNull();

        revision.attachSourceSnapshot("{\"name\":\"demo\"}");
        revision.updateDraft("initial publication", null);
        revision.submit();
        assertThat(revision.getStatus()).isEqualTo(McpRevisionStatus.PENDING_REVIEW);

        revision.withdraw();
        assertThat(revision.getStatus()).isEqualTo(McpRevisionStatus.DRAFT);

        revision.submit();
        revision.approve();
        assertThat(revision.getStatus()).isEqualTo(McpRevisionStatus.PUBLISHED);
        assertThat(revision.getPublishedAt()).isNotNull();
    }

    @Test
    void publishedRevisionsRejectEditsAndTerminalTransitions() {
        McpEntryRevision revision = new McpEntryRevision(1L, 1, "MANUAL", "user-1");
        revision.submit();
        revision.reject();
        assertThat(revision.getStatus()).isEqualTo(McpRevisionStatus.REJECTED);

        assertThatThrownBy(() -> revision.updateDraft("edit", null))
                .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException.class);
        assertThatThrownBy(() -> revision.attachSourceSnapshot("{}"))
                .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException.class);
        assertThatThrownBy(revision::approve)
                .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException.class);
    }

    @Test
    void onlyDraftAndRejectedRevisionsAreDeletable() {
        McpEntryRevision draft = new McpEntryRevision(1L, 1, "MANUAL", "user-1");
        draft.requireDeletable();
        draft.submit();
        assertThatThrownBy(draft::requireDeletable)
                .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException.class);
    }
}
