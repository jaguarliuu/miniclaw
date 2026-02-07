package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.memory.search.MemorySearchService;
import com.jaguarliu.ai.skills.index.SkillIndexBuilder;
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

    @Value("${tools.workspace:./workspace}")
    private String workspace;

    @Value("${agent.system-prompt:}")
    private String customSystemPrompt;

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

    public SystemPromptBuilder(ToolRegistry toolRegistry, SkillIndexBuilder skillIndexBuilder,
                                MemorySearchService memorySearchService) {
        this.toolRegistry = toolRegistry;
        this.skillIndexBuilder = skillIndexBuilder;
        this.memorySearchService = memorySearchService;
    }

    /**
     * 构建完整的系统提示
     */
    public String build(PromptMode mode) {
        return build(mode, null);
    }

    /**
     * 构建系统提示（可指定工具白名单）
     */
    public String build(PromptMode mode, Set<String> allowedTools) {
        if (mode == PromptMode.NONE) {
            return "You are MiniClaw, an AI coding assistant.";
        }

        StringBuilder sb = new StringBuilder();

        // 1. Identity
        sb.append(IDENTITY_SECTION.trim());
        sb.append("\n\n");

        // 2. Tooling
        sb.append(buildToolingSection(allowedTools));

        // 3. Safety (only in FULL mode)
        if (mode == PromptMode.FULL) {
            sb.append(SAFETY_SECTION.trim());
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

        // 7. Current Date & Time (only in FULL mode)
        if (mode == PromptMode.FULL) {
            sb.append(buildDateTimeSection());
        }

        // 8. Runtime
        sb.append(buildRuntimeSection(mode));

        // Append custom prompt if configured
        if (customSystemPrompt != null && !customSystemPrompt.isBlank()) {
            sb.append("\n---\n\n");
            sb.append("## Custom Instructions\n\n");
            sb.append(customSystemPrompt.trim());
            sb.append("\n");
        }

        return sb.toString().trim();
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
    private String buildToolingSection(Set<String> allowedTools) {
        List<ToolDefinition> tools = toolRegistry.listDefinitions();

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
        sb.append("Skills are specialized instruction sets that enhance your capabilities for specific tasks.\n\n");
        sb.append("**How to use skills:**\n");
        sb.append("1. **Manual trigger**: User types `/skill-name arguments` (e.g., `/frontend-design create a login page`)\n");
        sb.append("2. **Auto trigger**: When you identify a task that matches a skill, respond with `[USE_SKILL:skill-name]`\n\n");
        sb.append("**When to auto-trigger a skill:**\n");
        sb.append("- The user's request clearly matches a skill's purpose\n");
        sb.append("- The skill would significantly improve the quality of your response\n");
        sb.append("- You need specialized knowledge or workflow guidance\n\n");
        sb.append("**Important**: When you respond with `[USE_SKILL:xxx]`, the system will:\n");
        sb.append("1. Load the full skill instructions\n");
        sb.append("2. Re-invoke you with the skill context\n");
        sb.append("3. You will then follow the skill's detailed instructions\n\n");

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
     * Prompt 模式
     */
    public enum PromptMode {
        /** 完整提示，包含所有段落 */
        FULL,
        /** 精简提示，用于子代理，省略 Skills、Safety、DateTime */
        MINIMAL,
        /** 仅身份行 */
        NONE
    }
}
