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
     * 始终需要 HITL 确认的工具名称（用户配置）
     * 例如: ["shell", "shell_start", "write_file"]
     */
    private List<String> alwaysConfirmTools = new ArrayList<>();

    /**
     * 用户自定义的危险命令关键词
     * 命令中包含任一关键词即触发 HITL 确认（大小写不敏感的子串匹配）
     * 例如: ["docker rm", "npm publish", "DROP TABLE"]
     */
    private List<String> dangerousKeywords = new ArrayList<>();

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

    /**
     * 检查工具是否在用户配置的"始终确认"列表中
     */
    public boolean isAlwaysConfirmTool(String toolName) {
        return alwaysConfirmTools.stream()
                .anyMatch(t -> t.equalsIgnoreCase(toolName));
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
