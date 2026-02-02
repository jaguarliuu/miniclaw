package com.jaguarliu.ai.gateway.rpc.handler;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * tool.list 处理器
 * 列出所有已注册的工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolListHandler implements RpcHandler {

    private final ToolRegistry toolRegistry;

    @Override
    public String getMethod() {
        return "tool.list";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        List<ToolDefinition> tools = toolRegistry.listDefinitions();

        List<Map<String, Object>> toolDtos = tools.stream()
                .map(t -> Map.<String, Object>of(
                        "name", t.getName(),
                        "description", t.getDescription(),
                        "hitl", t.isHitl()
                ))
                .toList();

        return Mono.just(RpcResponse.success(request.getId(), Map.of(
                "tools", toolDtos,
                "count", toolDtos.size()
        )));
    }
}
