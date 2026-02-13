package com.jaguarliu.ai.gateway.rpc.handler.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.llm.LlmConfigService;
import com.jaguarliu.ai.llm.model.LlmProviderConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LLM 配置获取 — llm.config.get
 * 返回多 Provider 配置结构
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
                RpcResponse.success(request.getId(), llmConfigService.getMultiConfig())
        );
    }
}

/**
 * LLM 配置保存 — llm.config.save
 * 向后兼容单 provider 保存
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

/**
 * LLM Provider 添加 — llm.provider.add
 */
@Slf4j
@Component
@RequiredArgsConstructor
class LlmProviderAddHandler implements RpcHandler {

    private final LlmConfigService llmConfigService;

    @Override
    public String getMethod() {
        return "llm.provider.add";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> params = extractParams(request.getPayload());
            String name = (String) params.get("name");
            String endpoint = (String) params.get("endpoint");
            String apiKey = (String) params.get("apiKey");
            String id = (String) params.get("id");

            if (endpoint == null || endpoint.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAM", "endpoint is required");
            }
            if (apiKey == null || apiKey.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAM", "apiKey is required");
            }

            @SuppressWarnings("unchecked")
            List<String> models = params.get("models") instanceof List
                    ? (List<String>) params.get("models")
                    : new ArrayList<>();

            LlmProviderConfig config = LlmProviderConfig.builder()
                    .id(id)
                    .name(name != null ? name : "Provider")
                    .endpoint(endpoint)
                    .apiKey(apiKey)
                    .models(models)
                    .build();

            String providerId = llmConfigService.addProvider(config);
            return RpcResponse.success(request.getId(), Map.of("providerId", providerId));
        }).onErrorResume(e -> {
            log.error("Failed to add provider", e);
            return Mono.just(RpcResponse.error(request.getId(), "ADD_FAILED", e.getMessage()));
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractParams(Object payload) {
        if (payload instanceof Map) return (Map<String, Object>) payload;
        throw new IllegalArgumentException("Invalid payload format");
    }
}

/**
 * LLM Provider 更新 — llm.provider.update
 */
@Slf4j
@Component
@RequiredArgsConstructor
class LlmProviderUpdateHandler implements RpcHandler {

    private final LlmConfigService llmConfigService;

    @Override
    public String getMethod() {
        return "llm.provider.update";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> params = extractParams(request.getPayload());
            String providerId = (String) params.get("providerId");

            if (providerId == null || providerId.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAM", "providerId is required");
            }

            String name = (String) params.get("name");
            String endpoint = (String) params.get("endpoint");
            String apiKey = (String) params.get("apiKey");

            @SuppressWarnings("unchecked")
            List<String> models = params.get("models") instanceof List
                    ? (List<String>) params.get("models")
                    : null;

            llmConfigService.updateProvider(providerId, name, endpoint, apiKey, models);
            return RpcResponse.success(request.getId(), Map.of("success", true));
        }).onErrorResume(e -> {
            log.error("Failed to update provider", e);
            return Mono.just(RpcResponse.error(request.getId(), "UPDATE_FAILED", e.getMessage()));
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractParams(Object payload) {
        if (payload instanceof Map) return (Map<String, Object>) payload;
        throw new IllegalArgumentException("Invalid payload format");
    }
}

/**
 * LLM Provider 删除 — llm.provider.remove
 */
@Slf4j
@Component
@RequiredArgsConstructor
class LlmProviderRemoveHandler implements RpcHandler {

    private final LlmConfigService llmConfigService;

    @Override
    public String getMethod() {
        return "llm.provider.remove";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> params = extractParams(request.getPayload());
            String providerId = (String) params.get("providerId");

            if (providerId == null || providerId.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAM", "providerId is required");
            }

            llmConfigService.removeProvider(providerId);
            return RpcResponse.success(request.getId(), Map.of("success", true));
        }).onErrorResume(e -> {
            log.error("Failed to remove provider", e);
            return Mono.just(RpcResponse.error(request.getId(), "REMOVE_FAILED", e.getMessage()));
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractParams(Object payload) {
        if (payload instanceof Map) return (Map<String, Object>) payload;
        throw new IllegalArgumentException("Invalid payload format");
    }
}

/**
 * LLM 默认模型设置 — llm.config.setDefault
 */
@Slf4j
@Component
@RequiredArgsConstructor
class LlmDefaultModelSetHandler implements RpcHandler {

    private final LlmConfigService llmConfigService;

    @Override
    public String getMethod() {
        return "llm.config.setDefault";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> params = extractParams(request.getPayload());
            String defaultModel = (String) params.get("defaultModel");

            if (defaultModel == null || defaultModel.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAM", "defaultModel is required");
            }

            llmConfigService.setDefaultModel(defaultModel);
            return RpcResponse.success(request.getId(), Map.of("success", true));
        }).onErrorResume(e -> {
            log.error("Failed to set default model", e);
            return Mono.just(RpcResponse.error(request.getId(), "SET_DEFAULT_FAILED", e.getMessage()));
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractParams(Object payload) {
        if (payload instanceof Map) return (Map<String, Object>) payload;
        throw new IllegalArgumentException("Invalid payload format");
    }
}
