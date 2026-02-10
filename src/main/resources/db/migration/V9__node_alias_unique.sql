-- PostgreSQL: 确保 alias 唯一索引（大小写不敏感）
DROP INDEX IF EXISTS idx_nodes_alias;
CREATE UNIQUE INDEX IF NOT EXISTS idx_nodes_alias_unique ON nodes(LOWER(alias));
