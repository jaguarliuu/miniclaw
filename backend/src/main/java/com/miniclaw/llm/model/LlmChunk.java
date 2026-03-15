package com.miniclaw.llm.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

/**
 * LLM 流式响应块
 * 
 * 对应 OpenAI Chat Completions API 流式响应的单个数据块
 * 
 * 为什么叫 Chunk（块）而不是 Response？
 * - 流式输出是"一块一块"到达的
 * - 每个 Chunk 只包含增量（delta）
 * - 需要累积多个 Chunk 才能得到完整响应
 * 
 * 与 LlmResponse 的区别：
 * - LlmResponse：完整响应（一次性返回）
 * - LlmChunk：增量块（多次返回）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmChunk {

    /**
     * 内容增量
     * 
     * 每次 SSE 事件返回的文本片段
     * 需要累积拼接才能得到完整内容
     * 
     * 示例（流式输出过程）：
     * Chunk 1: delta = "你"
     * Chunk 2: delta = "好"
     * Chunk 3: delta = "！"
     * 累积结果: "你好！"
     */
    private String delta;

    /**
     * 工具调用列表（可选）
     * 
     * 在 finishReason = "tool_calls" 时才有
     * 包含完整的工具调用信息（已累积完成）
     * 
     * 为什么在 Chunk 中返回？
     * - 流式输出结束时，需要知道完整的 tool_calls
     * - 避免在流式过程中解析不完整的 JSON
     */
    private List<ToolCall> toolCalls;

    /**
     * 完成原因
     * 
     * - "stop"：正常结束
     * - "tool_calls"：调用了工具
     * - "length"：达到 token 限制
     * - null：还在输出中
     */
    private String finishReason;

    /**
     * 是否是最后一个 chunk
     * 
     * - true：流结束
     * - false：还有更多数据
     * 
     * 用途：
     * - 前端知道何时停止显示加载动画
     * - 后端知道何时清理资源
     */
    private boolean done;

    /**
     * 工具调用函数名（首次 delta 时有值）
     * 
     * 在流式输出工具调用时，第一个 chunk 会包含函数名
     * 用于提前知道要调用哪个工具
     * 
     * 为什么单独存函数名？
     * - 工具调用的 arguments 是分片到达的
     * - 但函数名在第一个 chunk 就能确定
     * - 可以提前做准备工作（如参数校验）
     */
    private String toolCallFunctionName;

    /**
     * 工具调用参数增量片段
     * 
     * 原始 JSON 片段，需要累积拼接
     * 
     * 示例（流式输出工具调用）：
     * Chunk 1: toolCallArgumentsDelta = "{\"ci"
     * Chunk 2: toolCallArgumentsDelta = "ty\":"
     * Chunk 3: toolCallArgumentsDelta = "\"北京\""
     * Chunk 4: toolCallArgumentsDelta = "}"
     * 累积结果: "{\"city\":\"北京\"}"
     */
    private String toolCallArgumentsDelta;

    /**
     * Token 使用量（可选）
     * 
     * 仅在流式输出的最后一个 chunk 携带
     * 其他 chunk 为 null
     * 
     * 为什么只在最后携带？
     * - LLM 需要生成完才知道用了多少 token
     * - 减少 SSE 数据量
     */
    private LlmResponse.Usage usage;

    /**
     * 判断是否有工具调用
     */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
