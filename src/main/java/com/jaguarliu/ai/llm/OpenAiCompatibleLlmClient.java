package com.jaguarliu.ai.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.llm.model.LlmChunk;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.llm.model.LlmResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

/**
 * OpenAI 兼容的 LLM 客户端
 * 支持 OpenAI、DeepSeek、通义千问、Ollama 等
 */
@Slf4j
@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

    private final LlmProperties properties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleLlmClient(LlmProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        String endpoint = normalizeEndpoint(properties.getEndpoint());
        this.webClient = WebClient.builder()
                .baseUrl(endpoint)
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
        log.info("LLM Client initialized: endpoint={}, model={}", endpoint, properties.getModel());
    }

    /**
     * 规范化 endpoint 路径
     * - 如果以 /v* 结尾（如 /v1, /v4），保持不变
     * - 否则自动追加 /v1
     */
    private String normalizeEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "http://localhost:11434/v1";
        }

        // 移除末尾的斜杠
        endpoint = endpoint.replaceAll("/+$", "");

        // 检查是否已经以 /v + 数字 结尾
        if (endpoint.matches(".*?/v\\d+$")) {
            return endpoint;
        }

        // 否则追加 /v1
        return endpoint + "/v1";
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        ChatCompletionRequest apiRequest = buildApiRequest(request, false);

        String responseBody = webClient.post()
                .uri("/chat/completions")
                .bodyValue(apiRequest)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(properties.getTimeout()))
                .block();

        return parseResponse(responseBody);
    }

    @Override
    public Flux<LlmChunk> stream(LlmRequest request) {
        ChatCompletionRequest apiRequest = buildApiRequest(request, true);

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(apiRequest)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofSeconds(properties.getTimeout()))
                .filter(line -> !line.isBlank())
                .flatMap(this::parseSseChunk)
                .doOnError(e -> log.error("Stream error", e));
    }

    /**
     * 解析 SSE 数据块
     * 格式: data: {"choices":[{"delta":{"content":"xxx"},"finish_reason":null}]}
     */
    private Flux<LlmChunk> parseSseChunk(String line) {
        // 处理 SSE 格式，可能是 "data: {...}" 或直接是 JSON
        String data = line;
        if (line.startsWith("data:")) {
            data = line.substring(5).trim();
        }

        // 处理结束标记
        if (data.equals("[DONE]") || data.isEmpty()) {
            return Flux.just(LlmChunk.builder().done(true).build());
        }

        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode choices = root.get("choices");

            if (choices == null || choices.isEmpty()) {
                return Flux.empty();
            }

            JsonNode firstChoice = choices.get(0);
            JsonNode delta = firstChoice.get("delta");
            JsonNode finishReasonNode = firstChoice.get("finish_reason");

            String content = null;
            if (delta != null && delta.has("content")) {
                content = delta.get("content").asText();
            }

            String finishReason = null;
            if (finishReasonNode != null && !finishReasonNode.isNull()) {
                finishReason = finishReasonNode.asText();
            }

            boolean isDone = finishReason != null;

            // 如果没有内容且不是结束，跳过
            if (content == null && !isDone) {
                return Flux.empty();
            }

            return Flux.just(LlmChunk.builder()
                    .delta(content)
                    .finishReason(finishReason)
                    .done(isDone)
                    .build());

        } catch (JsonProcessingException e) {
            log.warn("Failed to parse SSE chunk: {}", data, e);
            return Flux.empty();
        }
    }

    /**
     * 构建 API 请求
     */
    private ChatCompletionRequest buildApiRequest(LlmRequest request, boolean stream) {
        List<ChatMessage> messages = request.getMessages().stream()
                .map(m -> new ChatMessage(m.getRole(), m.getContent()))
                .toList();

        return ChatCompletionRequest.builder()
                .model(request.getModel() != null ? request.getModel() : properties.getModel())
                .messages(messages)
                .temperature(request.getTemperature() != null ? request.getTemperature() : properties.getTemperature())
                .maxTokens(request.getMaxTokens() != null ? request.getMaxTokens() : properties.getMaxTokens())
                .stream(stream)
                .build();
    }

    /**
     * 解析同步响应
     */
    private LlmResponse parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.get("choices");

            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("No choices in response");
            }

            JsonNode firstChoice = choices.get(0);
            JsonNode message = firstChoice.get("message");
            String content = message.get("content").asText();
            String finishReason = firstChoice.has("finish_reason") ?
                    firstChoice.get("finish_reason").asText() : null;

            LlmResponse.Usage usage = null;
            if (root.has("usage")) {
                JsonNode usageNode = root.get("usage");
                usage = LlmResponse.Usage.builder()
                        .promptTokens(usageNode.get("prompt_tokens").asInt())
                        .completionTokens(usageNode.get("completion_tokens").asInt())
                        .totalTokens(usageNode.get("total_tokens").asInt())
                        .build();
            }

            return LlmResponse.builder()
                    .content(content)
                    .finishReason(finishReason)
                    .usage(usage)
                    .build();

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse LLM response", e);
        }
    }

    /**
     * OpenAI API 请求格式
     */
    @Data
    @lombok.Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class ChatCompletionRequest {
        private String model;
        private List<ChatMessage> messages;
        private Double temperature;
        @JsonProperty("max_tokens")
        private Integer maxTokens;
        private Boolean stream;
    }

    @Data
    @lombok.AllArgsConstructor
    static class ChatMessage {
        private String role;
        private String content;
    }
}
