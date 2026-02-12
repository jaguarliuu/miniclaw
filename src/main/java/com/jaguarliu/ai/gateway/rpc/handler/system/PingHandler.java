package com.jaguarliu.ai.gateway.rpc.handler.system;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Ping 处理器（测试用）
 */
@Component
public class PingHandler implements RpcHandler {

    @Override
    public String getMethod() {
        return "ping";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.just(RpcResponse.success(request.getId(), Map.of("message", "pong")));
    }
}
