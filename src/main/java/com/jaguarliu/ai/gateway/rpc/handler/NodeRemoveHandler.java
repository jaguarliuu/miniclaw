package com.jaguarliu.ai.gateway.rpc.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.nodeconsole.NodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * nodes.remove - 删除节点
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NodeRemoveHandler implements RpcHandler {

    private final NodeService nodeService;
    private final ObjectMapper objectMapper;

    @Override
    public String getMethod() {
        return "nodes.remove";
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

            nodeService.remove(id);
            return RpcResponse.success(request.getId(), Map.of("success", true));
        }).onErrorResume(e -> {
            log.error("Failed to remove node: {}", e.getMessage());
            return Mono.just(RpcResponse.error(request.getId(), "REMOVE_FAILED", e.getMessage()));
        });
    }
}
