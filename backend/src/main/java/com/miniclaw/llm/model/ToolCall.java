package com.miniclaw.llm.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LLM 工具调用
 * 
 * 对应 OpenAI Function Calling 响应中的 tool_calls
 * 
 * 什么是 Function Calling？
 * - 让 LLM 能够调用外部工具
 * - LLM 决定调用哪个工具、传什么参数
 * - 应用执行工具，返回结果给 LLM
 * 
 * 典型流程：
 * 1. 用户："北京今天天气怎么样？"
 * 2. LLM 返回 ToolCall(name="get_weather", arguments="{\"city\":\"北京\"}")
 * 3. 应用执行 get_weather("北京")，得到 "北京今天晴，25°C"
 * 4. 应用将结果作为 tool 消息发回 LLM
 * 5. LLM 生成最终回复："北京今天天气晴朗，温度 25°C"
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {

    /**
     * 调用 ID（必填）
     * 
     * 每次工具调用的唯一标识
     * 用于关联工具结果（tool 消息）
     * 
     * 示例：call_abc123
     */
    private String id;

    /**
     * 类型（默认为 "function"）
     * 
     * OpenAI 当前只支持 "function"
     * 保留字段，未来可能支持其他类型
     */
    @Builder.Default
    private String type = "function";

    /**
     * 函数调用信息（必填）
     * 
     * 包含函数名和参数
     */
    private FunctionCall function;

    /**
     * 函数调用信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionCall {
        
        /**
         * 函数名称
         * 
         * 对应工具定义中的 function.name
         * 示例：get_weather、read_file、execute_sql
         */
        private String name;

        /**
         * 参数 JSON 字符串
         * 
         * LLM 生成的参数，JSON 格式
         * 需要解析后传给实际的工具函数
         * 
         * 示例：
         * - "{\"city\":\"北京\"}"
         * - "{\"path\":\"/tmp/test.txt\"}"
         * 
         * 为什么是字符串而不是 Map？
         * - JSON 是通用格式，可以表示复杂结构
         * - LLM 直接输出 JSON 字符串
         * - 应用可以按需解析
         */
        private String arguments;
    }

    // ==================== 便捷方法 ====================

    /**
     * 获取工具名称
     * 
     * @return 函数名，如果 function 为 null 则返回 null
     */
    public String getName() {
        return function != null ? function.getName() : null;
    }

    /**
     * 获取参数 JSON
     * 
     * @return 参数 JSON 字符串，如果 function 为 null 则返回 null
     */
    public String getArguments() {
        return function != null ? function.getArguments() : null;
    }
}
