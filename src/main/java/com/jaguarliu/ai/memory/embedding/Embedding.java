package com.jaguarliu.ai.memory.embedding;

import java.util.Collections;
import java.util.Map;

/**
 * Embedding 向量结果
 *
 * 参考 Spring AI 和 LangChain4j 的设计，提供结构化的返回值。
 *
 * @param vector   向量数组
 * @param index    批量请求时的索引位置
 * @param metadata 元数据（如 token 使用量、模型信息等）
 */
public record Embedding(
        float[] vector,
        int index,
        Map<String, Object> metadata
) {
    /**
     * 创建简单的 Embedding（无 metadata）
     */
    public static Embedding of(float[] vector) {
        return new Embedding(vector, 0, Collections.emptyMap());
    }

    /**
     * 创建带索引的 Embedding
     */
    public static Embedding of(float[] vector, int index) {
        return new Embedding(vector, index, Collections.emptyMap());
    }

    /**
     * 获取向量维度
     */
    public int dimensions() {
        return vector != null ? vector.length : 0;
    }
}
