package com.iflytek.skillhub.domain.software;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import java.util.List;

class SoftwareUpdateReleaseTest {
    @Test
    void cannotPublishAnIncompleteUpdateSnapshot() {
        var release = new SoftwareUpdateRelease(1L, 2L, "1.2.3", "v1.2.3", "WINDOWS", "X86_64",
                "stable", "default", "ELECTRON", null, "notes", "admin");
        assertThatThrownBy(() -> release.publish(List.of(), "admin"))
                .hasMessageContaining("error.software.update.incomplete");
    }

    @Test
    void publishedSnapshotFreezesItsCompleteAssetSet() {
        var release = new SoftwareUpdateRelease(1L, 2L, "1.2.3", "v1.2.3", "WINDOWS", "X86_64",
                "stable", "default", "ELECTRON", null, "notes", "admin");
        var installer = new SoftwareReleaseAsset(3L, "app.exe", "INSTALLER", 4L, "obj/exe", 3,
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", "hash");
        var metadata = new SoftwareReleaseAsset(3L, "latest.yml", "METADATA", null, "obj/yaml", 42,
                "digest", "hash");
        release.publish(List.of(installer, metadata), "admin");
        assertThat(release.getStatus()).isEqualTo("PUBLISHED");
        assertThatThrownBy(release::requireDraft).hasMessageContaining("error.software.update.immutable");
    }
}
