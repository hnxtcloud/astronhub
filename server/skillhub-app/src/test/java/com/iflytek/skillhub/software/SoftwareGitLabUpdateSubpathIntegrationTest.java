package com.iflytek.skillhub.software;

import org.springframework.boot.test.context.SpringBootTest;

/** The same original client requests also cross a real Servlet context boundary. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.servlet.context-path=/hub",
        "skillhub.software.storage.temp-dir=./target/sw-gitlab-prefix-tmp",
        "skillhub.storage.local.base-path=./target/sw-gitlab-prefix-base",
        "skillhub.builtin-skills.enabled=false"
})
class SoftwareGitLabUpdateSubpathIntegrationTest extends SoftwareGitLabUpdateReadIntegrationTest {
    @Override String contextPath() { return "/hub"; }
}
