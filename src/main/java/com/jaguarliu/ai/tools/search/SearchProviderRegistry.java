package com.jaguarliu.ai.tools.search;

import com.jaguarliu.ai.tools.ToolConfigProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 搜索引擎提供商注册表
 * 管理所有可用的搜索提供商，根据配置和查询内容智能选择
 */
@Slf4j
@Component
public class SearchProviderRegistry {

    private final List<SearchProvider> allProviders;
    private final ToolConfigProperties configProperties;
    private final DefaultSearchProvider defaultProvider;

    private List<SearchProvider> activeProviders = new ArrayList<>();

    public SearchProviderRegistry(List<SearchProvider> allProviders,
                                  ToolConfigProperties configProperties,
                                  DefaultSearchProvider defaultProvider) {
        this.allProviders = allProviders;
        this.configProperties = configProperties;
        this.defaultProvider = defaultProvider;
    }

    @PostConstruct
    void init() {
        rebuild();
    }

    /**
     * 根据查询内容智能选择最佳搜索提供商
     */
    public SearchProvider selectProvider(String query) {
        List<SearchProvider> active = getActiveProviders();
        if (active.isEmpty()) {
            return defaultProvider;
        }
        if (active.size() == 1) {
            return active.get(0);
        }

        // 基于关键词的智能选择
        String q = query.toLowerCase();

        if (q.contains("github") || q.contains("repo") || q.contains("repository")) {
            return findByType(active, "github").orElseGet(() -> findFirstGeneral(active));
        }
        if (q.contains("paper") || q.contains("arxiv") || q.contains("research") || q.contains("论文")) {
            return findByType(active, "arxiv").orElseGet(() -> findFirstGeneral(active));
        }

        // 默认：优先通用搜索引擎 bing > tavily > perplexity > 第一个
        return findFirstGeneral(active);
    }

    /**
     * 从配置重建活跃提供商列表（配置保存后调用）
     */
    public void rebuild() {
        List<SearchProvider> newActive = new ArrayList<>();

        for (ToolConfigProperties.SearchProviderConfig config : configProperties.getSearchProviders()) {
            if (!config.isEnabled()) continue;

            findProviderBean(config.getType()).ifPresent(provider -> {
                // 配置 API Key
                configureProvider(provider, config.getApiKey());
                newActive.add(provider);
                log.info("Activated search provider: {} ({})", provider.getDisplayName(), provider.getType());
            });
        }

        this.activeProviders = newActive;
        log.info("Search provider registry rebuilt: {} active providers", activeProviders.size());
    }

    /**
     * 获取所有活跃的提供商
     */
    public List<SearchProvider> getActiveProviders() {
        return activeProviders;
    }

    // ==================== Private ====================

    private Optional<SearchProvider> findByType(List<SearchProvider> providers, String type) {
        return providers.stream().filter(p -> type.equals(p.getType())).findFirst();
    }

    private SearchProvider findFirstGeneral(List<SearchProvider> providers) {
        // 优先级：bing > tavily > perplexity > 列表中第一个
        String[] preferred = {"bing", "tavily", "perplexity"};
        for (String type : preferred) {
            Optional<SearchProvider> found = findByType(providers, type);
            if (found.isPresent()) return found.get();
        }
        return providers.get(0);
    }

    private Optional<SearchProvider> findProviderBean(String type) {
        return allProviders.stream()
                .filter(p -> type.equals(p.getType()))
                .findFirst();
    }

    private void configureProvider(SearchProvider provider, String apiKey) {
        if (provider instanceof BingSearchProvider p) {
            p.configure(apiKey);
        } else if (provider instanceof TavilySearchProvider p) {
            p.configure(apiKey);
        } else if (provider instanceof PerplexitySearchProvider p) {
            p.configure(apiKey);
        } else if (provider instanceof GithubSearchProvider p) {
            p.configure(apiKey);
        }
        // DefaultSearchProvider 和 ArxivSearchProvider 不需要配置
    }
}
