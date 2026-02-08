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
 * GitHub Search API 提供商
 * API Key 可选（无 Key 时有速率限制）
 */
@Slf4j
@Component
public class GithubSearchProvider implements SearchProvider {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private String apiKey;

    private static final String GITHUB_SEARCH_ENDPOINT = "https://api.github.com/search/repositories";

    public GithubSearchProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .defaultHeader("Accept", "application/vnd.github.v3+json")
                .defaultHeader("User-Agent", "MiniClaw-Agent")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    @Override
    public String getType() {
        return "github";
    }

    @Override
    public String getDisplayName() {
        return "GitHub Search";
    }

    public void configure(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public Mono<List<SearchResult>> search(String query, int maxResults) {
        var requestSpec = webClient.get()
                .uri(GITHUB_SEARCH_ENDPOINT + "?q={query}&per_page={count}&sort=stars", query, maxResults);

        if (apiKey != null && !apiKey.isBlank()) {
            requestSpec = requestSpec.header("Authorization", "token " + apiKey);
        }

        return requestSpec
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10))
                .map(body -> parseResults(body, maxResults))
                .onErrorResume(e -> {
                    log.error("GitHub search failed for '{}': {}", query, e.getMessage());
                    return Mono.just(List.of());
                });
    }

    private List<SearchResult> parseResults(String body, int maxResults) {
        List<SearchResult> results = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode items = root.path("items");
            if (items.isArray()) {
                for (int i = 0; i < items.size() && results.size() < maxResults; i++) {
                    JsonNode item = items.get(i);
                    String fullName = item.path("full_name").asText("");
                    String desc = item.path("description").asText("");
                    String htmlUrl = item.path("html_url").asText("");
                    int stars = item.path("stargazers_count").asInt(0);
                    String language = item.path("language").asText("");

                    String snippet = desc;
                    if (!language.isBlank() || stars > 0) {
                        snippet += " [" + language + " | " + stars + " stars]";
                    }

                    results.add(SearchResult.builder()
                            .title(fullName)
                            .url(htmlUrl)
                            .snippet(snippet)
                            .build());
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse GitHub response", e);
        }
        return results;
    }
}
