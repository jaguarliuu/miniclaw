package com.miniclaw.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniclaw.config.LlmProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Slf4j
class LlmExecutionSupport {

    private static final double RETRY_JITTER = 0.2d;

    private final LlmProperties properties;
    private final ObjectMapper objectMapper;

    LlmExecutionSupport(LlmProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    Mono<String> executeChat(ResolvedLlmContext context, OpenAiChatCompletionRequest request) {
        Mono<String> pipeline = context.getClient().post()
                .uri("/chat/completions")
                .bodyValue(request)
                .exchangeToMono(this::readChatBody)
                .timeout(Duration.ofSeconds(properties.getTimeout()))
                .onErrorMap(this::asLlmException);

        return applyRetry(pipeline, "chat");
    }

    Flux<String> executeStream(ResolvedLlmContext context, OpenAiChatCompletionRequest request) {
        Flux<String> pipeline = context.getClient().post()
                .uri("/chat/completions")
                .bodyValue(request)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchangeToFlux(this::readStreamBody)
                .timeout(Duration.ofSeconds(properties.getTimeout()))
                .filter(line -> !line.isBlank())
                .onErrorMap(this::asLlmException);

        return applyRetry(pipeline, "stream");
    }

    LlmException asLlmException(Throwable throwable) {
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
}
