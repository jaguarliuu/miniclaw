package com.jaguarliu.ai.mcp.transport;

import com.jaguarliu.ai.mcp.McpProperties;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Transport 工厂
 * 根据配置创建对应的传输层实例
 * 支持三种传输方式：STDIO, SSE, HTTP
 */
@Slf4j
@Component
public class McpTransportFactory {

    private final McpJsonMapper jsonMapper = McpJsonMapper.getDefault();

    /**
     * 根据配置创建 Transport
     *
     * @param config MCP Server 配置
     * @return Transport 实例
     * @throws IllegalArgumentException 配置无效或不支持的传输类型
     */
    public McpClientTransport createTransport(McpProperties.ServerConfig config) {
        McpProperties.TransportType transport = config.getTransport();

        return switch (transport) {
            case STDIO -> createStdioTransport(config);
            case SSE -> createSseTransport(config);
            case HTTP -> createHttpTransport(config);
        };
    }

    /**
     * 创建 STDIO Transport
     * 适用场景：本地进程通信（如 npx 启动的 MCP 服务器）
     */
    private McpClientTransport createStdioTransport(McpProperties.ServerConfig config) {
        if (config.getCommand() == null || config.getCommand().isBlank()) {
            throw new IllegalArgumentException("STDIO transport: command is required");
        }

        log.info("Creating STDIO transport for command: {} with args: {}",
                config.getCommand(), config.getArgs());

        // 使用 Builder 创建 ServerParameters - builder() 需要 command 参数
        ServerParameters.Builder builder = ServerParameters.builder(config.getCommand());

        // 添加参数
        if (config.getArgs() != null && !config.getArgs().isEmpty()) {
            builder.args(config.getArgs());
        }

        // 设置环境变量
        if (config.getEnv() != null && !config.getEnv().isEmpty()) {
            Map<String, String> envMap = new HashMap<>();
            for (String env : config.getEnv()) {
                String[] parts = env.split("=", 2);
                if (parts.length == 2) {
                    envMap.put(parts[0], parts[1]);
                }
            }
            builder.env(envMap);
        }

        ServerParameters params = builder.build();
        return new StdioClientTransport(params, jsonMapper);
    }

    /**
     * 创建 SSE Transport
     * 适用场景：远程 HTTP 服务器，单向流式数据 (Server-Sent Events)
     */
    private McpClientTransport createSseTransport(McpProperties.ServerConfig config) {
        if (config.getUrl() == null || config.getUrl().isBlank()) {
            throw new IllegalArgumentException("SSE transport: url is required");
        }

        log.info("Creating SSE transport for URL: {}", config.getUrl());

        return HttpClientSseClientTransport.builder(config.getUrl())
                .build();
    }

    /**
     * 创建 HTTP Transport (Streamable-HTTP)
     * 适用场景：远程 HTTP 服务器，双向流式通信
     * 注意：SSE 和 HTTP 在 MCP Java SDK 中使用相同的传输类
     */
    private McpClientTransport createHttpTransport(McpProperties.ServerConfig config) {
        if (config.getUrl() == null || config.getUrl().isBlank()) {
            throw new IllegalArgumentException("HTTP transport: url is required");
        }

        log.info("Creating HTTP transport for URL: {}", config.getUrl());

        // HTTP Streamable transport 使用相同的 HttpClientSseClientTransport
        return HttpClientSseClientTransport.builder(config.getUrl())
                .build();
    }
}
