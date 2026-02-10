-- 确保 alias 唯一索引存在（大小写不敏感）
-- 删除旧的普通索引（如果存在）
DROP INDEX IF EXISTS idx_nodes_alias;

-- 创建大小写不敏感的唯一索引
-- SQLite 中 UNIQUE 约束已经在表定义中，这里添加额外的函数索引确保大小写不敏感
CREATE UNIQUE INDEX IF NOT EXISTS idx_nodes_alias_unique ON nodes(LOWER(alias));
