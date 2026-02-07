package com.jaguarliu.ai.tools.builtin;

import com.jaguarliu.ai.nodeconsole.NodeService;
import com.jaguarliu.ai.nodeconsole.RemoteCommandClassifier;
import com.jaguarliu.ai.nodeconsole.RemoteCommandClassifier.SafetyLevel;
import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * kubectl_exec 工具
 * 在 Kubernetes 集群上执行 kubectl 子命令
 */
@Component
@RequiredArgsConstructor
public class KubectlExecTool implements Tool {

    private final NodeService nodeService;
    private final RemoteCommandClassifier classifier;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("kubectl_exec")
                .description("在 Kubernetes 集群上执行 kubectl 子命令（如 get pods, describe deployment 等）。需要指定 K8s 类型节点的别名。使用 node_list(type='k8s') 查看可用的 K8s 节点。")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "alias", Map.of(
                                        "type", "string",
                                        "description", "K8s 节点别名"
                                ),
                                "command", Map.of(
                                        "type", "string",
                                        "description", "kubectl 子命令（如 'get pods -n default'）"
                                )
                        ),
                        "required", List.of("alias", "command")
                ))
                .hitl(false) // 动态由 ToolDispatcher 判断
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String alias = (String) arguments.get("alias");
            String command = (String) arguments.get("command");

            if (alias == null || alias.isBlank()) {
                return ToolResult.error("alias is required");
            }
            if (command == null || command.isBlank()) {
                return ToolResult.error("command is required");
            }

            // 安全检查：Level 2 直接拒绝
            String policy = nodeService.getSafetyPolicy(alias);
            // 添加 kubectl 前缀用于分类
            String fullCommand = "kubectl " + command;
            var classification = classifier.classify(fullCommand, policy);
            if (classification.level() == SafetyLevel.DESTRUCTIVE) {
                return ToolResult.error("Command rejected (destructive): " + classification.reason());
            }

            String output = nodeService.executeCommand(alias, command);
            return ToolResult.success(output);
        });
    }
}
