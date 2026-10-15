package com.iflytek.skillhub.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.TestRedisConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Exports Springdoc's real HTTP contract without a running database or Redis server. */
@SpringBootTest(properties = "skillhub.builtin-skills.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@EnabledIfSystemProperty(named = "astronhub.openapi.export", matches = "true")
class OpenApiContractExportTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void exportsTheCompleteSpringdocHttpContract() throws Exception {
        byte[] contract = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsByteArray();

        JsonNode document = objectMapper.readTree(contract);
        assertThat(document.path("openapi").asText()).startsWith("3.");
        assertThat(document.path("paths").isObject()).isTrue();
        assertThat(document.path("paths").has("/api/v1/plugins")).isTrue();
        assertThat(document.path("paths").has("/api/v1/mcp-servers")).isTrue();
        assertThat(document.path("paths").has("/api/v1/catalog/search")).isTrue();
        assertThat(document.path("paths").has("/api/v1/software/apps")).isTrue();
        assertThat(document.path("components").path("schemas").has("CapabilityCatalogItem")).isTrue();

        Path output = Path.of(System.getProperty("astronhub.openapi.output", "target/openapi/openapi.json"))
                .toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        Files.write(output, contract);
    }
}
