package com.jaguarliu.ai.tools.builtin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.nodeconsole.NodeService;
import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * node_list 工具
 * 列出所有已注册的远程节点（无凭据）
 */
@Component
@RequiredArgsConstructor
public class NodeListTool implements Tool {

    private final NodeService nodeService;
    private final ObjectMapper objectMapper;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("node_list")
                .description("列出所有已注册的远程节点。可按类型（ssh/k8s/db）或标签过滤。返回节点别名、类型、主机、标签、安全策略和连接状态，不包含凭据信息。")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "type", Map.of(
                                        "type", "string",
                                        "description", "按连接器类型过滤: ssh, k8s, db",
                                        "enum", List.of("ssh", "k8s", "db")
                                ),
                                "tag", Map.of(
                                        "type", "string",
                                        "description", "按标签过滤（模糊匹配）"
                                )
                        ),
                        "required", List.of()
                ))
                .hitl(false)
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String type = (String) arguments.get("type");
            String tag = (String) arguments.get("tag");

            var nodes = nodeService.listForLlm(type, tag);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(nodes);
            return ToolResult.success(json);
        });
    }
}
