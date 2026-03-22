package com.miniclaw.gateway.rpc;


import com.miniclaw.gateway.rpc.model.RpcErrorFrame;
import com.miniclaw.gateway.rpc.model.RpcRequestFrame;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 根据 method 把入站 RPC 请求分发给对应处理器。
 */
@Component
public class RpcRouter {

    private final Map<String, RpcHandler> handlersByMethod;

    public RpcRouter(List<RpcHandler> handlers) {
        this.handlersByMethod = handlers.stream()
                .flatMap(handler -> handler.supportedMethods().stream()
                        .map(method -> Map.entry(method, handler)))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Mono<Object> route(String connectionId, RpcRequestFrame request) {
        RpcHandler handler = handlersByMethod.get(request.getMethod());
        if (handler == null) {
            return Mono.just(RpcErrorFrame.of(
                    request.getRequestId(),
                    request.getSessionId(),
                    "METHOD_NOT_FOUND",
                    "Unknown method: " + request.getMethod()
            ));
        }

        return handler.handle(connectionId, request);
    }
}
