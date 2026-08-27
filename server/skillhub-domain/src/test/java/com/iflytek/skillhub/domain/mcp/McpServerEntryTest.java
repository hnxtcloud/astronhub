package com.iflytek.skillhub.domain.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class McpServerEntryTest {
    @Test
    void catalogLifecycleDoesNotImplyGatewayRuntimeState() {
        McpServerEntry entry = new McpServerEntry(10L, "postgres", "Postgres MCP", "user-1",
                McpVisibility.PRIVATE);

        assertThat(entry.getStatus()).isEqualTo(McpEntryStatus.ACTIVE);
        assertThat(entry.getLatestRevisionId()).isNull();

        entry.hide();
        entry.archive();

        assertThat(entry.isHidden()).isTrue();
        assertThat(entry.getStatus()).isEqualTo(McpEntryStatus.ARCHIVED);
    }
}
