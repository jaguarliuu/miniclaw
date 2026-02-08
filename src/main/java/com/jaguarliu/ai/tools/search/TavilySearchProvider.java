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
import java.util.Map;

/**
 * Tavily Search API 提供商
 * 需要 API Key
 */
@Slf4j
@Component
public class TavilySearchProvider implements SearchProvider {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private String apiKey;

    private static final String TAVILY_ENDPOINT = "https://api.tavily.com/search";

    public TavilySearchProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    @Override
    public String getType() {
        return "tavily";
    }

    @Override
    public String getDisplayName() {
        return "Tavily";
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

        Map<String, Object> body = Map.of(
                "api_key", apiKey,
                "query", query,
                "max_results", maxResults
        );

        return webClient.post()
                .uri(TAVILY_ENDPOINT)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(15))
                .map(resp -> parseResults(resp, maxResults))
                .onErrorResume(e -> {
                    log.error("Tavily search failed for '{}': {}", query, e.getMessage());
                    return Mono.just(List.of());
                });
    }

    private List<SearchResult> parseResults(String body, int maxResults) {
        List<SearchResult> results = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode items = root.path("results");
            if (items.isArray()) {
                for (int i = 0; i < items.size() && results.size() < maxResults; i++) {
                    JsonNode item = items.get(i);
                    results.add(SearchResult.builder()
                            .title(item.path("title").asText(""))
                            .url(item.path("url").asText(""))
                            .snippet(item.path("content").asText(""))
                            .build());
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse Tavily response", e);
        }
        return results;
    }
}
