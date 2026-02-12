package com.jaguarliu.ai.tools.datasource;

import com.jaguarliu.ai.datasource.application.service.DataSourceService;
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

/**
 * 获取指定表的详细列结构
 * 返回列名、类型、是否可空、主键、注释等
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetTableSchemaTool implements Tool {

    private final DataSourceService dataSourceService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("get_table_schema")
                .description("Get detailed column schema for a specific table. " +
                        "Returns column names, data types, nullable flags, primary keys, comments, and default values. " +
                        "Use this after list_tables to understand the structure of relevant tables.")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "id", Map.of(
                                        "type", "string",
                                        "description", "Data source ID"
                                ),
                                "table_name", Map.of(
                                        "type", "string",
                                        "description", "The table name to get schema for"
                                )
                        ),
                        "required", List.of("id", "table_name")
                ))
                .hitl(false)
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        String id = (String) arguments.get("id");
        String tableName = (String) arguments.get("table_name");

        if (id == null || id.isBlank()) {
            return Mono.just(ToolResult.error("Missing required parameter: id"));
        }
        if (tableName == null || tableName.isBlank()) {
            return Mono.just(ToolResult.error("Missing required parameter: table_name"));
        }

        return Mono.fromCallable(() -> {
            SchemaMetadata schema = dataSourceService.getSchemaMetadata(id);
            if (schema == null || schema.getTables() == null) {
                return ToolResult.error("No schema metadata available for data source: " + id);
            }

            // Find the requested table
            SchemaMetadata.TableMetadata table = schema.getTables().stream()
                    .filter(t -> tableName.equalsIgnoreCase(t.getTableName()))
                    .findFirst()
                    .orElse(null);

            if (table == null) {
                return ToolResult.error("Table not found: " + tableName +
                        ". Use list_tables to see available tables.");
            }

            return ToolResult.success(formatTableSchema(table));
        }).onErrorResume(e -> {
            log.error("Failed to get table schema for {}.{}: {}", id, tableName, e.getMessage(), e);
            return Mono.just(ToolResult.error("Failed to get table schema: " + e.getMessage()));
        });
    }

    private String formatTableSchema(SchemaMetadata.TableMetadata table) {
        StringBuilder sb = new StringBuilder();
        sb.append("Table: ").append(table.getTableName());
        if (table.getComment() != null && !table.getComment().isEmpty()) {
            sb.append(" (").append(table.getComment()).append(")");
        }
        sb.append("\n");

        if (table.getRowCount() != null) {
            sb.append("Estimated rows: ").append(table.getRowCount()).append("\n");
        }

        List<SchemaMetadata.ColumnMetadata> columns = table.getColumns();
        if (columns == null || columns.isEmpty()) {
            sb.append("No column information available.\n");
            return sb.toString();
        }

        sb.append("Columns: ").append(columns.size()).append("\n\n");

        // Header
        sb.append(String.format("| %-30s | %-20s | %-8s | %-4s | %-40s | %-20s |\n",
                "Column", "Type", "Nullable", "PK", "Comment", "Default"));
        sb.append("|").append("-".repeat(32)).append("|").append("-".repeat(22))
                .append("|").append("-".repeat(10)).append("|").append("-".repeat(6))
                .append("|").append("-".repeat(42)).append("|").append("-".repeat(22)).append("|\n");

        // Data rows
        for (SchemaMetadata.ColumnMetadata col : columns) {
            String name = col.getColumnName();
            String type = col.getDataType() != null ? col.getDataType() : "unknown";
            String nullable = col.isNullable() ? "YES" : "NO";
            String pk = col.isPrimaryKey() ? "YES" : "";
            String comment = col.getComment() != null ? col.getComment() : "";
            String defaultVal = col.getDefaultValue() != null ? col.getDefaultValue() : "";

            // Truncate long values
            if (name.length() > 30) name = name.substring(0, 27) + "...";
            if (type.length() > 20) type = type.substring(0, 17) + "...";
            if (comment.length() > 40) comment = comment.substring(0, 37) + "...";
            if (defaultVal.length() > 20) defaultVal = defaultVal.substring(0, 17) + "...";

            sb.append(String.format("| %-30s | %-20s | %-8s | %-4s | %-40s | %-20s |\n",
                    name, type, nullable, pk, comment, defaultVal));
        }

        return sb.toString();
    }
}
