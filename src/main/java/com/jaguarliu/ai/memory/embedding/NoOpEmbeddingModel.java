package com.jaguarliu.ai.memory.embedding;

import java.util.List;

/**
 * 空操作 Embedding 模型
 *
 * 用于以下场景：
 * - 显式禁用向量检索（provider=none）
 * - 没有可用的 embedding provider
 * - 测试场景
 *
 * 特点：
 * - 所有方法返回空结果
 * - 不抛出异常
 * - 明确的 providerType 标识
 */
public class NoOpEmbeddingModel implements EmbeddingModel {

    public static final NoOpEmbeddingModel INSTANCE = new NoOpEmbeddingModel();

    private NoOpEmbeddingModel() {
        // 单例
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        return EmbeddingResponse.of(List.of());
    }

    @Override
    public float[] embed(String text) {
        return new float[0];
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        return List.of();
    }

    @Override
    public int dimensions() {
        return 0;
    }

    @Override
    public String modelName() {
        return "none";
    }

    @Override
    public String providerType() {
        return "none";
    }
}
