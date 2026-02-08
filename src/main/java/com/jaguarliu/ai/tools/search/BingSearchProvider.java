package com.jaguarliu.ai.tools.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Bing Web Search API 提供商
 * 需要 API Key（Ocp-Apim-Subscription-Key）
 */
@Slf4j
@Component
public class BingSearchProvider implements SearchProvider {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private String apiKey;

    private static final String BING_ENDPOINT = "https://api.bing.microsoft.com/v7.0/search";

    public BingSearchProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    @Override
    public String getType() {
        return "bing";
    }

    @Override
    public String getDisplayName() {
        return "Bing Web Search";
    }

    public void configure(String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public Mono<List<SearchResult>> search(String query, int maxResults) {
        if (!isConfigured()) {
            return Mono.just(List.of());
        }

        return webClient.get()
                .uri(BING_ENDPOINT + "?q={query}&count={count}", query, maxResults)
                .header("Ocp-Apim-Subscription-Key", apiKey)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10))
                .map(body -> parseResults(body, maxResults))
                .onErrorResume(e -> {
                    log.error("Bing search failed for '{}': {}", query, e.getMessage());
                    return Mono.just(List.of());
                });
    }

    private List<SearchResult> parseResults(String body, int maxResults) {
        List<SearchResult> results = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode webPages = root.path("webPages").path("value");
            if (webPages.isArray()) {
                for (int i = 0; i < webPages.size() && results.size() < maxResults; i++) {
                    JsonNode item = webPages.get(i);
                    results.add(SearchResult.builder()
                            .title(item.path("name").asText(""))
                            .url(item.path("url").asText(""))
                            .snippet(item.path("snippet").asText(""))
                            .build());
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse Bing response", e);
        }
        return results;
    }
}
