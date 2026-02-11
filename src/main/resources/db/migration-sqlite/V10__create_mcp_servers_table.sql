-- MCP Server 配置表 (SQLite)
CREATE TABLE mcp_servers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(255) NOT NULL UNIQUE,
    transport_type VARCHAR(50) NOT NULL,  -- STDIO, SSE, HTTP

    -- STDIO 配置
    command VARCHAR(500),
    args TEXT,  -- JSON array (stored as TEXT in SQLite)
    working_dir VARCHAR(500),
    env TEXT,   -- JSON array (stored as TEXT in SQLite)

    -- SSE/HTTP 配置
    url VARCHAR(1000),

    -- 通用配置
    enabled INTEGER NOT NULL DEFAULT 1,  -- SQLite uses INTEGER for BOOLEAN
    tool_prefix VARCHAR(100) DEFAULT '',
    requires_hitl INTEGER NOT NULL DEFAULT 0,  -- SQLite uses INTEGER for BOOLEAN
    hitl_tools TEXT,  -- JSON array (stored as TEXT in SQLite)
    request_timeout_seconds INTEGER NOT NULL DEFAULT 30,

    -- 元数据
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_transport_config CHECK (
        (transport_type = 'STDIO' AND command IS NOT NULL) OR
        (transport_type IN ('SSE', 'HTTP') AND url IS NOT NULL)
    )
);

-- 索引
CREATE INDEX idx_mcp_servers_enabled ON mcp_servers(enabled);
CREATE INDEX idx_mcp_servers_name ON mcp_servers(name);
