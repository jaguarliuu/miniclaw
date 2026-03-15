-- MiniClaw 数据库初始化迁移脚本
-- 
-- Flyway 迁移脚本命名规则：V{版本号}__{描述}.sql
-- 例如：V1__init_schema.sql
-- 
-- 版本号必须是递增的，Flyway 会按顺序执行

-- ============================================================
-- Session 会话表
-- ============================================================
-- 为什么需要 Session 表？
-- - AI Agent 的对话是有状态的，需要持久化保存
-- - 一个 Session 代表一次完整的对话会话
-- - 后续的 Memory 系统需要基于 Session 进行检索

CREATE TABLE sessions (
    -- 主键：使用 UUID 而不是自增 ID
    -- 优点：分布式系统不冲突、可在客户端生成、更安全
    id VARCHAR(36) PRIMARY KEY,
    
    -- 会话标题（可选）
    title VARCHAR(255),
    
    -- 用户标识
    user_id VARCHAR(255) NOT NULL,
    
    -- Agent 标识（区分不同用途的 Agent）
    agent_id VARCHAR(255),
    
    -- 会话状态：ACTIVE（活跃）、ARCHIVED（归档）、ENDED（结束）
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    
    -- 创建时间（自动填充）
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- 更新时间（每次更新自动更新）
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- 软删除标记（为什么用软删除？用户可能误删，需要恢复）
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    
    -- 扩展元数据（JSON 格式，存储额外的会话信息）
    metadata TEXT
);

-- 索引：加速按用户查询
CREATE INDEX idx_sessions_user_id ON sessions(user_id);

-- 索引：加速按状态查询
CREATE INDEX idx_sessions_status ON sessions(status);

-- 索引：加速按创建时间排序
CREATE INDEX idx_sessions_created_at ON sessions(created_at DESC);

-- 注释：表说明
COMMENT ON TABLE sessions IS 'AI Agent 会话表';
COMMENT ON COLUMN sessions.id IS '会话唯一标识（UUID）';
COMMENT ON COLUMN sessions.user_id IS '用户标识';
COMMENT ON COLUMN sessions.status IS '会话状态：ACTIVE/ARCHIVED/ENDED';
COMMENT ON COLUMN sessions.deleted IS '软删除标记';

-- ============================================================
-- Message 消息表
-- ============================================================
-- 为什么需要 Message 表？
-- - 会话由多条消息组成，需要单独存储
-- - 每条消息有独立的角色（用户/助手/系统）
-- - 后续需要基于消息内容构建 Memory 系统

CREATE TABLE messages (
    -- 主键
    id VARCHAR(36) PRIMARY KEY,
    
    -- 外键：关联到 Session
    session_id VARCHAR(36) NOT NULL,
    
    -- 消息角色：USER（用户）、ASSISTANT（助手）、SYSTEM（系统）、TOOL（工具）
    role VARCHAR(20) NOT NULL,
    
    -- 消息内容
    content TEXT NOT NULL,
    
    -- Token 数量（用于成本计算）
    token_count INTEGER,
    
    -- 模型名称（记录这条消息是用哪个模型生成的）
    model VARCHAR(100),
    
    -- 创建时间
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- 扩展元数据（存储工具调用参数和结果等）
    metadata TEXT,
    
    -- 外键约束
    CONSTRAINT fk_messages_session 
        FOREIGN KEY (session_id) 
        REFERENCES sessions(id) 
        ON DELETE CASCADE
);

-- 索引：加速按会话查询消息
CREATE INDEX idx_messages_session_id ON messages(session_id);

-- 索引：加速按创建时间排序
CREATE INDEX idx_messages_created_at ON messages(session_id, created_at);

-- 索引：加速按角色查询
CREATE INDEX idx_messages_role ON messages(role);

-- 注释：表说明
COMMENT ON TABLE messages IS 'AI Agent 消息表';
COMMENT ON COLUMN messages.session_id IS '所属会话ID';
COMMENT ON COLUMN messages.role IS '消息角色：USER/ASSISTANT/SYSTEM/TOOL';
COMMENT ON COLUMN messages.content IS '消息内容';
COMMENT ON COLUMN messages.token_count IS 'Token数量（用于成本计算）';

-- ============================================================
-- Memory Chunk 记忆分块表
-- ============================================================
-- 为什么需要 Memory Chunk 表？
-- - Memory 系统需要将长文本分块存储
-- - 每个块需要有向量表示，用于语义检索
-- - pgvector 扩展提供向量存储和检索能力

CREATE TABLE memory_chunks (
    -- 主键
    id VARCHAR(36) PRIMARY KEY,
    
    -- 关联的会话（可选，全局记忆可以为空）
    session_id VARCHAR(36),
    
    -- 来源类型：MEMORY_FILE（记忆文件）、CONVERSATION（对话）、DOCUMENT（文档）
    source_type VARCHAR(50) NOT NULL,
    
    -- 来源路径或标识
    source_path VARCHAR(500),
    
    -- 分块内容
    content TEXT NOT NULL,
    
    -- 向量嵌入（使用 pgvector 的 vector 类型）
    -- 1536 是 OpenAI text-embedding-ada-002 的维度
    -- 如果使用其他模型，需要调整维度
    embedding vector(1536),
    
    -- 创建时间
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- 外键约束（允许为空）
    CONSTRAINT fk_memory_chunks_session 
        FOREIGN KEY (session_id) 
        REFERENCES sessions(id) 
        ON DELETE SET NULL
);

-- 向量索引：加速相似度检索
-- HNSW（Hierarchical Navigable Small World）是 pgvector 提供的高效向量索引
CREATE INDEX idx_memory_chunks_embedding 
    ON memory_chunks 
    USING hnsw (embedding vector_cosine_ops);

-- 索引：加速按来源查询
CREATE INDEX idx_memory_chunks_source ON memory_chunks(source_type, source_path);

-- 注释：表说明
COMMENT ON TABLE memory_chunks IS 'Memory 分块表（支持向量检索）';
COMMENT ON COLUMN memory_chunks.embedding IS '向量嵌入（用于语义检索）';

-- ============================================================
-- Cron Job 定时任务表
-- ============================================================
-- 为什么需要 Cron Job 表？
-- - Quartz 调度器需要持久化存储任务配置
-- - 支持分布式部署时任务不重复执行

CREATE TABLE cron_jobs (
    -- 主键
    id VARCHAR(36) PRIMARY KEY,
    
    -- 任务名称
    job_name VARCHAR(255) NOT NULL,
    
    -- Cron 表达式（如：0 0 9 * * ? 表示每天 9 点执行）
    cron_expression VARCHAR(100) NOT NULL,
    
    -- 任务类型
    job_type VARCHAR(50) NOT NULL,
    
    -- 任务配置（JSON 格式）
    job_config TEXT,
    
    -- 是否启用
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    
    -- 创建时间
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- 更新时间
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- 唯一约束：同一个任务名称不能重复
    CONSTRAINT uk_cron_jobs_name UNIQUE (job_name)
);

-- 注释：表说明
COMMENT ON TABLE cron_jobs IS '定时任务配置表';
COMMENT ON COLUMN cron_jobs.cron_expression IS 'Cron 表达式';
