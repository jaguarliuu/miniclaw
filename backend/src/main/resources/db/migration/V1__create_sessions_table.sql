-- Flyway 迁移脚本：创建 sessions 表
-- 
-- 文件命名规则：V{版本号}__{描述}.sql
-- - V：固定前缀
-- - 1：版本号（数字，递增）
-- - __：双下划线分隔
-- - create_sessions_table：描述（下划线分隔）
-- - .sql：文件后缀
--
-- Flyway 会按照版本号顺序执行迁移脚本

-- 创建 sessions 表
-- 存储 AI Agent 的会话信息
CREATE TABLE sessions (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(255),
    user_id VARCHAR(255) NOT NULL,
    agent_id VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    metadata TEXT
);

-- 创建索引
-- user_id 是高频查询字段，需要索引
CREATE INDEX idx_sessions_user_id ON sessions(user_id);
CREATE INDEX idx_sessions_status ON sessions(status);
CREATE INDEX idx_sessions_created_at ON sessions(created_at);

-- 添加注释
COMMENT ON TABLE sessions IS 'AI Agent 会话表';
COMMENT ON COLUMN sessions.id IS '会话唯一标识（UUID）';
COMMENT ON COLUMN sessions.title IS '会话标题';
COMMENT ON COLUMN sessions.user_id IS '用户标识';
COMMENT ON COLUMN sessions.agent_id IS 'Agent 标识';
COMMENT ON COLUMN sessions.status IS '会话状态：ACTIVE/ARCHIVED/ENDED';
COMMENT ON COLUMN sessions.created_at IS '创建时间';
COMMENT ON COLUMN sessions.updated_at IS '更新时间';
COMMENT ON COLUMN sessions.deleted IS '软删除标记';
COMMENT ON COLUMN sessions.metadata IS '扩展元数据（JSON）';
