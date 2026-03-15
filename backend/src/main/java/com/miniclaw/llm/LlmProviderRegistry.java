package com.miniclaw.llm;

import com.miniclaw.config.LlmProviderConfig;
import com.miniclaw.config.LlmProperties;
import com.miniclaw.llm.model.LlmRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
class LlmProviderRegistry {

    private static final String LEGACY_CLIENT_KEY = "__legacy__";

    private final LlmProperties properties;
    private final Map<String, WebClient> clientCache;

    LlmProviderRegistry(LlmProperties properties) {
        this.properties = properties;
        this.clientCache = new ConcurrentHashMap<>();
        initializeClients();
    }

    ResolvedLlmContext resolve(LlmRequest request) {
        if (clientCache.containsKey(LEGACY_CLIENT_KEY)) {
            return new ResolvedLlmContext(LEGACY_CLIENT_KEY, null, clientCache.get(LEGACY_CLIENT_KEY), true);
        }

        String providerId = resolveProviderId(request);
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalStateException("No default LLM provider configured");
        }

        WebClient client = clientCache.get(providerId);
        if (client == null) {
            throw new IllegalArgumentException("Unknown LLM provider: " + providerId);
        }

        return new ResolvedLlmContext(providerId, properties.getProvider(providerId), client, false);
    }

    private void initializeClients() {
        if (properties.getProviders() != null && !properties.getProviders().isEmpty()) {
            for (LlmProviderConfig provider : properties.getProviders()) {
                if (provider.getId() == null || provider.getId().isBlank()) {
                    continue;
                }
                String endpoint = normalizeEndpoint(provider.getEndpoint());
                clientCache.put(provider.getId(), buildWebClient(endpoint, provider.getApiKey()));
                log.info("LLM provider initialized: id={}, endpoint={}", provider.getId(), endpoint);
            }
            return;
        }

        String endpoint = normalizeEndpoint(properties.getEndpoint());
        clientCache.put(LEGACY_CLIENT_KEY, buildWebClient(endpoint, properties.getApiKey()));
        log.info("LLM Client initialized: endpoint={}, model={}", endpoint, properties.getModel());
    }

    private WebClient buildWebClient(String endpoint, String apiKey) {
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(endpoint)
                .defaultHeader("Content-Type", "application/json");

        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + apiKey);
        }

        return builder.build();
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

    private String resolveProviderId(LlmRequest request) {
        if (request.getProviderId() != null && !request.getProviderId().isBlank()) {
            return request.getProviderId();
        }
        return properties.getDefaultProviderId();
    }
}
