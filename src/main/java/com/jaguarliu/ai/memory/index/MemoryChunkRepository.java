package com.jaguarliu.ai.memory.index;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * memory_chunks 存储库
 *
 * 全局记忆检索，不区分会话。
 * 数据库特有的检索操作（向量/FTS）已提取到 {@link MemoryChunkSearchOps}。
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
}
