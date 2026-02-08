package com.jaguarliu.ai.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.util.Map;

/**
 * LLM 配置属性
 * 启动时优先从本地配置文件加载，覆盖空值
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    /**
     * API 端点
     * 自动处理：若不以 /v* 结尾，自动追加 /v1
     */
    private String endpoint;

    /**
     * API Key
     */
    private String apiKey;

    /**
     * 默认模型
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
     * 同步调用：整个请求的超时时间
     * 流式调用：两个 chunk 之间的最大等待时间
     * 默认 300 秒，适配推理模型首 token 可能较慢的场景
     */
    private Integer timeout = 300;

    @Value("${miniclaw.config-dir:./data}")
    private String configDir;

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
            Map<String, Object> config = yamlMapper.readValue(configFile, Map.class);

            if (isBlank(endpoint) && config.containsKey("endpoint")) {
                endpoint = (String) config.get("endpoint");
            }
            if (isBlank(apiKey) && config.containsKey("apiKey")) {
                apiKey = (String) config.get("apiKey");
            }
            if (isBlank(model) && config.containsKey("model")) {
                model = (String) config.get("model");
            }

            log.info("Loaded LLM config from file: endpoint={}, model={}", endpoint, model);
        } catch (Exception e) {
            log.warn("Failed to load LLM config file: {}", e.getMessage());
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
