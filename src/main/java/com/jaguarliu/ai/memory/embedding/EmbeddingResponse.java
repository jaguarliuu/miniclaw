package com.jaguarliu.ai.memory.embedding;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Embedding 响应封装
 *
 * 参考 Spring AI EmbeddingResponse 设计，包含：
 * - 向量列表
 * - 使用量统计
 * - 响应元数据
 */
public record EmbeddingResponse(
        List<Embedding> embeddings,
        Usage usage,
        Map<String, Object> metadata
) {
    /**
     * Token 使用量统计
     */
    public record Usage(int promptTokens, int totalTokens) {
        public static Usage of(int promptTokens, int totalTokens) {
            return new Usage(promptTokens, totalTokens);
        }

        public static Usage empty() {
            return new Usage(0, 0);
        }
    }

    /**
     * 创建简单响应
     */
    public static EmbeddingResponse of(List<Embedding> embeddings) {
        return new EmbeddingResponse(embeddings, Usage.empty(), Collections.emptyMap());
    }

    /**
     * 创建带使用量的响应
     */
    public static EmbeddingResponse of(List<Embedding> embeddings, Usage usage) {
        return new EmbeddingResponse(embeddings, usage, Collections.emptyMap());
    }

    /**
     * 获取第一个 embedding（单个请求时使用）
     */
    public Embedding first() {
        return embeddings != null && !embeddings.isEmpty() ? embeddings.get(0) : null;
    }

    /**
     * 获取所有向量数组
     */
    public List<float[]> vectors() {
        if (embeddings == null) return List.of();
        return embeddings.stream().map(Embedding::vector).toList();
    }
}
