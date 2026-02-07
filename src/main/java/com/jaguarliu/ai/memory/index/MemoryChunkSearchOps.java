package com.jaguarliu.ai.memory.index;

import java.util.List;

/**
 * memory_chunks 的数据库特有检索操作
 *
 * PG 使用 pgvector + tsvector native query，
 * SQLite 使用 FTS5 + no-op 向量操作。
 */
public interface MemoryChunkSearchOps {

    /**
     * 向量检索（余弦相似度）
     *
     * @param embedding 向量字符串，格式 [0.1,0.2,...]
     * @param limit     返回数量限制
     * @return Object[] 数组：[id, file_path, line_start, line_end, content, similarity]
     */
    List<Object[]> searchByVector(String embedding, int limit);

    /**
     * 全文检索
     *
     * @param query FTS 查询字符串
     * @param limit 返回数量限制
     * @return Object[] 数组：[id, file_path, line_start, line_end, content, rank]
     */
    List<Object[]> searchByFts(String query, int limit);

    /**
     * 更新 chunk 的 embedding
     *
     * @param id        chunk ID
     * @param embedding 向量字符串，格式 [0.1,0.2,...]
     */
    void updateEmbedding(String id, String embedding);

    /**
     * 统计有 embedding 的 chunk 数量
     */
    long countWithEmbedding();

    /**
     * 统计总 chunk 数量
     */
    long countTotal();

    /**
     * 查找没有 embedding 的 chunks（用于增量索引）
     *
     * @param limit 返回数量限制
     * @return Object[] 数组：[id, file_path, line_start, line_end, content]
     */
    List<Object[]> findChunksWithoutEmbedding(int limit);
}
