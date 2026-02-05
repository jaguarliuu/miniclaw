package com.jaguarliu.ai.gateway.rpc.handler;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.memory.index.MemoryIndexer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * memory.rebuild - 从 Markdown 重建全部全局记忆索引
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryRebuildHandler implements RpcHandler {

    private final MemoryIndexer indexer;

    @Override
    public String getMethod() {
        return "memory.rebuild";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            log.info("Global memory index rebuild requested by connection: {}", connectionId);
            indexer.rebuild();
            MemoryIndexer.IndexStatus status = indexer.getStatus();
            return RpcResponse.success(request.getId(), Map.of(
                    "message", "Global memory index rebuilt successfully",
                    "totalChunks", status.totalChunks(),
                    "chunksWithEmbedding", status.chunksWithEmbedding()
            ));
        });
    }
}
