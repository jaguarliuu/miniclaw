-- V2__memory_chunks.sql
-- Memory 子系统：全局记忆 chunk 索引表（SQLite 版）
-- 使用 FTS5 替代 PG 的 tsvector + GIN
-- 无向量检索支持（SQLite 无 pgvector）

CREATE TABLE memory_chunks (
    id          VARCHAR(36) PRIMARY KEY,
    file_path   TEXT        NOT NULL,
    line_start  INT         NOT NULL,
    line_end    INT         NOT NULL,
    content     TEXT        NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_memory_chunks_file_path ON memory_chunks(file_path);

-- FTS5 虚拟表（替代 tsvector + GIN 索引）
CREATE VIRTUAL TABLE memory_chunks_fts USING fts5(chunk_id UNINDEXED, content, tokenize='unicode61');

-- 自动同步触发器（替代 PG 的 plpgsql 触发器）
CREATE TRIGGER trg_mc_fts_insert AFTER INSERT ON memory_chunks BEGIN
    INSERT INTO memory_chunks_fts(chunk_id, content) VALUES (NEW.id, NEW.content);
END;

CREATE TRIGGER trg_mc_fts_delete AFTER DELETE ON memory_chunks BEGIN
    DELETE FROM memory_chunks_fts WHERE chunk_id = OLD.id;
END;

CREATE TRIGGER trg_mc_fts_update AFTER UPDATE ON memory_chunks BEGIN
    DELETE FROM memory_chunks_fts WHERE chunk_id = OLD.id;
    INSERT INTO memory_chunks_fts(chunk_id, content) VALUES (NEW.id, NEW.content);
END;
