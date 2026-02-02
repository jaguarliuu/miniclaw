package com.jaguarliu.ai.gateway.rpc.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolRegistry;
import com.jaguarliu.ai.tools.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;

/**
 * tool.execute 处理器
 * 直接执行指定工具（测试用）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolExecuteHandler implements RpcHandler {

    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    @Override
    public String getMethod() {
        return "tool.execute";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        Map<String, Object> payload = extractPayload(request.getPayload());
        String toolName = (String) payload.get("name");

        if (toolName == null || toolName.isBlank()) {
            return Mono.just(RpcResponse.error(request.getId(), "INVALID_PARAMS", "Missing tool name"));
        }

        Optional<Tool> toolOpt = toolRegistry.get(toolName);
        if (toolOpt.isEmpty()) {
            return Mono.just(RpcResponse.error(request.getId(), "NOT_FOUND", "Tool not found: " + toolName));
        }

        Tool tool = toolOpt.get();

        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) payload.getOrDefault("arguments", Map.of());

        log.info("Executing tool: {} with arguments: {}", toolName, arguments);

        return tool.execute(arguments)
                .map(result -> RpcResponse.success(request.getId(), Map.of(
                        "tool", toolName,
                        "success", result.isSuccess(),
                        "content", result.getContent()
                )))
                .onErrorResume(e -> {
                    log.error("Tool execution failed: {}", toolName, e);
                    return Mono.just(RpcResponse.error(request.getId(), "EXECUTION_ERROR", e.getMessage()));
                });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractPayload(Object payload) {
        if (payload instanceof Map) {
            return (Map<String, Object>) payload;
        }
        return Map.of();
    }
}
