package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.skills.index.SkillIndexBuilder;
import com.jaguarliu.ai.skills.model.LoadedSkill;
import com.jaguarliu.ai.skills.registry.SkillRegistry;
import com.jaguarliu.ai.skills.selector.SkillSelection;
import com.jaguarliu.ai.skills.selector.SkillSelector;
import com.jaguarliu.ai.skills.template.SkillTemplateEngine;
import com.jaguarliu.ai.tools.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 上下文构建器
 * 负责组装 LLM 请求的 messages 列表
 *
 * 支持 Skill 集成：
 * - 索引注入：在 system prompt 中添加可用 skill 列表
 * - Skill 激活：激活 skill 后注入完整指令
 * - 工具限制：根据 skill 的 allowed-tools 过滤工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContextBuilder {

    private final ToolRegistry toolRegistry;
    private final SkillRegistry skillRegistry;
    private final SkillIndexBuilder skillIndexBuilder;
    private final SkillSelector skillSelector;
    private final SkillTemplateEngine templateEngine;

    @Value("${agent.system-prompt:你是一个有帮助的 AI 助手。}")
    private String defaultSystemPrompt;

    @Value("${skills.auto-select-enabled:true}")
    private boolean autoSelectEnabled;

    /**
     * 构建 LLM 请求
     * @param systemPrompt 系统提示（可选，为空则使用默认）
     * @param history 历史消息
     * @param userPrompt 用户当前输入
     * @return LlmRequest
     */
    public LlmRequest build(String systemPrompt, List<LlmRequest.Message> history, String userPrompt) {
        List<LlmRequest.Message> messages = new ArrayList<>();

        // 1. System prompt
        String system = (systemPrompt != null && !systemPrompt.isBlank()) ? systemPrompt : defaultSystemPrompt;
        messages.add(LlmRequest.Message.system(system));

        // 2. 历史消息
        if (history != null && !history.isEmpty()) {
            messages.addAll(history);
        }

        // 3. 当前用户输入
        messages.add(LlmRequest.Message.user(userPrompt));

        log.debug("Built context: systemPrompt={} chars, history={} msgs, userPrompt={} chars",
                system.length(), history != null ? history.size() : 0, userPrompt.length());

        return LlmRequest.builder()
                .messages(messages)
                .build();
    }

    /**
     * 简化版：只有用户输入
     */
    public LlmRequest build(String userPrompt) {
        return build(null, null, userPrompt);
    }

    /**
     * 带历史的版本
     */
    public LlmRequest buildWithHistory(List<LlmRequest.Message> history, String userPrompt) {
        return build(null, history, userPrompt);
    }

    /**
     * 构建带工具的 LLM 请求
     * @param history 历史消息
     * @param userPrompt 用户当前输入
     * @param enableTools 是否启用工具
     * @return LlmRequest
     */
    public LlmRequest buildWithTools(List<LlmRequest.Message> history, String userPrompt, boolean enableTools) {
        LlmRequest request = build(null, history, userPrompt);

        if (enableTools && toolRegistry.size() > 0) {
            request.setTools(toolRegistry.toOpenAiTools());
            request.setToolChoice("auto");
            log.debug("Enabled {} tools for request", toolRegistry.size());
        }

        return request;
    }

    /**
     * 构建带 Skill 索引的请求
     * 在 system prompt 中注入可用 skill 列表
     *
     * @param history 历史消息
     * @param userPrompt 用户当前输入
     * @param enableTools 是否启用工具
     * @return LlmRequest
     */
    public LlmRequest buildWithSkillIndex(List<LlmRequest.Message> history, String userPrompt, boolean enableTools) {
        // 构建带 skill 索引的 system prompt
        String systemWithSkills = defaultSystemPrompt + skillIndexBuilder.buildIndex();

        LlmRequest request = build(systemWithSkills, history, userPrompt);

        if (enableTools && toolRegistry.size() > 0) {
            request.setTools(toolRegistry.toOpenAiTools());
            request.setToolChoice("auto");
        }

        log.debug("Built context with skill index: {} available skills",
                skillRegistry.getAvailable().size());

        return request;
    }

    /**
     * 构建激活 Skill 后的请求
     * 使用 skill 的完整指令替代普通 system prompt
     *
     * @param skill 已加载的 skill
     * @param arguments 用户参数（$ARGUMENTS）
     * @param history 历史消息
     * @param userPrompt 用户当前输入
     * @param enableTools 是否启用工具
     * @return SkillAwareRequest 包含请求和工具限制信息
     */
    public SkillAwareRequest buildWithActiveSkill(
            LoadedSkill skill,
            String arguments,
            List<LlmRequest.Message> history,
            String userPrompt,
            boolean enableTools) {

        // 使用模板引擎编译 skill body
        SkillTemplateEngine.TemplateContext context = templateEngine.createContext()
                .withArguments(arguments != null ? arguments : userPrompt)
                .withProjectRoot(System.getProperty("user.dir"))
                .withCwd(System.getProperty("user.dir"));

        String compiledBody = templateEngine.render(skill.getBody(), context);

        // 构建 system prompt
        StringBuilder systemPrompt = new StringBuilder();
        systemPrompt.append(defaultSystemPrompt);
        systemPrompt.append("\n\n---\n\n");
        systemPrompt.append("## Active Skill: ").append(skill.getName()).append("\n\n");
        systemPrompt.append(compiledBody);

        // 添加工具限制提示
        if (skill.getAllowedTools() != null && !skill.getAllowedTools().isEmpty()) {
            systemPrompt.append("\n\n---\n");
            systemPrompt.append("**Tool Restriction**: Only use these tools: ");
            systemPrompt.append(String.join(", ", skill.getAllowedTools()));
        }

        LlmRequest request = build(systemPrompt.toString(), history, userPrompt);

        // 设置工具（可能需要过滤）
        Set<String> allowedTools = skill.getAllowedTools();
        if (enableTools && toolRegistry.size() > 0) {
            if (allowedTools != null && !allowedTools.isEmpty()) {
                // 只包含允许的工具
                request.setTools(toolRegistry.toOpenAiTools(allowedTools));
                log.debug("Enabled {} filtered tools for skill '{}'",
                        allowedTools.size(), skill.getName());
            } else {
                // 无限制，使用所有工具
                request.setTools(toolRegistry.toOpenAiTools());
            }
            request.setToolChoice("auto");
        }

        log.info("Built context with active skill: {} (allowed tools: {})",
                skill.getName(),
                allowedTools != null ? allowedTools.size() : "all");

        return new SkillAwareRequest(request, skill.getName(), allowedTools, skill.getConfirmBefore());
    }

    /**
     * 智能构建：自动检测是否为 slash command 并处理
     *
     * @param history 历史消息
     * @param userPrompt 用户当前输入
     * @param enableTools 是否启用工具
     * @return SkillAwareRequest
     */
    public SkillAwareRequest buildSmart(List<LlmRequest.Message> history, String userPrompt, boolean enableTools) {
        // 1. 尝试手动 skill 选择
        SkillSelection selection = skillSelector.tryManualSelection(userPrompt);

        if (selection.isSelected()) {
            // 激活 skill
            Optional<LoadedSkill> loaded = skillRegistry.activate(selection.getSkillName());
            if (loaded.isPresent()) {
                return buildWithActiveSkill(
                        loaded.get(),
                        selection.getArguments(),
                        history,
                        selection.getArguments() != null ? selection.getArguments() : userPrompt,
                        enableTools
                );
            }
        }

        // 2. 如果启用自动选择，注入 skill 索引
        if (autoSelectEnabled && !skillRegistry.getAvailable().isEmpty()) {
            LlmRequest request = buildWithSkillIndex(history, userPrompt, enableTools);
            return new SkillAwareRequest(request, null, null, null);
        }

        // 3. 普通请求
        LlmRequest request = buildWithTools(history, userPrompt, enableTools);
        return new SkillAwareRequest(request, null, null, null);
    }

    /**
     * 处理 LLM 回复中的 [USE_SKILL:xxx]
     * 如果检测到，返回新的请求用于重新调用 LLM
     *
     * @param llmResponse LLM 的回复
     * @param originalInput 原始用户输入
     * @param history 历史消息
     * @param enableTools 是否启用工具
     * @return 如果需要重新调用返回新请求，否则返回 empty
     */
    public Optional<SkillAwareRequest> handleAutoSkillSelection(
            String llmResponse,
            String originalInput,
            List<LlmRequest.Message> history,
            boolean enableTools) {

        SkillSelection selection = skillSelector.parseFromLlmResponse(llmResponse, originalInput);

        if (!selection.isSelected()) {
            return Optional.empty();
        }

        Optional<LoadedSkill> loaded = skillRegistry.activate(selection.getSkillName());
        if (loaded.isEmpty()) {
            return Optional.empty();
        }

        log.info("Auto skill activation from LLM: {}", selection.getSkillName());

        return Optional.of(buildWithActiveSkill(
                loaded.get(),
                selection.getArguments(),
                history,
                originalInput,
                enableTools
        ));
    }

    /**
     * 构建消息列表（不包含 LlmRequest 包装，供 AgentRuntime 使用）
     * @param history 历史消息
     * @param userPrompt 用户当前输入
     * @return 消息列表（可变）
     */
    public List<LlmRequest.Message> buildMessages(List<LlmRequest.Message> history, String userPrompt) {
        List<LlmRequest.Message> messages = new ArrayList<>();

        // 1. System prompt
        messages.add(LlmRequest.Message.system(defaultSystemPrompt));

        // 2. 历史消息
        if (history != null && !history.isEmpty()) {
            messages.addAll(history);
        }

        // 3. 当前用户输入
        messages.add(LlmRequest.Message.user(userPrompt));

        return messages;
    }

    /**
     * 带 Skill 信息的请求包装
     */
    public record SkillAwareRequest(
            LlmRequest request,
            String activeSkillName,
            Set<String> allowedTools,
            Set<String> confirmBefore
    ) {
        /**
         * 是否有激活的 skill
         */
        public boolean hasActiveSkill() {
            return activeSkillName != null;
        }

        /**
         * 检查工具是否被允许
         */
        public boolean isToolAllowed(String toolName) {
            if (allowedTools == null || allowedTools.isEmpty()) {
                return true; // 无限制
            }
            return allowedTools.contains(toolName);
        }

        /**
         * 检查工具是否需要确认
         */
        public boolean requiresConfirmation(String toolName) {
            if (confirmBefore == null || confirmBefore.isEmpty()) {
                return false;
            }
            return confirmBefore.contains(toolName);
        }
    }
}
