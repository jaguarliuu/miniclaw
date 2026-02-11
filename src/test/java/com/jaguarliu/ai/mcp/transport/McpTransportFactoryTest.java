package com.jaguarliu.ai.mcp.transport;

import com.jaguarliu.ai.mcp.McpProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpTransportFactoryTest {

    private final McpTransportFactory factory = new McpTransportFactory();

    @Test
    void shouldCreateStdioTransport() {
        var config = new McpProperties.ServerConfig();
        config.setTransport(McpProperties.TransportType.STDIO);
        config.setCommand("npx");
        config.setArgs(List.of("-y", "@modelcontextprotocol/server-filesystem", "/tmp"));

        var transport = factory.createTransport(config);

        assertThat(transport).isNotNull();
    }

    @Test
    void shouldThrowExceptionForStdioWithoutCommand() {
        var config = new McpProperties.ServerConfig();
        config.setTransport(McpProperties.TransportType.STDIO);

        assertThatThrownBy(() -> factory.createTransport(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("command is required");
    }

    @Test
    void shouldCreateSseTransport() {
        var config = new McpProperties.ServerConfig();
        config.setTransport(McpProperties.TransportType.SSE);
        config.setUrl("http://localhost:3000/sse");

        var transport = factory.createTransport(config);

        assertThat(transport).isNotNull();
    }

    @Test
    void shouldCreateHttpTransport() {
        var config = new McpProperties.ServerConfig();
        config.setTransport(McpProperties.TransportType.HTTP);
        config.setUrl("http://localhost:3000/mcp");

        var transport = factory.createTransport(config);

        assertThat(transport).isNotNull();
    }

    @Test
    void shouldThrowExceptionForSseWithoutUrl() {
        var config = new McpProperties.ServerConfig();
        config.setTransport(McpProperties.TransportType.SSE);

        assertThatThrownBy(() -> factory.createTransport(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("url is required");
    }

    @Test
    void shouldThrowExceptionForHttpWithoutUrl() {
        var config = new McpProperties.ServerConfig();
        config.setTransport(McpProperties.TransportType.HTTP);

        assertThatThrownBy(() -> factory.createTransport(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("url is required");
    }
}
