package com.jaguarliu.ai.datasource.domain;

/**
 * 数据源类型枚举
 * 支持 JDBC 数据库和文件类型数据源
 */
public enum DataSourceType {
    // JDBC 数据库
    MYSQL("MySQL", "jdbc", true),
    POSTGRESQL("PostgreSQL", "jdbc", true),
    ORACLE("Oracle", "jdbc", true),
    GAUSS("GaussDB", "jdbc", true),

    // 文件数据源
    CSV("CSV File", "file", false),
    XLSX("Excel File", "file", false);

    private final String displayName;
    private final String category;  // jdbc or file
    private final boolean requiresConnection;  // 是否需要持久连接

    DataSourceType(String displayName, String category, boolean requiresConnection) {
        this.displayName = displayName;
        this.category = category;
        this.requiresConnection = requiresConnection;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCategory() {
        return category;
    }

    public boolean isJdbc() {
        return "jdbc".equals(category);
    }

    public boolean isFile() {
        return "file".equals(category);
    }

    public boolean requiresConnection() {
        return requiresConnection;
    }
}
