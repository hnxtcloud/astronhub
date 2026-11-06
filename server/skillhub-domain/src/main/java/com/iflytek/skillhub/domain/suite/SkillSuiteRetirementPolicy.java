package com.iflytek.skillhub.domain.suite;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Stops new Suite content while allowing only persistently identified legacy work to finish. */
@Component
public class SkillSuiteRetirementPolicy {
    private final boolean enabled;

    public SkillSuiteRetirementPolicy(@Value("${skillhub.suite.retirement.enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void requireNewContentAllowed() {
        if (enabled) {
            throw new DomainBadRequestException("error.suite.retired");
        }
    }

    public boolean canComplete(SkillSuiteVersion version) {
        return !enabled || version.isRetirementCompletionAllowed();
    }

    public void requireVersionCompletion(SkillSuiteVersion version) {
        if (!canComplete(version)) {
            throw new DomainBadRequestException("error.suite.retired");
        }
    }

    public void requireBundleCompletion(SkillSuiteBundleExecutionOperation operation) {
        if (enabled && !operation.isRetirementCompletionAllowed()) {
            throw new DomainBadRequestException("error.suite.retired");
        }
    }
}
