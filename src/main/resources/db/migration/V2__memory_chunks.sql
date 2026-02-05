-- V2__memory_chunks.sql
-- Memory 子系统：全局记忆 chunk 索引表（派生索引，可从 Markdown 重建）
-- 注意：没有 session_id！记忆是全局的、跨会话的。

CREATE TABLE memory_chunks (
    id          VARCHAR(36) PRIMARY KEY,
    file_path   TEXT        NOT NULL,  -- 相对于 workspace/memory/ 的路径
    line_start  INT         NOT NULL,  -- chunk 起始行 (1-based)
    line_end    INT         NOT NULL,  -- chunk 结束行 (1-based，含)
    content     TEXT        NOT NULL,  -- chunk 原文
    embedding   vector(1536),          -- 向量（可为 NULL，无 embedding provider 时）
    tsv         tsvector,              -- 全文检索向量（始终填充）
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 向量检索索引（IVFFlat，适合中小数据量）
-- 注意：IVFFlat 需要先有数据才能创建，这里用 HNSW 替代（支持空表）
CREATE INDEX idx_memory_chunks_embedding ON memory_chunks
    USING hnsw (embedding vector_cosine_ops);

-- 全文检索索引（GIN，始终可用）
CREATE INDEX idx_memory_chunks_tsv ON memory_chunks USING GIN (tsv);

-- 文件路径索引（用于按文件更新/删除）
CREATE INDEX idx_memory_chunks_file_path ON memory_chunks (file_path);

-- tsvector 自动更新触发器
-- 使用 'simple' 配置，对中英文都友好
CREATE OR REPLACE FUNCTION memory_chunks_tsv_trigger() RETURNS trigger AS $$
BEGIN
    NEW.tsv := to_tsvector('simple', NEW.content);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_memory_chunks_tsv
    BEFORE INSERT OR UPDATE ON memory_chunks
    FOR EACH ROW EXECUTE FUNCTION memory_chunks_tsv_trigger();
