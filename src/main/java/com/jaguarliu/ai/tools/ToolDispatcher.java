package com.jaguarliu.ai.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import com.jaguarliu.ai.nodeconsole.RemoteCommandClassifier;
import com.jaguarliu.ai.nodeconsole.NodeService;

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
    private final DangerousCommandDetector dangerousCommandDetector;
    private final RemoteCommandClassifier remoteCommandClassifier;
    private final Optional<NodeService> nodeService;

    /**
     * 需要进行危险命令检测的工具
     */
    private static final Set<String> COMMAND_TOOLS = Set.of("shell", "shell_start");

    /**
     * 需要进行远程命令安全分类的工具
     */
    private static final Set<String> REMOTE_COMMAND_TOOLS = Set.of("remote_exec", "kubectl_exec");

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
        return requiresHitl(toolName, null);
    }

    /**
     * 检查工具是否需要 HITL 确认（支持 skill 覆盖）
     *
     * Skill 的 confirm-before 配置可以覆盖工具默认的 HITL 设置：
     * - confirmBefore 包含该工具 → 强制需要确认
     * - confirmBefore 为空/null → 使用工具默认配置
     *
     * @param toolName      工具名称
     * @param confirmBefore skill 配置的需要确认的工具列表
     * @return 是否需要确认
     */
    public boolean requiresHitl(String toolName, Set<String> confirmBefore) {
        return requiresHitl(toolName, confirmBefore, null);
    }

    /**
     * 检查工具是否需要 HITL 确认（支持 skill 覆盖和参数检查）
     *
     * 检查顺序：
     * 1. Skill 的 confirmBefore 配置（最高优先级）
     * 2. 对于 shell/shell_start 工具，检查命令是否包含危险模式
     * 3. 工具默认的 hitl 配置
     *
     * @param toolName      工具名称
     * @param confirmBefore skill 配置的需要确认的工具列表
     * @param arguments     工具参数（用于检查命令内容）
     * @return 是否需要确认
     */
    public boolean requiresHitl(String toolName, Set<String> confirmBefore, Map<String, Object> arguments) {
        // 1. Skill 覆盖优先
        if (confirmBefore != null && confirmBefore.contains(toolName)) {
            log.debug("Tool {} requires HITL (skill override)", toolName);
            return true;
        }

        // 2. 对于命令执行工具，检查命令内容是否危险
        if (COMMAND_TOOLS.contains(toolName) && arguments != null) {
            String command = (String) arguments.get("command");
            if (command != null && dangerousCommandDetector.isDangerous(command)) {
                String reason = dangerousCommandDetector.getDangerReason(command);
                log.info("Tool {} requires HITL (dangerous command: {})", toolName, reason);
                return true;
            }
        }

        // 3. 对于远程命令工具，使用 RemoteCommandClassifier 判断
        if (REMOTE_COMMAND_TOOLS.contains(toolName) && arguments != null) {
            String command = (String) arguments.get("command");
            String alias = (String) arguments.get("alias");
            if (command != null) {
                String fullCommand = "kubectl_exec".equals(toolName) ? "kubectl " + command : command;
                String policy = nodeService.map(ns -> alias != null ? ns.getSafetyPolicy(alias) : "strict")
                        .orElse("strict");
                var cls = remoteCommandClassifier.classify(fullCommand, policy);
                if (cls.level() == RemoteCommandClassifier.SafetyLevel.SIDE_EFFECT) {
                    log.info("Tool {} requires HITL (remote command classified as SIDE_EFFECT: {})", toolName, cls.reason());
                    return true;
                }
            }
        }

        // 4. 使用工具默认配置
        return toolRegistry.get(toolName)
                .map(tool -> tool.getDefinition().isHitl())
                .orElse(false);
    }

    /**
     * 检查工具是否被允许执行
     *
     * @param toolName     工具名称
     * @param allowedTools 允许的工具白名单，null 表示允许所有
     * @return 是否允许
     */
    public boolean isToolAllowed(String toolName, Set<String> allowedTools) {
        if (allowedTools == null || allowedTools.isEmpty()) {
            return true;
        }
        return allowedTools.contains(toolName);
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
