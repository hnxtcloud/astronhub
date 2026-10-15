package com.iflytek.skillhub.domain.plugin;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PluginChannelPublicationTest {
    @Test
    void completingAnotherChannelPreservesHostedAndDoesNotExposeIncompleteRelease() {
        var task =
                new PluginPublicationTask(
                        1L, "reviewer", "GIT,HOSTED,NPM", "PUBLIC\n1.0.0\npi:digest");
        task.completeHosted();
        task.completeChannel("NPM");
        assertThat(task.getCompletedChannels()).isEqualTo("HOSTED,NPM");
        assertThat(task.getStatus()).isEqualTo("QUEUED");
        task.completeChannel("GIT");
        assertThat(task.getStatus()).isEqualTo("SUCCEEDED");
        task.completeChannel("NPM");
        assertThat(task.getCompletedChannels()).isEqualTo("GIT,HOSTED,NPM");
    }

    @Test
    void missingTargetIsNeverClaimedAndExpiredAttemptsCannotOverwriteNewerReceipts() {
        var blocked = new PluginPublicationChannel(1L, "NPM", null);
        assertThat(blocked.claim(java.time.Instant.now())).isNull();
        assertThat(blocked.getState()).isEqualTo("BLOCKED");
        var channel = new PluginPublicationChannel(2L, "NPM", "fixed-target");
        var old = channel.claim(java.time.Instant.parse("2026-10-15T00:00:00Z"));
        var current = channel.claim(java.time.Instant.parse("2026-10-15T00:10:00Z"));
        assertThat(channel.finish(old, "stale", null)).isFalse();
        assertThat(channel.finish(current, "durable-receipt", null)).isTrue();
        assertThat(channel.getReceipt()).isEqualTo("durable-receipt");
        assertThat(channel.claim(java.time.Instant.now())).isNull();
    }
}
