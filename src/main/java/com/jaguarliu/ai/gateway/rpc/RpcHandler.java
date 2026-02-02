package com.jaguarliu.ai.gateway.rpc;

import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import reactor.core.publisher.Mono;

/**
 * RPC 方法处理器接口
 */
public interface RpcHandler {

    /**
     * 获取处理的方法名
     */
    String getMethod();

    /**
     * 处理请求
     * @param connectionId 连接 ID
     * @param request 请求
     * @return 响应
     */
    Mono<RpcResponse> handle(String connectionId, RpcRequest request);
}
