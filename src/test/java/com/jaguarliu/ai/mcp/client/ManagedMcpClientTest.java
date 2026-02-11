package com.jaguarliu.ai.mcp.client;

import com.jaguarliu.ai.mcp.McpProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ManagedMcpClient 的单元测试
 * 注意：这里主要测试客户端的元数据管理和状态跟踪
 * 真正的 MCP 客户端连接测试在集成测试中进行
 */
class ManagedMcpClientTest {

    @Test
    void shouldCreateManagedClientWithoutRealConnection() {
        var config = new McpProperties.ServerConfig();
        config.setName("test-server");
        config.setTransport(McpProperties.TransportType.STDIO);
        config.setCommand("npx");
        config.setRequestTimeoutSeconds(30);

        // 创建托管客户端（不初始化真实连接）
        var client = new ManagedMcpClient("test-server", config, Duration.ofSeconds(30));

        assertThat(client).isNotNull();
        assertThat(client.getName()).isEqualTo("test-server");
        assertThat(client.isConnected()).isFalse();
        assertThat(client.getRequestTimeout()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void shouldTrackConnectionState() {
        var config = new McpProperties.ServerConfig();
        config.setName("test-server");

        var client = new ManagedMcpClient("test-server", config, Duration.ofSeconds(30));

        assertThat(client.isConnected()).isFalse();

        client.markConnected();
        assertThat(client.isConnected()).isTrue();

        client.markDisconnected();
        assertThat(client.isConnected()).isFalse();
    }

    @Test
    void shouldGetToolPrefix() {
        var config = new McpProperties.ServerConfig();
        config.setName("test-server");
        config.setToolPrefix("fs_");

        var client = new ManagedMcpClient("test-server", config, Duration.ofSeconds(30));

        assertThat(client.getToolPrefix()).isEqualTo("fs_");
    }

    @Test
    void shouldGetEmptyToolPrefixWhenNotSet() {
        var config = new McpProperties.ServerConfig();
        config.setName("test-server");

        var client = new ManagedMcpClient("test-server", config, Duration.ofSeconds(30));

        assertThat(client.getToolPrefix()).isEmpty();
    }

    @Test
    void shouldGetConfiguredTimeout() {
        var config = new McpProperties.ServerConfig();
        config.setName("test-server");
        config.setRequestTimeoutSeconds(60);

        var client = new ManagedMcpClient("test-server", config, Duration.ofSeconds(60));

        assertThat(client.getRequestTimeout()).isEqualTo(Duration.ofSeconds(60));
    }
}
