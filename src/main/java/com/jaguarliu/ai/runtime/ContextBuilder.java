package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.llm.model.LlmRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文构建器
 * 负责组装 LLM 请求的 messages 列表
 */
@Slf4j
@Component
public class ContextBuilder {

    @Value("${agent.system-prompt:你是一个有帮助的 AI 助手。}")
    private String defaultSystemPrompt;

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
}
