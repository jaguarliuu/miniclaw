-- MCP Server 配置表 (PostgreSQL)
CREATE TABLE mcp_servers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    transport_type VARCHAR(50) NOT NULL,  -- STDIO, SSE, HTTP

    -- STDIO 配置
    command VARCHAR(500),
    args JSONB,  -- JSON array (native JSONB in PostgreSQL)
    working_dir VARCHAR(500),
    env JSONB,   -- JSON array (native JSONB in PostgreSQL)

    -- SSE/HTTP 配置
    url VARCHAR(1000),

    -- 通用配置
    enabled BOOLEAN NOT NULL DEFAULT true,
    tool_prefix VARCHAR(100) DEFAULT '',
    requires_hitl BOOLEAN NOT NULL DEFAULT false,
    hitl_tools JSONB,  -- JSON array (native JSONB in PostgreSQL)
    request_timeout_seconds INTEGER NOT NULL DEFAULT 30,

    -- 元数据
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_transport_config CHECK (
        (transport_type = 'STDIO' AND command IS NOT NULL) OR
        (transport_type IN ('SSE', 'HTTP') AND url IS NOT NULL)
    )
);

-- 索引
CREATE INDEX idx_mcp_servers_enabled ON mcp_servers(enabled);
CREATE INDEX idx_mcp_servers_name ON mcp_servers(name);
