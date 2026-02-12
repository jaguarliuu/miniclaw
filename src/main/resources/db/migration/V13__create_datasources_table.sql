-- 创建数据源表
CREATE TABLE IF NOT EXISTS datasources (
    id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    type VARCHAR(20) NOT NULL,
    connection_config TEXT NOT NULL,
    security_config TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    last_tested_at TIMESTAMP,
    last_error VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_datasources_status ON datasources(status);
CREATE INDEX IF NOT EXISTS idx_datasources_name ON datasources(name);
CREATE INDEX IF NOT EXISTS idx_datasources_type ON datasources(type);

