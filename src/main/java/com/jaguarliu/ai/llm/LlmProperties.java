package com.jaguarliu.ai.llm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * LLM 配置属性
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    /**
     * API 端点
     * 自动处理：若不以 /v* 结尾，自动追加 /v1
     */
    private String endpoint = "http://localhost:11434";

    /**
     * API Key
     */
    private String apiKey;

    /**
     * 默认模型
     */
    private String model = "gpt-3.5-turbo";

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
    private Integer timeout = 60;
}
