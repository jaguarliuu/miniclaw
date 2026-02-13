package com.jaguarliu.ai.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.jaguarliu.ai.llm.model.LlmMultiConfig;
import com.jaguarliu.ai.llm.model.LlmProviderConfig;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LLM 配置属性
 * 支持多 Provider 配置（v2 格式）
 * 启动时优先从本地配置文件加载，自动迁移旧格式
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    /**
     * API 端点（向后兼容，从 defaultProvider 派生）
     */
    private String endpoint;

    /**
     * API Key（向后兼容）
     */
    private String apiKey;

    /**
     * 默认模型（向后兼容）
     */
    private String model;

    /**
     * 默认温度
     */
    private Double temperature = 0.7;

    /**
     * 默认最大 token 数
     */
    private Integer maxTokens = 2048;

    /**
     * 请求超时时间（秒）
     */
    private Integer timeout = 300;

    @Value("${miniclaw.config-dir:./data}")
    private String configDir;

    // ==================== Multi-Provider Fields ====================

    /**
     * Provider 列表
     */
    private List<LlmProviderConfig> providers = new ArrayList<>();

    /**
     * 默认模型，格式 "providerId:modelName"
     */
    private String defaultModel;

    @PostConstruct
    void loadFromFile() {
        File configFile = new File(configDir, "llm-config.yml");
        if (!configFile.exists()) {
            log.info("No local LLM config file found at {}", configFile.getAbsolutePath());
            return;
        }

        try {
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = yamlMapper.readValue(configFile, Map.class);

            if (raw.containsKey("version") && Integer.valueOf(2).equals(raw.get("version"))) {
                // v2 格式：多 Provider
                loadV2Config(yamlMapper, configFile);
            } else {
                // v1 旧格式：自动迁移
                migrateV1Config(raw, yamlMapper);
            }
        } catch (Exception e) {
            log.warn("Failed to load LLM config file: {}", e.getMessage());
        }
    }

    /**
     * 加载 v2 多 Provider 配置
     */
    private void loadV2Config(ObjectMapper yamlMapper, File configFile) throws Exception {
        LlmMultiConfig multiConfig = yamlMapper.readValue(configFile, LlmMultiConfig.class);

        if (multiConfig.getProviders() != null) {
            this.providers = multiConfig.getProviders();
        }
        if (multiConfig.getDefaultModel() != null) {
            this.defaultModel = multiConfig.getDefaultModel();
        }

        // 从 defaultModel 派生旧的单字段（向后兼容）
        syncLegacyFieldsFromDefault();

        log.info("Loaded v2 LLM config: {} providers, defaultModel={}",
                providers.size(), defaultModel);
    }

    /**
     * 迁移 v1 旧格式到 v2
     */
    private void migrateV1Config(Map<String, Object> raw, ObjectMapper yamlMapper) {
        String ep = (String) raw.get("endpoint");
        String key = (String) raw.get("apiKey");
        String mod = (String) raw.get("model");

        // 填充旧字段
        if (isBlank(endpoint) && ep != null) {
            endpoint = ep;
        }
        if (isBlank(apiKey) && key != null) {
            apiKey = key;
        }
        if (isBlank(model) && mod != null) {
            model = mod;
        }

        // 创建 default provider
        if (!isBlank(endpoint)) {
            LlmProviderConfig defaultProvider = LlmProviderConfig.builder()
                    .id("default")
                    .name("Default")
                    .endpoint(endpoint)
                    .apiKey(apiKey)
                    .models(isBlank(model) ? List.of() : List.of(model))
                    .build();

            this.providers = new ArrayList<>(List.of(defaultProvider));
            this.defaultModel = "default:" + (isBlank(model) ? "" : model);

            // 写回 v2 格式
            try {
                writeV2ConfigFile(yamlMapper);
                log.info("Auto-migrated LLM config from v1 to v2 format");
            } catch (Exception e) {
                log.warn("Failed to write migrated config: {}", e.getMessage());
            }
        }

        log.info("Loaded LLM config from file (v1 migrated): endpoint={}, model={}", endpoint, model);
    }

    /**
     * 写 v2 格式配置文件
     */
    void writeV2ConfigFile(ObjectMapper yamlMapper) throws Exception {
        File dir = new File(configDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        LlmMultiConfig config = new LlmMultiConfig();
        config.setVersion(2);
        config.setDefaultModel(defaultModel);
        config.setProviders(providers);

        File configFile = new File(dir, "llm-config.yml");
        yamlMapper.writeValue(configFile, config);
        log.info("LLM config file written (v2) to {}", configFile.getAbsolutePath());
    }

    /**
     * 从 defaultModel 同步旧字段
     */
    void syncLegacyFieldsFromDefault() {
        if (isBlank(defaultModel) || providers.isEmpty()) return;

        String providerId = getDefaultProviderId();
        String modelName = getDefaultModelName();

        LlmProviderConfig provider = getProvider(providerId);
        if (provider != null) {
            if (isBlank(endpoint)) {
                this.endpoint = provider.getEndpoint();
            }
            if (isBlank(apiKey)) {
                this.apiKey = provider.getApiKey();
            }
            if (isBlank(model)) {
                this.model = modelName;
            }
        }
    }

    // ==================== Helper Methods ====================

    /**
     * 获取指定 Provider
     */
    public LlmProviderConfig getProvider(String id) {
        if (id == null || providers == null) return null;
        return providers.stream()
                .filter(p -> id.equals(p.getId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取默认 Provider ID（从 defaultModel 解析）
     */
    public String getDefaultProviderId() {
        if (isBlank(defaultModel)) return null;
        int idx = defaultModel.indexOf(':');
        return idx > 0 ? defaultModel.substring(0, idx) : defaultModel;
    }

    /**
     * 获取默认 Model 名称（从 defaultModel 解析）
     */
    public String getDefaultModelName() {
        if (isBlank(defaultModel)) return null;
        int idx = defaultModel.indexOf(':');
        return idx > 0 ? defaultModel.substring(idx + 1) : null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
