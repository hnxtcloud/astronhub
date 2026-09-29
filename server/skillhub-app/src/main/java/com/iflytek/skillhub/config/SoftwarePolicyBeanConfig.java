package com.iflytek.skillhub.config;

import com.iflytek.skillhub.domain.software.SoftwarePolicyDeploymentLimits;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Deployment-declared hard limits for the software center. These caps come
 * from the environment (proxy request body, worker container memory) and are
 * read-only: the editable runtime policy must stay within them.
 */
@Configuration
public class SoftwarePolicyBeanConfig {

    @Bean
    public SoftwarePolicyDeploymentLimits softwarePolicyDeploymentLimits(
            @Value("${skillhub.software.limits.proxy-max-body-bytes:104857600}") long proxyMaxBodyBytes,
            @Value("${skillhub.software.limits.worker-memory-hard-limit-bytes:1073741824}")
            long workerMemoryHardLimitBytes,
            @Value("${skillhub.software.limits.worker-memory-reserved-bytes:536870912}")
            long workerMemoryReservedBytes) {
        return new SoftwarePolicyDeploymentLimits(
                proxyMaxBodyBytes, workerMemoryHardLimitBytes, workerMemoryReservedBytes);
    }
}
