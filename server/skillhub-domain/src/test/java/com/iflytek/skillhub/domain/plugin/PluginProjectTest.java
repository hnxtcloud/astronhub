package com.iflytek.skillhub.domain.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PluginProjectTest {
    @Test
    void projectLifecycleIsIndependentFromSkillLifecycle() {
        PluginProject project = new PluginProject(10L, "review-tools", "Review Tools", "user-1",
                PluginVisibility.NAMESPACE_ONLY);

        assertThat(project.getStatus()).isEqualTo(PluginProjectStatus.ACTIVE);
        assertThat(project.isHidden()).isFalse();

        project.hide();
        project.archive();

        assertThat(project.isHidden()).isTrue();
        assertThat(project.getStatus()).isEqualTo(PluginProjectStatus.ARCHIVED);

        project.show();
        project.restore();

        assertThat(project.isHidden()).isFalse();
        assertThat(project.getStatus()).isEqualTo(PluginProjectStatus.ACTIVE);
    }
}
