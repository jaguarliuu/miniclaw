package com.miniclaw.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LLM 配置属性
 * 
 * 从 application.yml 读取 llm.* 配置
 * 自动绑定到这个类的字段
 * 
 * 使用示例：
 * <pre>
 * llm:
 *   endpoint: https://api.deepseek.com
 *   api-key: ${LLM_API_KEY}
 *   model: deepseek-chat
 *   temperature: 0.7
 *   max-tokens: 2048
 *   timeout: 60
 * </pre>
 * 
 * 为什么不用 Spring AI 的配置？
 * - Spring AI 的配置结构复杂，学习成本高
 * - 我们的配置简单直接，更容易理解
 * - 完全掌控配置，方便调试和定制
 */
@Data
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    /**
     * LLM API 端点
     * 
     * 支持的端点：
     * - https://api.openai.com（OpenAI）
     * - https://api.deepseek.com（DeepSeek）
     * - https://dashscope.aliyuncs.com/compatible-mode/v1（通义千问）
     * - http://localhost:11434/v1（Ollama 本地）
     */
    private String endpoint = "https://api.deepseek.com";

    /**
     * API 密钥
     * 
     * 建议通过环境变量注入：${LLM_API_KEY}
     * 不要硬编码在配置文件中！
     */
    private String apiKey;

    /**
     * 默认模型名称
     * 
     * 示例：
     * - gpt-4o（OpenAI）
     * - deepseek-chat（DeepSeek）
     * - qwen-plus（通义千问）
     * - llama3（Ollama）
     */
    private String model = "deepseek-chat";

    /**
     * 温度参数（0-2）
     * 
     * - 0：确定性输出，适合代码生成
     * - 0.7：平衡创造性和一致性
     * - 1.5+：高随机性，适合创意写作
     */
    private Double temperature = 0.7;

    /**
     * 最大 token 数
     * 
     * 限制响应长度，避免成本失控
     */
    private Integer maxTokens = 2048;

    /**
     * 请求超时（秒）
     * 
     * LLM 响应可能较慢，建议 60 秒以上
     */
    private Integer timeout = 60;

    /**
     * 最大重试次数
     * 
     * 网络错误、限流时可自动重试
     */
    private Integer maxRetries = 3;

    private Long retryMinBackoffMillis = 200L;

    private Long retryMaxBackoffMillis = 2000L;
}
