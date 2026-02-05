package com.jaguarliu.ai.memory.embedding;

import java.util.List;
import java.util.Map;

/**
 * Embedding 请求封装
 *
 * 参考 Spring AI EmbeddingRequest 设计，支持：
 * - 单个或批量文本
 * - 可选的模型覆盖
 * - 可选的维度覆盖
 * - 扩展选项
 */
public record EmbeddingRequest(
        List<String> inputs,
        EmbeddingOptions options
) {
    /**
     * Embedding 选项
     */
    public record EmbeddingOptions(
            String model,
            Integer dimensions,
            Map<String, Object> additionalOptions
    ) {
        public static EmbeddingOptions defaults() {
            return new EmbeddingOptions(null, null, Map.of());
        }

        public static EmbeddingOptions withModel(String model) {
            return new EmbeddingOptions(model, null, Map.of());
        }

        public static EmbeddingOptions withDimensions(int dimensions) {
            return new EmbeddingOptions(null, dimensions, Map.of());
        }
    }

    /**
     * 创建单文本请求
     */
    public static EmbeddingRequest of(String text) {
        return new EmbeddingRequest(List.of(text), EmbeddingOptions.defaults());
    }

    /**
     * 创建批量文本请求
     */
    public static EmbeddingRequest of(List<String> texts) {
        return new EmbeddingRequest(texts, EmbeddingOptions.defaults());
    }

    /**
     * 创建带选项的请求
     */
    public static EmbeddingRequest of(List<String> texts, EmbeddingOptions options) {
        return new EmbeddingRequest(texts, options);
    }

    /**
     * 获取输入数量
     */
    public int size() {
        return inputs != null ? inputs.size() : 0;
    }

    /**
     * 是否为单个输入
     */
    public boolean isSingle() {
        return size() == 1;
    }
}
