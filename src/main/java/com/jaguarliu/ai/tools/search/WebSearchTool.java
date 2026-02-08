package com.jaguarliu.ai.tools.search;

import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolConfigProperties;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Web 搜索工具
 * 根据查询内容智能选择最佳搜索引擎
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSearchTool implements Tool {

    private final SearchProviderRegistry registry;
    private final ToolConfigProperties toolConfigProperties;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("web_search")
                .description("搜索互联网获取最新信息。根据查询内容智能选择最佳搜索引擎。")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of("type", "string", "description", "搜索关键词"),
                                "max_results", Map.of("type", "integer", "description", "最大结果数(默认5)")
                        ),
                        "required", List.of("query")
                ))
                .hitl(false)
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        String query = (String) arguments.get("query");
        if (query == null || query.isBlank()) {
            return Mono.just(ToolResult.error("Missing required parameter: query"));
        }

        int maxResults = 5;
        if (arguments.containsKey("max_results") && arguments.get("max_results") != null) {
            maxResults = ((Number) arguments.get("max_results")).intValue();
        }

        SearchProvider provider = registry.selectProvider(query);
        log.info("Web search: '{}' via {}", query, provider.getDisplayName());

        return provider.search(query, maxResults)
                .map(results -> {
                    if (results.isEmpty()) {
                        return ToolResult.success("No results found for: " + query);
                    }

                    // 将搜索结果中的域名注册到临时白名单，允许后续 http_get 访问
                    registerDiscoveredDomains(results);

                    StringBuilder sb = new StringBuilder();
                    sb.append("Search results for: ").append(query)
                            .append(" (via ").append(provider.getDisplayName()).append(")\n\n");

                    for (int i = 0; i < results.size(); i++) {
                        SearchProvider.SearchResult r = results.get(i);
                        sb.append(i + 1).append(". **").append(r.getTitle()).append("**\n");
                        sb.append("   ").append(r.getUrl()).append("\n");
                        if (r.getSnippet() != null && !r.getSnippet().isBlank()) {
                            sb.append("   ").append(r.getSnippet()).append("\n");
                        }
                        sb.append("\n");
                    }
                    return ToolResult.success(sb.toString());
                })
                .onErrorResume(e -> {
                    log.error("Web search failed for '{}': {}", query, e.getMessage());
                    return Mono.just(ToolResult.error("Search failed: " + e.getMessage()));
                });
    }

    /**
     * 从搜索结果中提取域名，注册到临时白名单
     */
    private void registerDiscoveredDomains(List<SearchProvider.SearchResult> results) {
        Set<String> domains = results.stream()
                .map(SearchProvider.SearchResult::getUrl)
                .filter(url -> url != null && !url.isBlank())
                .map(url -> {
                    try {
                        return URI.create(url).getHost();
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(host -> host != null && !host.isBlank())
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        if (!domains.isEmpty()) {
            toolConfigProperties.addSearchDiscoveredDomains(domains);
            log.debug("Registered {} search-discovered domains to temp whitelist", domains.size());
        }
    }
}
