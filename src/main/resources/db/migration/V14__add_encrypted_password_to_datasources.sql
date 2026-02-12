-- 为数据源表添加加密密码字段
ALTER TABLE datasources ADD COLUMN encrypted_password TEXT;
ALTER TABLE datasources ADD COLUMN password_iv VARCHAR(44);
