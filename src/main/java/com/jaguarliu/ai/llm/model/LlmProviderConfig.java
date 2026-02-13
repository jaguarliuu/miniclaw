package com.jaguarliu.ai.llm.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 单个 LLM Provider 配置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmProviderConfig {

    /**
     * Provider 唯一标识，例如 "deepseek", "openai"
     */
    private String id;

    /**
     * 显示名称，例如 "DeepSeek"
     */
    private String name;

    /**
     * API 端点，例如 "https://api.deepseek.com"
     */
    private String endpoint;

    /**
     * API Key
     */
    private String apiKey;

    /**
     * 该 Provider 可用的模型列表
     */
    private List<String> models;
}
