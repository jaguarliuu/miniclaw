package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.datasource.application.dto.DataSourceDTO;
import com.jaguarliu.ai.datasource.application.service.DataSourceService;
import com.jaguarliu.ai.datasource.domain.SchemaMetadata;
import com.jaguarliu.ai.mcp.prompt.McpPromptProvider;
import com.jaguarliu.ai.memory.search.MemorySearchService;
import com.jaguarliu.ai.skills.index.SkillIndexBuilder;
import com.jaguarliu.ai.soul.SoulConfigService;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * System Prompt 构建器
 *
 * 参考 OpenClaw 的结构化设计，构建包含以下固定段落的系统提示：
 * 1. Identity - 基本身份
 * 2. Tooling - 工具列表和说明
 * 3. Safety - 安全防护提醒
 * 4. Memory - 全局记忆系统使用说明
 * 5. Skills - 技能使用方式（当有可用技能时）
 * 6. Workspace - 工作目录
 * 7. Current Date & Time - 当前时间
 * 8. Runtime - 运行环境信息
 * 9. MCP Server Capabilities - MCP 服务器提供的提示词（如果有）
 *
 * 支持三种 Prompt Mode：
 * - FULL: 完整提示（默认）
 * - MINIMAL: 精简提示（用于子代理）
 * - NONE: 仅身份行
 */
@Slf4j
@Component
public class SystemPromptBuilder {

    private final ToolRegistry toolRegistry;
    private final SkillIndexBuilder skillIndexBuilder;
    private final MemorySearchService memorySearchService;
    private final Optional<McpPromptProvider> mcpPromptProvider;
    private final SoulConfigService soulConfigService;
    private final Optional<DataSourceService> dataSourceService;

    @Value("${tools.workspace:./workspace}")
    private String workspace;

    // 身份段落
    private static final String IDENTITY_SECTION = """
        You are MiniClaw, an AI coding assistant. You help users with software engineering tasks including:
        - Writing, reviewing, and debugging code
        - Explaining technical concepts
        - File operations and shell commands
        - Creating documents (PPTX, XLSX, etc.)

        Respond concisely and accurately. Use Chinese when the user writes in Chinese.
        """;

    // 安全段落
    private static final String SAFETY_SECTION = """
        ## Safety Guidelines

        - Do not execute destructive operations without explicit user confirmation
        - Avoid accessing sensitive files (credentials, private keys, etc.) unless necessary
        - When uncertain, ask for clarification before proceeding
        - Do not bypass security measures or perform unauthorized actions
        - Respect file system boundaries (stay within workspace when possible)
        """;

    // 子代理策略段落
    private static final String SUBAGENT_SECTION = """
        ## SubAgent (sessions_spawn)

        You have the ability to spawn SubAgents — independent assistants that execute tasks asynchronously \
        in isolated sessions. Use the `sessions_spawn` tool to delegate work.

        **When to use SubAgents:**
        - **Long-running tasks**: Tasks that may take significant time (network requests, large file processing, \
        complex computations). Spawn a subagent so the user can continue chatting.
        - **Parallel independent tasks**: Multiple tasks with no dependencies between them. Spawn one subagent \
        per task to run them concurrently.
        - **Isolated context tasks**: Tasks that benefit from a clean, focused context (e.g., researching a \
        specific topic, generating a standalone artifact).

        **Examples of good subagent use:**
        - "Monitor this URL every 30 seconds for 5 minutes" → spawn a subagent for the monitoring
        - "Analyze these 3 log files for errors" → spawn 3 subagents, one per file
        - "Research best practices for X while I work on Y" → spawn a subagent for the research
        - "Run these tests and report back" → spawn a subagent for test execution

        **When NOT to use SubAgents:**
        - Simple, fast operations (reading a file, quick calculations)
        - Tasks that require interactive back-and-forth with the user
        - Tasks that depend on the result of your current work

        **How it works:**
        1. Call `sessions_spawn` with a clear `task` description
        2. The subagent runs independently; you'll be notified when it completes
        3. Results are automatically announced back to the current session
        4. You can continue responding to the user while subagents work

        **Important**: Be proactive about using subagents. When you identify a task that fits the criteria above, \
        spawn a subagent without waiting for explicit instructions. Explain to the user what you've delegated.
        """;

    // 自适应规划协议段落
    private static final String PLANNING_SECTION = """
        ## Planning Protocol (MANDATORY)

        **CRITICAL RULE**: For any task that requires 2 or more tool calls, you MUST first output a plan \
        in plain text BEFORE making any tool calls. Never jump directly into tool execution for complex tasks.

        **When you receive a multi-step request, your FIRST response must be:**
        1. A brief assessment of what's needed
        2. A numbered list of steps you'll take (2-4 bullets)
        3. Any clarifying questions if requirements are ambiguous

        **Only AFTER outputting this plan** should you begin executing with tools.

        **Skip planning for:**
        - Simple questions (factual lookups, explanations)
        - Single-tool operations (read one file, run one command)
        - Follow-up actions in an ongoing task where the plan is already established

        **Key Principle**: Prefer asking one good clarifying question over making wrong assumptions \
        that lead to wasted effort. But don't over-ask — if the intent is reasonably clear, proceed \
        with your plan and start executing.
        """;

    public SystemPromptBuilder(ToolRegistry toolRegistry, SkillIndexBuilder skillIndexBuilder,
                                MemorySearchService memorySearchService,
                                Optional<McpPromptProvider> mcpPromptProvider,
                                SoulConfigService soulConfigService,
                                Optional<DataSourceService> dataSourceService) {
        this.toolRegistry = toolRegistry;
        this.skillIndexBuilder = skillIndexBuilder;
        this.memorySearchService = memorySearchService;
        this.mcpPromptProvider = mcpPromptProvider;
        this.soulConfigService = soulConfigService;
        this.dataSourceService = dataSourceService;
    }

    /**
     * 构建完整的系统提示
     */
    public String build(PromptMode mode) {
        return build(mode, null, null, null);
    }

    /**
     * 构建系统提示（可指定工具白名单）
     */
    public String build(PromptMode mode, Set<String> allowedTools) {
        return build(mode, allowedTools, null, null);
    }

    /**
     * 构建系统提示（可指定工具白名单和排除的 MCP 服务器）
     */
    public String build(PromptMode mode, Set<String> allowedTools, Set<String> excludedMcpServers) {
        return build(mode, allowedTools, excludedMcpServers, null);
    }

    /**
     * 构建系统提示（完整版本，支持所有参数）
     */
    public String build(PromptMode mode, Set<String> allowedTools, Set<String> excludedMcpServers, String dataSourceId) {
        if (mode == PromptMode.NONE) {
            return "You are MiniClaw, an AI coding assistant.";
        }

        StringBuilder sb = new StringBuilder();

        // 1. Identity
        sb.append(IDENTITY_SECTION.trim());
        sb.append("\n\n");

        // 1.5 Soul Configuration (only in FULL mode)
        if (mode == PromptMode.FULL) {
            String soulSection = buildSoulSection();
            if (!soulSection.isEmpty()) {
                sb.append(soulSection);
            }
        }

        // 2. Tooling (SKILL 模式跳过——工具定义已通过 tools 参数传递给 LLM)
        if (mode != PromptMode.SKILL) {
            sb.append(buildToolingSection(allowedTools, excludedMcpServers));
        }

        // 3. Safety (only in FULL mode)
        if (mode == PromptMode.FULL) {
            sb.append(SAFETY_SECTION.trim());
            sb.append("\n\n");
        }

        // 3.3 Planning Protocol (only in FULL mode)
        if (mode == PromptMode.FULL) {
            sb.append(PLANNING_SECTION.trim());
            sb.append("\n\n");
        }

        // 3.5 SubAgent guidance (only in FULL mode, when sessions_spawn tool is available)
        if (mode == PromptMode.FULL && hasSessionsSpawnTool(allowedTools)) {
            sb.append(SUBAGENT_SECTION.trim());
            sb.append("\n\n");
        }

        // 4. Memory (only in FULL mode)
        if (mode == PromptMode.FULL) {
            sb.append(buildMemorySection());
        }

        // 5. Skills (only in FULL mode, when available)
        if (mode == PromptMode.FULL) {
            String skillsSection = buildSkillsSection();
            if (!skillsSection.isEmpty()) {
                sb.append(skillsSection);
            }
        }

        // 6. Workspace
        sb.append(buildWorkspaceSection());

        // 6.5. DataSource (only in FULL mode, when dataSourceId is provided)
        if (mode == PromptMode.FULL && dataSourceId != null && !dataSourceId.isBlank()) {
            String dataSourceSection = buildDataSourceSection(dataSourceId);
            if (!dataSourceSection.isEmpty()) {
                sb.append(dataSourceSection);
            }
        }

        // 7. Current Date & Time (only in FULL mode)
        if (mode == PromptMode.FULL) {
            sb.append(buildDateTimeSection());
        }

        // 8. Runtime
        sb.append(buildRuntimeSection(mode));

        // 9. MCP Server Capabilities (only in FULL mode)
        if (mode == PromptMode.FULL) {
            mcpPromptProvider.ifPresent(provider -> {
                String mcpAdditions = provider.getSystemPromptAdditions(excludedMcpServers);
                if (!mcpAdditions.isEmpty()) {
                    sb.append(mcpAdditions.trim());
                    sb.append("\n\n");
                }
            });
        }

        return sb.toString().trim();
    }

    /**
     * 构建 Soul 配置段落
     */
    private String buildSoulSection() {
        try {
            String soulPrompt = soulConfigService.generateSystemPrompt();
            if (soulPrompt != null && !soulPrompt.isEmpty()) {
                return soulPrompt.trim() + "\n\n";
            }
        } catch (Exception e) {
            log.warn("Failed to build soul section", e);
        }
        return "";
    }

    /**
     * 检查 sessions_spawn 工具是否可用
     */
    private boolean hasSessionsSpawnTool(Set<String> allowedTools) {
        List<ToolDefinition> tools = toolRegistry.listDefinitions();
        boolean toolExists = tools.stream().anyMatch(t -> "sessions_spawn".equals(t.getName()));
        if (!toolExists) return false;
        // 如果有白名单，检查是否在白名单中
        return allowedTools == null || allowedTools.contains("sessions_spawn");
    }

    /**
     * 构建工具段落
     */
    private String buildToolingSection(Set<String> allowedTools, Set<String> excludedMcpServers) {
        List<ToolDefinition> tools = toolRegistry.listDefinitions(excludedMcpServers);

        if (tools.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## Available Tools\n\n");
        sb.append("You can use the following tools to help complete tasks:\n\n");

        for (ToolDefinition tool : tools) {
            // 如果有白名单且当前工具不在白名单中，跳过
            if (allowedTools != null && !allowedTools.contains(tool.getName())) {
                continue;
            }

            sb.append(String.format("- **%s**: %s", tool.getName(), tool.getDescription()));
            if (tool.isHitl()) {
                sb.append(" _(requires confirmation)_");
            }
            sb.append("\n");
        }

        sb.append("\nUse tools when they help accomplish the task. Always explain what you're doing.\n\n");
        return sb.toString();
    }

    /**
     * 构建技能段落
     */
    private String buildSkillsSection() {
        String skillIndex = skillIndexBuilder.buildIndex();

        if (skillIndex.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## Skills\n\n");
        sb.append("Skills are specialized instruction sets that dramatically improve output quality for specific tasks.\n\n");
        sb.append("**How to use skills:**\n");
        sb.append("1. **Manual trigger**: User types `/skill-name arguments` (e.g., `/frontend-design create a login page`)\n");
        sb.append("2. **Auto trigger**: Call the `use_skill` tool when a task matches an available skill\n\n");
        sb.append("**CRITICAL — Auto-activation rules:**\n");
        sb.append("- **BEFORE** writing code, creating files, or generating content for a task, check if any skill matches\n");
        sb.append("- If a skill matches, call `use_skill(skill_name=\"...\")` FIRST to load expert instructions\n");
        sb.append("- Skills provide specialized workflows and quality standards — always prefer using a matching skill\n");
        sb.append("- Do NOT skip skill activation to save a step — the quality improvement is significant\n\n");

        // 附加技能索引（只有 XML 部分，说明已在上面）
        sb.append(skillIndexBuilder.buildCompactIndex());
        sb.append("\n\n");

        return sb.toString();
    }

    /**
     * 构建记忆段落
     */
    private String buildMemorySection() {
        StringBuilder sb = new StringBuilder();
        sb.append("## Memory\n\n");
        sb.append("You have access to a **global, cross-session** memory system:\n\n");
        sb.append("- `memory_search(query)`: Search all historical memories (preferences, facts, past summaries)\n");
        sb.append("- `memory_get(path)`: Read specific memory files\n");
        sb.append("- `memory_write(content, target)`: Save important information\n");
        sb.append("  - target=\"core\" → MEMORY.md (long-term: preferences, constraints)\n");
        sb.append("  - target=\"daily\" → Today's log (session summaries, work records)\n\n");
        sb.append("**Key point**: Memories are global and cross-session. Information saved today ");
        sb.append("will be searchable in all future conversations. This is a personal assistant, not multi-tenant.\n\n");
        sb.append("**When to use memory:**\n");
        sb.append("- Search for relevant context at conversation start\n");
        sb.append("- Save user preferences/constraints to core memory\n");
        sb.append("- Summarize significant tasks to daily log\n\n");
        return sb.toString();
    }

    /**
     * 构建工作目录段落
     */
    private String buildWorkspaceSection() {
        Path workspacePath = Path.of(workspace).toAbsolutePath().normalize();

        StringBuilder sb = new StringBuilder();
        sb.append("## Workspace\n\n");
        sb.append(String.format("Working directory: `%s`\n\n", workspacePath));
        sb.append("File operations should be relative to this directory unless specified otherwise.\n\n");
        return sb.toString();
    }

    /**
     * 构建日期时间段落
     */
    private String buildDateTimeSection() {
        LocalDateTime now = LocalDateTime.now();
        ZoneId zoneId = ZoneId.systemDefault();

        StringBuilder sb = new StringBuilder();
        sb.append("## Current Date & Time\n\n");
        sb.append(String.format("- Date: %s\n", now.format(DateTimeFormatter.ISO_LOCAL_DATE)));
        sb.append(String.format("- Time: %s\n", now.format(DateTimeFormatter.ofPattern("HH:mm"))));
        sb.append(String.format("- Timezone: %s\n\n", zoneId.getId()));
        return sb.toString();
    }

    /**
     * 构建运行环境段落
     */
    private String buildRuntimeSection(PromptMode mode) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Runtime\n\n");
        sb.append(String.format("- OS: %s\n", System.getProperty("os.name")));
        sb.append(String.format("- Java: %s\n", System.getProperty("java.version")));
        sb.append(String.format("- Mode: %s\n", mode.name().toLowerCase()));
        sb.append("\n");
        return sb.toString();
    }

    /**
     * 构建数据源段落
     *
     * @deprecated 数据源查询已由 DataQueryAgentStrategy 通过独立的 DataQueryPromptBuilder 处理。
     *             此方法保留用于向后兼容，不再被主流程调用。
     */
    @Deprecated
    private String buildDataSourceSection(String dataSourceId) {
        if (dataSourceService.isEmpty()) {
            return "";
        }

        try {
            DataSourceDTO dataSource = dataSourceService.get().getDataSource(dataSourceId);
            if (dataSource == null) {
                log.warn("DataSource not found: {}", dataSourceId);
                return "";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("## Active Data Source\n\n");
            sb.append(String.format("**Name**: %s\n", dataSource.getName()));
            sb.append(String.format("**Type**: %s\n", dataSource.getType()));
            sb.append("\n");

            // 获取 schema 信息
            try {
                SchemaMetadata schema = dataSourceService.get().getSchemaMetadata(dataSourceId);
                if (schema != null) {
                    sb.append("**Schema Information**:\n\n");

                    if (schema.getTables() != null && !schema.getTables().isEmpty()) {
                        sb.append("Available tables:\n");
                        for (SchemaMetadata.TableMetadata table : schema.getTables()) {
                            sb.append(String.format("- **%s**: %s\n",
                                table.getTableName(),
                                table.getComment() != null ? table.getComment() : ""));

                            if (table.getColumns() != null && !table.getColumns().isEmpty()) {
                                sb.append("  Columns:\n");
                                for (SchemaMetadata.ColumnMetadata column : table.getColumns()) {
                                    sb.append(String.format("  - `%s` (%s)%s%s\n",
                                        column.getColumnName(),
                                        column.getDataType(),
                                        column.isPrimaryKey() ? " [PK]" : "",
                                        column.getComment() != null ? " - " + column.getComment() : ""));
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to fetch schema for dataSource: {}", dataSourceId, e);
            }

            sb.append("\n**IMPORTANT INSTRUCTIONS**:\n");
            sb.append("- The user has selected this data source for their query\n");
            sb.append("- **CRITICAL**: You can ONLY execute SELECT queries. INSERT, UPDATE, DELETE, DROP, and other write operations are FORBIDDEN\n");
            sb.append("- Use the `datasource_query` tool to execute read-only SQL queries against this database\n");
            sb.append("- **Tool parameters**: datasource_query(id=\"<dataSourceId>\", query=\"SELECT ...\")\n");
            sb.append("  - The `id` parameter is automatically set to: " + dataSourceId + "\n");
            sb.append("  - The `query` parameter should contain your SELECT statement\n");
            sb.append("- Analyze the user's question and construct appropriate SELECT queries based on the schema above\n");
            sb.append("- After retrieving data, you should:\n");
            sb.append("  1. Summarize the findings in a clear, concise manner\n");
            sb.append("  2. If appropriate, suggest creating visualizations (charts, graphs) to present the data\n");
            sb.append("  3. Use the appropriate tools to generate visual representations when helpful\n");
            sb.append("- Always validate your SQL queries against the schema before execution\n");
            sb.append("- Handle errors gracefully and explain any issues to the user\n");
            sb.append("- **NEVER** attempt to modify data - this is a read-only connection\n\n");

            return sb.toString();
        } catch (Exception e) {
            log.error("Failed to build data source section for: {}", dataSourceId, e);
            return "";
        }
    }

    /**
     * Prompt 模式
     */
    public enum PromptMode {
        /** 完整提示，包含所有段落 */
        FULL,
        /** 精简提示，用于子代理，省略 Skills、Safety、DateTime */
        MINIMAL,
        /** 技能模式，仅 Identity + Workspace + Runtime，不含工具列表（工具通过 tools 参数传递） */
        SKILL,
        /** 仅身份行 */
        NONE
    }
}
