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
 * 列出数据源中所有表
 * 返回表名、注释和预估行数
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ListTablesTool implements Tool {

    private final DataSourceService dataSourceService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("list_tables")
                .description("List all tables in the specified data source. " +
                        "Returns table names, comments, and estimated row counts. " +
                        "Use this as the first step to understand the database structure.")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "id", Map.of(
                                        "type", "string",
                                        "description", "Data source ID"
                                )
                        ),
                        "required", List.of("id")
                ))
                .hitl(false)
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        String id = (String) arguments.get("id");

        if (id == null || id.isBlank()) {
            return Mono.just(ToolResult.error("Missing required parameter: id"));
        }

        return Mono.fromCallable(() -> {
            SchemaMetadata schema = dataSourceService.getSchemaMetadata(id);
            if (schema == null || schema.getTables() == null || schema.getTables().isEmpty()) {
                return ToolResult.success("No tables found in data source: " + id);
            }

            return ToolResult.success(formatTables(schema));
        }).onErrorResume(e -> {
            log.error("Failed to list tables for datasource {}: {}", id, e.getMessage(), e);
            return Mono.just(ToolResult.error("Failed to list tables: " + e.getMessage()));
        });
    }

    private String formatTables(SchemaMetadata schema) {
        List<SchemaMetadata.TableMetadata> tables = schema.getTables();

        StringBuilder sb = new StringBuilder();
        sb.append("Database: ").append(schema.getSchemaName() != null ? schema.getSchemaName() : "default").append("\n");
        sb.append("Total tables: ").append(tables.size()).append("\n\n");

        // Header
        sb.append(String.format("| %-40s | %-50s | %-12s |\n", "Table Name", "Comment", "Est. Rows"));
        sb.append("|").append("-".repeat(42)).append("|").append("-".repeat(52)).append("|").append("-".repeat(14)).append("|\n");

        // Data rows
        for (SchemaMetadata.TableMetadata table : tables) {
            String name = table.getTableName();
            String comment = table.getComment() != null ? table.getComment() : "";
            String rows = table.getRowCount() != null ? table.getRowCount().toString() : "N/A";

            // Truncate long values
            if (name.length() > 40) name = name.substring(0, 37) + "...";
            if (comment.length() > 50) comment = comment.substring(0, 47) + "...";

            sb.append(String.format("| %-40s | %-50s | %-12s |\n", name, comment, rows));
        }

        return sb.toString();
    }
}
