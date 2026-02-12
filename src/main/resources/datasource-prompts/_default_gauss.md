## GaussDB SQL 规范

### 标识符与引用
- 兼容 PostgreSQL 语法，标识符默认小写
- 大小写敏感时使用双引号: `"TableName"`

### 分页
- `LIMIT n OFFSET m`（同 PostgreSQL）
- 禁止无 LIMIT 的全表扫描

### 禁止事项
- 禁止 `SELECT *`，必须明确列出所需字段
- 禁止写入操作（INSERT/UPDATE/DELETE/DROP）

### 类型与函数
- NULL 处理: `COALESCE(expr, default)` 或 `NVL(expr, default)`（GaussDB 同时支持）
- 日期格式化: `TO_CHAR(date, 'YYYY-MM-DD')`
- 日期提取: `EXTRACT(YEAR FROM date)`, `DATE_TRUNC('month', date)`
- 日期差值: `NOW() - interval '7 days'`, `AGE(end_date, start_date)`
- 字符串聚合: `STRING_AGG(column, ',' ORDER BY column)` 或 `LISTAGG`
- 模糊匹配: `ILIKE`（不区分大小写）, `LIKE`（区分大小写）
- 条件表达式: `CASE WHEN ... THEN ... ELSE ... END`
- 类型转换: `expr::INT`, `CAST(expr AS type)`

### 聚合与窗口函数
- `GROUP BY` 必须包含 SELECT 中所有非聚合列
- 排名: `ROW_NUMBER() OVER (ORDER BY ...)`, `RANK()`, `DENSE_RANK()`
- 分位: `NTILE(n) OVER (ORDER BY ...)`
- 分组内排名: `ROW_NUMBER() OVER (PARTITION BY group_col ORDER BY ...)`

### JOIN 规范
- 显式 JOIN: `INNER JOIN`, `LEFT JOIN`
- 不允许隐式逗号 JOIN
- 多表关联明确每组 ON 条件

### CTE（WITH 子句）
- 复杂查询优先使用 CTE 提高可读性
- CTE 是中间步骤，最终 SELECT 必须完整

### GaussDB 特性
- 大部分 PostgreSQL 语法可直接使用
- 支持 `MERGE INTO` 语法（但本场景只读，不涉及）
- 分布式场景注意分布键选择对查询性能的影响
