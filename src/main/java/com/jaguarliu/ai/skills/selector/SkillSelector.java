package com.jaguarliu.ai.skills.selector;

import com.jaguarliu.ai.skills.registry.SkillRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skill 选择器
 *
 * 职责：
 * 1. 手动触发：解析 /skill-name args 格式
 * 2. 自动选择：解析 LLM 回复中的 [USE_SKILL:xxx]
 *
 * 使用场景：
 * - 用户输入时调用 tryManualSelection() 检查是否为 slash command
 * - LLM 回复时调用 parseFromLlmResponse() 检查是否请求使用 skill
 */
@Slf4j
@Service
public class SkillSelector {

    /**
     * 匹配 /skill-name 或 /skill-name args
     * 组1: skill 名称
     * 组2: 参数（可选）
     */
    private static final Pattern SLASH_COMMAND = Pattern.compile(
            "^/(\\S+)(?:\\s+(.*))?$",
            Pattern.DOTALL
    );

    /**
     * 匹配 LLM 回复中的 [USE_SKILL:skill-name]
     * 组1: skill 名称
     */
    private static final Pattern USE_SKILL = Pattern.compile(
            "\\[USE_SKILL:(\\S+?)\\]"
    );

    private final SkillRegistry registry;

    public SkillSelector(SkillRegistry registry) {
        this.registry = registry;
    }

    /**
     * 尝试解析手动触发的 skill（/skill-name args）
     *
     * @param userInput 用户输入
     * @return 选择结果
     */
    public SkillSelection tryManualSelection(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return SkillSelection.none(userInput);
        }

        String trimmed = userInput.trim();

        // 必须以 / 开头
        if (!trimmed.startsWith("/")) {
            return SkillSelection.none(userInput);
        }

        Matcher matcher = SLASH_COMMAND.matcher(trimmed);
        if (!matcher.matches()) {
            return SkillSelection.none(userInput);
        }

        String skillName = matcher.group(1);
        String arguments = matcher.group(2);

        // 检查 skill 是否存在且可用
        if (!registry.isAvailable(skillName)) {
            log.warn("Skill not found or unavailable: {}", skillName);
            return SkillSelection.none(userInput);
        }

        log.info("Manual skill selection: {} (args: {})",
                skillName, arguments != null ? arguments.substring(0, Math.min(50, arguments.length())) : "none");

        return SkillSelection.manual(skillName, arguments, userInput);
    }

    /**
     * 从 LLM 回复中解析 [USE_SKILL:xxx]
     *
     * @param llmResponse   LLM 的回复内容
     * @param originalInput 原始用户输入（作为 $ARGUMENTS）
     * @return 选择结果
     */
    public SkillSelection parseFromLlmResponse(String llmResponse, String originalInput) {
        if (llmResponse == null || llmResponse.isBlank()) {
            return SkillSelection.none(originalInput);
        }

        Matcher matcher = USE_SKILL.matcher(llmResponse);
        if (!matcher.find()) {
            return SkillSelection.none(originalInput);
        }

        String skillName = matcher.group(1);

        // 检查 skill 是否存在且可用
        if (!registry.isAvailable(skillName)) {
            log.warn("LLM requested unavailable skill: {}", skillName);
            return SkillSelection.none(originalInput);
        }

        log.info("Auto skill selection from LLM: {}", skillName);

        return SkillSelection.auto(skillName, originalInput);
    }

    /**
     * 检查用户输入是否为 slash command 格式
     * （不检查 skill 是否存在）
     */
    public boolean isSlashCommand(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return false;
        }
        return SLASH_COMMAND.matcher(userInput.trim()).matches();
    }

    /**
     * 从 slash command 中提取 skill 名称
     * （不检查 skill 是否存在）
     */
    public String extractSkillName(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return null;
        }

        Matcher matcher = SLASH_COMMAND.matcher(userInput.trim());
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 检查 LLM 回复是否包含 USE_SKILL 指令
     */
    public boolean containsUseSkill(String llmResponse) {
        if (llmResponse == null || llmResponse.isBlank()) {
            return false;
        }
        return USE_SKILL.matcher(llmResponse).find();
    }

    /**
     * 从 LLM 回复中提取 skill 名称
     * （不检查 skill 是否存在）
     */
    public String extractSkillNameFromLlm(String llmResponse) {
        if (llmResponse == null || llmResponse.isBlank()) {
            return null;
        }

        Matcher matcher = USE_SKILL.matcher(llmResponse);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 从 LLM 回复中移除 [USE_SKILL:xxx] 标记
     * （用于清理显示给用户的内容）
     */
    public String removeUseSkillMarker(String llmResponse) {
        if (llmResponse == null) {
            return null;
        }
        return USE_SKILL.matcher(llmResponse).replaceAll("").trim();
    }
}
