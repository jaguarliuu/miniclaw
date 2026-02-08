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
 * DuckDuckGo HTML 抓取搜索（免费，无需 API Key）
 * 作为默认回退搜索引擎
 */
@Slf4j
@Component
public class DefaultSearchProvider implements SearchProvider {

    private final WebClient webClient;

    private static final Pattern RESULT_PATTERN = Pattern.compile(
            "<a[^>]+class=\"result__a\"[^>]*href=\"([^\"]*)\"[^>]*>(.*?)</a>",
            Pattern.DOTALL
    );
    private static final Pattern SNIPPET_PATTERN = Pattern.compile(
            "<a[^>]+class=\"result__snippet\"[^>]*>(.*?)</a>",
            Pattern.DOTALL
    );

    public DefaultSearchProvider() {
        this.webClient = WebClient.builder()
                .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    @Override
    public String getType() {
        return "default";
    }

    @Override
    public String getDisplayName() {
        return "DuckDuckGo (Default)";
    }

    @Override
    public Mono<List<SearchResult>> search(String query, int maxResults) {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://html.duckduckgo.com/html/?q=" + encoded;

        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(15))
                .map(html -> parseResults(html, maxResults))
                .onErrorResume(e -> {
                    log.error("DuckDuckGo search failed for '{}': {}", query, e.getMessage());
                    return Mono.just(List.of());
                });
    }

    private List<SearchResult> parseResults(String html, int maxResults) {
        List<SearchResult> results = new ArrayList<>();
        Matcher linkMatcher = RESULT_PATTERN.matcher(html);
        Matcher snippetMatcher = SNIPPET_PATTERN.matcher(html);

        while (linkMatcher.find() && results.size() < maxResults) {
            String href = linkMatcher.group(1);
            String title = stripHtml(linkMatcher.group(2));
            String snippet = snippetMatcher.find() ? stripHtml(snippetMatcher.group(1)) : "";

            // DuckDuckGo 的链接可能是重定向 URL，提取实际 URL
            String actualUrl = extractUrl(href);
            if (actualUrl != null && !actualUrl.isBlank() && !title.isBlank()) {
                results.add(SearchResult.builder()
                        .title(title)
                        .url(actualUrl)
                        .snippet(snippet)
                        .build());
            }
        }
        return results;
    }

    private String extractUrl(String href) {
        // DuckDuckGo 使用 //duckduckgo.com/l/?uddg=ENCODED_URL 重定向
        if (href.contains("uddg=")) {
            try {
                int start = href.indexOf("uddg=") + 5;
                int end = href.indexOf("&", start);
                String encoded = end > 0 ? href.substring(start, end) : href.substring(start);
                return java.net.URLDecoder.decode(encoded, StandardCharsets.UTF_8);
            } catch (Exception e) {
                return href;
            }
        }
        return href;
    }

    private String stripHtml(String html) {
        return html.replaceAll("<[^>]+>", "").replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<").replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"").replaceAll("&#39;", "'")
                .trim();
    }
}
