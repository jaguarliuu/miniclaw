package com.jaguarliu.ai.mcp.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MCP Server 配置仓库
 */
@Repository
public interface McpServerRepository extends JpaRepository<McpServerEntity, Long> {

    /**
     * 查找所有已启用的 MCP Server
     */
    List<McpServerEntity> findByEnabledTrue();

    /**
     * 根据名称查找 MCP Server
     */
    Optional<McpServerEntity> findByName(String name);

    /**
     * 检查指定名称的 MCP Server 是否存在
     */
    boolean existsByName(String name);
}
