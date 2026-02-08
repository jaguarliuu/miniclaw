package com.jaguarliu.ai.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LLM 配置持久化 + 运行时热更新服务
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
        return !isBlank(properties.getEndpoint())
                && !isBlank(properties.getApiKey())
                && !isBlank(properties.getModel());
    }

    /**
     * 获取当前配置（apiKey 脱敏）
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
     * 保存配置：写文件 + 更新内存 + 刷新 client
     */
    public void saveConfig(String endpoint, String apiKey, String model) {
        // 更新内存
        properties.setEndpoint(endpoint);
        properties.setApiKey(apiKey);
        properties.setModel(model);

        // 刷新 LLM Client
        llmClient.reconfigure(endpoint, apiKey);

        // 写文件
        writeConfigFile(endpoint, apiKey, model);

        log.info("LLM config saved: endpoint={}, model={}", endpoint, model);
    }

    private void writeConfigFile(String endpoint, String apiKey, String model) {
        try {
            File dir = new File(configDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File configFile = new File(dir, "llm-config.yml");
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

            Map<String, String> config = new LinkedHashMap<>();
            config.put("endpoint", endpoint);
            config.put("apiKey", apiKey);
            config.put("model", model);

            yamlMapper.writeValue(configFile, config);
            log.info("LLM config file written to {}", configFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to write LLM config file", e);
            throw new RuntimeException("Failed to save LLM config: " + e.getMessage());
        }
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
