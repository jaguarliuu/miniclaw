package com.jaguarliu.ai.gateway.rpc.handler.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.llm.LlmConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

/**
 * LLM 配置获取 — llm.config.get
 */
@Slf4j
@Component
@RequiredArgsConstructor
class LlmConfigGetHandler implements RpcHandler {

    private final LlmConfigService llmConfigService;

    @Override
    public String getMethod() {
        return "llm.config.get";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() ->
                RpcResponse.success(request.getId(), llmConfigService.getConfig())
        );
    }
}

/**
 * LLM 配置保存 — llm.config.save
 */
@Slf4j
@Component
@RequiredArgsConstructor
class LlmConfigSaveHandler implements RpcHandler {

    private final LlmConfigService llmConfigService;

    @Override
    public String getMethod() {
        return "llm.config.save";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> params = extractParams(request.getPayload());
            String endpoint = (String) params.get("endpoint");
            String apiKey = (String) params.get("apiKey");
            String model = (String) params.get("model");

            if (endpoint == null || endpoint.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAM", "endpoint is required");
            }
            if (apiKey == null || apiKey.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAM", "apiKey is required");
            }
            if (model == null || model.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAM", "model is required");
            }

            llmConfigService.saveConfig(endpoint, apiKey, model);
            return RpcResponse.success(request.getId(), Map.of("success", true));
        }).onErrorResume(e -> {
            log.error("Failed to save LLM config", e);
            return Mono.just(RpcResponse.error(request.getId(), "SAVE_FAILED", e.getMessage()));
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractParams(Object payload) {
        if (payload instanceof Map) {
            return (Map<String, Object>) payload;
        }
        throw new IllegalArgumentException("Invalid payload format");
    }
}

/**
 * LLM 配置测试 — llm.config.test
 * 用传入参数临时创建 WebClient 测试连通性（不保存配置）
 */
@Slf4j
@Component
@RequiredArgsConstructor
class LlmConfigTestHandler implements RpcHandler {

    private final ObjectMapper objectMapper;

    @Override
    public String getMethod() {
        return "llm.config.test";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> params = extractParams(request.getPayload());
            String endpoint = (String) params.get("endpoint");
            String apiKey = (String) params.get("apiKey");
            String model = (String) params.get("model");

            if (endpoint == null || endpoint.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAM", "endpoint is required");
            }
            if (apiKey == null || apiKey.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAM", "apiKey is required");
            }
            if (model == null || model.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAM", "model is required");
            }

            String normalizedEndpoint = normalizeEndpoint(endpoint);

            WebClient tempClient = WebClient.builder()
                    .baseUrl(normalizedEndpoint)
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .defaultHeader("Content-Type", "application/json")
                    .build();

            // 构建一个极简的 chat completion 请求
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", java.util.List.of(Map.of("role", "user", "content", "hi")),
                    "max_tokens", 5
            );

            long start = System.currentTimeMillis();

            String response = tempClient.post()
                    .uri("/chat/completions")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofSeconds(10))
                    .block();

            long latencyMs = System.currentTimeMillis() - start;

            // 验证响应包含 choices
            var root = objectMapper.readTree(response);
            if (root.has("choices") && !root.get("choices").isEmpty()) {
                return RpcResponse.success(request.getId(), Map.of(
                        "success", true,
                        "message", "Connection successful",
                        "latencyMs", latencyMs
                ));
            } else {
                return RpcResponse.success(request.getId(), Map.of(
                        "success", false,
                        "message", "Unexpected response format"
                ));
            }
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> {
              log.error("LLM config test failed", e);
              String message = e.getMessage() != null ? e.getMessage() : "Connection failed";
              return Mono.just(RpcResponse.success(request.getId(), Map.of(
                      "success", false,
                      "message", message
              )));
          });
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractParams(Object payload) {
        if (payload instanceof Map) {
            return (Map<String, Object>) payload;
        }
        throw new IllegalArgumentException("Invalid payload format");
    }
}
