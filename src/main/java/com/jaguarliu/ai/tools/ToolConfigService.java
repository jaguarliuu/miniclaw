package com.jaguarliu.ai.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.jaguarliu.ai.tools.search.SearchProviderRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;

/**
 * 工具配置持久化 + 运行时热更新服务
 * 参照 LlmConfigService 模式：内存 + YAML 文件双写
 */
@Slf4j
@Service
public class ToolConfigService {

    private final ToolConfigProperties properties;
    private final SearchProviderRegistry searchProviderRegistry;

    public ToolConfigService(ToolConfigProperties properties,
                             @Lazy SearchProviderRegistry searchProviderRegistry) {
        this.properties = properties;
        this.searchProviderRegistry = searchProviderRegistry;
    }

    @Value("${miniclaw.config-dir:./data}")
    private String configDir;

    @PostConstruct
    void init() {
        loadFromFile();
    }

    /**
     * 获取当前配置（API Key 脱敏）
     */
    public Map<String, Object> getConfig() {
        Map<String, Object> result = new LinkedHashMap<>();

        // Trusted domains
        Map<String, Object> trustedDomains = new LinkedHashMap<>();
        trustedDomains.put("defaults", properties.getDefaultDomains());
        trustedDomains.put("user", properties.getUserDomains());
        result.put("trustedDomains", trustedDomains);

        // Search providers — 返回所有 6 种类型的当前状态
        List<Map<String, Object>> providers = new ArrayList<>();
        providers.add(buildProviderEntry("bing", "Bing Web Search", true));
        providers.add(buildProviderEntry("tavily", "Tavily", true));
        providers.add(buildProviderEntry("perplexity", "Perplexity", true));
        providers.add(buildProviderEntry("github", "GitHub Search", false));
        providers.add(buildProviderEntry("arxiv", "arXiv", false));
        result.put("searchProviders", providers);

        // HITL configuration
        Map<String, Object> hitl = new LinkedHashMap<>();
        hitl.put("alwaysConfirmTools", properties.getAlwaysConfirmTools());
        hitl.put("dangerousKeywords", properties.getDangerousKeywords());
        result.put("hitl", hitl);

        return result;
    }

    /**
     * 保存配置：写文件 + 更新内存
     */
    @SuppressWarnings("unchecked")
    public void saveConfig(Map<String, Object> params) {
        // 更新用户域名
        if (params.containsKey("userDomains")) {
            List<String> userDomains = (List<String>) params.get("userDomains");
            properties.setUserDomains(userDomains != null ? new ArrayList<>(userDomains) : new ArrayList<>());
        }

        // 更新搜索引擎提供商
        if (params.containsKey("searchProviders")) {
            List<Map<String, Object>> providerList = (List<Map<String, Object>>) params.get("searchProviders");
            List<ToolConfigProperties.SearchProviderConfig> configs = new ArrayList<>();
            if (providerList != null) {
                for (Map<String, Object> p : providerList) {
                    configs.add(ToolConfigProperties.SearchProviderConfig.builder()
                            .type((String) p.get("type"))
                            .apiKey((String) p.get("apiKey"))
                            .enabled(Boolean.TRUE.equals(p.get("enabled")))
                            .build());
                }
            }
            properties.setSearchProviders(configs);
        }

        // 更新 HITL 配置
        if (params.containsKey("hitl")) {
            Map<String, Object> hitl = (Map<String, Object>) params.get("hitl");
            if (hitl != null) {
                if (hitl.containsKey("alwaysConfirmTools")) {
                    List<String> tools = (List<String>) hitl.get("alwaysConfirmTools");
                    properties.setAlwaysConfirmTools(tools != null ? new ArrayList<>(tools) : new ArrayList<>());
                }
                if (hitl.containsKey("dangerousKeywords")) {
                    List<String> keywords = (List<String>) hitl.get("dangerousKeywords");
                    properties.setDangerousKeywords(keywords != null ? new ArrayList<>(keywords) : new ArrayList<>());
                }
            }
        }

        // 持久化到文件
        writeConfigFile();

        // 重建搜索引擎注册表
        searchProviderRegistry.rebuild();

        log.info("Tool config saved: {} user domains, {} search providers",
                properties.getUserDomains().size(), properties.getSearchProviders().size());
    }

    // ==================== Private ====================

    private void loadFromFile() {
        File configFile = new File(configDir, "tool-config.yml");
        if (!configFile.exists()) {
            log.info("No tool config file found at {}", configFile.getAbsolutePath());
            return;
        }

        try {
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            @SuppressWarnings("unchecked")
            Map<String, Object> config = yamlMapper.readValue(configFile, Map.class);

            if (config.containsKey("userDomains")) {
                @SuppressWarnings("unchecked")
                List<String> domains = (List<String>) config.get("userDomains");
                if (domains != null) {
                    properties.setUserDomains(new ArrayList<>(domains));
                }
            }

            if (config.containsKey("searchProviders")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> providerList = (List<Map<String, Object>>) config.get("searchProviders");
                if (providerList != null) {
                    List<ToolConfigProperties.SearchProviderConfig> configs = new ArrayList<>();
                    for (Map<String, Object> p : providerList) {
                        configs.add(ToolConfigProperties.SearchProviderConfig.builder()
                                .type((String) p.get("type"))
                                .apiKey((String) p.get("apiKey"))
                                .enabled(Boolean.TRUE.equals(p.get("enabled")))
                                .build());
                    }
                    properties.setSearchProviders(configs);
                }
            }

            if (config.containsKey("hitl")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> hitl = (Map<String, Object>) config.get("hitl");
                if (hitl != null) {
                    if (hitl.containsKey("alwaysConfirmTools")) {
                        @SuppressWarnings("unchecked")
                        List<String> tools = (List<String>) hitl.get("alwaysConfirmTools");
                        if (tools != null) {
                            properties.setAlwaysConfirmTools(new ArrayList<>(tools));
                        }
                    }
                    if (hitl.containsKey("dangerousKeywords")) {
                        @SuppressWarnings("unchecked")
                        List<String> keywords = (List<String>) hitl.get("dangerousKeywords");
                        if (keywords != null) {
                            properties.setDangerousKeywords(new ArrayList<>(keywords));
                        }
                    }
                }
            }

            log.info("Loaded tool config from file: {} user domains, {} search providers",
                    properties.getUserDomains().size(), properties.getSearchProviders().size());
        } catch (Exception e) {
            log.warn("Failed to load tool config file: {}", e.getMessage());
        }
    }

    private void writeConfigFile() {
        try {
            File dir = new File(configDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File configFile = new File(dir, "tool-config.yml");
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

            Map<String, Object> config = new LinkedHashMap<>();
            config.put("userDomains", properties.getUserDomains());

            List<Map<String, Object>> providers = new ArrayList<>();
            for (ToolConfigProperties.SearchProviderConfig p : properties.getSearchProviders()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("type", p.getType());
                entry.put("apiKey", p.getApiKey());
                entry.put("enabled", p.isEnabled());
                providers.add(entry);
            }
            config.put("searchProviders", providers);

            // HITL configuration
            Map<String, Object> hitl = new LinkedHashMap<>();
            hitl.put("alwaysConfirmTools", properties.getAlwaysConfirmTools());
            hitl.put("dangerousKeywords", properties.getDangerousKeywords());
            config.put("hitl", hitl);

            yamlMapper.writeValue(configFile, config);
            log.info("Tool config file written to {}", configFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to write tool config file", e);
            throw new RuntimeException("Failed to save tool config: " + e.getMessage());
        }
    }

    /**
     * 构建单个提供商的 GET 响应条目
     */
    private Map<String, Object> buildProviderEntry(String type, String displayName, boolean keyRequired) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", type);
        entry.put("displayName", displayName);
        entry.put("keyRequired", keyRequired);

        // 从当前配置中查找该类型的状态
        Optional<ToolConfigProperties.SearchProviderConfig> existing = properties.getSearchProviders().stream()
                .filter(p -> type.equals(p.getType()))
                .findFirst();

        if (existing.isPresent()) {
            entry.put("apiKey", maskApiKey(existing.get().getApiKey()));
            entry.put("enabled", existing.get().isEnabled());
        } else {
            entry.put("apiKey", "");
            entry.put("enabled", false);
        }

        return entry;
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "";
        }
        if (apiKey.length() <= 8) {
            return "***";
        }
        return apiKey.substring(0, 3) + "***" + apiKey.substring(apiKey.length() - 3);
    }
}
