package com.miniclaw.llm.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

/**
 * LLM 响应模型（同步调用）
 * 
 * 对应 OpenAI Chat Completions API 的响应格式
 * 
 * 与 LlmChunk 的区别：
 * - LlmResponse：完整的响应（同步调用）
 * - LlmChunk：增量的响应块（流式调用）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmResponse {

    /**
     * 响应内容（有 tool_calls 时可能为 null）
     * 
     * - 普通对话：AI 的文本回复
     * - Function Calling：可能为 null，需要查看 toolCalls
     */
    private String content;

    /**
     * 工具调用列表（可选）
     * 
     * 当 LLM 决定调用工具时，这个字段有值
     * 此时 content 可能为 null 或包含文本说明
     * 
     * 典型流程：
     * 1. LLM 返回 toolCalls
     * 2. 应用执行工具，获取结果
     * 3. 将结果作为 tool 消息发回 LLM
     * 4. LLM 返回最终回复（content）
     */
    private List<ToolCall> toolCalls;

    /**
     * 完成原因
     * 
     * - "stop"：正常结束
     * - "length"：达到 max_tokens 限制
     * - "tool_calls"：LLM 调用了工具
     * - "content_filter"：内容被过滤
     */
    private String finishReason;

    /**
     * Token 使用量
     * 
     * 用于：
     * - 成本计算
     * - 使用限制
     * - 性能监控
     */
    private Usage usage;

    /**
     * 判断是否有工具调用
     * 
     * @return true 如果有工具调用
     */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    /**
     * Token 使用量
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Usage {
        
        /**
         * 输入 token 数
         * 
         * 包括 system prompt + 历史消息 + 当前用户消息
         */
        private Integer promptTokens;

        /**
         * 输出 token 数
         * 
         * LLM 生成的 token 数
         */
        private Integer completionTokens;

        /**
         * 总 token 数
         * 
         * = promptTokens + completionTokens
         */
        private Integer totalTokens;

        /**
         * 缓存命中的输入 token 数（可选）
         * 
         * OpenAI 提供的 Prompt Caching 功能
         * 命中缓存的部分成本更低
         */
        private Integer cacheReadInputTokens;

        /**
         * 缓存创建的输入 token 数（可选）
         * 
         * 首次创建缓存的 token 数
         */
        private Integer cacheCreationInputTokens;
    }
}
