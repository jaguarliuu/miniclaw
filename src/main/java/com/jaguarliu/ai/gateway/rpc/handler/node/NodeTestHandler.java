package com.jaguarliu.ai.gateway.rpc.handler.node;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.nodeconsole.NodeService;
import com.jaguarliu.ai.nodeconsole.LogSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * nodes.test - 测试节点连接
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NodeTestHandler implements RpcHandler {

    private final NodeService nodeService;
    private final ObjectMapper objectMapper;

    @Override
    public String getMethod() {
        return "nodes.test";
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> params = objectMapper.convertValue(request.getPayload(), Map.class);

            String id = (String) params.get("id");
            if (id == null || id.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "id is required");
            }

            boolean success = nodeService.testConnection(id);
            return RpcResponse.success(request.getId(), Map.of("success", success));
        }).onErrorResume(e -> {
            log.error("Failed to test node: {}", e.getMessage());
            return Mono.just(RpcResponse.error(request.getId(), "TEST_FAILED", "Connection test failed"));
        });
    }
}
