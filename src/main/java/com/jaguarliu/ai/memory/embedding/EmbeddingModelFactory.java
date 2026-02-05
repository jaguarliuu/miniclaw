package com.jaguarliu.ai.memory.embedding;

import com.jaguarliu.ai.llm.LlmProperties;
import com.jaguarliu.ai.memory.MemoryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Embedding Model 工厂
 *
 * 职责：
 * - 根据配置自动探测和创建合适的 EmbeddingModel
 * - 支持显式指定 provider
 * - 支持自动降级（无 provider 时返回 NoOp）
 *
 * 自动探测策略（优先级从高到低）：
 * 1. 显式配置 memory.embedding.provider = openai/llm/none
 * 2. auto 模式下自动探测：
 *    a. memory.embedding.endpoint + apiKey 存在 → OpenAI 兼容
 *    b. llm.endpoint + llm.api-key 存在 → 复用 LLM 的 endpoint
 *    c. 都没有 → 返回 NoOpEmbeddingModel
 *
 * 扩展点：
 * - 新增 provider 类型时，在 create() 的 switch 中添加分支
 * - 或者通过 SPI 机制动态注册（未来扩展）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingModelFactory {

    private final MemoryProperties memoryProperties;
    private final LlmProperties llmProperties;

    /**
     * 创建 EmbeddingModel
     *
     * @return 如果能创建返回具体实现，否则返回 NoOpEmbeddingModel
     */
    public EmbeddingModel create() {
        MemoryProperties.EmbeddingConfig config = memoryProperties.getEmbedding();
        String providerType = config.getProvider();

        return switch (providerType) {
            case "none" -> {
                log.info("Embedding provider explicitly disabled (provider=none)");
                yield NoOpEmbeddingModel.INSTANCE;
            }
            case "openai" -> createOpenAiCompatible(config);
            case "llm" -> createFromLlm(config);
            case "auto" -> autoDetect(config);
            default -> {
                log.warn("Unknown embedding provider: {}, falling back to auto", providerType);
                yield autoDetect(config);
            }
        };
    }

    /**
     * 创建 EmbeddingModel，返回 Optional
     *
     * @return 如果成功创建实际实现返回 Optional.of()，NoOp 返回 empty
     */
    public Optional<EmbeddingModel> createOptional() {
        EmbeddingModel model = create();
        if (model instanceof NoOpEmbeddingModel) {
            return Optional.empty();
        }
        return Optional.of(model);
    }

    /**
     * 自动探测
     */
    private EmbeddingModel autoDetect(MemoryProperties.EmbeddingConfig config) {
        log.info("Auto-detecting embedding provider...");

        // 1. 检查是否有专用 embedding 配置
        if (isNotBlank(config.getEndpoint()) && isNotBlank(config.getApiKey())) {
            log.info("Detected dedicated embedding endpoint");
            return createOpenAiCompatible(config);
        }

        // 2. 检查是否能复用 LLM endpoint
        if (isNotBlank(llmProperties.getEndpoint()) && isNotBlank(llmProperties.getApiKey())) {
            log.info("Detected LLM endpoint, reusing for embeddings");
            return createFromLlm(config);
        }

        // 3. 都没有 → 禁用
        log.info("No embedding provider available. Vector search disabled, FTS fallback active.");
        return NoOpEmbeddingModel.INSTANCE;
    }

    /**
     * 使用专用配置创建 OpenAI 兼容客户端
     */
    private EmbeddingModel createOpenAiCompatible(MemoryProperties.EmbeddingConfig config) {
        String endpoint = isNotBlank(config.getEndpoint()) ? config.getEndpoint() : llmProperties.getEndpoint();
        String apiKey = isNotBlank(config.getApiKey()) ? config.getApiKey() : llmProperties.getApiKey();

        if (!isNotBlank(endpoint) || !isNotBlank(apiKey)) {
            log.warn("OpenAI embedding config incomplete, disabling vector search");
            return NoOpEmbeddingModel.INSTANCE;
        }

        try {
            OpenAiCompatibleEmbeddingModel model = new OpenAiCompatibleEmbeddingModel(
                    endpoint, apiKey, config.getModel(), config.getDimensions());
            log.info("Created OpenAI-compatible embedding model: endpoint={}, model={}",
                    endpoint, config.getModel());
            return model;
        } catch (Exception e) {
            log.error("Failed to create embedding model: {}", e.getMessage());
            return NoOpEmbeddingModel.INSTANCE;
        }
    }

    /**
     * 复用 LLM 配置创建 embedding 客户端
     */
    private EmbeddingModel createFromLlm(MemoryProperties.EmbeddingConfig config) {
        if (!isNotBlank(llmProperties.getEndpoint()) || !isNotBlank(llmProperties.getApiKey())) {
            log.warn("LLM config not available for embedding, disabling vector search");
            return NoOpEmbeddingModel.INSTANCE;
        }

        try {
            OpenAiCompatibleEmbeddingModel model = new OpenAiCompatibleEmbeddingModel(
                    llmProperties.getEndpoint(),
                    llmProperties.getApiKey(),
                    config.getModel(),
                    config.getDimensions());
            log.info("Created embedding model from LLM config: endpoint={}, model={}",
                    llmProperties.getEndpoint(), config.getModel());
            return model;
        } catch (Exception e) {
            log.error("Failed to create embedding model from LLM: {}", e.getMessage());
            return NoOpEmbeddingModel.INSTANCE;
        }
    }

    private boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }
}
