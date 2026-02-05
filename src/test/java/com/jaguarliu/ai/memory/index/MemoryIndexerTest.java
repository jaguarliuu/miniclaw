package com.jaguarliu.ai.memory.index;

import com.jaguarliu.ai.memory.MemoryProperties;
import com.jaguarliu.ai.memory.embedding.EmbeddingModel;
import com.jaguarliu.ai.memory.embedding.EmbeddingModelFactory;
import com.jaguarliu.ai.memory.embedding.NoOpEmbeddingModel;
import com.jaguarliu.ai.memory.store.MemoryStore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * MemoryIndexer 单元测试
 *
 * 测试覆盖：
 * 1. 初始化 - 有/无 embedding provider
 * 2. indexFile - 正常流程、空文件、异常处理
 * 3. rebuild - 全量重建
 * 4. removeFile - 删除索引
 * 5. getStatus - 状态查询
 * 6. 边界值和异常场景
 */
@DisplayName("MemoryIndexer Tests")
@ExtendWith(MockitoExtension.class)
class MemoryIndexerTest {

    @Mock
    private MemoryStore memoryStore;

    @Mock
    private MemoryChunker chunker;

    @Mock
    private MemoryChunkRepository chunkRepository;

    @Mock
    private EmbeddingModelFactory embeddingFactory;

    @Spy
    private MemoryProperties properties = new MemoryProperties();

    @InjectMocks
    private MemoryIndexer indexer;

    // ==================== 初始化测试 ====================

    @Nested
    @DisplayName("Initialization")
    class InitializationTests {

        @Test
        @DisplayName("有 embedding provider 时启用向量检索")
        void initWithEmbeddingProvider() {
            EmbeddingModel mockModel = mock(EmbeddingModel.class);
            when(mockModel.providerType()).thenReturn("openai-compatible");
            when(mockModel.modelName()).thenReturn("text-embedding-3-small");
            when(embeddingFactory.create()).thenReturn(mockModel);

            indexer.init();

            assertTrue(indexer.isVectorSearchEnabled());
            assertEquals(mockModel, indexer.getEmbeddingModel());
        }

        @Test
        @DisplayName("无 embedding provider 时禁用向量检索")
        void initWithoutEmbeddingProvider() {
            when(embeddingFactory.create()).thenReturn(NoOpEmbeddingModel.INSTANCE);

            indexer.init();

            assertFalse(indexer.isVectorSearchEnabled());
            assertInstanceOf(NoOpEmbeddingModel.class, indexer.getEmbeddingModel());
        }
    }

    // ==================== indexFile 测试 ====================

    @Nested
    @DisplayName("indexFile")
    class IndexFileTests {

        @BeforeEach
        void setUp() {
            when(embeddingFactory.create()).thenReturn(NoOpEmbeddingModel.INSTANCE);
            indexer.init();
        }

        @Test
        @DisplayName("正常索引文件")
        void indexFileNormally() throws IOException {
            String content = "Line1\nLine2\nLine3";
            when(memoryStore.read("MEMORY.md")).thenReturn(content);

            List<MemoryChunk> chunks = List.of(
                    MemoryChunk.builder()
                            .filePath("MEMORY.md")
                            .lineStart(1)
                            .lineEnd(3)
                            .content(content)
                            .build()
            );
            when(chunker.chunk("MEMORY.md", content)).thenReturn(chunks);

            indexer.indexFile("MEMORY.md");

            verify(chunkRepository).deleteByFilePath("MEMORY.md");
            verify(chunkRepository).saveAll(anyList());
        }

        @Test
        @DisplayName("空文件不生成 chunks")
        void emptyFileNoChunks() throws IOException {
            when(memoryStore.read("empty.md")).thenReturn("");
            when(chunker.chunk("empty.md", "")).thenReturn(List.of());

            indexer.indexFile("empty.md");

            verify(chunkRepository).deleteByFilePath("empty.md");
            verify(chunkRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("文件读取失败时不抛异常")
        void fileReadFailureHandled() throws IOException {
            when(memoryStore.read("missing.md")).thenThrow(new IOException("File not found"));

            // 不应抛出异常
            assertDoesNotThrow(() -> indexer.indexFile("missing.md"));
        }

        @Test
        @DisplayName("多个 chunks 正确保存")
        void multipleChunksSaved() throws IOException {
            when(memoryStore.read("large.md")).thenReturn("content");

            List<MemoryChunk> chunks = List.of(
                    MemoryChunk.builder().filePath("large.md").lineStart(1).lineEnd(10).content("c1").build(),
                    MemoryChunk.builder().filePath("large.md").lineStart(8).lineEnd(20).content("c2").build(),
                    MemoryChunk.builder().filePath("large.md").lineStart(18).lineEnd(30).content("c3").build()
            );
            when(chunker.chunk("large.md", "content")).thenReturn(chunks);

            indexer.indexFile("large.md");

            ArgumentCaptor<List<MemoryChunkEntity>> captor = ArgumentCaptor.forClass(List.class);
            verify(chunkRepository).saveAll(captor.capture());
            assertEquals(3, captor.getValue().size());
        }
    }

    // ==================== indexFile with embedding 测试 ====================

    @Nested
    @DisplayName("indexFile with embedding")
    class IndexFileWithEmbeddingTests {

        @Mock
        private EmbeddingModel embeddingModel;

        @BeforeEach
        void setUp() {
            when(embeddingModel.providerType()).thenReturn("openai-compatible");
            when(embeddingModel.modelName()).thenReturn("test-model");
            when(embeddingFactory.create()).thenReturn(embeddingModel);
            indexer.init();
        }

        @Test
        @DisplayName("有 embedding provider 时生成向量")
        void generatesEmbeddingsWhenProviderAvailable() throws IOException {
            when(memoryStore.read("test.md")).thenReturn("content");

            List<MemoryChunk> chunks = List.of(
                    MemoryChunk.builder().filePath("test.md").lineStart(1).lineEnd(1).content("content").build()
            );
            when(chunker.chunk("test.md", "content")).thenReturn(chunks);
            when(embeddingModel.embed(anyList())).thenReturn(List.of(new float[]{0.1f, 0.2f}));

            indexer.indexFile("test.md");

            verify(embeddingModel).embed(anyList());
            verify(chunkRepository).updateEmbedding(anyString(), anyString());
        }

        @Test
        @DisplayName("embedding 失败不影响基础功能")
        void embeddingFailureDoesNotAffectBasicFunction() throws IOException {
            when(memoryStore.read("test.md")).thenReturn("content");

            List<MemoryChunk> chunks = List.of(
                    MemoryChunk.builder().filePath("test.md").lineStart(1).lineEnd(1).content("content").build()
            );
            when(chunker.chunk("test.md", "content")).thenReturn(chunks);
            when(embeddingModel.embed(anyList())).thenThrow(new RuntimeException("API error"));

            // 不应抛出异常
            assertDoesNotThrow(() -> indexer.indexFile("test.md"));

            // chunks 仍然被保存
            verify(chunkRepository).saveAll(anyList());
        }
    }

    // ==================== rebuild 测试 ====================

    @Nested
    @DisplayName("rebuild")
    class RebuildTests {

        @BeforeEach
        void setUp() {
            when(embeddingFactory.create()).thenReturn(NoOpEmbeddingModel.INSTANCE);
            indexer.init();
        }

        @Test
        @DisplayName("重建时先清空所有 chunks")
        void rebuildClearsAllChunks() throws IOException {
            when(memoryStore.listFiles()).thenReturn(List.of());

            indexer.rebuild();

            verify(chunkRepository).deleteAllChunks();
        }

        @Test
        @DisplayName("重建时索引所有文件")
        void rebuildIndexesAllFiles() throws IOException {
            List<MemoryStore.MemoryFileInfo> files = List.of(
                    new MemoryStore.MemoryFileInfo("MEMORY.md", 100, 0),
                    new MemoryStore.MemoryFileInfo("2026-01-15.md", 200, 0)
            );
            when(memoryStore.listFiles()).thenReturn(files);
            when(memoryStore.read(anyString())).thenReturn("content");
            when(chunker.chunk(anyString(), anyString())).thenReturn(List.of());

            indexer.rebuild();

            verify(memoryStore).read("MEMORY.md");
            verify(memoryStore).read("2026-01-15.md");
        }

        @Test
        @DisplayName("listFiles 失败时不抛异常")
        void listFilesFailureHandled() throws IOException {
            when(memoryStore.listFiles()).thenThrow(new IOException("Access denied"));

            assertDoesNotThrow(() -> indexer.rebuild());
        }
    }

    // ==================== removeFile 测试 ====================

    @Nested
    @DisplayName("removeFile")
    class RemoveFileTests {

        @BeforeEach
        void setUp() {
            when(embeddingFactory.create()).thenReturn(NoOpEmbeddingModel.INSTANCE);
            indexer.init();
        }

        @Test
        @DisplayName("删除指定文件的索引")
        void removesFileIndex() {
            indexer.removeFile("old.md");

            verify(chunkRepository).deleteByFilePath("old.md");
        }
    }

    // ==================== getStatus 测试 ====================

    @Nested
    @DisplayName("getStatus")
    class GetStatusTests {

        @Test
        @DisplayName("无 embedding 时返回正确状态")
        void statusWithoutEmbedding() {
            when(embeddingFactory.create()).thenReturn(NoOpEmbeddingModel.INSTANCE);
            indexer.init();

            when(chunkRepository.countTotal()).thenReturn(100L);

            MemoryIndexer.IndexStatus status = indexer.getStatus();

            assertEquals(100L, status.totalChunks());
            assertEquals(0L, status.chunksWithEmbedding());
            assertFalse(status.vectorSearchEnabled());
            assertEquals("none", status.embeddingProvider());
        }

        @Test
        @DisplayName("有 embedding 时返回正确状态")
        void statusWithEmbedding() {
            EmbeddingModel mockModel = mock(EmbeddingModel.class);
            when(mockModel.providerType()).thenReturn("openai-compatible");
            when(mockModel.modelName()).thenReturn("text-embedding-3-small");
            when(embeddingFactory.create()).thenReturn(mockModel);
            indexer.init();

            when(chunkRepository.countTotal()).thenReturn(100L);
            when(chunkRepository.countWithEmbedding()).thenReturn(80L);

            MemoryIndexer.IndexStatus status = indexer.getStatus();

            assertEquals(100L, status.totalChunks());
            assertEquals(80L, status.chunksWithEmbedding());
            assertTrue(status.vectorSearchEnabled());
            assertEquals("openai-compatible", status.embeddingProvider());
            assertEquals("text-embedding-3-small", status.embeddingModel());
        }
    }

    // ==================== indexPendingEmbeddings 测试 ====================

    @Nested
    @DisplayName("indexPendingEmbeddings")
    class IndexPendingEmbeddingsTests {

        @Test
        @DisplayName("无 embedding provider 时返回 0")
        void returnsZeroWhenNoProvider() {
            when(embeddingFactory.create()).thenReturn(NoOpEmbeddingModel.INSTANCE);
            indexer.init();

            int result = indexer.indexPendingEmbeddings(10);

            assertEquals(0, result);
            verify(chunkRepository, never()).findChunksWithoutEmbedding(anyInt());
        }

        @Test
        @DisplayName("无待处理 chunks 时返回 0")
        void returnsZeroWhenNoPending() {
            EmbeddingModel mockModel = mock(EmbeddingModel.class);
            when(mockModel.providerType()).thenReturn("test");
            when(mockModel.modelName()).thenReturn("test");
            when(embeddingFactory.create()).thenReturn(mockModel);
            indexer.init();

            when(chunkRepository.findChunksWithoutEmbedding(10)).thenReturn(List.of());

            int result = indexer.indexPendingEmbeddings(10);

            assertEquals(0, result);
        }

        @Test
        @DisplayName("处理待处理 chunks 并返回数量")
        void processesPendingChunks() {
            EmbeddingModel mockModel = mock(EmbeddingModel.class);
            when(mockModel.providerType()).thenReturn("test");
            when(mockModel.modelName()).thenReturn("test");
            when(mockModel.embed(anyList())).thenReturn(List.of(
                    new float[]{0.1f},
                    new float[]{0.2f}
            ));
            when(embeddingFactory.create()).thenReturn(mockModel);
            indexer.init();

            List<Object[]> pendingRows = List.of(
                    new Object[]{"id1", "path", 1, 10, "content1"},
                    new Object[]{"id2", "path", 11, 20, "content2"}
            );
            when(chunkRepository.findChunksWithoutEmbedding(10)).thenReturn(pendingRows);

            int result = indexer.indexPendingEmbeddings(10);

            assertEquals(2, result);
            verify(chunkRepository, times(2)).updateEmbedding(anyString(), anyString());
        }
    }

    // ==================== IndexStatus record 测试 ====================

    @Nested
    @DisplayName("IndexStatus record")
    class IndexStatusTests {

        @Test
        @DisplayName("record 字段访问正常")
        void recordFieldsAccessible() {
            MemoryIndexer.IndexStatus status = new MemoryIndexer.IndexStatus(
                    100L, 80L, true, "openai", "text-embedding-3-small"
            );

            assertEquals(100L, status.totalChunks());
            assertEquals(80L, status.chunksWithEmbedding());
            assertTrue(status.vectorSearchEnabled());
            assertEquals("openai", status.embeddingProvider());
            assertEquals("text-embedding-3-small", status.embeddingModel());
        }

        @Test
        @DisplayName("相同值的 record 相等")
        void equalRecords() {
            MemoryIndexer.IndexStatus s1 = new MemoryIndexer.IndexStatus(100L, 80L, true, "openai", "model");
            MemoryIndexer.IndexStatus s2 = new MemoryIndexer.IndexStatus(100L, 80L, true, "openai", "model");

            assertEquals(s1, s2);
            assertEquals(s1.hashCode(), s2.hashCode());
        }
    }
}
