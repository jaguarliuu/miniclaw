package com.jaguarliu.ai.tools;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具配置属性（内存态）
 * 包含 HTTP 可信域名列表和搜索引擎提供商配置
 */
@Data
@Component
public class ToolConfigProperties {

    /**
     * 默认可信域名（始终允许，用户不可编辑）
     */
    private List<String> defaultDomains = List.of(
            "baidu.com", "google.com", "github.com", "githubusercontent.com",
            "stackoverflow.com", "wikipedia.org", "npmjs.com",
            "maven.org", "pypi.org", "docs.oracle.com"
    );

    /**
     * 用户添加的可信域名（通过设置页编辑）
     */
    private List<String> userDomains = new ArrayList<>();

    /**
     * 搜索引擎提供商配置
     */
    private List<SearchProviderConfig> searchProviders = new ArrayList<>();

    /**
     * 搜索结果发现的域名（临时白名单，session 结束时清除）
     * 使用 ConcurrentHashMap.newKeySet() 保证线程安全
     */
    private final Set<String> searchDiscoveredDomains = ConcurrentHashMap.newKeySet();

    /**
     * 检查主机名是否在可信域名列表中
     * 匹配规则：精确匹配 或 以 .domain 结尾
     * 检查顺序：默认域名 → 用户域名 → 搜索发现域名
     */
    public boolean isDomainTrusted(String host) {
        if (host == null) return false;
        String h = host.toLowerCase();
        return defaultDomains.stream().anyMatch(d -> h.equals(d) || h.endsWith("." + d))
                || userDomains.stream().anyMatch(d -> h.equals(d) || h.endsWith("." + d))
                || searchDiscoveredDomains.contains(h);
    }

    /**
     * 注册搜索结果发现的域名到临时白名单
     */
    public void addSearchDiscoveredDomains(Collection<String> domains) {
        searchDiscoveredDomains.addAll(domains);
    }

    /**
     * 清除搜索结果临时白名单（session/run 结束时调用）
     */
    public void clearSearchDiscoveredDomains() {
        searchDiscoveredDomains.clear();
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SearchProviderConfig {
        /**
         * 提供商类型：bing, tavily, perplexity, github, arxiv
         */
        private String type;

        /**
         * API Key（免费提供商为空）
         */
        private String apiKey;

        /**
         * 是否启用
         */
        private boolean enabled;
    }
}
