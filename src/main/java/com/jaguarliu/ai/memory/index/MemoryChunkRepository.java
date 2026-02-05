package com.jaguarliu.ai.memory.index;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * memory_chunks 存储库
 *
 * 全局记忆检索，不区分会话。
 * 向量检索和 FTS 使用 Native Query（JPA 不直接支持 pgvector）
 */
@Repository
public interface MemoryChunkRepository extends JpaRepository<MemoryChunkEntity, String> {

    /**
     * 按文件路径查找所有 chunks
     */
    List<MemoryChunkEntity> findByFilePath(String filePath);

    /**
     * 删除指定文件的所有 chunks
     */
    @Transactional
    @Modifying
    void deleteByFilePath(String filePath);

    /**
     * 删除所有 chunks（重建索引时使用）
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM MemoryChunkEntity")
    void deleteAllChunks();

    /**
     * 向量检索（余弦相似度）- 全局检索
     * 注意：embedding 参数以 [0.1,0.2,...] 字符串格式传入
     *
     * @param embedding 向量字符串，格式 [0.1,0.2,...]
     * @param limit     返回数量限制
     * @return Object[] 数组：[id, file_path, line_start, line_end, content, similarity]
     */
    @Query(value = """
        SELECT id, file_path, line_start, line_end, content,
               1 - (embedding <=> cast(:embedding as vector)) AS similarity
        FROM memory_chunks
        WHERE embedding IS NOT NULL
        ORDER BY embedding <=> cast(:embedding as vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> searchByVector(@Param("embedding") String embedding, @Param("limit") int limit);

    /**
     * 全文检索 - 全局检索
     *
     * @param query FTS 查询字符串
     * @param limit 返回数量限制
     * @return Object[] 数组：[id, file_path, line_start, line_end, content, rank]
     */
    @Query(value = """
        SELECT id, file_path, line_start, line_end, content,
               ts_rank(tsv, plainto_tsquery('simple', :query)) AS rank
        FROM memory_chunks
        WHERE tsv @@ plainto_tsquery('simple', :query)
        ORDER BY rank DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> searchByFts(@Param("query") String query, @Param("limit") int limit);

    /**
     * 更新 chunk 的 embedding（Native SQL，因为 JPA 不支持 vector 类型）
     *
     * @param id        chunk ID
     * @param embedding 向量字符串，格式 [0.1,0.2,...]
     */
    @Transactional
    @Modifying
    @Query(value = """
        UPDATE memory_chunks SET embedding = cast(:embedding as vector), updated_at = NOW()
        WHERE id = :id
        """, nativeQuery = true)
    void updateEmbedding(@Param("id") String id, @Param("embedding") String embedding);

    /**
     * 批量更新 embeddings
     * 注意：由于 JPA 不支持批量 vector 更新，此方法逐个调用 updateEmbedding
     */
    default void updateEmbeddings(List<String> ids, List<String> embeddings) {
        for (int i = 0; i < ids.size() && i < embeddings.size(); i++) {
            updateEmbedding(ids.get(i), embeddings.get(i));
        }
    }

    /**
     * 统计有 embedding 的 chunk 数量
     */
    @Query(value = "SELECT COUNT(*) FROM memory_chunks WHERE embedding IS NOT NULL", nativeQuery = true)
    long countWithEmbedding();

    /**
     * 统计总 chunk 数量
     */
    @Query(value = "SELECT COUNT(*) FROM memory_chunks", nativeQuery = true)
    long countTotal();

    /**
     * 查找没有 embedding 的 chunks（用于增量索引）
     */
    @Query(value = """
        SELECT id, file_path, line_start, line_end, content
        FROM memory_chunks
        WHERE embedding IS NULL
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findChunksWithoutEmbedding(@Param("limit") int limit);
}
