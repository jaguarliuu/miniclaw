package com.jaguarliu.ai.memory.index;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * PostgreSQL 实现：使用 pgvector + tsvector native query
 */
@Repository
@Profile("pg")
public class PgMemoryChunkSearchOps implements MemoryChunkSearchOps {

    @PersistenceContext
    private EntityManager em;

    @Override
    public List<Object[]> searchByVector(String embedding, int limit) {
        return em.createNativeQuery("""
                SELECT id, file_path, line_start, line_end, content,
                       1 - (embedding <=> cast(:embedding as vector)) AS similarity
                FROM memory_chunks
                WHERE embedding IS NOT NULL
                ORDER BY embedding <=> cast(:embedding as vector)
                LIMIT :limit
                """)
                .setParameter("embedding", embedding)
                .setParameter("limit", limit)
                .getResultList();
    }

    @Override
    public List<Object[]> searchByFts(String query, int limit) {
        return em.createNativeQuery("""
                SELECT id, file_path, line_start, line_end, content,
                       ts_rank(tsv, plainto_tsquery('simple', :query)) AS rank
                FROM memory_chunks
                WHERE tsv @@ plainto_tsquery('simple', :query)
                ORDER BY rank DESC
                LIMIT :limit
                """)
                .setParameter("query", query)
                .setParameter("limit", limit)
                .getResultList();
    }

    @Override
    @Transactional
    public void updateEmbedding(String id, String embedding) {
        em.createNativeQuery("""
                UPDATE memory_chunks SET embedding = cast(:embedding as vector), updated_at = NOW()
                WHERE id = :id
                """)
                .setParameter("embedding", embedding)
                .setParameter("id", id)
                .executeUpdate();
    }

    @Override
    public long countWithEmbedding() {
        return ((Number) em.createNativeQuery("SELECT COUNT(*) FROM memory_chunks WHERE embedding IS NOT NULL")
                .getSingleResult()).longValue();
    }

    @Override
    public long countTotal() {
        return ((Number) em.createNativeQuery("SELECT COUNT(*) FROM memory_chunks")
                .getSingleResult()).longValue();
    }

    @Override
    public List<Object[]> findChunksWithoutEmbedding(int limit) {
        return em.createNativeQuery("""
                SELECT id, file_path, line_start, line_end, content
                FROM memory_chunks
                WHERE embedding IS NULL
                LIMIT :limit
                """)
                .setParameter("limit", limit)
                .getResultList();
    }
}
