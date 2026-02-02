package com.jaguarliu.ai.llm.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LLM 工具调用
 * 对应 OpenAI Function Calling 响应中的 tool_calls
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {

    /**
     * 调用 ID（用于关联工具结果）
     */
    private String id;

    /**
     * 类型（固定为 "function"）
     */
    @Builder.Default
    private String type = "function";

    /**
     * 函数调用信息
     */
    private FunctionCall function;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionCall {
        /**
         * 函数名称
         */
        private String name;

        /**
         * 参数 JSON 字符串
         */
        private String arguments;
    }

    /**
     * 便捷方法：获取工具名称
     */
    public String getName() {
        return function != null ? function.getName() : null;
    }

    /**
     * 便捷方法：获取参数 JSON
     */
    public String getArguments() {
        return function != null ? function.getArguments() : null;
    }
}
