package com.jaguarliu.ai.memory.index;

import com.jaguarliu.ai.memory.MemoryProperties;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MemoryChunker 单元测试
 *
 * 测试覆盖：
 * 1. chunk 方法 - 正常分块、重叠、边界情况
 * 2. estimateTokens - 中英文、特殊字符、边界值
 * 3. 防止无限循环
 */
@DisplayName("MemoryChunker Tests")
class MemoryChunkerTest {

    private MemoryChunker chunker;
    private MemoryProperties properties;

    @BeforeEach
    void setUp() {
        properties = new MemoryProperties();
        // 使用较小的值便于测试
        properties.getChunk().setTargetTokens(50);
        properties.getChunk().setOverlapTokens(10);
        chunker = new MemoryChunker(properties);
    }

    // ==================== chunk 方法测试 ====================

    @Nested
    @DisplayName("chunk method")
    class ChunkMethodTests {

        @Test
        @DisplayName("null 内容返回空列表")
        void nullContentReturnsEmpty() {
            List<MemoryChunk> chunks = chunker.chunk("test.md", null);
            assertTrue(chunks.isEmpty());
        }

        @Test
        @DisplayName("空字符串返回空列表")
        void emptyContentReturnsEmpty() {
            List<MemoryChunk> chunks = chunker.chunk("test.md", "");
            assertTrue(chunks.isEmpty());
        }

        @Test
        @DisplayName("空白内容返回空列表")
        void blankContentReturnsEmpty() {
            List<MemoryChunk> chunks = chunker.chunk("test.md", "   \n  \n  ");
            assertTrue(chunks.isEmpty());
        }

        @Test
        @DisplayName("单行内容生成单个 chunk")
        void singleLineContent() {
            List<MemoryChunk> chunks = chunker.chunk("test.md", "Hello world");
            assertEquals(1, chunks.size());
            assertEquals("test.md", chunks.get(0).getFilePath());
            assertEquals(1, chunks.get(0).getLineStart());
            assertEquals(1, chunks.get(0).getLineEnd());
            assertEquals("Hello world", chunks.get(0).getContent());
        }

        @Test
        @DisplayName("多行内容在 target 内生成单个 chunk")
        void multipleLinesWithinTarget() {
            String content = "Line1\nLine2\nLine3";
            List<MemoryChunk> chunks = chunker.chunk("test.md", content);
            assertEquals(1, chunks.size());
            assertEquals(1, chunks.get(0).getLineStart());
            assertEquals(3, chunks.get(0).getLineEnd());
        }

        @Test
        @DisplayName("超出 target 生成多个 chunks")
        void exceedTargetGeneratesMultipleChunks() {
            // 每行约 30 tokens (100 字符 * 0.3)，target=50，需要约2行一个chunk
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10; i++) {
                sb.append("x".repeat(100)).append("\n");
            }
            List<MemoryChunk> chunks = chunker.chunk("test.md", sb.toString());
            assertTrue(chunks.size() > 1);
        }

        @Test
        @DisplayName("chunks 有重叠")
        void chunksHaveOverlap() {
            // 设置较小的值来测试重叠
            properties.getChunk().setTargetTokens(20);
            properties.getChunk().setOverlapTokens(5);

            String content = "Line1 text here\nLine2 text here\nLine3 text here\nLine4 text here\nLine5 text here";
            List<MemoryChunk> chunks = chunker.chunk("test.md", content);

            if (chunks.size() >= 2) {
                // 检查第二个 chunk 的 lineStart 小于第一个 chunk 的 lineEnd
                assertTrue(chunks.get(1).getLineStart() <= chunks.get(0).getLineEnd(),
                        "Chunks should have overlap");
            }
        }

        @Test
        @DisplayName("filePath 正确传递")
        void filePathCorrectlyPassed() {
            List<MemoryChunk> chunks = chunker.chunk("memory/2026-01-15.md", "content");
            assertEquals("memory/2026-01-15.md", chunks.get(0).getFilePath());
        }

        @Test
        @DisplayName("行号是 1-based")
        void lineNumbersAreOneBased() {
            String content = "Line1\nLine2\nLine3";
            List<MemoryChunk> chunks = chunker.chunk("test.md", content);
            assertEquals(1, chunks.get(0).getLineStart());
            assertTrue(chunks.get(0).getLineEnd() >= 1);
        }

        @Test
        @DisplayName("处理中文内容")
        void handleChineseContent() {
            // 中文每字 2 tokens，50 target 约 25 个中文字
            String content = "这是一段中文测试内容，用于验证分块器对中文的处理能力。\n" +
                    "第二行也是中文内容，继续测试。\n" +
                    "第三行中文。";
            List<MemoryChunk> chunks = chunker.chunk("test.md", content);
            assertFalse(chunks.isEmpty());
            assertTrue(chunks.get(0).getContent().contains("中文"));
        }

        @Test
        @DisplayName("处理混合中英文内容")
        void handleMixedContent() {
            String content = "Hello 你好 World 世界\nTest 测试 Content 内容";
            List<MemoryChunk> chunks = chunker.chunk("test.md", content);
            assertFalse(chunks.isEmpty());
        }

        @Test
        @DisplayName("保留空行在内容中")
        void preserveEmptyLinesInContent() {
            String content = "Line1\n\nLine3";
            List<MemoryChunk> chunks = chunker.chunk("test.md", content);
            // trim 后空行可能被移除，但不应该崩溃
            assertFalse(chunks.isEmpty());
        }

        @Test
        @DisplayName("不会产生无限循环 - 大量相同内容")
        void noInfiniteLoopWithLargeContent() {
            // 创建大量内容确保不会无限循环
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                sb.append("Line ").append(i).append(" content\n");
            }
            List<MemoryChunk> chunks = chunker.chunk("test.md", sb.toString());
            assertTrue(chunks.size() > 0 && chunks.size() < 2000);
        }
    }

    // ==================== estimateTokens 测试 ====================

    @Nested
    @DisplayName("estimateTokens")
    class EstimateTokensTests {

        @Test
        @DisplayName("null 返回 1")
        void nullReturnsOne() {
            assertEquals(1, MemoryChunker.estimateTokens(null));
        }

        @Test
        @DisplayName("空字符串返回 1")
        void emptyStringReturnsOne() {
            assertEquals(1, MemoryChunker.estimateTokens(""));
        }

        @Test
        @DisplayName("纯英文字符 - 约 0.3 tokens/char")
        void englishCharacters() {
            // 10 chars * 0.3 = 3 tokens
            int tokens = MemoryChunker.estimateTokens("HelloWorld");
            assertEquals(3, tokens);
        }

        @Test
        @DisplayName("纯中文字符 - 2 tokens/char")
        void chineseCharacters() {
            // 5 中文 * 2 = 10 tokens
            int tokens = MemoryChunker.estimateTokens("你好世界啊");
            assertEquals(10, tokens);
        }

        @Test
        @DisplayName("混合中英文")
        void mixedContent() {
            // "Hello" = 5 * 0.3 = 1.5 -> 1
            // "你好" = 2 * 2 = 4
            // 总计约 5-6
            int tokens = MemoryChunker.estimateTokens("Hello你好");
            assertTrue(tokens >= 5 && tokens <= 6);
        }

        @Test
        @DisplayName("包含空格和标点")
        void withSpacesAndPunctuation() {
            // 空格和标点都算 other，0.3 each
            int tokens = MemoryChunker.estimateTokens("Hello, World!");
            assertTrue(tokens > 0);
        }

        @Test
        @DisplayName("单个字符返回至少 1")
        void singleCharReturnsAtLeastOne() {
            assertEquals(1, MemoryChunker.estimateTokens("a"));
            assertEquals(2, MemoryChunker.estimateTokens("中"));
        }

        @Test
        @DisplayName("边界值 - 非常长的字符串")
        void veryLongString() {
            String longString = "x".repeat(10000);
            int tokens = MemoryChunker.estimateTokens(longString);
            // 10000 * 0.3 = 3000
            assertEquals(3000, tokens);
        }

        @Test
        @DisplayName("特殊 Unicode 字符")
        void specialUnicodeCharacters() {
            // Emoji 不是 HAN script，算 other
            int tokens = MemoryChunker.estimateTokens("🎉🎊");
            assertTrue(tokens >= 1);
        }

        @Test
        @DisplayName("日文字符（非 HAN）")
        void japaneseNonHanCharacters() {
            // 平假名不是 HAN script
            int tokens = MemoryChunker.estimateTokens("あいうえお");
            assertTrue(tokens >= 1);
        }

        @Test
        @DisplayName("日文汉字（HAN script）")
        void japaneseKanjiCharacters() {
            // 汉字是 HAN script，2 tokens each
            int tokens = MemoryChunker.estimateTokens("東京");
            assertEquals(4, tokens);
        }
    }

    // ==================== MemoryChunk 数据模型测试 ====================

    @Nested
    @DisplayName("MemoryChunk model")
    class MemoryChunkModelTests {

        @Test
        @DisplayName("Builder 创建对象")
        void builderCreatesObject() {
            MemoryChunk chunk = MemoryChunk.builder()
                    .filePath("test.md")
                    .lineStart(1)
                    .lineEnd(10)
                    .content("test content")
                    .build();

            assertEquals("test.md", chunk.getFilePath());
            assertEquals(1, chunk.getLineStart());
            assertEquals(10, chunk.getLineEnd());
            assertEquals("test content", chunk.getContent());
        }

        @Test
        @DisplayName("Setter 可修改值")
        void setterModifiesValue() {
            MemoryChunk chunk = MemoryChunk.builder().build();
            chunk.setFilePath("new.md");
            chunk.setLineStart(5);
            chunk.setLineEnd(15);
            chunk.setContent("new content");

            assertEquals("new.md", chunk.getFilePath());
            assertEquals(5, chunk.getLineStart());
            assertEquals(15, chunk.getLineEnd());
            assertEquals("new content", chunk.getContent());
        }

        @Test
        @DisplayName("equals 和 hashCode")
        void equalsAndHashCode() {
            MemoryChunk chunk1 = MemoryChunk.builder()
                    .filePath("test.md")
                    .lineStart(1)
                    .lineEnd(10)
                    .content("content")
                    .build();

            MemoryChunk chunk2 = MemoryChunk.builder()
                    .filePath("test.md")
                    .lineStart(1)
                    .lineEnd(10)
                    .content("content")
                    .build();

            assertEquals(chunk1, chunk2);
            assertEquals(chunk1.hashCode(), chunk2.hashCode());
        }

        @Test
        @DisplayName("边界值 - lineStart 和 lineEnd 相同")
        void sameStartAndEnd() {
            MemoryChunk chunk = MemoryChunk.builder()
                    .filePath("test.md")
                    .lineStart(5)
                    .lineEnd(5)
                    .content("single line")
                    .build();

            assertEquals(5, chunk.getLineStart());
            assertEquals(5, chunk.getLineEnd());
        }
    }

    // ==================== 配置影响测试 ====================

    @Nested
    @DisplayName("Configuration effects")
    class ConfigurationEffectsTests {

        @Test
        @DisplayName("更大的 targetTokens 产生更少的 chunks")
        void largerTargetFewerChunks() {
            String content = "x".repeat(500); // 500 * 0.3 = 150 tokens

            properties.getChunk().setTargetTokens(50);
            List<MemoryChunk> smallTarget = chunker.chunk("test.md", content);

            properties.getChunk().setTargetTokens(200);
            List<MemoryChunk> largeTarget = chunker.chunk("test.md", content);

            assertTrue(largeTarget.size() <= smallTarget.size());
        }

        @Test
        @DisplayName("overlapTokens 为 0 时无重叠")
        void zeroOverlapNoOverlap() {
            properties.getChunk().setTargetTokens(20);
            properties.getChunk().setOverlapTokens(0);

            String content = "Line1 content\nLine2 content\nLine3 content\nLine4 content";
            List<MemoryChunk> chunks = chunker.chunk("test.md", content);

            if (chunks.size() >= 2) {
                // 无重叠时，第二个 chunk 的 lineStart 应该等于或大于第一个的 lineEnd
                assertTrue(chunks.get(1).getLineStart() >= chunks.get(0).getLineEnd());
            }
        }
    }
}
