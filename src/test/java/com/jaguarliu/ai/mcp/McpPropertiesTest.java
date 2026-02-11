package com.jaguarliu.ai.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class McpPropertiesTest {

    @Autowired
    private McpProperties mcpProperties;

    @Test
    void shouldLoadDefaultConfig() {
        assertThat(mcpProperties).isNotNull();
        assertThat(mcpProperties.getServers()).isNotNull();
        assertThat(mcpProperties.getHealthCheck()).isNotNull();
        assertThat(mcpProperties.getHealthCheck().getIntervalSeconds()).isEqualTo(60);
    }

    @Test
    void shouldSupportThreeTransportTypes() {
        var stdioConfig = new McpProperties.ServerConfig();
        stdioConfig.setTransport(McpProperties.TransportType.STDIO);
        assertThat(stdioConfig.getTransport()).isEqualTo(McpProperties.TransportType.STDIO);

        var sseConfig = new McpProperties.ServerConfig();
        sseConfig.setTransport(McpProperties.TransportType.SSE);
        assertThat(sseConfig.getTransport()).isEqualTo(McpProperties.TransportType.SSE);

        var httpConfig = new McpProperties.ServerConfig();
        httpConfig.setTransport(McpProperties.TransportType.HTTP);
        assertThat(httpConfig.getTransport()).isEqualTo(McpProperties.TransportType.HTTP);
    }
}
