package com.miniclaw.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniclaw.config.LlmProperties;
import com.miniclaw.llm.model.LlmChunk;
import com.miniclaw.llm.model.LlmRequest;
import com.miniclaw.llm.model.LlmResponse;
import com.miniclaw.llm.model.ToolCall;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final double RETRY_JITTER = 0.2d;

    private final LlmProperties properties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleLlmClient(LlmProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webClient = buildWebClient();

        log.info("LLM Client initialized: endpoint={}, model={}",
                properties.getEndpoint(), properties.getModel());
    }

    private WebClient buildWebClient() {
        String endpoint = normalizeEndpoint(properties.getEndpoint());

        return WebClient.builder()
                .baseUrl(endpoint)
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

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
        try {
            LlmResponse response = executeChat(request).block();
            if (response == null) {
                throw new LlmException(LlmErrorType.INVALID_RESPONSE, false, null, "LLM returned an empty response");
            }
            return response;
        } catch (RuntimeException e) {
            LlmException failure = asLlmException(e);
            log.error("LLM chat request failed: type={}, status={}, retryable={}, message={}",
                    failure.getErrorType(), failure.getHttpStatus(), failure.isRetryable(), failure.getMessage());
            throw failure;
        }
    }

    @Override
    public Flux<LlmChunk> stream(LlmRequest request) {
        ChatCompletionRequest apiRequest = buildApiRequest(request, true);

        Flux<LlmChunk> pipeline = webClient.post()
                .uri("/chat/completions")
                .bodyValue(apiRequest)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchangeToFlux(this::readStreamBody)
                .timeout(Duration.ofSeconds(properties.getTimeout()))
                .filter(line -> !line.isBlank())
                .flatMap(this::parseSseChunk)
                .onErrorMap(this::asLlmException);

        return applyRetry(pipeline, "stream")
                .doOnError(e -> {
                    LlmException failure = asLlmException(e);
                    log.error("LLM stream request failed: type={}, status={}, retryable={}, message={}",
                            failure.getErrorType(), failure.getHttpStatus(), failure.isRetryable(), failure.getMessage());
                });
    }

    private Mono<LlmResponse> executeChat(LlmRequest request) {
        ChatCompletionRequest apiRequest = buildApiRequest(request, false);

        Mono<LlmResponse> pipeline = webClient.post()
                .uri("/chat/completions")
                .bodyValue(apiRequest)
                .exchangeToMono(this::readChatBody)
                .timeout(Duration.ofSeconds(properties.getTimeout()))
                .map(this::parseResponse)
                .onErrorMap(this::asLlmException);

        return applyRetry(pipeline, "chat");
    }

    private Mono<String> readChatBody(ClientResponse response) {
        if (response.statusCode().isError()) {
            return response.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .flatMap(body -> Mono.error(toHttpException(response.statusCode(), body)));
        }
        return response.bodyToMono(String.class);
    }

    private Flux<String> readStreamBody(ClientResponse response) {
        if (response.statusCode().isError()) {
            return response.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .flatMapMany(body -> Flux.error(toHttpException(response.statusCode(), body)));
        }
        return response.bodyToFlux(String.class);
    }

    private Flux<LlmChunk> parseSseChunk(String line) {
        String data = line;
        if (line.startsWith("data:")) {
            data = line.substring(5).trim();
        }

        if (data.isEmpty()) {
            return Flux.empty();
        }

        if ("[DONE]".equals(data)) {
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

            boolean done = finishReason != null;
            if (content == null && !done) {
                return Flux.empty();
            }

            return Flux.just(LlmChunk.builder()
                    .delta(content)
                    .finishReason(finishReason)
                    .done(done)
                    .build());
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse SSE chunk: {}", data);
            return Flux.empty();
        }
    }

    private ChatCompletionRequest buildApiRequest(LlmRequest request, boolean stream) {
        List<ChatMessage> messages = request.getMessages().stream()
                .map(this::convertMessage)
                .toList();

        String model = request.getModel() != null
                ? request.getModel()
                : properties.getModel();

        ChatCompletionRequest.ChatCompletionRequestBuilder builder = ChatCompletionRequest.builder()
                .model(model)
                .messages(messages)
                .temperature(request.getTemperature() != null
                        ? request.getTemperature()
                        : properties.getTemperature())
                .maxTokens(request.getMaxTokens() != null
                        ? request.getMaxTokens()
                        : properties.getMaxTokens())
                .stream(stream);

        if (request.getTools() != null && !request.getTools().isEmpty()) {
            builder.tools(request.getTools());
            builder.toolChoice(request.getToolChoice() != null
                    ? request.getToolChoice()
                    : "auto");
        }

        return builder.build();
    }

    private ChatMessage convertMessage(LlmRequest.Message message) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setRole(message.getRole());
        chatMessage.setContent(message.getContent());

        if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
            List<ChatToolCall> chatToolCalls = message.getToolCalls().stream()
                    .map(toolCall -> {
                        ChatToolCall chatToolCall = new ChatToolCall();
                        chatToolCall.setId(toolCall.getId());
                        chatToolCall.setType(toolCall.getType());
                        chatToolCall.setFunction(new ChatToolCall.Function(
                                toolCall.getFunction().getName(),
                                toolCall.getFunction().getArguments()));
                        return chatToolCall;
                    })
                    .toList();
            chatMessage.setToolCalls(chatToolCalls);
        }

        if ("tool".equals(message.getRole())) {
            chatMessage.setToolCallId(message.getToolCallId());
        }

        return chatMessage;
    }

    private LlmResponse parseResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new LlmException(LlmErrorType.INVALID_RESPONSE, false, null, "LLM returned an empty response body");
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.get("choices");

            if (choices == null || choices.isEmpty()) {
                throw new LlmException(LlmErrorType.INVALID_RESPONSE, false, null, "LLM response did not contain choices");
            }

            JsonNode firstChoice = choices.get(0);
            JsonNode message = firstChoice.get("message");
            if (message == null || message.isNull()) {
                throw new LlmException(LlmErrorType.INVALID_RESPONSE, false, null, "LLM response did not contain a message");
            }

            String content = null;
            if (message.has("content") && !message.get("content").isNull()) {
                content = message.get("content").asText();
            }

            String finishReason = firstChoice.has("finish_reason")
                    ? firstChoice.get("finish_reason").asText()
                    : null;

            List<ToolCall> toolCalls = null;
            if (message.has("tool_calls")) {
                toolCalls = new ArrayList<>();
                for (JsonNode tcNode : message.get("tool_calls")) {
                    ToolCall toolCall = ToolCall.builder()
                            .id(tcNode.get("id").asText())
                            .type(tcNode.has("type")
                                    ? tcNode.get("type").asText()
                                    : "function")
                            .function(ToolCall.FunctionCall.builder()
                                    .name(tcNode.get("function").get("name").asText())
                                    .arguments(tcNode.get("function").get("arguments").asText())
                                    .build())
                            .build();
                    toolCalls.add(toolCall);
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
            throw new LlmException(LlmErrorType.INVALID_RESPONSE, false, null, "Failed to parse LLM response", e);
        }
    }

    private LlmException toHttpException(HttpStatusCode status, String responseBody) {
        int statusCode = status.value();
        String detail = extractErrorMessage(responseBody);

        return switch (statusCode) {
            case 400 -> new LlmException(LlmErrorType.BAD_REQUEST, false, statusCode,
                    "LLM request was rejected: " + detail);
            case 401 -> new LlmException(LlmErrorType.AUTHENTICATION, false, statusCode,
                    "LLM authentication failed: " + detail);
            case 403 -> new LlmException(LlmErrorType.PERMISSION_DENIED, false, statusCode,
                    "LLM request was forbidden: " + detail);
            case 404 -> new LlmException(LlmErrorType.NOT_FOUND, false, statusCode,
                    "LLM endpoint or model was not found: " + detail);
            case 408 -> new LlmException(LlmErrorType.TIMEOUT, true, statusCode,
                    "LLM request timed out: " + detail);
            case 429 -> new LlmException(LlmErrorType.RATE_LIMIT, true, statusCode,
                    "LLM rate limit exceeded: " + detail);
            default -> {
                boolean retryable = statusCode >= 500;
                yield new LlmException(
                        retryable ? LlmErrorType.SERVER_ERROR : LlmErrorType.UNKNOWN,
                        retryable,
                        statusCode,
                        "LLM request failed with HTTP " + statusCode + ": " + detail
                );
            }
        };
    }

    private String extractErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "no error payload";
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode error = root.get("error");
            if (error != null && error.has("message") && !error.get("message").isNull()) {
                return error.get("message").asText();
            }
        } catch (JsonProcessingException ignored) {
            // Fall back to the raw body.
        }

        return responseBody;
    }

    private LlmException asLlmException(Throwable throwable) {
        Throwable failure = Exceptions.unwrap(throwable);
        if (failure instanceof LlmException llmException) {
            return llmException;
        }

        if (failure instanceof WebClientRequestException requestException) {
            if (hasCause(requestException, TimeoutException.class) || hasCause(requestException, SocketTimeoutException.class)) {
                return new LlmException(LlmErrorType.TIMEOUT, true, null,
                        "LLM request timed out", requestException);
            }

            return new LlmException(LlmErrorType.NETWORK, true, null,
                    "LLM network request failed: " + rootMessage(requestException), requestException);
        }

        if (failure instanceof TimeoutException || failure instanceof SocketTimeoutException) {
            return new LlmException(LlmErrorType.TIMEOUT, true, null,
                    "LLM request timed out", failure);
        }

        return new LlmException(LlmErrorType.UNKNOWN, false, null,
                "LLM request failed: " + rootMessage(failure), failure);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() != null ? current.getMessage() : current.getClass().getSimpleName();
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private <T> Mono<T> applyRetry(Mono<T> pipeline, String operation) {
        if (maxRetries() <= 0) {
            return pipeline;
        }
        return pipeline.retryWhen(buildRetrySpec(operation));
    }

    private <T> Flux<T> applyRetry(Flux<T> pipeline, String operation) {
        if (maxRetries() <= 0) {
            return pipeline;
        }
        return pipeline.retryWhen(buildRetrySpec(operation));
    }

    private RetryBackoffSpec buildRetrySpec(String operation) {
        return Retry.backoff(maxRetries(), Duration.ofMillis(minBackoffMillis()))
                .maxBackoff(Duration.ofMillis(maxBackoffMillis()))
                .jitter(RETRY_JITTER)
                .filter(this::isRetryableFailure)
                .doBeforeRetry(signal -> {
                    LlmException failure = asLlmException(signal.failure());
                    log.warn("Retrying LLM {} request: attempt={}/{}, type={}, status={}, message={}",
                            operation,
                            signal.totalRetriesInARow() + 1,
                            maxRetries(),
                            failure.getErrorType(),
                            failure.getHttpStatus(),
                            failure.getMessage());
                })
                .onRetryExhaustedThrow((spec, signal) -> signal.failure());
    }

    private boolean isRetryableFailure(Throwable throwable) {
        return asLlmException(throwable).isRetryable();
    }

    private int maxRetries() {
        return properties.getMaxRetries() != null ? properties.getMaxRetries() : 0;
    }

    private long minBackoffMillis() {
        return properties.getRetryMinBackoffMillis() != null ? properties.getRetryMinBackoffMillis() : 200L;
    }

    private long maxBackoffMillis() {
        return properties.getRetryMaxBackoffMillis() != null ? properties.getRetryMaxBackoffMillis() : 2000L;
    }

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
