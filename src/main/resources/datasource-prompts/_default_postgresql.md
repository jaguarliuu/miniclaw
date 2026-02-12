## PostgreSQL SQL 规范

### 标识符与引用
- 标识符默认小写，大小写敏感时使用双引号: "TableName"
- 一般不需要引号，直接使用小写: `select id from users`

### 分页
- `LIMIT n OFFSET m`（OFFSET 可省略）
- 禁止无 LIMIT 的全表扫描

### 禁止事项
- 禁止 `SELECT *`，必须明确列出所需字段
- 禁止写入操作（INSERT/UPDATE/DELETE/DROP）

### 类型与函数
- NULL 处理: `COALESCE(expr, default)`
- 日期格式化: `TO_CHAR(date, 'YYYY-MM-DD')`
- 日期提取: `EXTRACT(YEAR FROM date)`, `DATE_TRUNC('month', date)`
- 日期差值: `NOW() - interval '7 days'`, `AGE(end_date, start_date)`, `EXTRACT(DAY FROM NOW() - date)::INT`
- 字符串聚合: `STRING_AGG(column, ',' ORDER BY column)`
- 模糊匹配: `ILIKE`（不区分大小写）, `LIKE`（区分大小写）
- 条件表达式: `CASE WHEN ... THEN ... ELSE ... END`
- 布尔值: `TRUE / FALSE`（不要用 1/0）
- JSON 字段: `column->>'key'`（文本）, `column->'key'`（JSON）
- 数组: `ANY(ARRAY[...])`, `column = ANY('{a,b,c}')`
- 类型转换: `expr::INT`, `expr::NUMERIC(10,2)`, `CAST(expr AS type)`

### 聚合与窗口函数
- `GROUP BY` 必须包含 SELECT 中所有非聚合列
- 排名: `ROW_NUMBER() OVER (ORDER BY ...)`, `RANK()`, `DENSE_RANK()`
- 分位: `NTILE(n) OVER (ORDER BY ...)`
- 累计: `SUM(...) OVER (ORDER BY ... ROWS UNBOUNDED PRECEDING)`
- 分组内排名: `ROW_NUMBER() OVER (PARTITION BY group_col ORDER BY ...)`

### JOIN 规范
- 显式 JOIN: `INNER JOIN`, `LEFT JOIN`, `CROSS JOIN`
- 不允许隐式逗号 JOIN: ~~`FROM a, b WHERE a.id = b.a_id`~~
- ENUM 类型可直接比较字符串: `WHERE status = 'paid'`（无需 CAST）

### CTE（WITH 子句）
- 复杂查询优先使用 CTE 提高可读性
- CTE 是中间步骤，最终 SELECT 必须完整
- PostgreSQL 12+ 的 CTE 会被自动内联优化
