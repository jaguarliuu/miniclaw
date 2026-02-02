package com.jaguarliu.ai.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 工具调度器
 * 负责根据工具名称分发执行请求，并进行权限检查和错误封装
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolDispatcher {

    private final ToolRegistry toolRegistry;

    /**
     * 执行工具调用
     *
     * @param toolName  工具名称
     * @param arguments 参数
     * @return 执行结果
     */
    public Mono<ToolResult> dispatch(String toolName, Map<String, Object> arguments) {
        return dispatch(toolName, arguments, null);
    }

    /**
     * 执行工具调用（带白名单权限检查）
     *
     * @param toolName     工具名称
     * @param arguments    参数
     * @param allowedTools 允许的工具白名单，null 表示允许所有
     * @return 执行结果
     */
    public Mono<ToolResult> dispatch(String toolName, Map<String, Object> arguments, Set<String> allowedTools) {
        log.debug("Dispatching tool call: {} with arguments: {}", toolName, arguments);

        // 1. 工具名称校验
        if (toolName == null || toolName.isBlank()) {
            log.warn("Tool dispatch failed: empty tool name");
            return Mono.just(ToolResult.error("Tool name is required"));
        }

        // 2. 白名单权限检查
        if (allowedTools != null && !allowedTools.contains(toolName)) {
            log.warn("Tool dispatch denied: {} not in allowed list {}", toolName, allowedTools);
            return Mono.just(ToolResult.error("Tool '" + toolName + "' is not allowed"));
        }

        // 3. 获取工具
        Optional<Tool> toolOpt = toolRegistry.get(toolName);
        if (toolOpt.isEmpty()) {
            log.warn("Tool dispatch failed: tool not found: {}", toolName);
            return Mono.just(ToolResult.error("Tool not found: " + toolName));
        }

        Tool tool = toolOpt.get();

        // 4. 参数处理（确保 arguments 不为 null）
        Map<String, Object> safeArguments = arguments != null ? arguments : Map.of();

        // 5. 执行工具
        log.info("Executing tool: {} with arguments: {}", toolName, safeArguments);

        return tool.execute(safeArguments)
                .doOnSuccess(result -> {
                    if (result.isSuccess()) {
                        log.info("Tool {} executed successfully", toolName);
                    } else {
                        log.warn("Tool {} returned error: {}", toolName, result.getContent());
                    }
                })
                .onErrorResume(e -> {
                    log.error("Tool {} execution exception: {}", toolName, e.getMessage(), e);
                    return Mono.just(ToolResult.error("Tool execution failed: " + e.getMessage()));
                });
    }

    /**
     * 检查工具是否需要 HITL 确认
     *
     * @param toolName 工具名称
     * @return 是否需要确认，工具不存在时返回 false
     */
    public boolean requiresHitl(String toolName) {
        return toolRegistry.get(toolName)
                .map(tool -> tool.getDefinition().isHitl())
                .orElse(false);
    }

    /**
     * 获取工具定义
     *
     * @param toolName 工具名称
     * @return 工具定义
     */
    public Optional<ToolDefinition> getToolDefinition(String toolName) {
        return toolRegistry.get(toolName)
                .map(Tool::getDefinition);
    }
}
