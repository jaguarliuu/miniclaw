package com.jaguarliu.ai.memory.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容的 Embedding 模型实现
 *
 * 支持所有 OpenAI 兼容接口：
 * - OpenAI
 * - DeepSeek
 * - 通义千问 (dashscope compatible-mode)
 * - Ollama
 * - Azure OpenAI
 * - 其他兼容实现
 *
 * 设计特点：
 * - 构造时配置，运行时无状态
 * - 请求级别可覆盖 model 和 dimensions
 * - 详细的错误处理和日志
 */
@Slf4j
public class OpenAiCompatibleEmbeddingModel implements EmbeddingModel {

    private final WebClient webClient;
    private final String defaultModel;
    private final int defaultDimensions;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造函数
     *
     * @param endpoint   API 端点（如 https://api.openai.com/v1）
     * @param apiKey     API Key
     * @param model      默认模型名称
     * @param dimensions 默认向量维度
     */
    public OpenAiCompatibleEmbeddingModel(String endpoint, String apiKey, String model, int dimensions) {
        this.defaultModel = model;
        this.defaultDimensions = dimensions;

        String baseUrl = normalizeEndpoint(endpoint);

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(16 * 1024 * 1024))
                .build();

        log.info("OpenAiCompatibleEmbeddingModel initialized: endpoint={}, model={}, dimensions={}",
                baseUrl, model, dimensions);
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        if (request == null || request.inputs() == null || request.inputs().isEmpty()) {
            return EmbeddingResponse.of(List.of());
        }

        // 确定使用的 model（请求级 > 默认）
        String model = defaultModel;
        if (request.options() != null && request.options().model() != null) {
            model = request.options().model();
        }

        // 构建请求体
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "input", request.inputs()
        );

        try {
            String responseJson = webClient.post()
                    .uri("/embeddings")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseResponse(responseJson);

        } catch (WebClientResponseException e) {
            log.error("Embedding API error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new EmbeddingException("Embedding API error: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Embedding request failed: {}", e.getMessage(), e);
            throw new EmbeddingException("Embedding request failed: " + e.getMessage(), e);
        }
    }

    @Override
    public int dimensions() {
        return defaultDimensions;
    }

    @Override
    public String modelName() {
        return defaultModel;
    }

    @Override
    public String providerType() {
        return "openai-compatible";
    }

    /**
     * 解析 OpenAI 格式的响应
     */
    private EmbeddingResponse parseResponse(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode dataArray = root.get("data");

            List<Embedding> embeddings = new ArrayList<>();
            for (JsonNode item : dataArray) {
                int index = item.has("index") ? item.get("index").asInt() : embeddings.size();
                JsonNode embeddingNode = item.get("embedding");

                float[] vector = new float[embeddingNode.size()];
                for (int i = 0; i < embeddingNode.size(); i++) {
                    vector[i] = embeddingNode.get(i).floatValue();
                }

                embeddings.add(Embedding.of(vector, index));
            }

            // 解析 usage
            EmbeddingResponse.Usage usage = EmbeddingResponse.Usage.empty();
            if (root.has("usage")) {
                JsonNode usageNode = root.get("usage");
                int promptTokens = usageNode.has("prompt_tokens") ? usageNode.get("prompt_tokens").asInt() : 0;
                int totalTokens = usageNode.has("total_tokens") ? usageNode.get("total_tokens").asInt() : 0;
                usage = EmbeddingResponse.Usage.of(promptTokens, totalTokens);
            }

            return EmbeddingResponse.of(embeddings, usage);

        } catch (Exception e) {
            throw new EmbeddingException("Failed to parse embedding response: " + e.getMessage(), e);
        }
    }

    /**
     * 规范化 endpoint URL
     */
    private String normalizeEndpoint(String endpoint) {
        if (endpoint == null) return "http://localhost:11434/v1";
        endpoint = endpoint.replaceAll("/+$", "");
        // 如果已含 /v* 路径，不再追加
        if (endpoint.matches(".*/(v\\d+)$")) {
            return endpoint;
        }
        return endpoint + "/v1";
    }

    /**
     * Embedding 异常
     */
    public static class EmbeddingException extends RuntimeException {
        public EmbeddingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
