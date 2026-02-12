package com.jaguarliu.ai.tools.datasource;

import com.jaguarliu.ai.datasource.application.service.DataSourceService;
import com.jaguarliu.ai.datasource.application.dto.DataSourceDTO;
import com.jaguarliu.ai.datasource.domain.DataSourceType;
import com.jaguarliu.ai.datasource.domain.QueryResult;
import com.jaguarliu.ai.datasource.domain.SchemaMetadata;
import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据采样工具
 * 对指定列进行 DISTINCT 采样，帮助 AI 了解枚举值和数据分布
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SampleDataTool implements Tool {

    private final DataSourceService dataSourceService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("sample_data")
                .description("Sample distinct values from a specific column. " +
                        "Useful for understanding enum values, data distribution, and filter conditions. " +
                        "Use this before constructing WHERE clauses to know what values exist.")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "id", Map.of(
                                        "type", "string",
                                        "description", "Data source ID"
                                ),
                                "table_name", Map.of(
                                        "type", "string",
                                        "description", "The table name to sample from"
                                ),
                                "column_name", Map.of(
                                        "type", "string",
                                        "description", "The column name to sample distinct values from"
                                ),
                                "limit", Map.of(
                                        "type", "integer",
                                        "description", "Maximum number of distinct values to return (default: 20, max: 100)"
                                )
                        ),
                        "required", List.of("id", "table_name", "column_name")
                ))
                .hitl(false)
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        String id = (String) arguments.get("id");
        String tableName = (String) arguments.get("table_name");
        String columnName = (String) arguments.get("column_name");

        if (id == null || id.isBlank()) {
            return Mono.just(ToolResult.error("Missing required parameter: id"));
        }
        if (tableName == null || tableName.isBlank()) {
            return Mono.just(ToolResult.error("Missing required parameter: table_name"));
        }
        if (columnName == null || columnName.isBlank()) {
            return Mono.just(ToolResult.error("Missing required parameter: column_name"));
        }

        int limit = 20;
        if (arguments.containsKey("limit") && arguments.get("limit") != null) {
            limit = Math.min(((Number) arguments.get("limit")).intValue(), 100);
        }

        final int effectiveLimit = Math.max(1, limit);

        return Mono.fromCallable(() -> {
            // 1. Validate table and column exist (prevents injection)
            SchemaMetadata schema = dataSourceService.getSchemaMetadata(id);
            if (schema == null || schema.getTables() == null) {
                return ToolResult.error("No schema metadata available for data source: " + id);
            }

            SchemaMetadata.TableMetadata table = schema.getTables().stream()
                    .filter(t -> tableName.equalsIgnoreCase(t.getTableName()))
                    .findFirst()
                    .orElse(null);

            if (table == null) {
                return ToolResult.error("Table not found: " + tableName);
            }

            if (table.getColumns() == null ||
                table.getColumns().stream().noneMatch(c -> columnName.equalsIgnoreCase(c.getColumnName()))) {
                return ToolResult.error("Column not found: " + columnName + " in table " + tableName);
            }

            // 2. Determine identifier quoting based on database type
            DataSourceDTO dataSource = dataSourceService.getDataSource(id);
            String quote = getQuoteChar(dataSource.getType());

            // 3. Build safe SQL using validated identifiers
            String sql = String.format("SELECT DISTINCT %s%s%s FROM %s%s%s LIMIT %d",
                    quote, columnName, quote,
                    quote, tableName, quote,
                    effectiveLimit);

            // For Oracle, use different syntax
            if (dataSource.getType() == DataSourceType.ORACLE) {
                sql = String.format("SELECT DISTINCT %s%s%s FROM %s%s%s FETCH FIRST %d ROWS ONLY",
                        quote, columnName, quote,
                        quote, tableName, quote,
                        effectiveLimit);
            }

            // 4. Execute
            QueryResult result = dataSourceService.executeQuery(id, sql, effectiveLimit, 15);

            if (!result.isSuccess()) {
                return ToolResult.error("Sample query failed: " + result.getError());
            }

            return ToolResult.success(formatSampleResult(tableName, columnName, result));
        }).onErrorResume(e -> {
            log.error("Failed to sample data for {}.{}.{}: {}", id, tableName, columnName, e.getMessage(), e);
            return Mono.just(ToolResult.error("Failed to sample data: " + e.getMessage()));
        });
    }

    private String getQuoteChar(DataSourceType type) {
        if (type == DataSourceType.MYSQL) {
            return "`";
        }
        // PostgreSQL, Oracle, GaussDB use double quotes
        return "\"";
    }

    private String formatSampleResult(String tableName, String columnName, QueryResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("Distinct values of ").append(tableName).append(".").append(columnName).append(":\n\n");

        List<Map<String, Object>> rows = result.getRows();
        if (rows.isEmpty()) {
            sb.append("(no data found)\n");
            return sb.toString();
        }

        sb.append("Total distinct values returned: ").append(rows.size()).append("\n\n");

        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            // Get the first (and only) value from each row
            Object value = row.values().iterator().next();
            sb.append(String.format("%d. %s\n", i + 1, value != null ? value.toString() : "NULL"));
        }

        return sb.toString();
    }
}
