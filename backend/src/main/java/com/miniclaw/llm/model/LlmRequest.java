package com.miniclaw.llm.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * LLM 请求模型
 * 
 * 对应 OpenAI Chat Completions API 的请求格式
 * 
 * 为什么需要这个模型？
 * - 类型安全：编译时检查字段类型
 * - 可读性：比 Map 更清晰
 * - 文档化：字段注释就是文档
 * 
 * OpenAI API 文档：
 * https://platform.openai.com/docs/api-reference/chat/create
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmRequest {

    /**
     * 消息列表（必填）
     * 
     * 消息按顺序排列，构成对话上下文
     * 通常包含：
     * - system: 系统提示词（可选）
     * - user: 用户消息
     * - assistant: AI 的历史回复（多轮对话）
     */
    private List<Message> messages;

    /**
     * 模型名称（可选）
     * 
     * 示例：
     * - gpt-4o（OpenAI）
     * - deepseek-chat（DeepSeek）
     * - qwen-plus（通义千问）
     * 
     * 如果不指定，使用 LlmProperties 中的默认模型
     */
    private String model;

    /**
     * 温度参数（可选，0-2）
     * 
     * 控制输出的随机性：
     * - 0：确定性输出，适合代码、事实性问题
     * - 0.7：平衡创造性和一致性（默认）
     * - 1.5+：高随机性，适合创意写作
     * 
     * 为什么叫"温度"？
     * 类比物理学中的温度：温度越高，分子运动越剧烈（随机性越大）
     */
    private Double temperature;

    /**
     * 最大 token 数（可选）
     * 
     * 限制响应长度：
     * - 避免成本失控
     * - 控制响应时间
     * - 适应特定场景（如摘要限制在 500 字内）
     */
    private Integer maxTokens;

    /**
     * 指定使用的 Provider ID（可选）
     */
    private String providerId;

    /**
     * 工具定义列表（可选）
     * 
     * OpenAI Function Calling 格式
     * 允许 LLM 调用外部工具
     * 
     * 示例：
     * [{
     *   "type": "function",
     *   "function": {
     *     "name": "get_weather",
     *     "description": "获取指定城市的天气",
     *     "parameters": {
     *       "type": "object",
     *       "properties": {
     *         "city": {"type": "string"}
     *       }
     *     }
     *   }
     * }]
     */
    private List<Map<String, Object>> tools;

    /**
     * 工具选择策略（可选）
     * 
     * - "auto"：LLM 自动决定是否调用工具（默认）
     * - "none"：不调用任何工具
     * - "required"：必须调用工具
     * - {"type": "function", "function": {"name": "xxx"}}：指定工具
     */
    private String toolChoice;

    /**
     * 消息模型
     * 
     * 表示对话中的一条消息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Message {
        
        /**
         * 角色（必填）
         * 
         * - system：系统提示词，定义 AI 的行为
         * - user：用户发送的消息
         * - assistant：AI 助手的回复
         * - tool：工具调用的结果
         */
        private String role;

        /**
         * 消息内容（assistant 有 tool_calls 时可能为 null）
         * 
         * 对于 user/assistant：对话内容
         * 对于 tool：工具返回的结果（JSON 字符串）
         */
        private String content;

        /**
         * 工具调用列表（仅 assistant 角色有）
         * 
         * 当 LLM 决定调用工具时，返回这个字段
         * 而不是 content
         */
        private List<ToolCall> toolCalls;

        /**
         * 工具调用 ID（仅 tool 角色有）
         * 
         * 用于关联 assistant 消息中的 tool_call
         */
        private String toolCallId;

        // ==================== 便捷工厂方法 ====================

        /**
         * 创建系统消息
         */
        public static Message system(String content) {
            return Message.builder()
                    .role("system")
                    .content(content)
                    .build();
        }

        /**
         * 创建用户消息
         */
        public static Message user(String content) {
            return Message.builder()
                    .role("user")
                    .content(content)
                    .build();
        }

        /**
         * 创建助手消息
         */
        public static Message assistant(String content) {
            return Message.builder()
                    .role("assistant")
                    .content(content)
                    .build();
        }

        /**
         * 创建带工具调用的助手消息
         */
        public static Message assistantWithToolCalls(List<ToolCall> toolCalls) {
            return Message.builder()
                    .role("assistant")
                    .toolCalls(toolCalls)
                    .build();
        }

        /**
         * 创建工具结果消息
         */
        public static Message toolResult(String toolCallId, String content) {
            return Message.builder()
                    .role("tool")
                    .toolCallId(toolCallId)
                    .content(content)
                    .build();
        }
    }
}
