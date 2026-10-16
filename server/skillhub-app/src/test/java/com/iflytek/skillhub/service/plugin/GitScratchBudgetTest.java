package com.iflytek.skillhub.service.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

class GitScratchBudgetTest {
    @TempDir Path root;

    @Test
    void observedGitObjectCanDisappearBeforeItsAttributesAreAccounted() throws Exception {
        Path object = Files.writeString(root.resolve("tmp_obj"), "object");
        var attributes = Files.readAttributes(object, BasicFileAttributes.class);
        var observed = new AtomicBoolean();
        var budget =
                new GitScratchBudget(
                        16,
                        (directory, visitor) -> {
                            // Real attributes followed by a real unlink deterministically model
                            // Git's atomic rename.
                            Files.delete(object);
                            observed.set(true);
                            visitor.visitFile(object, attributes);
                        });

        assertThatCode(() -> budget.check(root)).doesNotThrowAnyException();
        assertThat(observed).isTrue();
        assertThat(object).doesNotExist();
        var insufficient =
                new GitScratchBudget(
                        5, (directory, visitor) -> visitor.visitFile(object, attributes));
        assertThatThrownBy(() -> insufficient.check(root))
                .isInstanceOf(GitMarketplacePublisher.Failure.class)
                .hasMessage("GIT_SCRATCH_LIMIT");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void vanishedNonrootEntriesDoNotAbortAccountingOfTheRemainingTree(boolean duringIteration)
            throws Exception {
        Path vanished = root.resolve("objects");
        Path retained = Files.writeString(root.resolve("retained"), "abc");
        GitScratchBudget.DirectoryWalk filesystem =
                (directory, visitor) -> {
                    Files.createDirectory(vanished);
                    Files.delete(vanished);
                    try {
                        Files.readAttributes(vanished, BasicFileAttributes.class);
                    } catch (NoSuchFileException missing) {
                        if (duringIteration) visitor.postVisitDirectory(vanished, missing);
                        else visitor.visitFileFailed(vanished, missing);
                    }
                    visitor.visitFile(
                            retained, Files.readAttributes(retained, BasicFileAttributes.class));
                };

        assertThatCode(() -> new GitScratchBudget(3, filesystem).check(root))
                .doesNotThrowAnyException();
        // Ignore only the vanished entry; still reject the retained file over the budget.
        assertThatThrownBy(() -> new GitScratchBudget(2, filesystem).check(root))
                .isInstanceOf(GitMarketplacePublisher.Failure.class)
                .hasMessage("GIT_SCRATCH_LIMIT");
        assertThat(vanished).doesNotExist();
    }

    @Test
    void missingScratchRootFailsClosed() {
        assertThatThrownBy(() -> new GitScratchBudget(16).check(root.resolve("absent")))
                .isInstanceOf(NoSuchFileException.class);
    }

    @Test
    void accessDenialAndOtherIoFailuresFailClosed() {
        for (IOException failure :
                List.of(new AccessDeniedException("object"), new IOException("read failure"))) {
            var budget =
                    new GitScratchBudget(
                            16,
                            (directory, visitor) ->
                                    visitor.visitFileFailed(root.resolve("object"), failure));
            assertThatThrownBy(() -> budget.check(root)).isSameAs(failure);
        }
    }

    @Test
    void realTreeAtTheLimitPassesAndAnyAdditionalBytesAreRejected() throws Exception {
        Files.writeString(root.resolve("first"), "abc");
        Files.writeString(root.resolve("second"), "def");
        var budget = new GitScratchBudget(6);
        assertThatCode(() -> budget.check(root)).doesNotThrowAnyException();
        Files.writeString(root.resolve("extra"), "g");
        assertThatThrownBy(() -> budget.check(root))
                .isInstanceOf(GitMarketplacePublisher.Failure.class)
                .hasMessage("GIT_SCRATCH_LIMIT");
    }
}
