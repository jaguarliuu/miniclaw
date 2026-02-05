package com.jaguarliu.ai.memory.embedding;

import com.jaguarliu.ai.llm.LlmProperties;
import com.jaguarliu.ai.memory.MemoryProperties;
import org.junit.jupiter.api.*;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EmbeddingModelFactory 单元测试
 */
@DisplayName("EmbeddingModelFactory Tests")
class EmbeddingModelFactoryTest {

    private MemoryProperties memoryProperties;
    private LlmProperties llmProperties;
    private EmbeddingModelFactory factory;

    @BeforeEach
    void setUp() {
        memoryProperties = new MemoryProperties();
        llmProperties = new LlmProperties();
        factory = new EmbeddingModelFactory(memoryProperties, llmProperties);
    }

    // ==================== provider=none 测试 ====================

    @Nested
    @DisplayName("provider=none")
    class ProviderNoneTests {

        @Test
        @DisplayName("显式禁用返回 NoOpEmbeddingModel")
        void explicitlyDisabled() {
            memoryProperties.getEmbedding().setProvider("none");

            EmbeddingModel model = factory.create();

            assertInstanceOf(NoOpEmbeddingModel.class, model);
            assertEquals("none", model.providerType());
        }

        @Test
        @DisplayName("createOptional() 返回 empty")
        void createOptionalReturnsEmpty() {
            memoryProperties.getEmbedding().setProvider("none");

            Optional<EmbeddingModel> optional = factory.createOptional();

            assertTrue(optional.isEmpty());
        }
    }

    // ==================== provider=auto 测试 ====================

    @Nested
    @DisplayName("provider=auto")
    class ProviderAutoTests {

        @Test
        @DisplayName("无任何配置时返回 NoOp")
        void noConfigReturnsNoOp() {
            memoryProperties.getEmbedding().setProvider("auto");
            // 不设置任何 endpoint 或 apiKey

            EmbeddingModel model = factory.create();

            assertInstanceOf(NoOpEmbeddingModel.class, model);
        }

        @Test
        @DisplayName("有专用 embedding 配置时创建 OpenAI 兼容模型")
        void withDedicatedEmbeddingConfig() {
            memoryProperties.getEmbedding().setProvider("auto");
            memoryProperties.getEmbedding().setEndpoint("http://localhost:11434");
            memoryProperties.getEmbedding().setApiKey("test-key");

            EmbeddingModel model = factory.create();

            assertInstanceOf(OpenAiCompatibleEmbeddingModel.class, model);
            assertEquals("openai-compatible", model.providerType());
        }

        @Test
        @DisplayName("只有 LLM 配置时复用 LLM endpoint")
        void withLlmConfigOnly() {
            memoryProperties.getEmbedding().setProvider("auto");
            llmProperties.setEndpoint("http://localhost:11434");
            llmProperties.setApiKey("llm-key");

            EmbeddingModel model = factory.create();

            assertInstanceOf(OpenAiCompatibleEmbeddingModel.class, model);
        }

        @Test
        @DisplayName("专用配置优先于 LLM 配置")
        void dedicatedConfigTakesPrecedence() {
            memoryProperties.getEmbedding().setProvider("auto");
            memoryProperties.getEmbedding().setEndpoint("http://embedding-server:8080");
            memoryProperties.getEmbedding().setApiKey("embedding-key");
            llmProperties.setEndpoint("http://llm-server:8080");
            llmProperties.setApiKey("llm-key");

            EmbeddingModel model = factory.create();

            // 应该使用专用配置
            assertInstanceOf(OpenAiCompatibleEmbeddingModel.class, model);
        }
    }

    // ==================== provider=openai 测试 ====================

    @Nested
    @DisplayName("provider=openai")
    class ProviderOpenAiTests {

        @Test
        @DisplayName("有专用配置时创建模型")
        void withDedicatedConfig() {
            memoryProperties.getEmbedding().setProvider("openai");
            memoryProperties.getEmbedding().setEndpoint("https://api.openai.com");
            memoryProperties.getEmbedding().setApiKey("sk-xxx");

            EmbeddingModel model = factory.create();

            assertInstanceOf(OpenAiCompatibleEmbeddingModel.class, model);
        }

        @Test
        @DisplayName("配置不完整时回退到 LLM 配置")
        void fallbackToLlmConfig() {
            memoryProperties.getEmbedding().setProvider("openai");
            // 不设置专用 endpoint/apiKey
            llmProperties.setEndpoint("http://localhost:11434");
            llmProperties.setApiKey("llm-key");

            EmbeddingModel model = factory.create();

            assertInstanceOf(OpenAiCompatibleEmbeddingModel.class, model);
        }

        @Test
        @DisplayName("都没有配置时返回 NoOp")
        void noConfigReturnsNoOp() {
            memoryProperties.getEmbedding().setProvider("openai");
            // 不设置任何配置

            EmbeddingModel model = factory.create();

            assertInstanceOf(NoOpEmbeddingModel.class, model);
        }
    }

    // ==================== provider=llm 测试 ====================

    @Nested
    @DisplayName("provider=llm")
    class ProviderLlmTests {

        @Test
        @DisplayName("有 LLM 配置时创建模型")
        void withLlmConfig() {
            memoryProperties.getEmbedding().setProvider("llm");
            llmProperties.setEndpoint("http://localhost:11434");
            llmProperties.setApiKey("key");

            EmbeddingModel model = factory.create();

            assertInstanceOf(OpenAiCompatibleEmbeddingModel.class, model);
        }

        @Test
        @DisplayName("无 LLM 配置时返回 NoOp")
        void withoutLlmConfigReturnsNoOp() {
            memoryProperties.getEmbedding().setProvider("llm");
            // 不设置 LLM 配置

            EmbeddingModel model = factory.create();

            assertInstanceOf(NoOpEmbeddingModel.class, model);
        }

        @Test
        @DisplayName("LLM endpoint 为空时返回 NoOp")
        void emptyEndpointReturnsNoOp() {
            memoryProperties.getEmbedding().setProvider("llm");
            llmProperties.setEndpoint("");
            llmProperties.setApiKey("key");

            EmbeddingModel model = factory.create();

            assertInstanceOf(NoOpEmbeddingModel.class, model);
        }

        @Test
        @DisplayName("LLM apiKey 为空时返回 NoOp")
        void emptyApiKeyReturnsNoOp() {
            memoryProperties.getEmbedding().setProvider("llm");
            llmProperties.setEndpoint("http://localhost:11434");
            llmProperties.setApiKey("");

            EmbeddingModel model = factory.create();

            assertInstanceOf(NoOpEmbeddingModel.class, model);
        }
    }

    // ==================== 未知 provider 测试 ====================

    @Nested
    @DisplayName("Unknown provider")
    class UnknownProviderTests {

        @Test
        @DisplayName("未知 provider 回退到 auto")
        void unknownProviderFallsBackToAuto() {
            memoryProperties.getEmbedding().setProvider("unknown-provider");
            llmProperties.setEndpoint("http://localhost:11434");
            llmProperties.setApiKey("key");

            EmbeddingModel model = factory.create();

            // 应该回退到 auto 模式并使用 LLM 配置
            assertInstanceOf(OpenAiCompatibleEmbeddingModel.class, model);
        }

        @Test
        @DisplayName("未知 provider 无配置时返回 NoOp")
        void unknownProviderNoConfigReturnsNoOp() {
            memoryProperties.getEmbedding().setProvider("xyz");

            EmbeddingModel model = factory.create();

            assertInstanceOf(NoOpEmbeddingModel.class, model);
        }
    }

    // ==================== createOptional 测试 ====================

    @Nested
    @DisplayName("createOptional")
    class CreateOptionalTests {

        @Test
        @DisplayName("有实际实现时返回 Optional.of()")
        void returnsOptionalOfWhenImplementation() {
            memoryProperties.getEmbedding().setProvider("auto");
            llmProperties.setEndpoint("http://localhost:11434");
            llmProperties.setApiKey("key");

            Optional<EmbeddingModel> optional = factory.createOptional();

            assertTrue(optional.isPresent());
            assertInstanceOf(OpenAiCompatibleEmbeddingModel.class, optional.get());
        }

        @Test
        @DisplayName("NoOp 时返回 Optional.empty()")
        void returnsEmptyWhenNoOp() {
            memoryProperties.getEmbedding().setProvider("none");

            Optional<EmbeddingModel> optional = factory.createOptional();

            assertTrue(optional.isEmpty());
        }
    }

    // ==================== 边界值测试 ====================

    @Nested
    @DisplayName("Boundary values")
    class BoundaryValuesTests {

        @Test
        @DisplayName("空白 endpoint 视为未配置")
        void blankEndpointTreatedAsUnconfigured() {
            memoryProperties.getEmbedding().setProvider("auto");
            memoryProperties.getEmbedding().setEndpoint("   ");
            memoryProperties.getEmbedding().setApiKey("key");

            EmbeddingModel model = factory.create();

            assertInstanceOf(NoOpEmbeddingModel.class, model);
        }

        @Test
        @DisplayName("空白 apiKey 视为未配置")
        void blankApiKeyTreatedAsUnconfigured() {
            memoryProperties.getEmbedding().setProvider("auto");
            memoryProperties.getEmbedding().setEndpoint("http://localhost");
            memoryProperties.getEmbedding().setApiKey("   ");

            EmbeddingModel model = factory.create();

            assertInstanceOf(NoOpEmbeddingModel.class, model);
        }

        @Test
        @DisplayName("model 和 dimensions 正确传递")
        void modelAndDimensionsPassedCorrectly() {
            memoryProperties.getEmbedding().setProvider("openai");
            memoryProperties.getEmbedding().setEndpoint("http://localhost");
            memoryProperties.getEmbedding().setApiKey("key");
            memoryProperties.getEmbedding().setModel("custom-model");
            memoryProperties.getEmbedding().setDimensions(768);

            EmbeddingModel model = factory.create();

            assertEquals("custom-model", model.modelName());
            assertEquals(768, model.dimensions());
        }
    }
}
