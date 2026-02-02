package com.jaguarliu.ai.llm.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * LLM 响应模型（同步调用）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmResponse {

    /**
     * 响应内容
     */
    private String content;

    /**
     * 完成原因：stop, length, tool_calls 等
     */
    private String finishReason;

    /**
     * 使用的 token 数
     */
    private Usage usage;

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
