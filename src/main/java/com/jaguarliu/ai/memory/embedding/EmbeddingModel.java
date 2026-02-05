package com.jaguarliu.ai.memory.embedding;

import java.util.List;

/**
 * Embedding 模型接口
 *
 * 设计参考：
 * - Spring AI EmbeddingModel: 提供 call(request) 核心方法 + 便捷方法
 * - LangChain4j EmbeddingModel: embed/embedAll 简洁 API
 *
 * 扩展点：
 * - 实现此接口即可接入新的 embedding provider
 * - 通过 EmbeddingModelFactory 注册和自动发现
 *
 * 内置实现：
 * - OpenAiCompatibleEmbeddingModel: 支持所有 OpenAI 兼容 API
 * - NoOpEmbeddingModel: 空实现，用于禁用向量检索时
 */
public interface EmbeddingModel {

    /**
     * 核心方法：执行 embedding 请求
     *
     * @param request 包含输入文本和选项的请求
     * @return 包含向量和元数据的响应
     */
    EmbeddingResponse call(EmbeddingRequest request);

    /**
     * 便捷方法：对单个文本生成 embedding
     *
     * @param text 输入文本
     * @return embedding 向量数组
     */
    default float[] embed(String text) {
        EmbeddingResponse response = call(EmbeddingRequest.of(text));
        Embedding first = response.first();
        return first != null ? first.vector() : new float[0];
    }

    /**
     * 便捷方法：对多个文本批量生成 embedding
     *
     * @param texts 输入文本列表
     * @return embedding 向量数组列表
     */
    default List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        EmbeddingResponse response = call(EmbeddingRequest.of(texts));
        return response.vectors();
    }

    /**
     * 获取模型输出的向量维度
     *
     * @return 向量维度
     */
    int dimensions();

    /**
     * 获取模型名称/标识
     *
     * @return 模型名称
     */
    String modelName();

    /**
     * 获取 provider 类型标识
     *
     * @return provider 类型（如 "openai", "ollama", "none"）
     */
    String providerType();
}
