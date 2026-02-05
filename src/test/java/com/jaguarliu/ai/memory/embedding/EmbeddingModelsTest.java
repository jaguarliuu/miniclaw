package com.jaguarliu.ai.memory.embedding;

import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Embedding 数据模型单元测试
 */
@DisplayName("Embedding Data Models Tests")
class EmbeddingModelsTest {

    // ==================== Embedding record 测试 ====================

    @Nested
    @DisplayName("Embedding record")
    class EmbeddingTests {

        @Test
        @DisplayName("创建完整的 Embedding")
        void createFullEmbedding() {
            float[] vector = {0.1f, 0.2f, 0.3f};
            Map<String, Object> metadata = Map.of("model", "test");
            Embedding embedding = new Embedding(vector, 0, metadata);

            assertArrayEquals(vector, embedding.vector());
            assertEquals(0, embedding.index());
            assertEquals("test", embedding.metadata().get("model"));
        }

        @Test
        @DisplayName("of(vector) 工厂方法")
        void ofVectorOnly() {
            float[] vector = {1.0f, 2.0f};
            Embedding embedding = Embedding.of(vector);

            assertArrayEquals(vector, embedding.vector());
            assertEquals(0, embedding.index());
            assertTrue(embedding.metadata().isEmpty());
        }

        @Test
        @DisplayName("of(vector, index) 工厂方法")
        void ofVectorAndIndex() {
            float[] vector = {1.0f};
            Embedding embedding = Embedding.of(vector, 5);

            assertEquals(5, embedding.index());
        }

        @Test
        @DisplayName("dimensions() 返回向量长度")
        void dimensionsReturnsVectorLength() {
            Embedding embedding = Embedding.of(new float[]{1, 2, 3, 4, 5});
            assertEquals(5, embedding.dimensions());
        }

        @Test
        @DisplayName("边界值 - null vector 的 dimensions 返回 0")
        void nullVectorDimensionsZero() {
            Embedding embedding = new Embedding(null, 0, Map.of());
            assertEquals(0, embedding.dimensions());
        }

        @Test
        @DisplayName("边界值 - 空向量")
        void emptyVector() {
            Embedding embedding = Embedding.of(new float[0]);
            assertEquals(0, embedding.dimensions());
        }

        @Test
        @DisplayName("边界值 - 大向量")
        void largeVector() {
            float[] vector = new float[1536];
            Embedding embedding = Embedding.of(vector);
            assertEquals(1536, embedding.dimensions());
        }
    }

    // ==================== EmbeddingRequest record 测试 ====================

    @Nested
    @DisplayName("EmbeddingRequest record")
    class EmbeddingRequestTests {

        @Test
        @DisplayName("of(text) 创建单文本请求")
        void ofSingleText() {
            EmbeddingRequest request = EmbeddingRequest.of("hello");

            assertEquals(1, request.size());
            assertTrue(request.isSingle());
            assertEquals("hello", request.inputs().get(0));
            assertNotNull(request.options());
        }

        @Test
        @DisplayName("of(texts) 创建批量请求")
        void ofMultipleTexts() {
            EmbeddingRequest request = EmbeddingRequest.of(List.of("a", "b", "c"));

            assertEquals(3, request.size());
            assertFalse(request.isSingle());
        }

        @Test
        @DisplayName("of(texts, options) 创建带选项请求")
        void ofWithOptions() {
            var options = EmbeddingRequest.EmbeddingOptions.withModel("custom-model");
            EmbeddingRequest request = EmbeddingRequest.of(List.of("text"), options);

            assertEquals("custom-model", request.options().model());
        }

        @Test
        @DisplayName("边界值 - 空列表")
        void emptyInputs() {
            EmbeddingRequest request = EmbeddingRequest.of(List.of());
            assertEquals(0, request.size());
            assertFalse(request.isSingle());
        }

        @Test
        @DisplayName("EmbeddingOptions.defaults() 返回空选项")
        void defaultOptions() {
            var options = EmbeddingRequest.EmbeddingOptions.defaults();
            assertNull(options.model());
            assertNull(options.dimensions());
            assertTrue(options.additionalOptions().isEmpty());
        }

        @Test
        @DisplayName("EmbeddingOptions.withDimensions()")
        void optionsWithDimensions() {
            var options = EmbeddingRequest.EmbeddingOptions.withDimensions(768);
            assertEquals(768, options.dimensions());
        }
    }

    // ==================== EmbeddingResponse record 测试 ====================

    @Nested
    @DisplayName("EmbeddingResponse record")
    class EmbeddingResponseTests {

        @Test
        @DisplayName("of(embeddings) 创建简单响应")
        void ofEmbeddings() {
            List<Embedding> embeddings = List.of(
                    Embedding.of(new float[]{1, 2}),
                    Embedding.of(new float[]{3, 4})
            );
            EmbeddingResponse response = EmbeddingResponse.of(embeddings);

            assertEquals(2, response.embeddings().size());
            assertEquals(0, response.usage().promptTokens());
        }

        @Test
        @DisplayName("of(embeddings, usage) 创建带使用量响应")
        void ofWithUsage() {
            var usage = EmbeddingResponse.Usage.of(100, 100);
            EmbeddingResponse response = EmbeddingResponse.of(List.of(), usage);

            assertEquals(100, response.usage().promptTokens());
            assertEquals(100, response.usage().totalTokens());
        }

        @Test
        @DisplayName("first() 返回第一个 embedding")
        void firstEmbedding() {
            List<Embedding> embeddings = List.of(
                    Embedding.of(new float[]{1}),
                    Embedding.of(new float[]{2})
            );
            EmbeddingResponse response = EmbeddingResponse.of(embeddings);

            assertEquals(1, response.first().vector()[0]);
        }

        @Test
        @DisplayName("边界值 - first() 空列表返回 null")
        void firstOnEmpty() {
            EmbeddingResponse response = EmbeddingResponse.of(List.of());
            assertNull(response.first());
        }

        @Test
        @DisplayName("vectors() 返回所有向量")
        void vectorsReturnsAll() {
            List<Embedding> embeddings = List.of(
                    Embedding.of(new float[]{1}),
                    Embedding.of(new float[]{2}),
                    Embedding.of(new float[]{3})
            );
            EmbeddingResponse response = EmbeddingResponse.of(embeddings);

            List<float[]> vectors = response.vectors();
            assertEquals(3, vectors.size());
        }

        @Test
        @DisplayName("边界值 - null embeddings 的 vectors() 返回空")
        void vectorsWithNullEmbeddings() {
            EmbeddingResponse response = new EmbeddingResponse(null, EmbeddingResponse.Usage.empty(), Map.of());
            assertTrue(response.vectors().isEmpty());
        }

        @Test
        @DisplayName("Usage.empty() 返回零值")
        void usageEmpty() {
            var usage = EmbeddingResponse.Usage.empty();
            assertEquals(0, usage.promptTokens());
            assertEquals(0, usage.totalTokens());
        }
    }

    // ==================== NoOpEmbeddingModel 测试 ====================

    @Nested
    @DisplayName("NoOpEmbeddingModel")
    class NoOpEmbeddingModelTests {

        private final NoOpEmbeddingModel model = NoOpEmbeddingModel.INSTANCE;

        @Test
        @DisplayName("INSTANCE 是单例")
        void instanceIsSingleton() {
            assertSame(NoOpEmbeddingModel.INSTANCE, NoOpEmbeddingModel.INSTANCE);
        }

        @Test
        @DisplayName("call() 返回空响应")
        void callReturnsEmptyResponse() {
            EmbeddingResponse response = model.call(EmbeddingRequest.of("test"));
            assertTrue(response.embeddings().isEmpty());
        }

        @Test
        @DisplayName("embed(String) 返回空数组")
        void embedStringReturnsEmpty() {
            float[] result = model.embed("test");
            assertEquals(0, result.length);
        }

        @Test
        @DisplayName("embed(List) 返回空列表")
        void embedListReturnsEmpty() {
            List<float[]> result = model.embed(List.of("a", "b"));
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("dimensions() 返回 0")
        void dimensionsReturnsZero() {
            assertEquals(0, model.dimensions());
        }

        @Test
        @DisplayName("modelName() 返回 'none'")
        void modelNameReturnsNone() {
            assertEquals("none", model.modelName());
        }

        @Test
        @DisplayName("providerType() 返回 'none'")
        void providerTypeReturnsNone() {
            assertEquals("none", model.providerType());
        }
    }

    // ==================== EmbeddingModel 接口默认方法测试 ====================

    @Nested
    @DisplayName("EmbeddingModel default methods")
    class EmbeddingModelDefaultMethodsTests {

        /**
         * 简单的测试实现
         */
        private final EmbeddingModel testModel = new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                List<Embedding> embeddings = request.inputs().stream()
                        .map(text -> Embedding.of(new float[]{text.length()}, 0))
                        .toList();
                return EmbeddingResponse.of(embeddings);
            }

            @Override
            public int dimensions() {
                return 1;
            }

            @Override
            public String modelName() {
                return "test-model";
            }

            @Override
            public String providerType() {
                return "test";
            }
        };

        @Test
        @DisplayName("embed(String) 使用默认实现")
        void embedStringDefaultImpl() {
            float[] result = testModel.embed("hello");
            assertEquals(5.0f, result[0]); // "hello" 长度为 5
        }

        @Test
        @DisplayName("embed(List) 使用默认实现")
        void embedListDefaultImpl() {
            List<float[]> results = testModel.embed(List.of("a", "bb", "ccc"));
            assertEquals(3, results.size());
            assertEquals(1.0f, results.get(0)[0]);
            assertEquals(2.0f, results.get(1)[0]);
            assertEquals(3.0f, results.get(2)[0]);
        }

        @Test
        @DisplayName("embed(null List) 返回空列表")
        void embedNullReturnsEmpty() {
            List<float[]> results = testModel.embed((List<String>) null);
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("embed(空列表) 返回空列表")
        void embedEmptyListReturnsEmpty() {
            List<float[]> results = testModel.embed(List.of());
            assertTrue(results.isEmpty());
        }
    }
}
