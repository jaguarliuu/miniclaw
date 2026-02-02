package com.jaguarliu.ai.llm.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

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
     * 完成原因（最后一个 chunk 才有）
     */
    private String finishReason;

    /**
     * 是否是最后一个 chunk
     */
    private boolean done;
}
