-- MiniClaw 数据库初始化脚本
-- 
-- 这个脚本在 PostgreSQL 首次启动时自动执行
-- 用于创建 pgvector 扩展和其他必要的数据库配置

-- 创建 pgvector 扩展
-- pgvector 是 PostgreSQL 的向量相似度搜索扩展
-- 用于 Memory 系统的语义检索
CREATE EXTENSION IF NOT EXISTS vector;

-- 可选：设置日志级别
-- ALTER SYSTEM SET log_statement = 'all';

-- 验证扩展已安装
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
        RAISE NOTICE 'pgvector extension installed successfully';
    ELSE
        RAISE EXCEPTION 'Failed to install pgvector extension';
    END IF;
END $$;
