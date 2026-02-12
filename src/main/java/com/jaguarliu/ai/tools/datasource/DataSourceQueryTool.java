package com.jaguarliu.ai.tools.datasource;

import com.jaguarliu.ai.datasource.application.service.DataSourceService;
import com.jaguarliu.ai.datasource.domain.QueryResult;
import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 数据源查询工具
 * 允许 AI 执行只读 SQL 查询
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSourceQueryTool implements Tool {

    private final DataSourceService dataSourceService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("datasource_query")
                .description("Execute a read-only SQL query against the selected data source. " +
                        "Only SELECT queries are allowed. Returns query results in tabular format. " +
                        "Use this to retrieve data from the database based on the user's question.")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "id", Map.of(
                                        "type", "string",
                                        "description", "Data source ID (automatically provided by the system when user selects a data source)"
                                ),
                                "query", Map.of(
                                        "type", "string",
                                        "description", "SQL SELECT query to execute. Only read-only queries are permitted. Example: SELECT * FROM users WHERE age > 18"
                                ),
                                "maxRows", Map.of(
                                        "type", "integer",
                                        "description", "Maximum number of rows to return (default: 1000, max: 10000)"
                                ),
                                "timeoutSeconds", Map.of(
                                        "type", "integer",
                                        "description", "Query timeout in seconds (default: 30)"
                                )
                        ),
                        "required", List.of("id", "query")
                ))
                .hitl(false)
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        String id = (String) arguments.get("id");
        String query = (String) arguments.get("query");

        if (id == null || id.isBlank()) {
            return Mono.just(ToolResult.error("Missing required parameter: id"));
        }
        if (query == null || query.isBlank()) {
            return Mono.just(ToolResult.error("Missing required parameter: query"));
        }

        final Integer maxRows;
        if (arguments.containsKey("maxRows") && arguments.get("maxRows") != null) {
            maxRows = ((Number) arguments.get("maxRows")).intValue();
        } else {
            maxRows = null;
        }

        final Integer timeoutSeconds;
        if (arguments.containsKey("timeoutSeconds") && arguments.get("timeoutSeconds") != null) {
            timeoutSeconds = ((Number) arguments.get("timeoutSeconds")).intValue();
        } else {
            timeoutSeconds = null;
        }

        log.info("Executing query on datasource {}: {}", id, query);

        return Mono.fromCallable(() -> {
            QueryResult result = dataSourceService.executeQuery(id, query, maxRows, timeoutSeconds);
            return formatQueryResult(result, query);
        }).onErrorResume(e -> {
            log.error("Query execution failed: {}", e.getMessage(), e);
            String errorMsg = e.getMessage();

            // 提供更友好的错误信息
            if (errorMsg != null) {
                if (errorMsg.contains("Security validation failed") ||
                    errorMsg.contains("forbidden keywords") ||
                    errorMsg.contains("Only SELECT queries")) {
                    return Mono.just(ToolResult.error(
                        "SECURITY ERROR: " + errorMsg + "\n\n" +
                        "Only read-only SELECT queries are allowed. " +
                        "INSERT, UPDATE, DELETE, and other write operations are forbidden."
                    ));
                }
                if (errorMsg.contains("not found") || errorMsg.contains("NOT_FOUND")) {
                    return Mono.just(ToolResult.error(
                        "Data source not found: " + id + "\n\n" +
                        "Please verify that the data source ID is correct and the data source is active."
                    ));
                }
                if (errorMsg.contains("syntax")) {
                    return Mono.just(ToolResult.error(
                        "SQL SYNTAX ERROR: " + errorMsg + "\n\n" +
                        "Please check your SQL query syntax and try again."
                    ));
                }
            }

            return Mono.just(ToolResult.error("Query failed: " + errorMsg));
        });
    }

    /**
     * 格式化查询结果为可读的表格形式
     */
    private ToolResult formatQueryResult(QueryResult result, String query) {
        if (!result.isSuccess()) {
            return ToolResult.error("Query failed: " + result.getError());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Query executed successfully\n");
        sb.append("SQL: ").append(query).append("\n");
        sb.append("Execution time: ").append(result.getExecutionTime()).append("ms\n");
        sb.append("Rows returned: ").append(result.getRows().size());

        if (result.isTruncated()) {
            sb.append(" (truncated - more rows available)");
        }
        sb.append("\n\n");

        List<String> columns = result.getColumns();
        List<Map<String, Object>> rows = result.getRows();

        if (rows.isEmpty()) {
            sb.append("No rows returned.");
            return ToolResult.success(sb.toString());
        }

        // 计算每列的最大宽度（用于对齐）
        Map<String, Integer> columnWidths = new java.util.HashMap<>();
        for (String col : columns) {
            columnWidths.put(col, col.length());
        }
        for (Map<String, Object> row : rows) {
            for (String col : columns) {
                Object value = row.get(col);
                String strValue = value != null ? value.toString() : "NULL";
                columnWidths.put(col, Math.max(columnWidths.get(col), strValue.length()));
            }
        }

        // 表头
        sb.append("| ");
        for (String col : columns) {
            sb.append(String.format("%-" + columnWidths.get(col) + "s", col)).append(" | ");
        }
        sb.append("\n");

        // 分隔线
        sb.append("|");
        for (String col : columns) {
            sb.append("-".repeat(columnWidths.get(col) + 2)).append("|");
        }
        sb.append("\n");

        // 数据行
        for (Map<String, Object> row : rows) {
            sb.append("| ");
            for (String col : columns) {
                Object value = row.get(col);
                String strValue = value != null ? value.toString() : "NULL";
                sb.append(String.format("%-" + columnWidths.get(col) + "s", strValue)).append(" | ");
            }
            sb.append("\n");
        }

        return ToolResult.success(sb.toString());
    }
}
