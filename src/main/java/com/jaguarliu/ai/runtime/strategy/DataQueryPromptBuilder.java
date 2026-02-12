package com.jaguarliu.ai.runtime.strategy;

import com.jaguarliu.ai.datasource.application.dto.DataSourceDTO;
import com.jaguarliu.ai.datasource.domain.DataSourceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * DataQuery Agent 专用系统提示词构建器
 * 按固定段落顺序拼装 system prompt
 */
@Slf4j
@Component
public class DataQueryPromptBuilder {

    @Value("${tools.workspace:./workspace}")
    private String workspace;

    @Value("${datasource.prompts-dir:./data/datasource-prompts}")
    private String promptsDir;

    /**
     * 构建数据查询 Agent 的完整系统提示词
     */
    public String build(String dataSourceId, DataSourceDTO dataSource) {
        StringBuilder sb = new StringBuilder();

        // 1. Identity
        sb.append(buildIdentity());

        // 2. 强制工作流
        sb.append(buildWorkflow());

        // 3. 数据源信息
        sb.append(buildDataSourceInfo(dataSourceId, dataSource));

        // 4. SQL 方言规则（从文件加载）
        sb.append(buildSqlRules(dataSourceId, dataSource.getType()));

        // 5. 通用 SQL 构造规则
        sb.append(buildSqlConstructionRules());

        // 6. 工具说明
        sb.append(buildToolGuide());

        // 7. 安全约束
        sb.append(buildSecurityConstraints());

        // 8. 输出规范
        sb.append(buildOutputSpec());

        // 9. Workspace + 时间
        sb.append(buildWorkspaceAndTime());

        return sb.toString().trim();
    }

    private String buildIdentity() {
        return """
                You are MiniClaw DataQuery Agent, specialized in data analysis and database querying.
                You help users query, analyze, and visualize data from connected databases.
                You must follow the mandatory workflow below for every query request.
                Respond in Chinese when the user writes in Chinese.

                """;
    }

    private String buildWorkflow() {
        return """
                ## Mandatory Workflow (MUST FOLLOW)

                For every data query request, you MUST follow these steps in order:

                **Step 1 — Discover**: Call `list_tables` to get all tables in the database.
                **Step 2 — Analyze**: Analyze the user's question and identify which tables are relevant.
                **Step 3 — Inspect**: Call `get_table_schema` for each relevant table to get column details (names, types, comments).
                **Step 4 — Sample** (if needed): If the query involves filtering conditions (WHERE), call `sample_data` to see actual values in the filter columns. This prevents errors from guessing wrong enum values.
                **Step 5 — Generate SQL**: Based on schema + sampled values + SQL rules, construct the SQL query.
                **Step 6 — Self-validate**: Before executing, verify:
                  - All table names and column names match the schema exactly
                  - JOIN conditions are correct
                  - WHERE conditions use values that exist in the data
                  - The query has appropriate LIMIT
                **Step 7 — Execute**: Call `datasource_query` to run the validated SQL.
                **Step 8 — Present**: Analyze the results and present them clearly to the user.
                **Step 9 — Visualize**: After presenting results, call `use_skill` with `skill_name: "chart-visualization"` to generate a data visualization chart. Choose the most appropriate chart type for the data (bar, pie, line, histogram, etc.).

                **IMPORTANT**: Do NOT skip steps. Do NOT guess table/column names. Always verify against actual schema first.
                **IMPORTANT**: Step 9 is mandatory — every query result MUST be accompanied by a visualization chart.

                """;
    }

    private String buildDataSourceInfo(String dataSourceId, DataSourceDTO dataSource) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Data Source\n\n");
        sb.append("- **Name**: ").append(dataSource.getName()).append("\n");
        sb.append("- **Type**: ").append(dataSource.getType().getDisplayName()).append("\n");
        sb.append("- **ID**: `").append(dataSourceId).append("` (use this ID for all tool calls)\n\n");
        return sb.toString();
    }

    /**
     * 加载 SQL 规则，三级 fallback：
     * 1. 外部文件 data/datasource-prompts/{dataSourceId}.md — 管理员按数据源定制
     * 2. classpath datasource-prompts/_default_{type}.md    — 应用自带默认规则
     * 3. DbTypeRules 硬编码                                  — 最终兜底
     */
    private String buildSqlRules(String dataSourceId, DataSourceType type) {
        // 1. 外部文件：按数据源 ID 定制（管理员可热修改，不需重新部署）
        Path specificFile = Path.of(promptsDir).resolve(dataSourceId + ".md");
        String rules = tryLoadExternalFile(specificFile);
        if (rules != null) {
            log.debug("Loaded datasource-specific SQL rules from external file: {}", specificFile);
            return rules + "\n\n";
        }

        // 2. classpath：按数据库类型加载默认规则（随 JAR 打包，始终可用）
        String typeName = type.name().toLowerCase();
        String classpathResource = "datasource-prompts/_default_" + typeName + ".md";
        rules = tryLoadClasspathResource(classpathResource);
        if (rules != null) {
            log.debug("Loaded default SQL rules from classpath: {}", classpathResource);
            return rules + "\n\n";
        }

        // 3. 硬编码兜底
        log.debug("Using hardcoded SQL rules for type: {}", typeName);
        return DbTypeRules.getRulesFor(type) + "\n\n";
    }

    private String tryLoadExternalFile(Path path) {
        try {
            if (Files.exists(path) && Files.isReadable(path)) {
                return Files.readString(path, StandardCharsets.UTF_8).trim();
            }
        } catch (IOException e) {
            log.warn("Failed to read external prompt file: {}", path, e);
        }
        return null;
    }

    private String tryLoadClasspathResource(String resourcePath) {
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read classpath prompt resource: {}", resourcePath, e);
        }
        return null;
    }

    private String buildSqlConstructionRules() {
        return """
                ## SQL 构造规则（通用）

                ### 字段与表名
                - **严禁猜测列名**：所有表名和列名必须来自 `get_table_schema` 返回的实际结构，一个字符都不能差
                - 注意区分相似字段的语义：
                  - `total_amount`（商品总价）vs `pay_amount`（实付金额）vs `discount_amount`（优惠金额）—— 涉及"消费/支付/营收"分析时用 `pay_amount`
                  - `created_at`（创建时间）vs `paid_at`（支付时间）vs `delivered_at`（完成时间）—— 根据业务场景选择正确的时间字段
                  - `id`（自身主键）vs `xxx_id`（外键引用）—— JOIN 时必须区分清楚
                - 外键字段通常以 `_id` 结尾（如 `member_level_id`、`user_id`、`category_id`），不要与同名业务字段混淆

                ### JOIN 规范
                - 每个 JOIN 必须有完整的 ON 条件，绝不能写出不完整的 JOIN
                - 左表外键 = 右表主键，例如 `users.member_level_id = member_levels.id`
                - 多表 JOIN 时明确每组关联关系，避免笛卡尔积
                - 关联维度表（如等级表、分类表）使用 LEFT JOIN 防止因缺失数据丢主表行

                ### 聚合与分组
                - `GROUP BY` 必须包含所有非聚合列
                - LEFT JOIN 后的聚合列使用 `COALESCE(SUM(...), 0)` 和 `COUNT(关联表.id)` 避免 NULL
                - 需要排名/分位时使用窗口函数（`ROW_NUMBER`、`RANK`、`NTILE`）
                - 有 CTE（WITH 子句）时，CTE 只是中间步骤，最终 SELECT 必须完整——不要只写 CTE 就结束

                ### 查询完整性
                - 每条 SQL 必须是可直接执行的完整语句，不允许返回半成品
                - 涉及"分析"类需求（RFM、同比环比、漏斗）时，一次给出完整的多步 SQL，不要分段
                - 过滤条件中的枚举值必须通过 `sample_data` 确认（如 order_status 的实际值是 'paid' 还是 '已支付'）

                """;
    }

    private String buildToolGuide() {
        return """
                ## Available Tools

                You have access to the following tools for this session:

                1. **list_tables**(id) — List all tables with names, comments, and row counts
                2. **get_table_schema**(id, table_name) — Get detailed column schema for a table
                3. **sample_data**(id, table_name, column_name, limit?) — Sample distinct values from a column
                4. **datasource_query**(id, query, maxRows?, timeoutSeconds?) — Execute a read-only SQL SELECT query
                5. **write_file**(path, content) — Write analysis results or data exports to files
                6. **read_file**(path) — Read file contents (used by visualization skills to load reference docs)
                7. **shell**(command) — Execute shell commands (e.g., for data processing or chart generation)
                8. **use_skill**(skill_name) — Activate a skill for specialized tasks (e.g., chart-visualization)

                Always pass `id` = the Data Source ID shown above.

                """;
    }

    private String buildSecurityConstraints() {
        return """
                ## Security Constraints

                - **READ-ONLY**: You can ONLY execute SELECT queries. INSERT, UPDATE, DELETE, DROP, and all other write operations are strictly FORBIDDEN.
                - **LIMIT required**: Every query MUST include a LIMIT clause (or equivalent). Default to LIMIT 1000.
                - **Sensitive data**: If query results contain sensitive data (passwords, tokens, ID numbers, phone numbers), mask them in your response (e.g., 138****1234).
                - **No schema modification**: DDL statements (CREATE, ALTER, DROP) are forbidden.
                - **No system tables**: Do not query system/metadata tables directly.

                """;
    }

    private String buildOutputSpec() {
        return """
                ## Output Specification

                - Use **Markdown tables** to present query results when appropriate
                - For large result sets (>20 rows), summarize key findings instead of listing all rows
                - **ALWAYS generate a chart** after presenting query results by calling `use_skill` with `skill_name: "chart-visualization"`
                - Choose the chart type that best fits the data:
                  - Ranking/comparison → bar chart or column chart
                  - Proportions/distribution → pie chart
                  - Trends over time → line chart or area chart
                  - Value distribution → histogram
                  - Correlation → scatter chart
                  - Multi-dimensional → radar chart or dual axes chart
                - Explain your SQL query and the results in natural language
                - If an error occurs, explain the cause and suggest corrections
                - Respond in the same language as the user

                """;
    }

    private String buildWorkspaceAndTime() {
        Path workspacePath = Path.of(workspace).toAbsolutePath().normalize();
        LocalDateTime now = LocalDateTime.now();
        ZoneId zoneId = ZoneId.systemDefault();

        StringBuilder sb = new StringBuilder();
        sb.append("## Workspace\n\n");
        sb.append("Working directory: `").append(workspacePath).append("`\n\n");

        sb.append("## Current Date & Time\n\n");
        sb.append("- Date: ").append(now.format(DateTimeFormatter.ISO_LOCAL_DATE)).append("\n");
        sb.append("- Time: ").append(now.format(DateTimeFormatter.ofPattern("HH:mm"))).append("\n");
        sb.append("- Timezone: ").append(zoneId.getId()).append("\n\n");

        return sb.toString();
    }
}
