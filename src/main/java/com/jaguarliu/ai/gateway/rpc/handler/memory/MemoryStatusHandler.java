package com.jaguarliu.ai.gateway.rpc.handler.memory;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.memory.index.MemoryIndexer;
import com.jaguarliu.ai.memory.store.MemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Map;

/**
 * memory.status - 查询全局记忆系统状态
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryStatusHandler implements RpcHandler {

    private final MemoryIndexer indexer;
    private final MemoryStore memoryStore;

    @Override
    public String getMethod() {
        return "memory.status";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        MemoryIndexer.IndexStatus status = indexer.getStatus();

        int fileCount = 0;
        try {
            fileCount = memoryStore.listFiles().size();
        } catch (IOException e) {
            log.warn("Failed to count memory files: {}", e.getMessage());
        }

        return Mono.just(RpcResponse.success(request.getId(), Map.of(
                "totalChunks", status.totalChunks(),
                "chunksWithEmbedding", status.chunksWithEmbedding(),
                "vectorSearchEnabled", status.vectorSearchEnabled(),
                "embeddingProvider", status.embeddingProvider(),
                "embeddingModel", status.embeddingModel(),
                "memoryFileCount", fileCount,
                "note", "Memory is global and cross-session"
        )));
    }
}
