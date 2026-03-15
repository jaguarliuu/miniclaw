-- Flyway 迁移脚本：创建 memory_chunks 表（向量检索）
-- 
-- 版本号：3（在 V2 之后执行）
-- 这个表用于 Memory 系统的语义检索

-- 创建 memory_chunks 表
-- 存储分块后的记忆向量和原始文本
CREATE TABLE memory_chunks (
    id VARCHAR(36) PRIMARY KEY,
    source_type VARCHAR(50) NOT NULL,
    source_path VARCHAR(500) NOT NULL,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    embedding vector(1536),  -- OpenAI embedding 维度是 1536
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- 唯一约束：同一来源的同一块只能存在一次
    CONSTRAINT uk_memory_chunks_source UNIQUE (source_type, source_path, chunk_index)
);

-- 创建向量索引（使用 pgvector 的 IVFFlat 索引）
-- vector_cosine_ops：余弦相似度
-- lists = 100：聚类中心数量（数据量大时可以增加）
CREATE INDEX idx_memory_chunks_embedding ON memory_chunks 
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

-- 创建普通索引
CREATE INDEX idx_memory_chunks_source ON memory_chunks(source_type, source_path);

-- 添加注释
COMMENT ON TABLE memory_chunks IS 'Memory 分块向量索引表';
COMMENT ON COLUMN memory_chunks.id IS '分块唯一标识（UUID）';
COMMENT ON COLUMN memory_chunks.source_type IS '来源类型：MEMORY/DIARY/SKILL';
COMMENT ON COLUMN memory_chunks.source_path IS '来源路径（文件路径）';
COMMENT ON COLUMN memory_chunks.chunk_index IS '分块序号';
COMMENT ON COLUMN memory_chunks.content IS '分块文本内容';
COMMENT ON COLUMN memory_chunks.embedding IS '向量嵌入（1536维）';
COMMENT ON COLUMN memory_chunks.created_at IS '创建时间';
