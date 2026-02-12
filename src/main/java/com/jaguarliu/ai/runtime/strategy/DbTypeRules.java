package com.jaguarliu.ai.runtime.strategy;

import com.jaguarliu.ai.datasource.domain.DataSourceType;

/**
 * 各数据库类型的默认 SQL 规则（硬编码兜底）
 * 仅在文件系统中没有对应规则文件时使用
 */
public final class DbTypeRules {

    private DbTypeRules() {}

    /**
     * 获取指定数据源类型的默认 SQL 规则
     */
    public static String getRulesFor(DataSourceType type) {
        return switch (type) {
            case MYSQL -> MYSQL_RULES;
            case POSTGRESQL -> POSTGRESQL_RULES;
            case ORACLE -> ORACLE_RULES;
            case GAUSS -> GAUSS_RULES;
            default -> GENERIC_RULES;
        };
    }

    private static final String MYSQL_RULES = """
            ## MySQL SQL 规范

            - 使用反引号(`)引用标识符: `table_name`.`column_name`
            - 分页使用 LIMIT: SELECT ... LIMIT 100
            - 禁止 SELECT *，必须明确列出需要的列
            - 查询条件应尽量命中索引列
            - 使用 IFNULL(expr, default) 处理空值
            - 日期格式化: DATE_FORMAT(date, '%Y-%m-%d')
            - 字符串聚合: GROUP_CONCAT(column SEPARATOR ',')
            - 对大表查询必须带 WHERE 条件或 LIMIT
            - 多表 JOIN 时显式指定 JOIN 类型（INNER JOIN / LEFT JOIN）
            - 字符串比较默认不区分大小写（utf8mb4_general_ci）
            """;

    private static final String POSTGRESQL_RULES = """
            ## PostgreSQL SQL 规范

            - 使用双引号(")引用标识符: "table_name"."column_name"（大小写敏感时才需要）
            - 分页使用 LIMIT ... OFFSET: SELECT ... LIMIT 100 OFFSET 0
            - 禁止 SELECT *，必须明确列出需要的列
            - 使用 COALESCE(expr, default) 处理空值
            - 日期格式化: TO_CHAR(date, 'YYYY-MM-DD')
            - 字符串聚合: STRING_AGG(column, ',')
            - 对大表查询必须带 WHERE 条件或 LIMIT
            - 支持 ILIKE 进行不区分大小写的模糊匹配
            - 布尔类型直接使用 TRUE/FALSE
            - 多表 JOIN 时显式指定 JOIN 类型
            """;

    private static final String ORACLE_RULES = """
            ## Oracle SQL 规范

            - 使用双引号(")引用标识符（大小写敏感时）: "TABLE_NAME"."COLUMN_NAME"
            - Oracle 12c+ 分页: SELECT ... FETCH FIRST 100 ROWS ONLY
            - 旧版分页: SELECT * FROM (SELECT t.*, ROWNUM rn FROM (...) t WHERE ROWNUM <= 100) WHERE rn > 0
            - 禁止 SELECT *，必须明确列出需要的列
            - 使用 NVL(expr, default) 处理空值
            - 日期格式化: TO_CHAR(date, 'YYYY-MM-DD')
            - 字符串聚合: LISTAGG(column, ',') WITHIN GROUP (ORDER BY column)
            - 对大表查询必须带 WHERE 条件或分页
            - 字符串比较区分大小写，使用 UPPER() 进行不敏感比较
            - 注意 Oracle 的 NULL 处理和空字符串等价
            """;

    private static final String GAUSS_RULES = """
            ## GaussDB SQL 规范

            - 使用双引号(")引用标识符: "table_name"."column_name"
            - 分页使用 LIMIT ... OFFSET: SELECT ... LIMIT 100 OFFSET 0
            - 禁止 SELECT *，必须明确列出需要的列
            - 使用 COALESCE(expr, default) 处理空值
            - 日期格式化: TO_CHAR(date, 'YYYY-MM-DD')
            - 字符串聚合: STRING_AGG(column, ',')
            - 对大表查询必须带 WHERE 条件或 LIMIT
            - 兼容 PostgreSQL 语法
            - 多表 JOIN 时显式指定 JOIN 类型
            """;

    private static final String GENERIC_RULES = """
            ## SQL 通用规范

            - 禁止 SELECT *，必须明确列出需要的列
            - 对大表查询必须带 WHERE 条件或 LIMIT
            - 多表 JOIN 时显式指定 JOIN 类型
            """;
}
