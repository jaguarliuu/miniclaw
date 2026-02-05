package com.jaguarliu.ai.gateway.rpc.handler;

import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.memory.index.MemoryIndexer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * MemoryRebuildHandler 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MemoryRebuildHandler Tests")
class MemoryRebuildHandlerTest {

    @Mock
    private MemoryIndexer indexer;

    @InjectMocks
    private MemoryRebuildHandler handler;

    @Test
    @DisplayName("getMethod 返回正确的方法名")
    void getMethodReturnsCorrectName() {
        assertEquals("memory.rebuild", handler.getMethod());
    }

    @Test
    @DisplayName("重建成功返回状态信息")
    void rebuildSuccessReturnsStatus() throws IOException {
        MemoryIndexer.IndexStatus status = new MemoryIndexer.IndexStatus(
                150, 120, true, "openai", "text-embedding-3-small"
        );
        when(indexer.getStatus()).thenReturn(status);

        RpcRequest request = RpcRequest.builder().id("req-1").method("memory.rebuild").build();
        RpcResponse response = handler.handle("conn-1", request).block();

        assertNotNull(response);
        assertNotNull(response.getPayload());
        assertNull(response.getError());
        verify(indexer).rebuild();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.getPayload();
        assertEquals("Global memory index rebuilt successfully", result.get("message"));
        assertEquals(150L, result.get("totalChunks"));
        assertEquals(120L, result.get("chunksWithEmbedding"));
    }

    @Test
    @DisplayName("重建失败时抛出异常")
    void rebuildFailureThrowsException() {
        doThrow(new RuntimeException("Disk full")).when(indexer).rebuild();

        RpcRequest request = RpcRequest.builder().id("req-1").method("memory.rebuild").build();

        assertThrows(Exception.class, () -> handler.handle("conn-1", request).block());
    }

    @Test
    @DisplayName("向量禁用时也能重建")
    void rebuildWorksWithoutVector() throws IOException {
        MemoryIndexer.IndexStatus status = new MemoryIndexer.IndexStatus(
                50, 0, false, "none", "none"
        );
        when(indexer.getStatus()).thenReturn(status);

        RpcRequest request = RpcRequest.builder().id("req-1").method("memory.rebuild").build();
        RpcResponse response = handler.handle("conn-1", request).block();

        assertNotNull(response);
        assertNotNull(response.getPayload());
        verify(indexer).rebuild();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.getPayload();
        assertEquals(0L, result.get("chunksWithEmbedding"));
    }
}
