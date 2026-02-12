package com.jaguarliu.ai.tools.builtin;

import com.jaguarliu.ai.skills.registry.SkillRegistry;
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
 * Skill 激活工具
 *
 * 让 LLM 通过 function calling 激活技能，替代不可靠的 [USE_SKILL:xxx] 文本标记。
 * 工具调用是 LLM 的一等机制，激活率远高于文本模式匹配。
 *
 * 工作流程：
 * 1. LLM 识别到任务匹配某个 skill → 调用 use_skill(skill_name="xxx")
 * 2. 本工具验证 skill 存在且可用 → 返回 {"skill_activated": true, "skill_name": "xxx"}
 * 3. AgentRuntime 检测到 use_skill 调用 → 加载完整 skill 指令 → 替换系统提示词 → 继续循环
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UseSkillTool implements Tool {

    private final SkillRegistry skillRegistry;

    @Override
    public ToolDefinition getDefinition() {
        // 构建动态描述，包含当前可用的 skill 列表
        String skillList = skillRegistry.getAvailable().stream()
                .map(e -> e.getMetadata().getName() + " - " + truncate(e.getMetadata().getDescription(), 80))
                .collect(Collectors.joining("\n  "));

        String description = "Activate a specialized skill to enhance your capabilities for the current task. "
                + "Skills provide expert-level instructions and workflows for specific task types. "
                + "ALWAYS call this tool BEFORE writing code or creating files when a matching skill exists. "
                + "Available skills:\n  " + (skillList.isEmpty() ? "(none)" : skillList);

        return ToolDefinition.builder()
                .name("use_skill")
                .description(description)
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "skill_name", Map.of(
                                        "type", "string",
                                        "description", "The name of the skill to activate (e.g., 'frontend-design', 'pptx', 'xlsx')"
                                )
                        ),
                        "required", List.of("skill_name")
                ))
                .hitl(false)
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        String skillName = (String) arguments.get("skill_name");

        if (skillName == null || skillName.isBlank()) {
            return Mono.just(ToolResult.error("skill_name is required"));
        }

        skillName = skillName.trim();

        if (!skillRegistry.isAvailable(skillName)) {
            log.warn("use_skill called with unavailable skill: {}", skillName);
            String available = skillRegistry.getAvailable().stream()
                    .map(e -> e.getMetadata().getName())
                    .collect(Collectors.joining(", "));
            return Mono.just(ToolResult.error(
                    "Skill '" + skillName + "' not found or unavailable. Available skills: " + available));
        }

        log.info("use_skill tool activated skill: {}", skillName);

        // 返回激活标记，AgentRuntime 会拦截此结果并加载完整 skill 指令
        return Mono.just(ToolResult.success(
                "{\"skill_activated\": true, \"skill_name\": \"" + skillName + "\"}"));
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen - 3) + "...";
    }
}
