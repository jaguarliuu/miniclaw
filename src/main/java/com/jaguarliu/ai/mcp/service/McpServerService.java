package com.jaguarliu.ai.mcp.service;

import com.jaguarliu.ai.mcp.McpProperties;
import com.jaguarliu.ai.mcp.client.McpClientManager;
import com.jaguarliu.ai.mcp.persistence.McpServerEntity;
import com.jaguarliu.ai.mcp.persistence.McpServerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * MCP Server 动态管理服务
 * 支持运行时添加、更新、删除 MCP Server 配置
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpServerService {

    private final McpServerRepository repository;
    private final McpClientManager clientManager;

    /**
     * 创建并连接新的 MCP Server
     */
    @Transactional
    public McpServerEntity createServer(McpProperties.ServerConfig config) {
        log.info("Creating MCP server: {}", config.getName());

        // 检查名称是否已存在
        if (repository.existsByName(config.getName())) {
            throw new IllegalArgumentException("MCP server with name '" + config.getName() + "' already exists");
        }

        // 保存到数据库
        var entity = McpServerEntity.fromConfig(config);
        entity = repository.save(entity);

        // 如果启用，立即连接
        if (config.isEnabled()) {
            try {
                clientManager.connectServer(config);
                log.info("MCP server created and connected: {}", config.getName());
            } catch (Exception e) {
                log.error("Failed to connect MCP server after creation: {}", config.getName(), e);
                // 不回滚，允许保存配置但连接失败
            }
        }

        return entity;
    }

    /**
     * 更新 MCP Server 配置
     */
    @Transactional
    public McpServerEntity updateServer(Long id, McpProperties.ServerConfig config) {
        log.info("Updating MCP server ID {}: {}", id, config.getName());

        var entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("MCP server not found: " + id));

        String oldName = entity.getName();
        boolean wasEnabled = entity.getEnabled();

        // 更新实体
        entity.setName(config.getName());
        entity.setTransportType(config.getTransport());
        entity.setCommand(config.getCommand());
        entity.setArgs(config.getArgs());
        entity.setWorkingDir(config.getWorkingDir());
        entity.setEnv(config.getEnv());
        entity.setUrl(config.getUrl());
        entity.setEnabled(config.isEnabled());
        entity.setToolPrefix(config.getToolPrefix());
        entity.setRequiresHitl(config.isRequiresHitl());
        entity.setHitlTools(config.getHitlTools());
        entity.setRequestTimeoutSeconds(config.getRequestTimeoutSeconds());

        entity = repository.save(entity);

        // 处理连接变化
        if (wasEnabled) {
            clientManager.disconnectServer(oldName);
        }

        if (config.isEnabled()) {
            try {
                clientManager.connectServer(config);
            } catch (Exception e) {
                log.error("Failed to connect MCP server after update: {}", config.getName(), e);
            }
        }

        return entity;
    }

    /**
     * 删除 MCP Server
     */
    @Transactional
    public void deleteServer(Long id) {
        log.info("Deleting MCP server ID: {}", id);

        var entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("MCP server not found: " + id));

        // 断开连接
        if (entity.getEnabled()) {
            clientManager.disconnectServer(entity.getName());
        }

        // 删除配置
        repository.delete(entity);
        log.info("MCP server deleted: {}", entity.getName());
    }

    /**
     * 测试连接
     */
    public boolean testConnection(McpProperties.ServerConfig config) {
        log.info("Testing connection to MCP server: {}", config.getName());

        try {
            return clientManager.testConnection(config);
        } catch (Exception e) {
            log.error("Connection test failed for: {}", config.getName(), e);
            return false;
        }
    }

    /**
     * 列出所有服务器
     */
    public List<McpServerEntity> listServers() {
        return repository.findAll();
    }

    /**
     * 列出所有启用的服务器
     */
    public List<McpServerEntity> listEnabledServers() {
        return repository.findByEnabledTrue();
    }

    /**
     * 获取服务器详情
     */
    public McpServerEntity getServer(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("MCP server not found: " + id));
    }
}
