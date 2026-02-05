package com.jaguarliu.ai.gateway.rpc.handler;

import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.memory.index.MemoryIndexer;
import com.jaguarliu.ai.memory.store.MemoryStore;
import com.jaguarliu.ai.memory.store.MemoryStore.MemoryFileInfo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * MemoryStatusHandler 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MemoryStatusHandler Tests")
class MemoryStatusHandlerTest {

    @Mock
    private MemoryIndexer indexer;

    @Mock
    private MemoryStore memoryStore;

    @InjectMocks
    private MemoryStatusHandler handler;

    @Test
    @DisplayName("getMethod 返回正确的方法名")
    void getMethodReturnsCorrectName() {
        assertEquals("memory.status", handler.getMethod());
    }

    @Test
    @DisplayName("返回完整的状态信息")
    void returnsCompleteStatus() throws IOException {
        MemoryIndexer.IndexStatus status = new MemoryIndexer.IndexStatus(
                100, 80, true, "openai", "text-embedding-3-small"
        );
        when(indexer.getStatus()).thenReturn(status);
        when(memoryStore.listFiles()).thenReturn(List.of(
                new MemoryFileInfo("MEMORY.md", 1024, System.currentTimeMillis()),
                new MemoryFileInfo("2026-01-15.md", 512, System.currentTimeMillis())
        ));

        RpcRequest request = RpcRequest.builder().id("req-1").method("memory.status").build();
        RpcResponse response = handler.handle("conn-1", request).block();

        assertNotNull(response);
        assertNotNull(response.getPayload());
        assertNull(response.getError());

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.getPayload();
        assertEquals(100L, result.get("totalChunks"));
        assertEquals(80L, result.get("chunksWithEmbedding"));
        assertEquals(true, result.get("vectorSearchEnabled"));
        assertEquals("openai", result.get("embeddingProvider"));
        assertEquals("text-embedding-3-small", result.get("embeddingModel"));
        assertEquals(2, result.get("memoryFileCount"));
        assertEquals("Memory is global and cross-session", result.get("note"));
    }

    @Test
    @DisplayName("向量检索禁用时正确返回")
    void returnsStatusWithVectorDisabled() throws IOException {
        MemoryIndexer.IndexStatus status = new MemoryIndexer.IndexStatus(
                50, 0, false, "none", "none"
        );
        when(indexer.getStatus()).thenReturn(status);
        when(memoryStore.listFiles()).thenReturn(List.of());

        RpcRequest request = RpcRequest.builder().id("req-1").method("memory.status").build();
        RpcResponse response = handler.handle("conn-1", request).block();

        assertNotNull(response);
        assertNotNull(response.getPayload());

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.getPayload();
        assertEquals(false, result.get("vectorSearchEnabled"));
        assertEquals(0L, result.get("chunksWithEmbedding"));
        assertEquals(0, result.get("memoryFileCount"));
    }

    @Test
    @DisplayName("listFiles 失败时 fileCount 为 0")
    void handlesListFilesFailure() throws IOException {
        MemoryIndexer.IndexStatus status = new MemoryIndexer.IndexStatus(
                10, 5, true, "openai", "model"
        );
        when(indexer.getStatus()).thenReturn(status);
        when(memoryStore.listFiles()).thenThrow(new IOException("Permission denied"));

        RpcRequest request = RpcRequest.builder().id("req-1").method("memory.status").build();
        RpcResponse response = handler.handle("conn-1", request).block();

        assertNotNull(response);
        assertNotNull(response.getPayload());

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.getPayload();
        assertEquals(0, result.get("memoryFileCount"));
    }
}
