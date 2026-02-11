package com.jaguarliu.ai.mcp.tools;

import com.jaguarliu.ai.mcp.client.ManagedMcpClient;
import com.jaguarliu.ai.tools.ToolDefinition;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpToolAdapterTest {

    @Test
    void shouldAdaptMcpToolDefinition() {
        // Given: MCP tool (mocked)
        var mcpTool = mock(McpSchema.Tool.class);
        when(mcpTool.name()).thenReturn("get_weather");
        when(mcpTool.description()).thenReturn("Get weather for a city");
        when(mcpTool.inputSchema()).thenReturn(null); // Simplified - don't test schema details

        var serverConfig = mock(com.jaguarliu.ai.mcp.McpProperties.ServerConfig.class);
        when(serverConfig.isRequiresHitl()).thenReturn(false);
        when(serverConfig.getHitlTools()).thenReturn(List.of());

        var mcpClient = mock(ManagedMcpClient.class);
        when(mcpClient.getToolPrefix()).thenReturn("mcp_");
        when(mcpClient.getName()).thenReturn("test-server");
        when(mcpClient.getConfig()).thenReturn(serverConfig);

        // When: create adapter
        var adapter = new McpToolAdapter(mcpTool, mcpClient);

        // Then: should have prefixed name
        ToolDefinition def = adapter.getDefinition();
        assertThat(def.getName()).isEqualTo("mcp_get_weather");
        assertThat(def.getDescription()).contains("test-server");
        assertThat(def.getParameters()).isNotNull();
    }

    @Test
    void shouldAdaptToolWithoutPrefix() {
        // Given: MCP tool without prefix (mocked)
        var mcpTool = mock(McpSchema.Tool.class);
        when(mcpTool.name()).thenReturn("read_file");
        when(mcpTool.description()).thenReturn("Read a file");
        when(mcpTool.inputSchema()).thenReturn(null);

        var serverConfig = mock(com.jaguarliu.ai.mcp.McpProperties.ServerConfig.class);
        when(serverConfig.isRequiresHitl()).thenReturn(false);
        when(serverConfig.getHitlTools()).thenReturn(List.of());

        var mcpClient = mock(ManagedMcpClient.class);
        when(mcpClient.getToolPrefix()).thenReturn("");
        when(mcpClient.getName()).thenReturn("filesystem");
        when(mcpClient.getConfig()).thenReturn(serverConfig);

        // When: create adapter
        var adapter = new McpToolAdapter(mcpTool, mcpClient);

        // Then: should not have prefix
        assertThat(adapter.getDefinition().getName()).isEqualTo("read_file");
    }

    @Test
    void shouldMarkToolAsHitlWhenServerRequiresHitl() {
        // Given: MCP tool with HITL required (mocked)
        var mcpTool = mock(McpSchema.Tool.class);
        when(mcpTool.name()).thenReturn("delete_file");
        when(mcpTool.description()).thenReturn("Delete a file");
        when(mcpTool.inputSchema()).thenReturn(null);

        var serverConfig = mock(com.jaguarliu.ai.mcp.McpProperties.ServerConfig.class);
        when(serverConfig.isRequiresHitl()).thenReturn(true);
        when(serverConfig.getHitlTools()).thenReturn(List.of());

        var mcpClient = mock(ManagedMcpClient.class);
        when(mcpClient.getToolPrefix()).thenReturn("");
        when(mcpClient.getName()).thenReturn("filesystem");
        when(mcpClient.getConfig()).thenReturn(serverConfig);

        // When: create adapter
        var adapter = new McpToolAdapter(mcpTool, mcpClient);

        // Then: should require HITL
        assertThat(adapter.requiresHitl()).isTrue();
    }

    @Test
    void shouldMarkSpecificToolAsHitl() {
        // Given: Specific tool marked for HITL (mocked)
        var mcpTool = mock(McpSchema.Tool.class);
        when(mcpTool.name()).thenReturn("write_file");
        when(mcpTool.description()).thenReturn("Write to a file");
        when(mcpTool.inputSchema()).thenReturn(null);

        var serverConfig = mock(com.jaguarliu.ai.mcp.McpProperties.ServerConfig.class);
        when(serverConfig.isRequiresHitl()).thenReturn(false);
        when(serverConfig.getHitlTools()).thenReturn(List.of("write_file"));

        var mcpClient = mock(ManagedMcpClient.class);
        when(mcpClient.getToolPrefix()).thenReturn("");
        when(mcpClient.getName()).thenReturn("filesystem");
        when(mcpClient.getConfig()).thenReturn(serverConfig);

        // When: create adapter
        var adapter = new McpToolAdapter(mcpTool, mcpClient);

        // Then: should require HITL
        assertThat(adapter.requiresHitl()).isTrue();
    }
}
