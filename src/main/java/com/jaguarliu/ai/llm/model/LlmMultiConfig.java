package com.jaguarliu.ai.llm.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 多 Provider LLM 配置（v2 格式）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LlmMultiConfig {

    /**
     * 配置格式版本号
     */
    private int version = 2;

    /**
     * 默认模型，格式 "providerId:modelName"
     */
    private String defaultModel;

    /**
     * Provider 列表
     */
    private List<LlmProviderConfig> providers = new ArrayList<>();
}
