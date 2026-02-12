## MySQL SQL 规范

### 标识符与引用
- 使用反引号引用标识符: `` `table_name`.`column_name` ``
- 保留字作为列名时必须引用

### 分页
- `LIMIT n`（不带 OFFSET）或 `LIMIT offset, count`
- 禁止无 LIMIT 的全表扫描

### 禁止事项
- 禁止 `SELECT *`，必须明确列出所需字段
- 禁止写入操作（INSERT/UPDATE/DELETE/DROP）

### 类型与函数
- NULL 处理: `IFNULL(expr, default)` 或 `COALESCE(expr, default)`
- 日期格式化: `DATE_FORMAT(date, '%Y-%m-%d')`
- 日期提取: `YEAR(date)`, `MONTH(date)`, `DAY(date)`
- 日期差值: `DATEDIFF(end, start)`, `TIMESTAMPDIFF(HOUR, start, end)`
- 日期运算: `DATE_SUB(NOW(), INTERVAL 7 DAY)`, `DATE_ADD(date, INTERVAL 1 MONTH)`
- 字符串聚合: `GROUP_CONCAT(column ORDER BY column SEPARATOR ',')`
- 模糊匹配: `LIKE`（默认不区分大小写，取决于 collation）
- 条件表达式: `CASE WHEN ... THEN ... ELSE ... END` 或 `IF(condition, true_val, false_val)`
- 类型转换: `CAST(expr AS SIGNED)`, `CAST(expr AS DECIMAL(10,2))`, `CONVERT(expr, type)`
- JSON 字段: `JSON_EXTRACT(column, '$.key')` 或 `column->'$.key'`

### 聚合与窗口函数（MySQL 8.0+）
- `GROUP BY` 必须包含 SELECT 中所有非聚合列（除非 ONLY_FULL_GROUP_BY 关闭）
- 排名: `ROW_NUMBER() OVER (ORDER BY ...)`, `RANK()`, `DENSE_RANK()`
- 分位: `NTILE(n) OVER (ORDER BY ...)`
- 分组内排名: `ROW_NUMBER() OVER (PARTITION BY group_col ORDER BY ...)`

### JOIN 规范
- 显式 JOIN: `INNER JOIN`, `LEFT JOIN`
- 不允许隐式逗号 JOIN
- ENUM 类型直接用字符串比较: `WHERE status = 'paid'`

### CTE（WITH 子句，MySQL 8.0+）
- 复杂查询优先使用 CTE 提高可读性
- CTE 是中间步骤，最终 SELECT 必须完整
- MySQL 8.0 以下版本不支持 CTE，改用子查询
