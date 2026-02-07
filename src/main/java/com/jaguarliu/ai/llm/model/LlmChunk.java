package com.jaguarliu.ai.llm.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

/**
 * LLM 流式响应块
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmChunk {

    /**
     * 内容增量
     */
    private String delta;

    /**
     * 工具调用增量（流式模式下逐步累积）
     */
    private List<ToolCall> toolCalls;

    /**
     * 完成原因：stop / tool_calls / length
     */
    private String finishReason;

    /**
     * 是否是最后一个 chunk
     */
    private boolean done;

    /**
     * 工具调用函数名（首次 delta 时有值）
     */
    private String toolCallFunctionName;

    /**
     * 工具调用参数增量片段（原始 JSON 片段）
     */
    private String toolCallArgumentsDelta;

    /**
     * 是否有工具调用
     */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
