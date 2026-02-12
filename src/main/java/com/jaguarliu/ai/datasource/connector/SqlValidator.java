package com.jaguarliu.ai.datasource.connector;

import java.util.regex.Pattern;

/**
 * SQL 验证器
 * 确保只允许执行安全的只读查询
 */
public class SqlValidator {

    // 危险的 SQL 关键字（写操作）
    private static final Pattern DANGEROUS_KEYWORDS = Pattern.compile(
            "(?i)\\b(INSERT|UPDATE|DELETE|DROP|CREATE|ALTER|TRUNCATE|REPLACE|MERGE|GRANT|REVOKE|EXEC|EXECUTE|CALL)\\b"
    );

    // 允许的 SELECT 语句模式
    private static final Pattern SELECT_PATTERN = Pattern.compile(
            "^\\s*(?:/\\*.*?\\*/\\s*)?(?:--.*?\\n\\s*)*SELECT\\b",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    /**
     * 验证 SQL 是否为安全的只读查询
     * @param sql SQL 语句
     * @throws IllegalArgumentException 如果 SQL 不安全
     */
    public static void validateReadOnlyQuery(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL query cannot be empty");
        }

        String trimmedSql = sql.trim();

        // 检查是否为 SELECT 语句
        if (!SELECT_PATTERN.matcher(trimmedSql).find()) {
            throw new IllegalArgumentException(
                    "Only SELECT queries are allowed. Detected non-SELECT statement."
            );
        }

        // 检查是否包含危险关键字
        if (DANGEROUS_KEYWORDS.matcher(trimmedSql).find()) {
            throw new IllegalArgumentException(
                    "Query contains forbidden keywords (INSERT, UPDATE, DELETE, etc.). " +
                    "Only read-only SELECT queries are permitted."
            );
        }

        // 检查是否包含分号分隔的多条语句
        String withoutStrings = removeStringLiterals(trimmedSql);
        if (withoutStrings.contains(";")) {
            // 允许末尾的单个分号
            String withoutTrailingSemicolon = withoutStrings.replaceFirst(";\\s*$", "");
            if (withoutTrailingSemicolon.contains(";")) {
                throw new IllegalArgumentException(
                        "Multiple statements are not allowed. Only single SELECT queries permitted."
                );
            }
        }
    }

    /**
     * 移除字符串字面量，避免误判
     */
    private static String removeStringLiterals(String sql) {
        // 移除单引号字符串
        String result = sql.replaceAll("'([^'\\\\]|\\\\.)*'", "''");
        // 移除双引号字符串
        result = result.replaceAll("\"([^\"\\\\]|\\\\.)*\"", "\"\"");
        return result;
    }

    /**
     * 检查 SQL 是否为只读查询（不抛异常，返回 boolean）
     */
    public static boolean isReadOnlyQuery(String sql) {
        try {
            validateReadOnlyQuery(sql);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
