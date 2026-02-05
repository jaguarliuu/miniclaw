package com.jaguarliu.ai.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MemoryProperties 单元测试
 *
 * 测试覆盖：
 * 1. 默认值验证
 * 2. 嵌套配置对象
 * 3. 边界值测试
 * 4. 配置对象的可变性
 */
@DisplayName("MemoryProperties Tests")
class MemoryPropertiesTest {

    private MemoryProperties properties;

    @BeforeEach
    void setUp() {
        properties = new MemoryProperties();
    }

    // ==================== 默认值测试 ====================

    @Nested
    @DisplayName("Default Values")
    class DefaultValuesTests {

        @Test
        @DisplayName("path 默认值为 memory")
        void defaultPath() {
            assertEquals("memory", properties.getPath());
        }

        @Test
        @DisplayName("chunk 配置不为 null")
        void chunkConfigNotNull() {
            assertNotNull(properties.getChunk());
        }

        @Test
        @DisplayName("embedding 配置不为 null")
        void embeddingConfigNotNull() {
            assertNotNull(properties.getEmbedding());
        }

        @Test
        @DisplayName("search 配置不为 null")
        void searchConfigNotNull() {
            assertNotNull(properties.getSearch());
        }

        @Test
        @DisplayName("flush 配置不为 null")
        void flushConfigNotNull() {
            assertNotNull(properties.getFlush());
        }
    }

    // ==================== Chunk 配置测试 ====================

    @Nested
    @DisplayName("ChunkConfig")
    class ChunkConfigTests {

        @Test
        @DisplayName("targetTokens 默认值为 400")
        void defaultTargetTokens() {
            assertEquals(400, properties.getChunk().getTargetTokens());
        }

        @Test
        @DisplayName("overlapTokens 默认值为 80")
        void defaultOverlapTokens() {
            assertEquals(80, properties.getChunk().getOverlapTokens());
        }

        @Test
        @DisplayName("可以设置 targetTokens")
        void setTargetTokens() {
            properties.getChunk().setTargetTokens(500);
            assertEquals(500, properties.getChunk().getTargetTokens());
        }

        @Test
        @DisplayName("边界值 - targetTokens 为 0")
        void targetTokensZero() {
            properties.getChunk().setTargetTokens(0);
            assertEquals(0, properties.getChunk().getTargetTokens());
        }

        @Test
        @DisplayName("边界值 - targetTokens 为负数")
        void targetTokensNegative() {
            properties.getChunk().setTargetTokens(-1);
            assertEquals(-1, properties.getChunk().getTargetTokens());
        }

        @Test
        @DisplayName("边界值 - targetTokens 为 Integer.MAX_VALUE")
        void targetTokensMaxValue() {
            properties.getChunk().setTargetTokens(Integer.MAX_VALUE);
            assertEquals(Integer.MAX_VALUE, properties.getChunk().getTargetTokens());
        }

        @Test
        @DisplayName("overlapTokens 应小于 targetTokens（设计约束）")
        void overlapShouldBeLessThanTarget() {
            // 默认值满足约束
            assertTrue(properties.getChunk().getOverlapTokens() < properties.getChunk().getTargetTokens());
        }
    }

    // ==================== Embedding 配置测试 ====================

    @Nested
    @DisplayName("EmbeddingConfig")
    class EmbeddingConfigTests {

        @Test
        @DisplayName("provider 默认值为 auto")
        void defaultProvider() {
            assertEquals("auto", properties.getEmbedding().getProvider());
        }

        @Test
        @DisplayName("model 默认值为 text-embedding-3-small")
        void defaultModel() {
            assertEquals("text-embedding-3-small", properties.getEmbedding().getModel());
        }

        @Test
        @DisplayName("dimensions 默认值为 1536")
        void defaultDimensions() {
            assertEquals(1536, properties.getEmbedding().getDimensions());
        }

        @Test
        @DisplayName("batchSize 默认值为 20")
        void defaultBatchSize() {
            assertEquals(20, properties.getEmbedding().getBatchSize());
        }

        @Test
        @DisplayName("endpoint 默认值为 null")
        void defaultEndpoint() {
            assertNull(properties.getEmbedding().getEndpoint());
        }

        @Test
        @DisplayName("apiKey 默认值为 null")
        void defaultApiKey() {
            assertNull(properties.getEmbedding().getApiKey());
        }

        @Test
        @DisplayName("可以设置 provider 为 none")
        void setProviderNone() {
            properties.getEmbedding().setProvider("none");
            assertEquals("none", properties.getEmbedding().getProvider());
        }

        @Test
        @DisplayName("可以设置 provider 为 openai")
        void setProviderOpenai() {
            properties.getEmbedding().setProvider("openai");
            assertEquals("openai", properties.getEmbedding().getProvider());
        }

        @Test
        @DisplayName("可以设置 provider 为 llm")
        void setProviderLlm() {
            properties.getEmbedding().setProvider("llm");
            assertEquals("llm", properties.getEmbedding().getProvider());
        }

        @Test
        @DisplayName("边界值 - dimensions 为 0")
        void dimensionsZero() {
            properties.getEmbedding().setDimensions(0);
            assertEquals(0, properties.getEmbedding().getDimensions());
        }

        @Test
        @DisplayName("边界值 - batchSize 为 1（最小有效值）")
        void batchSizeMinimum() {
            properties.getEmbedding().setBatchSize(1);
            assertEquals(1, properties.getEmbedding().getBatchSize());
        }
    }

    // ==================== Search 配置测试 ====================

    @Nested
    @DisplayName("SearchConfig")
    class SearchConfigTests {

        @Test
        @DisplayName("vectorTopK 默认值为 10")
        void defaultVectorTopK() {
            assertEquals(10, properties.getSearch().getVectorTopK());
        }

        @Test
        @DisplayName("ftsTopK 默认值为 10")
        void defaultFtsTopK() {
            assertEquals(10, properties.getSearch().getFtsTopK());
        }

        @Test
        @DisplayName("finalTopK 默认值为 5")
        void defaultFinalTopK() {
            assertEquals(5, properties.getSearch().getFinalTopK());
        }

        @Test
        @DisplayName("snippetMaxChars 默认值为 700")
        void defaultSnippetMaxChars() {
            assertEquals(700, properties.getSearch().getSnippetMaxChars());
        }

        @Test
        @DisplayName("minSimilarity 默认值为 0.3")
        void defaultMinSimilarity() {
            assertEquals(0.3, properties.getSearch().getMinSimilarity(), 0.001);
        }

        @Test
        @DisplayName("边界值 - minSimilarity 为 0.0")
        void minSimilarityZero() {
            properties.getSearch().setMinSimilarity(0.0);
            assertEquals(0.0, properties.getSearch().getMinSimilarity(), 0.001);
        }

        @Test
        @DisplayName("边界值 - minSimilarity 为 1.0")
        void minSimilarityOne() {
            properties.getSearch().setMinSimilarity(1.0);
            assertEquals(1.0, properties.getSearch().getMinSimilarity(), 0.001);
        }

        @Test
        @DisplayName("边界值 - minSimilarity 超出正常范围（>1.0）")
        void minSimilarityOutOfRange() {
            properties.getSearch().setMinSimilarity(1.5);
            assertEquals(1.5, properties.getSearch().getMinSimilarity(), 0.001);
        }

        @Test
        @DisplayName("边界值 - snippetMaxChars 为 0")
        void snippetMaxCharsZero() {
            properties.getSearch().setSnippetMaxChars(0);
            assertEquals(0, properties.getSearch().getSnippetMaxChars());
        }

        @Test
        @DisplayName("finalTopK 应小于等于 vectorTopK + ftsTopK")
        void finalTopKConstraint() {
            // 默认值满足约束
            assertTrue(properties.getSearch().getFinalTopK() <=
                    properties.getSearch().getVectorTopK() + properties.getSearch().getFtsTopK());
        }
    }

    // ==================== Flush 配置测试 ====================

    @Nested
    @DisplayName("FlushConfig")
    class FlushConfigTests {

        @Test
        @DisplayName("enabled 默认值为 true")
        void defaultEnabled() {
            assertTrue(properties.getFlush().isEnabled());
        }

        @Test
        @DisplayName("tokenThreshold 默认值为 6000")
        void defaultTokenThreshold() {
            assertEquals(6000, properties.getFlush().getTokenThreshold());
        }

        @Test
        @DisplayName("可以禁用 flush")
        void disableFlush() {
            properties.getFlush().setEnabled(false);
            assertFalse(properties.getFlush().isEnabled());
        }

        @Test
        @DisplayName("边界值 - tokenThreshold 为 0")
        void tokenThresholdZero() {
            properties.getFlush().setTokenThreshold(0);
            assertEquals(0, properties.getFlush().getTokenThreshold());
        }

        @Test
        @DisplayName("边界值 - tokenThreshold 为 Integer.MAX_VALUE")
        void tokenThresholdMaxValue() {
            properties.getFlush().setTokenThreshold(Integer.MAX_VALUE);
            assertEquals(Integer.MAX_VALUE, properties.getFlush().getTokenThreshold());
        }
    }

    // ==================== 配置替换测试 ====================

    @Nested
    @DisplayName("Configuration Replacement")
    class ConfigReplacementTests {

        @Test
        @DisplayName("可以替换整个 ChunkConfig 对象")
        void replaceChunkConfig() {
            MemoryProperties.ChunkConfig newConfig = new MemoryProperties.ChunkConfig();
            newConfig.setTargetTokens(800);
            newConfig.setOverlapTokens(160);

            properties.setChunk(newConfig);

            assertEquals(800, properties.getChunk().getTargetTokens());
            assertEquals(160, properties.getChunk().getOverlapTokens());
        }

        @Test
        @DisplayName("可以替换整个 EmbeddingConfig 对象")
        void replaceEmbeddingConfig() {
            MemoryProperties.EmbeddingConfig newConfig = new MemoryProperties.EmbeddingConfig();
            newConfig.setProvider("none");
            newConfig.setModel("custom-model");

            properties.setEmbedding(newConfig);

            assertEquals("none", properties.getEmbedding().getProvider());
            assertEquals("custom-model", properties.getEmbedding().getModel());
        }

        @Test
        @DisplayName("可以设置自定义 path")
        void setCustomPath() {
            properties.setPath("/custom/memory/path");
            assertEquals("/custom/memory/path", properties.getPath());
        }

        @Test
        @DisplayName("边界值 - path 为空字符串")
        void emptyPath() {
            properties.setPath("");
            assertEquals("", properties.getPath());
        }

        @Test
        @DisplayName("边界值 - path 为 null")
        void nullPath() {
            properties.setPath(null);
            assertNull(properties.getPath());
        }
    }

    // ==================== equals/hashCode/toString 测试 ====================

    @Nested
    @DisplayName("Lombok Generated Methods")
    class LombokMethodsTests {

        @Test
        @DisplayName("两个相同配置的对象应该相等")
        void equalObjects() {
            MemoryProperties p1 = new MemoryProperties();
            MemoryProperties p2 = new MemoryProperties();
            assertEquals(p1, p2);
        }

        @Test
        @DisplayName("修改后的对象不应该相等")
        void notEqualAfterModification() {
            MemoryProperties p1 = new MemoryProperties();
            MemoryProperties p2 = new MemoryProperties();
            p2.setPath("different");
            assertNotEquals(p1, p2);
        }

        @Test
        @DisplayName("toString 不应为 null")
        void toStringNotNull() {
            assertNotNull(properties.toString());
        }

        @Test
        @DisplayName("hashCode 应该一致")
        void hashCodeConsistent() {
            MemoryProperties p1 = new MemoryProperties();
            MemoryProperties p2 = new MemoryProperties();
            assertEquals(p1.hashCode(), p2.hashCode());
        }
    }
}
