package com.jaguarliu.ai.mcp.tools;

import com.jaguarliu.ai.mcp.client.ManagedMcpClient;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpResourceToolTest {

    @Test
    void shouldCreateResourceTool() {
        var mcpClient = mock(ManagedMcpClient.class);
        when(mcpClient.getName()).thenReturn("test-server");
        when(mcpClient.getToolPrefix()).thenReturn("");

        var resourceTool = new McpResourceTool(mcpClient);

        assertThat(resourceTool.getDefinition().getName()).isEqualTo("mcp_read_resource");
        assertThat(resourceTool.getDefinition()).isNotNull();
    }

    @Test
    void shouldRequireResourceUriParameter() {
        var mcpClient = mock(ManagedMcpClient.class);
        when(mcpClient.getName()).thenReturn("test-server");
        when(mcpClient.getToolPrefix()).thenReturn("");

        var resourceTool = new McpResourceTool(mcpClient);
        var params = resourceTool.getDefinition().getParameters();

        assertThat(params).containsKey("properties");
        @SuppressWarnings("unchecked")
        var properties = (Map<String, Object>) params.get("properties");
        assertThat(properties).containsKey("uri");
    }

    @Test
    void shouldApplyToolPrefix() {
        var mcpClient = mock(ManagedMcpClient.class);
        when(mcpClient.getName()).thenReturn("docs-server");
        when(mcpClient.getToolPrefix()).thenReturn("docs_");

        var resourceTool = new McpResourceTool(mcpClient);

        assertThat(resourceTool.getDefinition().getName()).isEqualTo("docs_mcp_read_resource");
    }
}
