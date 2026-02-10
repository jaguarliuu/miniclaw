package com.jaguarliu.ai.gateway.rpc.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.nodeconsole.LogSanitizer;
import com.jaguarliu.ai.nodeconsole.NodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * nodes.register - 注册新节点
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NodeRegisterHandler implements RpcHandler {

    private final NodeService nodeService;
    private final ObjectMapper objectMapper;

    @Override
    public String getMethod() {
        return "nodes.register";
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> params = objectMapper.convertValue(request.getPayload(), Map.class);

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

            if (alias == null || alias.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "alias is required");
            }
            if (connectorType == null || connectorType.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "connectorType is required");
            }
            if (credential == null || credential.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "credential is required");
            }

            var node = nodeService.register(alias, displayName, connectorType, host, port,
                    username, authType, credential, tags, safetyPolicy);
            return RpcResponse.success(request.getId(), NodeService.toNodeDto(node));
        }).onErrorResume(e -> {
            // 日志脱敏：仅记录异常类名
            log.error("Failed to register node: {}", LogSanitizer.sanitizeException(e));

            // 对客户端返回通用错误，不暴露内部细节
            String errorMessage = e instanceof IllegalArgumentException
                ? e.getMessage()  // 参数错误可以返回（如 "alias already exists"）
                : "Node registration failed";  // 其他错误返回通用消息

            return Mono.just(RpcResponse.error(request.getId(), "REGISTER_FAILED", errorMessage));
        });
    }
}
