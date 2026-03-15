-- Flyway 迁移脚本：创建 messages 表
-- 
-- 版本号：2（在 V1 之后执行）

-- 创建 messages 表
-- 存储 AI Agent 对话中的每条消息
CREATE TABLE messages (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    role VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    token_count INTEGER,
    model VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata TEXT,
    
    -- 外键约束：消息必须属于有效的会话
    CONSTRAINT fk_messages_session 
        FOREIGN KEY (session_id) 
        REFERENCES sessions(id) 
        ON DELETE CASCADE
);

-- 创建索引
-- session_id 是高频查询字段，需要索引
CREATE INDEX idx_messages_session_id ON messages(session_id);
CREATE INDEX idx_messages_role ON messages(role);
CREATE INDEX idx_messages_created_at ON messages(created_at);

-- 添加注释
COMMENT ON TABLE messages IS 'AI Agent 消息表';
COMMENT ON COLUMN messages.id IS '消息唯一标识（UUID）';
COMMENT ON COLUMN messages.session_id IS '所属会话ID';
COMMENT ON COLUMN messages.role IS '消息角色：USER/ASSISTANT/SYSTEM/TOOL';
COMMENT ON COLUMN messages.content IS '消息内容';
COMMENT ON COLUMN messages.token_count IS 'Token 数量（用于成本计算）';
COMMENT ON COLUMN messages.model IS '生成该消息的模型名称';
COMMENT ON COLUMN messages.created_at IS '创建时间';
COMMENT ON COLUMN messages.metadata IS '扩展元数据（JSON）';
