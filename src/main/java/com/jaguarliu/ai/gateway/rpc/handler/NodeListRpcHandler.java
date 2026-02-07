package com.jaguarliu.ai.gateway.rpc.handler;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.nodeconsole.NodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * nodes.list - 列出所有节点（不含凭据）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NodeListRpcHandler implements RpcHandler {

    private final NodeService nodeService;

    @Override
    public String getMethod() {
        return "nodes.list";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            var nodes = nodeService.listAll().stream()
                    .map(NodeService::toNodeDto)
                    .toList();
            return RpcResponse.success(request.getId(), nodes);
        });
    }
}
