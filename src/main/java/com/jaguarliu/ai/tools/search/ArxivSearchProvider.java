package com.jaguarliu.ai.tools.search;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * arXiv API 搜索提供商（免费，无需 API Key）
 * 使用 Atom XML 格式的 arXiv 查询接口
 */
@Slf4j
@Component
public class ArxivSearchProvider implements SearchProvider {

    private final WebClient webClient;

    private static final String ARXIV_ENDPOINT = "https://export.arxiv.org/api/query";

    // 简单 XML 解析模式
    private static final Pattern ENTRY_PATTERN = Pattern.compile("<entry>(.*?)</entry>", Pattern.DOTALL);
    private static final Pattern TITLE_PATTERN = Pattern.compile("<title>(.*?)</title>", Pattern.DOTALL);
    private static final Pattern SUMMARY_PATTERN = Pattern.compile("<summary>(.*?)</summary>", Pattern.DOTALL);
    private static final Pattern LINK_PATTERN = Pattern.compile("<id>(.*?)</id>", Pattern.DOTALL);

    public ArxivSearchProvider() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    @Override
    public String getType() {
        return "arxiv";
    }

    @Override
    public String getDisplayName() {
        return "arXiv";
    }

    @Override
    public Mono<List<SearchResult>> search(String query, int maxResults) {
        String encoded = URLEncoder.encode("all:" + query, StandardCharsets.UTF_8);
        String url = ARXIV_ENDPOINT + "?search_query=" + encoded + "&start=0&max_results=" + maxResults;

        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(15))
                .map(xml -> parseResults(xml, maxResults))
                .onErrorResume(e -> {
                    log.error("arXiv search failed for '{}': {}", query, e.getMessage());
                    return Mono.just(List.of());
                });
    }

    private List<SearchResult> parseResults(String xml, int maxResults) {
        List<SearchResult> results = new ArrayList<>();
        Matcher entryMatcher = ENTRY_PATTERN.matcher(xml);

        while (entryMatcher.find() && results.size() < maxResults) {
            String entry = entryMatcher.group(1);

            String title = extractFirst(TITLE_PATTERN, entry).replaceAll("\\s+", " ").trim();
            String summary = extractFirst(SUMMARY_PATTERN, entry).replaceAll("\\s+", " ").trim();
            String link = extractFirst(LINK_PATTERN, entry).trim();

            if (!title.isBlank() && !link.isBlank()) {
                // 截断摘要
                if (summary.length() > 300) {
                    summary = summary.substring(0, 300) + "...";
                }

                results.add(SearchResult.builder()
                        .title(title)
                        .url(link)
                        .snippet(summary)
                        .build());
            }
        }
        return results;
    }

    private String extractFirst(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : "";
    }
}
