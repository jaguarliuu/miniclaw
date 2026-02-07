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
 * nodes.update - 更新节点信息
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NodeUpdateHandler implements RpcHandler {

    private final NodeService nodeService;
    private final ObjectMapper objectMapper;

    @Override
    public String getMethod() {
        return "nodes.update";
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

            String alias = (String) params.get("alias");
            String displayName = (String) params.get("displayName");
            String connectorType = (String) params.get("connectorType");
            String host = (String) params.get("host");
            Integer port = params.get("port") != null ? ((Number) params.get("port")).intValue() : null;
            String username = (String) params.get("username");
            String authType = (String) params.get("authType");
            String credential = (String) params.get("credential");
            String tags = (String) params.get("tags");
            String safetyPolicy = (String) params.get("safetyPolicy");

            var node = nodeService.update(id, alias, displayName, connectorType, host, port,
                    username, authType, credential, tags, safetyPolicy);
            return RpcResponse.success(request.getId(), NodeService.toNodeDto(node));
        }).onErrorResume(e -> {
            log.error("Failed to update node: {}", e.getMessage());
            return Mono.just(RpcResponse.error(request.getId(), "UPDATE_FAILED", e.getMessage()));
        });
    }
}
