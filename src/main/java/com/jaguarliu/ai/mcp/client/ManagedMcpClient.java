package com.jaguarliu.ai.mcp.client;

import com.jaguarliu.ai.mcp.McpProperties;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 托管的 MCP 客户端
 * 包装 McpSyncClient 并提供连接状态管理、健康检查等功能
 * 使用同步客户端（McpSyncClient）简化实现
 */
@Slf4j
@Getter
public class ManagedMcpClient {

    private final String name;
    private final McpProperties.ServerConfig config;
    private McpSyncClient client;
    private final Duration requestTimeout;
    private final AtomicBoolean connected = new AtomicBoolean(false);

    /**
     * 构造函数（用于单元测试）
     */
    public ManagedMcpClient(
            String name,
            McpProperties.ServerConfig config,
            Duration requestTimeout
    ) {
        this.name = name;
        this.config = config;
        this.requestTimeout = requestTimeout;
        this.client = null; // 延迟创建
    }

    /**
     * 创建托管客户端
     * 使用 McpClient.sync() 创建同步客户端
     *
     * 注意：协议版本配置在 SDK 0.17.2 中需要特殊处理
     * 将在实际集成时通过 transport 或其他方式配置
     */
    public static ManagedMcpClient create(
            McpProperties.ServerConfig config,
            McpClientTransport transport
    ) {
        log.info("Creating MCP sync client for server: {}", config.getName());

        Duration timeout = Duration.ofSeconds(config.getRequestTimeoutSeconds());

        // 创建托管客户端实例
        ManagedMcpClient managedClient = new ManagedMcpClient(
                config.getName(),
                config,
                timeout
        );

        // 构建真正的 MCP 同步客户端
        // TODO: 解决协议版本配置问题（SDK 0.17.2）
        try {
            McpSyncClient syncClient = McpClient.sync(transport)
                    .requestTimeout(timeout)
                    .clientInfo(new McpSchema.Implementation("miniclaw", "1.0.0"))
                    .capabilities(new McpSchema.ClientCapabilities(
                            null, // experimental capabilities
                            null, // roots
                            new McpSchema.ClientCapabilities.Sampling(), // sampling support
                            null  // elicitation
                    ))
                    .build();

            managedClient.client = syncClient;
        } catch (Exception e) {
            log.error("Failed to build MCP sync client for: {}", config.getName(), e);
            throw new RuntimeException("Failed to build MCP client: " + config.getName(), e);
        }

        return managedClient;
    }

    /**
     * 初始化连接
     */
    public void initialize() {
        if (client == null) {
            throw new IllegalStateException("MCP client not initialized");
        }

        log.info("Initializing MCP client: {}", name);
        try {
            client.initialize();
            markConnected();
            log.info("MCP client initialized successfully: {}", name);
        } catch (Exception e) {
            log.error("Failed to initialize MCP client: {}", name, e);
            markDisconnected();
            throw new RuntimeException("Failed to initialize MCP client: " + name, e);
        }
    }

    /**
     * 关闭连接
     */
    public void close() {
        if (client == null) {
            log.warn("MCP client already closed or not initialized: {}", name);
            return;
        }

        log.info("Closing MCP client: {}", name);
        try {
            client.close();
        } catch (Exception e) {
            log.warn("Error closing MCP client: {}", name, e);
        } finally {
            markDisconnected();
        }
    }

    /**
     * 标记为已连接
     */
    public void markConnected() {
        connected.set(true);
    }

    /**
     * 标记为已断开
     */
    public void markDisconnected() {
        connected.set(false);
    }

    /**
     * 检查是否已连接
     */
    public boolean isConnected() {
        return connected.get();
    }

    /**
     * 获取工具名称前缀
     */
    public String getToolPrefix() {
        return config.getToolPrefix() != null ? config.getToolPrefix() : "";
    }
}
