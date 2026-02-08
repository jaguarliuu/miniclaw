package com.jaguarliu.ai.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.llm.model.LlmChunk;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.llm.model.LlmResponse;
import com.jaguarliu.ai.llm.model.ToolCall;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容的 LLM 客户端
 * 支持 OpenAI、DeepSeek、通义千问、Ollama 等
 * 支持 Function Calling
 */
@Slf4j
@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

    private final LlmProperties properties;
    private volatile WebClient webClient;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleLlmClient(LlmProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
            String endpoint = normalizeEndpoint(properties.getEndpoint());
            this.webClient = buildWebClient(endpoint, properties.getApiKey());
            log.info("LLM Client initialized: endpoint={}, model={}", endpoint, properties.getModel());
        } else {
            log.info("LLM Client created without endpoint — waiting for configuration");
        }
    }

    /**
     * 运行时重新配置 LLM Client（热更新）
     */
    public void reconfigure(String endpoint, String apiKey) {
        String normalizedEndpoint = normalizeEndpoint(endpoint);
        this.webClient = buildWebClient(normalizedEndpoint, apiKey);
        log.info("LLM Client reconfigured: endpoint={}", normalizedEndpoint);
    }

    private WebClient buildWebClient(String endpoint, String apiKey) {
        return WebClient.builder()
                .baseUrl(endpoint)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * 规范化 endpoint 路径
     */
    private String normalizeEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "http://localhost:11434/v1";
        }
        endpoint = endpoint.replaceAll("/+$", "");
        if (endpoint.matches(".*?/v\\d+$")) {
            return endpoint;
        }
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

        // 用于累积 tool_calls（流式模式下 arguments 是分片到达的）
        Map<Integer, ToolCallAccumulator> toolCallAccumulators = new HashMap<>();

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(apiRequest)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofSeconds(properties.getTimeout()))
                .filter(line -> !line.isBlank())
                .flatMap(line -> parseSseChunk(line, toolCallAccumulators))
                .doOnError(e -> log.error("Stream error", e));
    }

    /**
     * 解析 SSE 数据块
     */
    private Flux<LlmChunk> parseSseChunk(String line, Map<Integer, ToolCallAccumulator> accumulators) {
        String data = line;
        if (line.startsWith("data:")) {
            data = line.substring(5).trim();
        }

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
            if (delta != null && delta.has("content") && !delta.get("content").isNull()) {
                content = delta.get("content").asText();
            }

            String finishReason = null;
            if (finishReasonNode != null && !finishReasonNode.isNull()) {
                finishReason = finishReasonNode.asText();
            }

            boolean isDone = finishReason != null;

            // 构建 chunk
            LlmChunk.LlmChunkBuilder chunkBuilder = LlmChunk.builder()
                    .delta(content)
                    .finishReason(finishReason)
                    .done(isDone);

            // 解析 tool_calls delta
            if (delta != null && delta.has("tool_calls")) {
                parseToolCallsDelta(delta.get("tool_calls"), accumulators);

                // 附加 delta 信息到 chunk（用于 artifact 流式提取）
                JsonNode firstTc = delta.get("tool_calls").get(0);
                if (firstTc != null) {
                    int idx = firstTc.has("index") ? firstTc.get("index").asInt() : 0;
                    ToolCallAccumulator acc = accumulators.get(idx);
                    if (acc != null && acc.functionName != null) {
                        chunkBuilder.toolCallFunctionName(acc.functionName);
                    }
                    if (firstTc.has("function") && firstTc.get("function").has("arguments")) {
                        chunkBuilder.toolCallArgumentsDelta(
                                firstTc.get("function").get("arguments").asText());
                    }
                }
            }

            // 如果是 tool_calls 结束，附带完整的 tool_calls
            if ("tool_calls".equals(finishReason) && !accumulators.isEmpty()) {
                List<ToolCall> toolCalls = accumulators.values().stream()
                        .map(ToolCallAccumulator::build)
                        .toList();
                chunkBuilder.toolCalls(toolCalls);
                log.debug("Tool calls completed: {}", toolCalls.size());
            }

            // 如果没有内容、没有 tool_calls、不是结束，跳过
            if (content == null && !isDone && accumulators.isEmpty()) {
                return Flux.empty();
            }

            return Flux.just(chunkBuilder.build());

        } catch (JsonProcessingException e) {
            log.warn("Failed to parse SSE chunk: {}", data, e);
            return Flux.empty();
        }
    }

    /**
     * 解析 tool_calls 增量并累积
     */
    private void parseToolCallsDelta(JsonNode toolCallsNode, Map<Integer, ToolCallAccumulator> accumulators) {
        for (JsonNode tcNode : toolCallsNode) {
            int index = tcNode.has("index") ? tcNode.get("index").asInt() : 0;

            ToolCallAccumulator acc = accumulators.computeIfAbsent(index, k -> new ToolCallAccumulator());

            if (tcNode.has("id")) {
                acc.id = tcNode.get("id").asText();
            }
            if (tcNode.has("type")) {
                acc.type = tcNode.get("type").asText();
            }
            if (tcNode.has("function")) {
                JsonNode funcNode = tcNode.get("function");
                if (funcNode.has("name")) {
                    acc.functionName = funcNode.get("name").asText();
                }
                if (funcNode.has("arguments")) {
                    acc.arguments.append(funcNode.get("arguments").asText());
                }
            }
        }
    }

    /**
     * 构建 API 请求
     */
    private ChatCompletionRequest buildApiRequest(LlmRequest request, boolean stream) {
        List<ChatMessage> messages = request.getMessages().stream()
                .map(this::convertMessage)
                .toList();

        ChatCompletionRequest.ChatCompletionRequestBuilder builder = ChatCompletionRequest.builder()
                .model(request.getModel() != null ? request.getModel() : properties.getModel())
                .messages(messages)
                .temperature(request.getTemperature() != null ? request.getTemperature() : properties.getTemperature())
                .maxTokens(request.getMaxTokens() != null ? request.getMaxTokens() : properties.getMaxTokens())
                .stream(stream);

        // 添加 tools
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            builder.tools(request.getTools());
            builder.toolChoice(request.getToolChoice() != null ? request.getToolChoice() : "auto");
        }

        return builder.build();
    }

    /**
     * 转换消息格式
     */
    private ChatMessage convertMessage(LlmRequest.Message m) {
        ChatMessage msg = new ChatMessage();
        msg.setRole(m.getRole());
        msg.setContent(m.getContent());

        // assistant 消息可能有 tool_calls
        if (m.getToolCalls() != null && !m.getToolCalls().isEmpty()) {
            List<ChatToolCall> chatToolCalls = m.getToolCalls().stream()
                    .map(tc -> {
                        ChatToolCall ctc = new ChatToolCall();
                        ctc.setId(tc.getId());
                        ctc.setType(tc.getType());
                        ctc.setFunction(new ChatToolCall.Function(tc.getName(), tc.getArguments()));
                        return ctc;
                    })
                    .toList();
            msg.setToolCalls(chatToolCalls);
        }

        // tool 消息有 tool_call_id
        if ("tool".equals(m.getRole())) {
            msg.setToolCallId(m.getToolCallId());
        }

        return msg;
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

            String content = null;
            if (message.has("content") && !message.get("content").isNull()) {
                content = message.get("content").asText();
            }

            String finishReason = firstChoice.has("finish_reason") ?
                    firstChoice.get("finish_reason").asText() : null;

            // 解析 tool_calls
            List<ToolCall> toolCalls = null;
            if (message.has("tool_calls")) {
                toolCalls = new ArrayList<>();
                for (JsonNode tcNode : message.get("tool_calls")) {
                    ToolCall tc = ToolCall.builder()
                            .id(tcNode.get("id").asText())
                            .type(tcNode.has("type") ? tcNode.get("type").asText() : "function")
                            .function(ToolCall.FunctionCall.builder()
                                    .name(tcNode.get("function").get("name").asText())
                                    .arguments(tcNode.get("function").get("arguments").asText())
                                    .build())
                            .build();
                    toolCalls.add(tc);
                }
            }

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
                    .toolCalls(toolCalls)
                    .finishReason(finishReason)
                    .usage(usage)
                    .build();

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse LLM response", e);
        }
    }

    /**
     * Tool Call 累积器（用于流式模式下累积分片的 arguments）
     */
    private static class ToolCallAccumulator {
        String id;
        String type = "function";
        String functionName;
        StringBuilder arguments = new StringBuilder();

        ToolCall build() {
            return ToolCall.builder()
                    .id(id)
                    .type(type)
                    .function(ToolCall.FunctionCall.builder()
                            .name(functionName)
                            .arguments(arguments.toString())
                            .build())
                    .build();
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
        private List<Map<String, Object>> tools;
        @JsonProperty("tool_choice")
        private String toolChoice;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class ChatMessage {
        private String role;
        private String content;
        @JsonProperty("tool_calls")
        private List<ChatToolCall> toolCalls;
        @JsonProperty("tool_call_id")
        private String toolCallId;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class ChatToolCall {
        private String id;
        private String type;
        private Function function;

        @Data
        @lombok.AllArgsConstructor
        @lombok.NoArgsConstructor
        static class Function {
            private String name;
            private String arguments;
        }
    }
}
