package com.iflytek.skillhub.software;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.servlet.context-path=/hub",
        "skillhub.software.storage.temp-dir=./target/sw-intelli-sub-tmp",
        "skillhub.storage.local.base-path=./target/sw-intelli-sub-base",
        "skillhub.builtin-skills.enabled=false"
})
class SoftwareIntelliClientContractSubpathIntegrationTest extends SoftwareIntelliClientContractIntegrationTest {
    @Override String contextPath() { return "/hub"; }
}
