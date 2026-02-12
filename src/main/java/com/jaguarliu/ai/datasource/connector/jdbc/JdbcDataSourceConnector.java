package com.jaguarliu.ai.datasource.connector.jdbc;

import com.jaguarliu.ai.datasource.connector.AbstractDataSourceConnector;
import com.jaguarliu.ai.datasource.connector.ConnectorException;
import com.jaguarliu.ai.datasource.connector.SqlValidator;
import com.jaguarliu.ai.datasource.domain.*;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * JDBC 数据源连接器
 *
 * 使用 HikariCP 连接池实现
 * 支持 MySQL、PostgreSQL、Oracle、GaussDB
 */
@Slf4j
public class JdbcDataSourceConnector extends AbstractDataSourceConnector {

    private final DataSourceType dataSourceType;
    private final JdbcConnectionConfig jdbcConfig;
    private HikariDataSource dataSource;

    public JdbcDataSourceConnector(
            DataSourceType type,
            JdbcConnectionConfig jdbcConfig,
            SecurityConfig securityConfig) {
        super(jdbcConfig, securityConfig);
        this.dataSourceType = type;
        this.jdbcConfig = jdbcConfig;
    }

    @Override
    public void connect() throws ConnectorException {
        if (connected) {
            log.debug("Already connected to {}", jdbcConfig.getDescription());
            return;
        }

        try {
            log.info("Connecting to {} database: {}", dataSourceType, jdbcConfig.getDescription());

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(jdbcConfig.buildJdbcUrl(dataSourceType));
            config.setUsername(jdbcConfig.getUsername());
            config.setPassword(jdbcConfig.getPassword());

            // 连接池配置
            config.setMaximumPoolSize(securityConfig.getMaxConnections());
            config.setMinimumIdle(securityConfig.getMinIdle());
            config.setConnectionTimeout(securityConfig.getConnectionTimeout());
            config.setIdleTimeout(securityConfig.getIdleTimeout());
            config.setMaxLifetime(securityConfig.getMaxLifetime());

            // 强制只读
            config.setReadOnly(securityConfig.isReadOnly());

            // 连接测试查询
            config.setConnectionTestQuery(getTestQuery());

            // 额外连接属性
            if (jdbcConfig.getProperties() != null) {
                jdbcConfig.getProperties().forEach((key, value) ->
                    config.addDataSourceProperty(key, value));
            }

            this.dataSource = new HikariDataSource(config);
            this.connected = true;

            log.info("Successfully connected to {}", jdbcConfig.getDescription());

        } catch (Exception e) {
            log.error("Failed to connect to {}: {}", jdbcConfig.getDescription(), e.getMessage(), e);
            throw new ConnectorException(
                    "Failed to connect: " + e.getMessage(),
                    e,
                    ConnectorException.ErrorType.CONNECTION_FAILED);
        }
    }

    @Override
    public void disconnect() throws ConnectorException {
        if (!connected) {
            return;
        }

        try {
            log.info("Disconnecting from {}", jdbcConfig.getDescription());
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
            this.connected = false;
            log.info("Successfully disconnected from {}", jdbcConfig.getDescription());
        } catch (Exception e) {
            log.error("Error disconnecting from {}: {}", jdbcConfig.getDescription(), e.getMessage());
            throw new ConnectorException("Failed to disconnect: " + e.getMessage(), e);
        }
    }

    @Override
    public QueryResult executeQuery(String query, int maxRows, int timeoutSeconds)
            throws ConnectorException {
        ensureConnected();

        // 验证 SQL 安全性（只允许 SELECT 查询）
        try {
            SqlValidator.validateReadOnlyQuery(query);
        } catch (IllegalArgumentException e) {
            log.error("SQL validation failed: {}", e.getMessage());
            throw new ConnectorException(
                    "Security validation failed: " + e.getMessage(),
                    e,
                    ConnectorException.ErrorType.SQL_SYNTAX_ERROR
            );
        }

        long startTime = System.currentTimeMillis();
        int effectiveMaxRows = getEffectiveMaxRows(maxRows);
        int effectiveTimeout = getEffectiveTimeout(timeoutSeconds);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // 设置超时
            stmt.setQueryTimeout(effectiveTimeout);

            // 设置最大行数
            stmt.setMaxRows(effectiveMaxRows);

            // 强制只读
            stmt.setFetchSize(100); // 批量获取，减少内存压力

            log.debug("Executing query: {}", query);

            try (ResultSet rs = stmt.executeQuery(query)) {
                // 提取列信息
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                List<String> columns = new ArrayList<>(columnCount);
                for (int i = 1; i <= columnCount; i++) {
                    columns.add(metaData.getColumnLabel(i));
                }

                // 提取数据行
                List<Map<String, Object>> rows = new ArrayList<>();
                int rowCount = 0;
                boolean truncated = false;

                while (rs.next() && rowCount < effectiveMaxRows) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.put(columns.get(i - 1), rs.getObject(i));
                    }
                    rows.add(row);
                    rowCount++;
                }

                // 检查是否被截断
                if (rs.next()) {
                    truncated = true;
                    log.warn("Query result truncated at {} rows", effectiveMaxRows);
                }

                long executionTime = System.currentTimeMillis() - startTime;
                log.info("Query executed successfully. Rows: {}, Time: {}ms, Truncated: {}",
                        rowCount, executionTime, truncated);

                return QueryResult.success(columns, rows, executionTime, truncated);
            }

        } catch (SQLException e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Query execution failed: {}", e.getMessage(), e);

            ConnectorException.ErrorType errorType = mapSqlException(e);
            throw new ConnectorException(
                    "Query failed: " + e.getMessage(),
                    e,
                    errorType);
        }
    }

    @Override
    public SchemaMetadata getSchemaMetadata() throws ConnectorException {
        ensureConnected();

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schema = conn.getSchema();

            List<SchemaMetadata.TableMetadata> tables = new ArrayList<>();

            // 获取所有表
            try (ResultSet rs = metaData.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String comment = rs.getString("REMARKS");

                    // 获取列信息
                    List<SchemaMetadata.ColumnMetadata> columns = getColumnMetadata(
                            metaData, catalog, schema, tableName);

                    // 估算行数（不是所有数据库都支持）
                    Long rowCount = estimateRowCount(tableName);

                    tables.add(SchemaMetadata.TableMetadata.builder()
                            .tableName(tableName)
                            .comment(comment)
                            .columns(columns)
                            .rowCount(rowCount)
                            .build());
                }
            }

            String schemaName = schema != null ? schema : catalog;
            return SchemaMetadata.builder()
                    .schemaName(schemaName)
                    .tables(tables)
                    .build();

        } catch (SQLException e) {
            log.error("Failed to get schema metadata: {}", e.getMessage(), e);
            throw new ConnectorException(
                    "Failed to get schema metadata: " + e.getMessage(),
                    e,
                    ConnectorException.ErrorType.CONNECTION_FAILED);
        }
    }

    @Override
    public boolean isHealthy() {
        if (!connected || dataSource == null) {
            return false;
        }

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(getTestQuery())) {
            return rs.next();
        } catch (Exception e) {
            log.warn("Health check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getConnectorType() {
        return "JDBC-" + dataSourceType.name();
    }

    /**
     * 获取列元数据
     */
    private List<SchemaMetadata.ColumnMetadata> getColumnMetadata(
            DatabaseMetaData metaData, String catalog, String schema, String tableName)
            throws SQLException {

        List<SchemaMetadata.ColumnMetadata> columns = new ArrayList<>();

        // 获取主键信息
        Set<String> primaryKeys = new HashSet<>();
        try (ResultSet pkRs = metaData.getPrimaryKeys(catalog, schema, tableName)) {
            while (pkRs.next()) {
                primaryKeys.add(pkRs.getString("COLUMN_NAME"));
            }
        }

        // 获取列信息
        try (ResultSet rs = metaData.getColumns(catalog, schema, tableName, "%")) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                String dataType = rs.getString("TYPE_NAME");
                boolean nullable = rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
                String comment = rs.getString("REMARKS");
                String defaultValue = rs.getString("COLUMN_DEF");

                columns.add(SchemaMetadata.ColumnMetadata.builder()
                        .columnName(columnName)
                        .dataType(dataType)
                        .nullable(nullable)
                        .primaryKey(primaryKeys.contains(columnName))
                        .comment(comment)
                        .defaultValue(defaultValue)
                        .build());
            }
        }

        return columns;
    }

    /**
     * 估算表行数
     */
    private Long estimateRowCount(String tableName) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception e) {
            log.debug("Failed to estimate row count for {}: {}", tableName, e.getMessage());
        }
        return null;
    }

    /**
     * 获取测试查询语句
     */
    private String getTestQuery() {
        return switch (dataSourceType) {
            case MYSQL, GAUSS -> "SELECT 1";
            case POSTGRESQL -> "SELECT 1";
            case ORACLE -> "SELECT 1 FROM DUAL";
            default -> "SELECT 1";
        };
    }

    /**
     * 映射 SQL 异常到连接器异常类型
     */
    private ConnectorException.ErrorType mapSqlException(SQLException e) {
        String sqlState = e.getSQLState();
        int errorCode = e.getErrorCode();

        // 根据 SQL State 判断错误类型
        if (sqlState != null) {
            if (sqlState.startsWith("08")) {
                return ConnectorException.ErrorType.CONNECTION_FAILED;
            } else if (sqlState.startsWith("28")) {
                return ConnectorException.ErrorType.AUTHENTICATION_FAILED;
            } else if (sqlState.startsWith("42")) {
                return ConnectorException.ErrorType.SQL_SYNTAX_ERROR;
            }
        }

        // 超时判断
        if (e instanceof SQLTimeoutException) {
            return ConnectorException.ErrorType.TIMEOUT;
        }

        return ConnectorException.ErrorType.UNKNOWN;
    }
}
