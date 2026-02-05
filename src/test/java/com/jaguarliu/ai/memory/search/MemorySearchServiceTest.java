package com.jaguarliu.ai.memory.search;

import com.jaguarliu.ai.memory.MemoryProperties;
import com.jaguarliu.ai.memory.embedding.EmbeddingModel;
import com.jaguarliu.ai.memory.index.MemoryChunkRepository;
import com.jaguarliu.ai.memory.index.MemoryIndexer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * MemorySearchService 单元测试
 *
 * 测试覆盖：
 * 1. search - 混合检索、降级、去重、排序
 * 2. searchByVectorOnly - 仅向量检索
 * 3. searchByFtsOnly - 仅 FTS 检索
 * 4. 边界值和异常场景
 */
@DisplayName("MemorySearchService Tests")
@ExtendWith(MockitoExtension.class)
class MemorySearchServiceTest {

    @Mock
    private MemoryChunkRepository chunkRepository;

    @Mock
    private MemoryIndexer indexer;

    @Spy
    private MemoryProperties properties = new MemoryProperties();

    @InjectMocks
    private MemorySearchService searchService;

    /**
     * 创建模拟的数据库行（解决 List.of(Object[]) 类型推断问题）
     */
    private static List<Object[]> rows(Object[]... rows) {
        return Arrays.asList(rows);
    }

    // ==================== search 测试 ====================

    @Nested
    @DisplayName("search - mixed retrieval")
    class SearchMixedTests {

        @Test
        @DisplayName("null 查询返回空列表")
        void nullQueryReturnsEmpty() {
            List<SearchResult> results = searchService.search(null);
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("空白查询返回空列表")
        void blankQueryReturnsEmpty() {
            List<SearchResult> results = searchService.search("   ");
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("无向量检索时仅使用 FTS")
        void ftsOnlyWhenNoVector() {
            when(indexer.isVectorSearchEnabled()).thenReturn(false);

            List<Object[]> ftsRows = rows(
                    new Object[]{"id1", "test.md", 1, 10, "content1", 0.8}
            );
            when(chunkRepository.searchByFts(anyString(), anyInt())).thenReturn(ftsRows);

            List<SearchResult> results = searchService.search("test query");

            assertEquals(1, results.size());
            assertEquals("fts", results.get(0).getSource());
            verify(chunkRepository, never()).searchByVector(anyString(), anyInt());
        }

        @Test
        @DisplayName("有向量检索时混合使用")
        void mixedWhenVectorEnabled() {
            when(indexer.isVectorSearchEnabled()).thenReturn(true);

            EmbeddingModel mockModel = mock(EmbeddingModel.class);
            when(mockModel.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f});
            when(indexer.getEmbeddingModel()).thenReturn(mockModel);

            List<Object[]> vectorRows = rows(
                    new Object[]{"id1", "test.md", 1, 10, "vector content", 0.9}
            );
            when(chunkRepository.searchByVector(anyString(), anyInt())).thenReturn(vectorRows);

            List<Object[]> ftsRows = rows(
                    new Object[]{"id2", "test.md", 20, 30, "fts content", 0.7}
            );
            when(chunkRepository.searchByFts(anyString(), anyInt())).thenReturn(ftsRows);

            List<SearchResult> results = searchService.search("test query");

            assertEquals(2, results.size());
            // 按分数降序排列
            assertEquals(0.9, results.get(0).getScore(), 0.01);
            assertEquals(0.7, results.get(1).getScore(), 0.01);
        }

        @Test
        @DisplayName("去重：相同位置的结果只保留一个")
        void deduplicatesSamePosition() {
            when(indexer.isVectorSearchEnabled()).thenReturn(true);

            EmbeddingModel mockModel = mock(EmbeddingModel.class);
            when(mockModel.embed(anyString())).thenReturn(new float[]{0.1f});
            when(indexer.getEmbeddingModel()).thenReturn(mockModel);

            // 向量和 FTS 返回相同位置的结果
            List<Object[]> vectorRows = rows(
                    new Object[]{"id1", "test.md", 1, 10, "content", 0.9}
            );
            when(chunkRepository.searchByVector(anyString(), anyInt())).thenReturn(vectorRows);

            List<Object[]> ftsRows = rows(
                    new Object[]{"id1", "test.md", 1, 10, "content", 0.7}  // 相同位置
            );
            when(chunkRepository.searchByFts(anyString(), anyInt())).thenReturn(ftsRows);

            List<SearchResult> results = searchService.search("test");

            assertEquals(1, results.size());
            // 保留向量结果（先添加的）
            assertEquals("vector", results.get(0).getSource());
        }

        @Test
        @DisplayName("过滤低于 minSimilarity 的向量结果")
        void filtersLowSimilarityResults() {
            when(indexer.isVectorSearchEnabled()).thenReturn(true);
            properties.getSearch().setMinSimilarity(0.5);

            EmbeddingModel mockModel = mock(EmbeddingModel.class);
            when(mockModel.embed(anyString())).thenReturn(new float[]{0.1f});
            when(indexer.getEmbeddingModel()).thenReturn(mockModel);

            List<Object[]> vectorRows = rows(
                    new Object[]{"id1", "test.md", 1, 10, "high", 0.8},
                    new Object[]{"id2", "test.md", 20, 30, "low", 0.3}  // 低于阈值
            );
            when(chunkRepository.searchByVector(anyString(), anyInt())).thenReturn(vectorRows);
            when(chunkRepository.searchByFts(anyString(), anyInt())).thenReturn(List.of());

            List<SearchResult> results = searchService.search("test");

            assertEquals(1, results.size());
            assertEquals(0.8, results.get(0).getScore(), 0.01);
        }

        @Test
        @DisplayName("限制返回数量为 finalTopK")
        void limitsByFinalTopK() {
            when(indexer.isVectorSearchEnabled()).thenReturn(false);
            properties.getSearch().setFinalTopK(2);

            List<Object[]> ftsRows = rows(
                    new Object[]{"id1", "a.md", 1, 10, "c1", 0.9},
                    new Object[]{"id2", "b.md", 1, 10, "c2", 0.8},
                    new Object[]{"id3", "c.md", 1, 10, "c3", 0.7},
                    new Object[]{"id4", "d.md", 1, 10, "c4", 0.6}
            );
            when(chunkRepository.searchByFts(anyString(), anyInt())).thenReturn(ftsRows);

            List<SearchResult> results = searchService.search("test");

            assertEquals(2, results.size());
        }

        @Test
        @DisplayName("向量检索失败时降级到 FTS")
        void fallsBackToFtsOnVectorFailure() {
            when(indexer.isVectorSearchEnabled()).thenReturn(true);

            EmbeddingModel mockModel = mock(EmbeddingModel.class);
            when(mockModel.embed(anyString())).thenThrow(new RuntimeException("API error"));
            when(indexer.getEmbeddingModel()).thenReturn(mockModel);

            List<Object[]> ftsRows = rows(
                    new Object[]{"id1", "test.md", 1, 10, "fts result", 0.7}
            );
            when(chunkRepository.searchByFts(anyString(), anyInt())).thenReturn(ftsRows);

            List<SearchResult> results = searchService.search("test");

            assertEquals(1, results.size());
            assertEquals("fts", results.get(0).getSource());
        }

        @Test
        @DisplayName("snippet 被截断到 maxChars")
        void truncatesSnippet() {
            when(indexer.isVectorSearchEnabled()).thenReturn(false);
            properties.getSearch().setSnippetMaxChars(10);

            String longContent = "This is a very long content that should be truncated";
            List<Object[]> ftsRows = rows(
                    new Object[]{"id1", "test.md", 1, 10, longContent, 0.7}
            );
            when(chunkRepository.searchByFts(anyString(), anyInt())).thenReturn(ftsRows);

            List<SearchResult> results = searchService.search("test");

            assertEquals(1, results.size());
            assertTrue(results.get(0).getSnippet().length() <= 13); // 10 + "..."
            assertTrue(results.get(0).getSnippet().endsWith("..."));
        }
    }

    // ==================== searchByVectorOnly 测试 ====================

    @Nested
    @DisplayName("searchByVectorOnly")
    class SearchByVectorOnlyTests {

        @Test
        @DisplayName("向量检索不可用时返回空")
        void returnsEmptyWhenDisabled() {
            when(indexer.isVectorSearchEnabled()).thenReturn(false);

            List<SearchResult> results = searchService.searchByVectorOnly("test", 10);

            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("正常返回向量检索结果")
        void returnsVectorResults() {
            when(indexer.isVectorSearchEnabled()).thenReturn(true);

            EmbeddingModel mockModel = mock(EmbeddingModel.class);
            when(mockModel.embed(anyString())).thenReturn(new float[]{0.1f});
            when(indexer.getEmbeddingModel()).thenReturn(mockModel);

            List<Object[]> rowList = rows(
                    new Object[]{"id1", "test.md", 1, 10, "content", 0.85}
            );
            when(chunkRepository.searchByVector(anyString(), eq(5))).thenReturn(rowList);

            List<SearchResult> results = searchService.searchByVectorOnly("test", 5);

            assertEquals(1, results.size());
            assertEquals("vector", results.get(0).getSource());
            assertEquals(0.85, results.get(0).getScore(), 0.01);
        }

        @Test
        @DisplayName("embedding 返回空向量时返回空结果")
        void returnsEmptyWhenEmbeddingFails() {
            when(indexer.isVectorSearchEnabled()).thenReturn(true);

            EmbeddingModel mockModel = mock(EmbeddingModel.class);
            when(mockModel.embed(anyString())).thenReturn(new float[0]);
            when(indexer.getEmbeddingModel()).thenReturn(mockModel);

            List<SearchResult> results = searchService.searchByVectorOnly("test", 10);

            assertTrue(results.isEmpty());
        }
    }

    // ==================== searchByFtsOnly 测试 ====================

    @Nested
    @DisplayName("searchByFtsOnly")
    class SearchByFtsOnlyTests {

        @Test
        @DisplayName("正常返回 FTS 结果")
        void returnsFtsResults() {
            List<Object[]> rowList = rows(
                    new Object[]{"id1", "test.md", 1, 10, "content", 0.75}
            );
            when(chunkRepository.searchByFts("keyword", 10)).thenReturn(rowList);

            List<SearchResult> results = searchService.searchByFtsOnly("keyword", 10);

            assertEquals(1, results.size());
            assertEquals("fts", results.get(0).getSource());
        }

        @Test
        @DisplayName("FTS rank 被归一化到 0~1")
        void normalizesRank() {
            List<Object[]> rowList = rows(
                    new Object[]{"id1", "a.md", 1, 10, "c", 1.5},  // > 1
                    new Object[]{"id2", "b.md", 1, 10, "c", -0.1}  // < 0
            );
            when(chunkRepository.searchByFts(anyString(), anyInt())).thenReturn(rowList);

            List<SearchResult> results = searchService.searchByFtsOnly("test", 10);

            assertEquals(2, results.size());
            assertEquals(1.0, results.get(0).getScore(), 0.01);  // 被限制到 1
            assertEquals(0.0, results.get(1).getScore(), 0.01);  // 被限制到 0
        }

        @Test
        @DisplayName("无结果时返回空列表")
        void returnsEmptyWhenNoResults() {
            when(chunkRepository.searchByFts(anyString(), anyInt())).thenReturn(List.of());

            List<SearchResult> results = searchService.searchByFtsOnly("nonexistent", 10);

            assertTrue(results.isEmpty());
        }
    }

    // ==================== SearchResult 测试 ====================

    @Nested
    @DisplayName("SearchResult")
    class SearchResultTests {

        @Test
        @DisplayName("Builder 创建对象")
        void builderCreatesObject() {
            SearchResult result = SearchResult.builder()
                    .filePath("test.md")
                    .lineStart(1)
                    .lineEnd(10)
                    .snippet("test content")
                    .score(0.85)
                    .source("vector")
                    .build();

            assertEquals("test.md", result.getFilePath());
            assertEquals(1, result.getLineStart());
            assertEquals(10, result.getLineEnd());
            assertEquals("test content", result.getSnippet());
            assertEquals(0.85, result.getScore(), 0.01);
            assertEquals("vector", result.getSource());
        }

        @Test
        @DisplayName("getDedupeKey 返回正确格式")
        void dedupeKeyFormat() {
            SearchResult result = SearchResult.builder()
                    .filePath("memory/2026-01-15.md")
                    .lineStart(42)
                    .build();

            assertEquals("memory/2026-01-15.md:42", result.getDedupeKey());
        }

        @Test
        @DisplayName("相同字段的结果相等")
        void equalResults() {
            SearchResult r1 = SearchResult.builder()
                    .filePath("test.md")
                    .lineStart(1)
                    .lineEnd(10)
                    .snippet("content")
                    .score(0.8)
                    .source("fts")
                    .build();

            SearchResult r2 = SearchResult.builder()
                    .filePath("test.md")
                    .lineStart(1)
                    .lineEnd(10)
                    .snippet("content")
                    .score(0.8)
                    .source("fts")
                    .build();

            assertEquals(r1, r2);
        }
    }

    // ==================== 边界值测试 ====================

    @Nested
    @DisplayName("Boundary values")
    class BoundaryValueTests {

        @Test
        @DisplayName("空字符串查询返回空")
        void emptyStringQuery() {
            List<SearchResult> results = searchService.search("");
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("FTS 失败时返回已有的向量结果")
        void vectorResultsWhenFtsFails() {
            when(indexer.isVectorSearchEnabled()).thenReturn(true);

            EmbeddingModel mockModel = mock(EmbeddingModel.class);
            when(mockModel.embed(anyString())).thenReturn(new float[]{0.1f});
            when(indexer.getEmbeddingModel()).thenReturn(mockModel);

            List<Object[]> vectorRows = rows(
                    new Object[]{"id1", "test.md", 1, 10, "content", 0.9}
            );
            when(chunkRepository.searchByVector(anyString(), anyInt())).thenReturn(vectorRows);
            when(chunkRepository.searchByFts(anyString(), anyInt()))
                    .thenThrow(new RuntimeException("FTS error"));

            List<SearchResult> results = searchService.search("test");

            assertEquals(1, results.size());
            assertEquals("vector", results.get(0).getSource());
        }

        @Test
        @DisplayName("两者都失败时返回空列表")
        void emptyWhenBothFail() {
            when(indexer.isVectorSearchEnabled()).thenReturn(true);

            EmbeddingModel mockModel = mock(EmbeddingModel.class);
            when(mockModel.embed(anyString())).thenThrow(new RuntimeException("Embed error"));
            when(indexer.getEmbeddingModel()).thenReturn(mockModel);

            when(chunkRepository.searchByFts(anyString(), anyInt()))
                    .thenThrow(new RuntimeException("FTS error"));

            List<SearchResult> results = searchService.search("test");

            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("snippet 为 null 时返回空字符串")
        void nullSnippetReturnsEmpty() {
            when(indexer.isVectorSearchEnabled()).thenReturn(false);

            List<Object[]> rowList = rows(
                    new Object[]{"id1", "test.md", 1, 10, null, 0.7}
            );
            when(chunkRepository.searchByFts(anyString(), anyInt())).thenReturn(rowList);

            List<SearchResult> results = searchService.search("test");

            assertEquals(1, results.size());
            assertEquals("", results.get(0).getSnippet());
        }
    }
}
