package com.jaguarliu.ai.mcp;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * MCP 配置属性
 * 支持三种传输方式：STDIO, SSE, HTTP
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "mcp")
public class McpProperties {

    /**
     * MCP Server 配置列表
     */
    private List<ServerConfig> servers = new ArrayList<>();

    /**
     * 健康检查配置
     */
    private HealthCheckConfig healthCheck = new HealthCheckConfig();

    /**
     * 传输类型枚举
     */
    public enum TransportType {
        /**
         * STDIO - 标准输入输出（进程间通信）
         * 适用场景：本地 MCP 服务器（如 npx 启动的服务）
         */
        STDIO,

        /**
         * SSE - Server-Sent Events（服务器推送事件）
         * 适用场景：远程 HTTP 服务器，单向流式数据
         */
        SSE,

        /**
         * HTTP - Streamable HTTP
         * 适用场景：远程 HTTP 服务器，双向流式通信
         */
        HTTP
    }

    @Data
    public static class ServerConfig {
        /**
         * Server 名称（唯一标识）
         */
        private String name;

        /**
         * 传输类型：stdio, sse, http
         */
        private TransportType transport = TransportType.STDIO;

        /**
         * STDIO: 可执行命令（如 "npx"）
         */
        private String command;

        /**
         * STDIO: 命令参数列表
         */
        private List<String> args = new ArrayList<>();

        /**
         * STDIO: 工作目录
         */
        private String workingDir;

        /**
         * STDIO: 环境变量
         */
        private List<String> env = new ArrayList<>();

        /**
         * SSE/HTTP: 服务器端点 URL
         */
        private String url;

        /**
         * 是否启用
         */
        private boolean enabled = true;

        /**
         * 工具名称前缀（避免冲突）
         */
        private String toolPrefix = "";

        /**
         * 是否需要 HITL 确认此服务器的所有工具
         */
        private boolean requiresHitl = false;

        /**
         * 需要 HITL 确认的工具名称列表（不含前缀）
         */
        private List<String> hitlTools = new ArrayList<>();

        /**
         * 请求超时时间（秒）
         */
        private int requestTimeoutSeconds = 30;
    }

    @Data
    public static class HealthCheckConfig {
        /**
         * 健康检查间隔（秒）
         */
        private int intervalSeconds = 60;

        /**
         * 重连最大尝试次数
         */
        private int maxRetries = 3;

        /**
         * 重连退避基数（秒）
         */
        private int retryBackoffSeconds = 5;
    }
}
