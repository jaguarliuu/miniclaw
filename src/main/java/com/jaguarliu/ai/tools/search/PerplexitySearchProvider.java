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
 * Perplexity Search API 提供商
 * 使用 Chat Completions 端点 + 搜索模型
 * 需要 API Key
 */
@Slf4j
@Component
public class PerplexitySearchProvider implements SearchProvider {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private String apiKey;

    private static final String PERPLEXITY_ENDPOINT = "https://api.perplexity.ai/chat/completions";

    public PerplexitySearchProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    @Override
    public String getType() {
        return "perplexity";
    }

    @Override
    public String getDisplayName() {
        return "Perplexity";
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
                "model", "sonar",
                "messages", List.of(Map.of("role", "user", "content", query)),
                "max_tokens", 1024
        );

        return webClient.post()
                .uri(PERPLEXITY_ENDPOINT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .map(resp -> parseResults(resp, query))
                .onErrorResume(e -> {
                    log.error("Perplexity search failed for '{}': {}", query, e.getMessage());
                    return Mono.just(List.of());
                });
    }

    private List<SearchResult> parseResults(String body, String query) {
        List<SearchResult> results = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                String content = choices.get(0).path("message").path("content").asText("");
                if (!content.isBlank()) {
                    // Perplexity 返回的是综合回答，包装为单条结果
                    results.add(SearchResult.builder()
                            .title("Perplexity Answer: " + query)
                            .url("https://www.perplexity.ai/search?q=" + query.replace(" ", "+"))
                            .snippet(content.length() > 500 ? content.substring(0, 500) + "..." : content)
                            .build());
                }
            }

            // 提取引用链接（如果有）
            JsonNode citations = root.path("citations");
            if (citations.isArray()) {
                for (int i = 0; i < citations.size(); i++) {
                    String url = citations.get(i).asText("");
                    if (!url.isBlank()) {
                        results.add(SearchResult.builder()
                                .title("Source " + (i + 1))
                                .url(url)
                                .snippet("")
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse Perplexity response", e);
        }
        return results;
    }
}
