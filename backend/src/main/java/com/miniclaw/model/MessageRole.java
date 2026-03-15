package com.miniclaw.model;

/**
 * 消息角色枚举
 * 
 * 对应 LLM API 中的消息角色
 * 
 * LLM API（如 OpenAI、DeepSeek）使用角色来区分消息来源：
 * - user: 用户发送的消息
 * - assistant: AI 助手的回复
 * - system: 系统提示词（设置 AI 行为）
 * - tool: 工具调用的结果
 */
public enum MessageRole {
    USER,       // 用户消息
    ASSISTANT,  // AI 助手消息
    SYSTEM,     // 系统提示
    TOOL        // 工具调用结果
}
