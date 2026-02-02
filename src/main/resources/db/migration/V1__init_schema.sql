-- V1__init_schema.sql
-- MiniClaw 初始化数据库表结构

-- 启用 pgvector 扩展（用于后续向量检索）
CREATE EXTENSION IF NOT EXISTS vector;

-- sessions 表：会话管理
CREATE TABLE sessions (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- runs 表：每次执行记录
CREATE TABLE runs (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL REFERENCES sessions(id),
    status VARCHAR(20) NOT NULL DEFAULT 'queued',
    prompt TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- messages 表：消息记录
CREATE TABLE messages (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL REFERENCES sessions(id),
    run_id VARCHAR(36) REFERENCES runs(id),
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 索引
CREATE INDEX idx_runs_session_id ON runs(session_id);
CREATE INDEX idx_runs_status ON runs(status);
CREATE INDEX idx_messages_session_id ON messages(session_id);
CREATE INDEX idx_messages_run_id ON messages(run_id);
CREATE INDEX idx_messages_created_at ON messages(created_at);
