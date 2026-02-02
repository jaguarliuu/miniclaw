package com.jaguarliu.ai.tools;

import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 工具接口
 * 所有工具都需要实现此接口
 */
public interface Tool {

    /**
     * 获取工具定义
     */
    ToolDefinition getDefinition();

    /**
     * 执行工具
     *
     * @param arguments LLM 传入的参数（从 JSON 解析）
     * @return 执行结果（响应式）
     */
    Mono<ToolResult> execute(Map<String, Object> arguments);

    /**
     * 获取工具名称（便捷方法）
     */
    default String getName() {
        return getDefinition().getName();
    }

    /**
     * 是否需要 HITL 确认（便捷方法）
     */
    default boolean requiresHitl() {
        return getDefinition().isHitl();
    }
}
