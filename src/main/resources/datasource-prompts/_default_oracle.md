## Oracle SQL 规范

### 标识符与引用
- 默认大写，大小写敏感时使用双引号: `"TableName"`
- 一般不需要引号，Oracle 自动转大写

### 分页
- Oracle 12c+: `FETCH FIRST n ROWS ONLY` 或 `OFFSET m ROWS FETCH NEXT n ROWS ONLY`
- 旧版: `SELECT * FROM (SELECT t.*, ROWNUM rn FROM (...) t WHERE ROWNUM <= 100) WHERE rn > 0`
- 禁止无分页的全表扫描

### 禁止事项
- 禁止 `SELECT *`，必须明确列出所需字段
- 禁止写入操作（INSERT/UPDATE/DELETE/DROP）

### 类型与函数
- NULL 处理: `NVL(expr, default)` 或 `COALESCE(expr, default)`
- **Oracle 中空字符串 '' 等同于 NULL**，注意区分
- 日期格式化: `TO_CHAR(date, 'YYYY-MM-DD')`
- 日期提取: `EXTRACT(YEAR FROM date)`, `TRUNC(date, 'MM')`
- 日期差值: `end_date - start_date`（返回天数）, `MONTHS_BETWEEN(end, start)`
- 日期运算: `SYSDATE - 7`（7天前）, `ADD_MONTHS(date, 1)`
- 字符串聚合: `LISTAGG(column, ',') WITHIN GROUP (ORDER BY column)`
- 模糊匹配: `LIKE`（区分大小写），不敏感比较用 `UPPER(col) LIKE UPPER('%keyword%')`
- 条件表达式: `CASE WHEN ... THEN ... ELSE ... END` 或 `DECODE(expr, val1, result1, default)`
- 类型转换: `TO_NUMBER(expr)`, `TO_DATE(str, 'YYYY-MM-DD')`, `CAST(expr AS NUMBER(10,2))`

### 聚合与窗口函数
- `GROUP BY` 必须包含 SELECT 中所有非聚合列
- 排名: `ROW_NUMBER() OVER (ORDER BY ...)`, `RANK()`, `DENSE_RANK()`
- 分组内排名: `ROW_NUMBER() OVER (PARTITION BY group_col ORDER BY ...)`
- 分析函数: `LAG()`, `LEAD()`, `FIRST_VALUE()`, `LAST_VALUE()`

### JOIN 规范
- 显式 JOIN: `INNER JOIN`, `LEFT OUTER JOIN`
- 不允许 Oracle 旧式 `(+)` 写法
- 多表关联明确每组 ON 条件

### CTE（WITH 子句）
- 复杂查询优先使用 CTE 提高可读性
- CTE 是中间步骤，最终 SELECT 必须完整
- Oracle 支持递归 CTE: `WITH RECURSIVE`

### 特殊注意
- `DUAL` 表用于无表查询: `SELECT SYSDATE FROM DUAL`
- 字符串用单引号，双引号仅用于标识符
- `ROWNUM` 在 WHERE 前赋值，要先子查询再过滤
