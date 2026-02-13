package com.jaguarliu.ai.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.jaguarliu.ai.llm.model.LlmProviderConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * LLM 配置持久化 + 运行时热更新服务
 * 支持多 Provider CRUD
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmConfigService {

    private final LlmProperties properties;
    private final OpenAiCompatibleLlmClient llmClient;

    @Value("${miniclaw.config-dir:./data}")
    private String configDir;

    /**
     * 检查 LLM 是否已配置
     */
    public boolean isConfigured() {
        // v2: 至少有一个 provider 且有 defaultModel
        if (!properties.getProviders().isEmpty() && !isBlank(properties.getDefaultModel())) {
            return true;
        }
        // 向后兼容 v1
        return !isBlank(properties.getEndpoint())
                && !isBlank(properties.getApiKey())
                && !isBlank(properties.getModel());
    }

    /**
     * 获取当前配置（apiKey 脱敏）— 向后兼容
     */
    public Map<String, Object> getConfig() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("endpoint", properties.getEndpoint() != null ? properties.getEndpoint() : "");
        result.put("apiKey", maskApiKey(properties.getApiKey()));
        result.put("model", properties.getModel() != null ? properties.getModel() : "");
        result.put("configured", isConfigured());
        return result;
    }

    /**
     * 获取多 Provider 配置（apiKeys 脱敏）
     */
    public Map<String, Object> getMultiConfig() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version", 2);
        result.put("defaultModel", properties.getDefaultModel() != null ? properties.getDefaultModel() : "");
        result.put("configured", isConfigured());

        List<Map<String, Object>> providerList = new ArrayList<>();
        for (LlmProviderConfig p : properties.getProviders()) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("id", p.getId());
            pm.put("name", p.getName());
            pm.put("endpoint", p.getEndpoint());
            pm.put("apiKey", maskApiKey(p.getApiKey()));
            pm.put("models", p.getModels() != null ? p.getModels() : List.of());
            providerList.add(pm);
        }
        result.put("providers", providerList);

        return result;
    }

    /**
     * 保存配置：写文件 + 更新内存 + 刷新 client（向后兼容单 provider）
     */
    public void saveConfig(String endpoint, String apiKey, String model) {
        // 更新内存
        properties.setEndpoint(endpoint);
        properties.setApiKey(apiKey);
        properties.setModel(model);

        // 如果 providers 为空，创建默认 provider
        if (properties.getProviders().isEmpty()) {
            LlmProviderConfig defaultProvider = LlmProviderConfig.builder()
                    .id("default")
                    .name("Default")
                    .endpoint(endpoint)
                    .apiKey(apiKey)
                    .models(List.of(model))
                    .build();
            properties.setProviders(new ArrayList<>(List.of(defaultProvider)));
            properties.setDefaultModel("default:" + model);
        } else {
            // 更新第一个 provider
            LlmProviderConfig first = properties.getProviders().get(0);
            first.setEndpoint(endpoint);
            if (!isBlank(apiKey)) {
                first.setApiKey(apiKey);
            }
            if (!first.getModels().contains(model)) {
                List<String> models = new ArrayList<>(first.getModels());
                models.add(model);
                first.setModels(models);
            }
            properties.setDefaultModel(first.getId() + ":" + model);
        }

        // 刷新 LLM Client
        llmClient.reconfigure(properties.getProviders().get(0).getId(), endpoint, apiKey);

        // 写文件
        writeMultiConfigFile();

        log.info("LLM config saved: endpoint={}, model={}", endpoint, model);
    }

    /**
     * 添加 Provider
     */
    public String addProvider(LlmProviderConfig providerConfig) {
        // 生成 ID（如果没有提供）
        if (isBlank(providerConfig.getId())) {
            providerConfig.setId(generateProviderId(providerConfig.getName()));
        }

        // 检查 ID 唯一性
        if (properties.getProvider(providerConfig.getId()) != null) {
            throw new IllegalArgumentException("Provider ID already exists: " + providerConfig.getId());
        }

        properties.getProviders().add(providerConfig);

        // 如果是第一个 provider，设为默认
        if (properties.getProviders().size() == 1 && isBlank(properties.getDefaultModel())) {
            String firstModel = providerConfig.getModels() != null && !providerConfig.getModels().isEmpty()
                    ? providerConfig.getModels().get(0) : "";
            properties.setDefaultModel(providerConfig.getId() + ":" + firstModel);
            properties.syncLegacyFieldsFromDefault();
        }

        // 构建 WebClient
        llmClient.reconfigure(providerConfig.getId(), providerConfig.getEndpoint(), providerConfig.getApiKey());

        // 持久化
        writeMultiConfigFile();

        log.info("Provider added: id={}, name={}", providerConfig.getId(), providerConfig.getName());
        return providerConfig.getId();
    }

    /**
     * 更新 Provider
     */
    public void updateProvider(String providerId, String name, String endpoint, String apiKey, List<String> models) {
        LlmProviderConfig provider = properties.getProvider(providerId);
        if (provider == null) {
            throw new IllegalArgumentException("Provider not found: " + providerId);
        }

        if (name != null) provider.setName(name);
        if (endpoint != null) provider.setEndpoint(endpoint);
        if (apiKey != null && !apiKey.isBlank()) provider.setApiKey(apiKey);
        if (models != null) provider.setModels(models);

        // 刷新 WebClient
        llmClient.reconfigure(providerId, provider.getEndpoint(), provider.getApiKey());

        // 同步旧字段
        properties.syncLegacyFieldsFromDefault();

        // 持久化
        writeMultiConfigFile();

        log.info("Provider updated: id={}", providerId);
    }

    /**
     * 删除 Provider
     */
    public void removeProvider(String providerId) {
        LlmProviderConfig provider = properties.getProvider(providerId);
        if (provider == null) {
            throw new IllegalArgumentException("Provider not found: " + providerId);
        }

        properties.getProviders().removeIf(p -> providerId.equals(p.getId()));
        llmClient.invalidateProvider(providerId);

        // 如果删除的是默认 provider，重置默认
        if (properties.getDefaultModel() != null && properties.getDefaultModel().startsWith(providerId + ":")) {
            if (!properties.getProviders().isEmpty()) {
                LlmProviderConfig first = properties.getProviders().get(0);
                String firstModel = first.getModels() != null && !first.getModels().isEmpty()
                        ? first.getModels().get(0) : "";
                properties.setDefaultModel(first.getId() + ":" + firstModel);
            } else {
                properties.setDefaultModel(null);
            }
            properties.syncLegacyFieldsFromDefault();
        }

        // 持久化
        writeMultiConfigFile();

        log.info("Provider removed: id={}", providerId);
    }

    /**
     * 设置默认模型
     */
    public void setDefaultModel(String defaultModel) {
        // 验证格式
        if (isBlank(defaultModel) || !defaultModel.contains(":")) {
            throw new IllegalArgumentException("Invalid defaultModel format, expected 'providerId:modelName'");
        }

        String providerId = defaultModel.substring(0, defaultModel.indexOf(':'));
        if (properties.getProvider(providerId) == null) {
            throw new IllegalArgumentException("Provider not found: " + providerId);
        }

        properties.setDefaultModel(defaultModel);
        properties.syncLegacyFieldsFromDefault();

        // 持久化
        writeMultiConfigFile();

        log.info("Default model set: {}", defaultModel);
    }

    /**
     * 写 v2 多 Provider 配置文件
     */
    private void writeMultiConfigFile() {
        try {
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            properties.writeV2ConfigFile(yamlMapper);
        } catch (Exception e) {
            log.error("Failed to write multi-config file", e);
            throw new RuntimeException("Failed to save LLM config: " + e.getMessage());
        }
    }

    private String generateProviderId(String name) {
        if (!isBlank(name)) {
            String base = name.toLowerCase().replaceAll("[^a-z0-9]", "");
            if (!base.isEmpty() && properties.getProvider(base) == null) {
                return base;
            }
        }
        return "provider-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String maskApiKey(String apiKey) {
        if (isBlank(apiKey)) {
            return "";
        }
        if (apiKey.length() <= 8) {
            return "***";
        }
        return apiKey.substring(0, 3) + "***" + apiKey.substring(apiKey.length() - 3);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
