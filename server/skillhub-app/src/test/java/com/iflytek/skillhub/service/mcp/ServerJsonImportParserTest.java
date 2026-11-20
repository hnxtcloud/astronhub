package com.iflytek.skillhub.service.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.mcp.McpTransportType;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;

import org.junit.jupiter.api.Test;

class ServerJsonImportParserTest {
    private final ServerJsonImportParser parser = new ServerJsonImportParser(new ObjectMapper());

    @Test
    void mapsRemotesAndNpmPackagesIntoConnectionTemplates() {
        ServerJsonImportParser.ServerJsonImport imported = parser.parse("""
                {
                  "name": "io.example/demo-server",
                  "title": "Demo Server",
                  "description": "A demo MCP server",
                  "version": "1.2.0",
                  "remotes": [
                    {"type": "streamable-http", "url": "https://mcp.example.test/api?key=${API_KEY}"},
                    {"type": "sse", "url": "https://legacy.example.test/sse"}
                  ],
                  "packages": [
                    {
                      "registry_type": "npm",
                      "identifier": "@demo/server",
                      "version": "1.2.0",
                      "environment_variables": [
                        {"name": "DEMO_TOKEN", "description": "Access token", "required": true},
                        {"name": "PORT", "value": "8080", "required": false}
                      ],
                      "package_arguments": [
                        {"type": "positional", "value": "serve"},
                        {"type": "named", "name": "verbose", "value": "true"}
                      ]
                    },
                    {"registry_type": "oci", "identifier": "registry.example.test/demo:1"}
                  ]
                }
                """);

        assertThat(imported.displayName()).isEqualTo("Demo Server");
        assertThat(imported.summary()).isEqualTo("A demo MCP server");

        assertThat(imported.profiles()).hasSize(3);
        var remoteHttp = imported.profiles().get(0);
        assertThat(remoteHttp.transportType()).isEqualTo(McpTransportType.STREAMABLE_HTTP);
        assertThat(remoteHttp.endpointTemplate()).isEqualTo("https://mcp.example.test/api?key=${API_KEY}");
        assertThat(remoteHttp.variables()).singleElement().satisfies(variable -> {
            assertThat(variable.name()).isEqualTo("API_KEY");
            assertThat(variable.secret()).isTrue();
            assertThat(variable.defaultValue()).isNull();
        });

        assertThat(imported.profiles().get(1).transportType()).isEqualTo(McpTransportType.LEGACY_HTTP_SSE);

        var npm = imported.profiles().get(2);
        assertThat(npm.transportType()).isEqualTo(McpTransportType.STDIO);
        assertThat(npm.commandTemplate()).isEqualTo("npx");
        assertThat(npm.arguments()).containsExactly("-y", "@demo/server@1.2.0", "serve", "--verbose", "true");
        assertThat(npm.variables()).extracting(v -> v.name() + ":" + v.secret() + ":" + v.defaultValue())
                .containsExactly("DEMO_TOKEN:true:null", "PORT:false:8080");
    }

    @Test
    void neverPersistsLiteralCredentialValuesFromTheSourceDocument() {
        ServerJsonImportParser.ServerJsonImport imported = parser.parse("""
                {
                  "name": "io.example/secretive",
                  "packages": [{
                    "registry_type": "pypi",
                    "identifier": "secretive",
                    "environment_variables": [
                      {"name": "SECRET_API_KEY", "value": "sk-live-123", "required": true}
                    ]
                  }]
                }
                """);

        var stdio = imported.profiles().get(0);
        assertThat(stdio.commandTemplate()).isEqualTo("uvx");
        assertThat(stdio.variables()).singleElement().satisfies(variable -> {
            assertThat(variable.name()).isEqualTo("SECRET_API_KEY");
            assertThat(variable.secret()).isTrue();
            assertThat(variable.defaultValue()).isNull();
        });
        assertThat(imported.profiles().toString()).doesNotContain("sk-live-123");
    }

    @Test
    void rejectsMalformedDocuments() {
        assertThatThrownBy(() -> parser.parse("{not json"))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("error.mcp.importInvalid");
        assertThatThrownBy(() -> parser.parse("{\"description\":\"no name\"}"))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("error.mcp.importInvalid");
    }
}
