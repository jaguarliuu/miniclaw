package com.jaguarliu.ai.gateway.rpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RPC 路由器
 * 根据 method 字段分发请求到对应的 handler
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RpcRouter {

    private final ObjectMapper objectMapper;
    private final List<RpcHandler> handlers;
    private final Map<String, RpcHandler> handlerMap = new HashMap<>();

    @PostConstruct
    public void init() {
        for (RpcHandler handler : handlers) {
            handlerMap.put(handler.getMethod(), handler);
            log.info("Registered RPC handler: {}", handler.getMethod());
        }
    }

    /**
     * 解析并路由请求
     * @param connectionId 连接 ID
     * @param message 原始 JSON 消息
     * @return 响应 JSON
     */
    public Mono<String> route(String connectionId, String message) {
        try {
            RpcRequest request = objectMapper.readValue(message, RpcRequest.class);

            if (!"request".equals(request.getType())) {
                return Mono.just(toJson(RpcResponse.error(
                        request.getId(), "INVALID_TYPE", "Expected type 'request'")));
            }

            String method = request.getMethod();
            RpcHandler handler = handlerMap.get(method);

            if (handler == null) {
                log.warn("Unknown method: {}", method);
                return Mono.just(toJson(RpcResponse.error(
                        request.getId(), "METHOD_NOT_FOUND", "Unknown method: " + method)));
            }

            log.debug("Routing request: method={}, id={}", method, request.getId());
            return handler.handle(connectionId, request)
                    .map(this::toJson)
                    .onErrorResume(e -> {
                        log.error("Handler error: method={}", method, e);
                        return Mono.just(toJson(RpcResponse.error(
                                request.getId(), "INTERNAL_ERROR", e.getMessage())));
                    });

        } catch (JsonProcessingException e) {
            log.error("Failed to parse request: {}", message, e);
            return Mono.just(toJson(RpcResponse.error(null, "PARSE_ERROR", "Invalid JSON")));
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize response", e);
            return "{\"type\":\"response\",\"error\":{\"code\":\"SERIALIZE_ERROR\",\"message\":\"Failed to serialize response\"}}";
        }
    }
}
