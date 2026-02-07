package com.jaguarliu.ai.memory.index;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * SQLite 实现：使用 FTS5 全文检索，向量检索不可用
 */
@Repository
@Profile("sqlite")
public class SqliteMemoryChunkSearchOps implements MemoryChunkSearchOps {

    @PersistenceContext
    private EntityManager em;

    @Override
    public List<Object[]> searchByVector(String embedding, int limit) {
        // SQLite 无向量支持
        return List.of();
    }

    @Override
    public List<Object[]> searchByFts(String query, int limit) {
        return em.createNativeQuery("""
                SELECT mc.id, mc.file_path, mc.line_start, mc.line_end, mc.content,
                       -bm25(memory_chunks_fts) AS rank
                FROM memory_chunks_fts fts
                JOIN memory_chunks mc ON mc.id = fts.chunk_id
                WHERE memory_chunks_fts MATCH :query
                ORDER BY rank DESC
                LIMIT :limit
                """)
                .setParameter("query", query)
                .setParameter("limit", limit)
                .getResultList();
    }

    @Override
    public void updateEmbedding(String id, String embedding) {
        // SQLite 无向量支持，no-op
    }

    @Override
    public long countWithEmbedding() {
        // SQLite 无向量支持
        return 0;
    }

    @Override
    public long countTotal() {
        return ((Number) em.createNativeQuery("SELECT COUNT(*) FROM memory_chunks")
                .getSingleResult()).longValue();
    }

    @Override
    public List<Object[]> findChunksWithoutEmbedding(int limit) {
        // SQLite 无向量支持
        return List.of();
    }
}
