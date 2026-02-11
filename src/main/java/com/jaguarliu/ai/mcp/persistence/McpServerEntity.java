package com.jaguarliu.ai.mcp.persistence;

import com.jaguarliu.ai.mcp.McpProperties;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * MCP Server 配置实体
 * 支持双数据库：SQLite（桌面端）和 PostgreSQL（服务器端）
 */
@Data
@Entity
@Table(name = "mcp_servers")
public class McpServerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Server 名称（唯一标识）
     */
    @Column(nullable = false, unique = true)
    private String name;

    /**
     * 传输类型：STDIO, SSE, HTTP
     */
    @Column(name = "transport_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private McpProperties.TransportType transportType;

    /**
     * STDIO: 可执行命令（如 "npx"）
     */
    @Column(length = 500)
    private String command;

    /**
     * STDIO: 命令参数列表
     * SQLite: 存储为 TEXT (JSON)
     * PostgreSQL: 存储为 JSONB
     */
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> args = new ArrayList<>();

    /**
     * STDIO: 工作目录
     */
    @Column(name = "working_dir", length = 500)
    private String workingDir;

    /**
     * STDIO: 环境变量列表
     * SQLite: 存储为 TEXT (JSON)
     * PostgreSQL: 存储为 JSONB
     */
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> env = new ArrayList<>();

    /**
     * SSE/HTTP: 服务器端点 URL
     */
    @Column(length = 1000)
    private String url;

    /**
     * 是否启用
     * SQLite: INTEGER (1/0)
     * PostgreSQL: BOOLEAN
     */
    @Column(nullable = false)
    private Boolean enabled = true;

    /**
     * 工具名称前缀（避免冲突）
     */
    @Column(name = "tool_prefix", length = 100)
    private String toolPrefix = "";

    /**
     * 是否需要 HITL 确认此服务器的所有工具
     * SQLite: INTEGER (1/0)
     * PostgreSQL: BOOLEAN
     */
    @Column(name = "requires_hitl", nullable = false)
    private Boolean requiresHitl = false;

    /**
     * 需要 HITL 确认的工具名称列表（不含前缀）
     * SQLite: 存储为 TEXT (JSON)
     * PostgreSQL: 存储为 JSONB
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "hitl_tools")
    private List<String> hitlTools = new ArrayList<>();

    /**
     * 请求超时时间（秒）
     */
    @Column(name = "request_timeout_seconds", nullable = false)
    private Integer requestTimeoutSeconds = 30;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 自动设置创建时间和更新时间
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    /**
     * 自动更新时间
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 转换为配置对象
     */
    public McpProperties.ServerConfig toConfig() {
        var config = new McpProperties.ServerConfig();
        config.setName(this.name);
        config.setTransport(this.transportType);
        config.setCommand(this.command);
        config.setArgs(this.args != null ? new ArrayList<>(this.args) : new ArrayList<>());
        config.setWorkingDir(this.workingDir);
        config.setEnv(this.env != null ? new ArrayList<>(this.env) : new ArrayList<>());
        config.setUrl(this.url);
        config.setEnabled(this.enabled);
        config.setToolPrefix(this.toolPrefix != null ? this.toolPrefix : "");
        config.setRequiresHitl(this.requiresHitl);
        config.setHitlTools(this.hitlTools != null ? new ArrayList<>(this.hitlTools) : new ArrayList<>());
        config.setRequestTimeoutSeconds(this.requestTimeoutSeconds);
        return config;
    }

    /**
     * 从配置对象创建实体
     */
    public static McpServerEntity fromConfig(McpProperties.ServerConfig config) {
        var entity = new McpServerEntity();
        entity.setName(config.getName());
        entity.setTransportType(config.getTransport());
        entity.setCommand(config.getCommand());
        entity.setArgs(config.getArgs() != null ? new ArrayList<>(config.getArgs()) : new ArrayList<>());
        entity.setWorkingDir(config.getWorkingDir());
        entity.setEnv(config.getEnv() != null ? new ArrayList<>(config.getEnv()) : new ArrayList<>());
        entity.setUrl(config.getUrl());
        entity.setEnabled(config.isEnabled());
        entity.setToolPrefix(config.getToolPrefix() != null ? config.getToolPrefix() : "");
        entity.setRequiresHitl(config.isRequiresHitl());
        entity.setHitlTools(config.getHitlTools() != null ? new ArrayList<>(config.getHitlTools()) : new ArrayList<>());
        entity.setRequestTimeoutSeconds(config.getRequestTimeoutSeconds());
        return entity;
    }
}
