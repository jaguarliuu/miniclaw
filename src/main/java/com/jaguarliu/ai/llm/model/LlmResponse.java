package com.jaguarliu.ai.llm.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

/**
 * LLM 响应模型（同步调用）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmResponse {

    /**
     * 响应内容（有 tool_calls 时可能为 null）
     */
    private String content;

    /**
     * 工具调用列表
     */
    private List<ToolCall> toolCalls;

    /**
     * 完成原因：stop, length, tool_calls 等
     */
    private String finishReason;

    /**
     * 使用的 token 数
     */
    private Usage usage;

    /**
     * 是否有工具调用
     */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Usage {
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
    }
}
