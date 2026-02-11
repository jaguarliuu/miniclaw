package com.jaguarliu.ai.mcp.integration;

import com.jaguarliu.ai.mcp.client.McpClientManager;
import com.jaguarliu.ai.tools.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MCP 集成测试
 * 测试完整的 MCP 工具发现和执行流程
 */
@SpringBootTest
class McpIntegrationTest {

    @Autowired
    private McpClientManager clientManager;

    @Autowired
    private ToolRegistry toolRegistry;

    @Test
    void mcpSystemShouldInitializeCorrectly() {
        assertThat(clientManager).isNotNull();
        assertThat(toolRegistry).isNotNull();

        // 验证系统能正常启动（即使没有配置 MCP 服务器）
        assertThat(clientManager.getAllClients()).isNotNull();
    }

    @Test
    void shouldHandleMissingMcpServersGracefully() {
        // 没有 MCP 服务器配置时不应该报错
        var clients = clientManager.getAllClients();
        assertThat(clients).isEmpty();
    }

    @Test
    void shouldSupportAllThreeTransportTypes() {
        // 验证所有三种传输类型都有对应的枚举值
        assertThat(com.jaguarliu.ai.mcp.McpProperties.TransportType.values())
                .hasSize(3)
                .contains(
                        com.jaguarliu.ai.mcp.McpProperties.TransportType.STDIO,
                        com.jaguarliu.ai.mcp.McpProperties.TransportType.SSE,
                        com.jaguarliu.ai.mcp.McpProperties.TransportType.HTTP
                );
    }
}
