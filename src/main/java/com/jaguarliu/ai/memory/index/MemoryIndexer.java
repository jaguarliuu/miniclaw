package com.jaguarliu.ai.memory.index;

import com.jaguarliu.ai.memory.MemoryProperties;
import com.jaguarliu.ai.memory.embedding.EmbeddingModel;
import com.jaguarliu.ai.memory.embedding.EmbeddingModelFactory;
import com.jaguarliu.ai.memory.embedding.NoOpEmbeddingModel;
import com.jaguarliu.ai.memory.store.MemoryStore;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 全局记忆索引器
 *
 * 职责：
 * 1. 将 Markdown 文件分块 → 写入 memory_chunks 表
 * 2. 如果 embedding provider 可用 → 生成向量并写入
 * 3. FTS (tsvector) 由数据库触发器自动填充
 *
 * 设计原则：
 * - 记忆是全局的、跨会话的
 * - Markdown 是真相源，索引是派生的
 * - 索引可随时从 Markdown 重建（rebuild）
 * - Embedding 是可选加速层：有就用，没有就关
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryIndexer {

    private final MemoryStore memoryStore;
    private final MemoryChunker chunker;
    private final MemoryChunkRepository chunkRepository;
    private final MemoryChunkSearchOps searchOps;
    private final EmbeddingModelFactory embeddingFactory;
    private final MemoryProperties properties;

    /** 当前可用的 Embedding 模型 */
    @Getter
    private EmbeddingModel embeddingModel;

    /** 向量检索是否可用 */
    @Getter
    private boolean vectorSearchEnabled;

    @PostConstruct
    public void init() {
        embeddingModel = embeddingFactory.create();
        vectorSearchEnabled = !(embeddingModel instanceof NoOpEmbeddingModel);

        if (vectorSearchEnabled) {
            log.info("MemoryIndexer initialized with embedding provider: {} (model: {})",
                    embeddingModel.providerType(), embeddingModel.modelName());
        } else {
            log.info("MemoryIndexer initialized WITHOUT embedding. FTS-only mode for global memory.");
        }
    }

    /**
     * 索引指定文件（全局记忆）
     * 删除旧 chunks → 重新分块 → 入库 → 可选 embedding
     *
     * @param relativePath 相对于 memory 目录的文件路径
     */
    public void indexFile(String relativePath) {
        log.info("Indexing global memory file: {}", relativePath);

        try {
            String content = memoryStore.read(relativePath);

            // 1. 删除旧 chunks
            chunkRepository.deleteByFilePath(relativePath);

            // 2. 分块
            List<MemoryChunk> chunks = chunker.chunk(relativePath, content);
            if (chunks.isEmpty()) {
                log.debug("No chunks generated for: {}", relativePath);
                return;
            }

            // 3. 写入 memory_chunks 表（FTS 由触发器自动填充）
            List<MemoryChunkEntity> entities = chunks.stream()
                    .map(c -> MemoryChunkEntity.builder()
                            .id(UUID.randomUUID().toString())
                            .filePath(c.getFilePath())
                            .lineStart(c.getLineStart())
                            .lineEnd(c.getLineEnd())
                            .content(c.getContent())
                            .build())
                    .toList();

            chunkRepository.saveAll(entities);
            log.info("Indexed {} chunks for global memory: {}", entities.size(), relativePath);

            // 4. 如果有 embedding provider，生成向量
            if (vectorSearchEnabled) {
                generateEmbeddings(entities);
            }

        } catch (IOException e) {
            log.error("Failed to index file: {}", relativePath, e);
        }
    }

    /**
     * 重建全部索引（全局记忆）
     * 从 Markdown 文件重新生成所有 chunks
     */
    public void rebuild() {
        log.info("Rebuilding global memory index from Markdown source...");

        // 1. 清空所有 chunks
        chunkRepository.deleteAllChunks();

        // 2. 列出所有记忆文件并索引
        try {
            List<MemoryStore.MemoryFileInfo> files = memoryStore.listFiles();
            for (MemoryStore.MemoryFileInfo file : files) {
                indexFile(file.relativePath());
            }
            log.info("Global memory index rebuild complete: {} files processed", files.size());
        } catch (IOException e) {
            log.error("Failed to rebuild memory index", e);
        }
    }

    /**
     * 删除指定文件的索引
     *
     * @param relativePath 相对于 memory 目录的文件路径
     */
    public void removeFile(String relativePath) {
        chunkRepository.deleteByFilePath(relativePath);
        log.info("Removed index for: {}", relativePath);
    }

    /**
     * 增量索引：为没有 embedding 的 chunks 生成向量
     * 用于 embedding provider 后来启用的场景
     *
     * @param batchSize 每批处理数量
     * @return 处理的 chunk 数量
     */
    public int indexPendingEmbeddings(int batchSize) {
        if (!vectorSearchEnabled) {
            log.debug("Vector search not enabled, skipping pending embeddings");
            return 0;
        }

        List<Object[]> pendingRows = searchOps.findChunksWithoutEmbedding(batchSize);
        if (pendingRows.isEmpty()) {
            return 0;
        }

        List<String> ids = pendingRows.stream()
                .map(row -> (String) row[0])
                .toList();
        List<String> contents = pendingRows.stream()
                .map(row -> (String) row[4])
                .toList();

        try {
            List<float[]> vectors = embeddingModel.embed(contents);

            for (int i = 0; i < ids.size() && i < vectors.size(); i++) {
                String vectorStr = formatVector(vectors.get(i));
                searchOps.updateEmbedding(ids.get(i), vectorStr);
            }

            log.info("Generated embeddings for {} pending chunks", ids.size());
            return ids.size();

        } catch (Exception e) {
            log.warn("Failed to generate pending embeddings: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 为指定 entities 生成 embedding 并写入
     */
    private void generateEmbeddings(List<MemoryChunkEntity> entities) {
        if (embeddingModel == null || !vectorSearchEnabled) return;

        int batchSize = properties.getEmbedding().getBatchSize();

        for (int i = 0; i < entities.size(); i += batchSize) {
            int end = Math.min(i + batchSize, entities.size());
            List<MemoryChunkEntity> batch = entities.subList(i, end);

            try {
                List<String> texts = batch.stream()
                        .map(MemoryChunkEntity::getContent)
                        .toList();

                List<float[]> vectors = embeddingModel.embed(texts);

                for (int j = 0; j < batch.size() && j < vectors.size(); j++) {
                    String vectorStr = formatVector(vectors.get(j));
                    searchOps.updateEmbedding(batch.get(j).getId(), vectorStr);
                }

                log.debug("Generated embeddings for batch {}-{}", i, end);

            } catch (Exception e) {
                log.warn("Failed to generate embeddings for batch {}-{}: {}",
                        i, end, e.getMessage());
                // 不抛异常 — embedding 失败不影响基础功能
            }
        }
    }

    /**
     * 格式化向量为 PostgreSQL vector 字符串
     */
    private String formatVector(float[] vector) {
        return Arrays.stream(toFloatArray(vector))
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
    }

    /**
     * float[] 转 double[]（用于 stream）
     */
    private double[] toFloatArray(float[] floats) {
        double[] doubles = new double[floats.length];
        for (int i = 0; i < floats.length; i++) {
            doubles[i] = floats[i];
        }
        return doubles;
    }

    /**
     * 获取索引状态
     */
    public IndexStatus getStatus() {
        long total = searchOps.countTotal();
        long withEmbedding = vectorSearchEnabled ? searchOps.countWithEmbedding() : 0;

        return new IndexStatus(
                total,
                withEmbedding,
                vectorSearchEnabled,
                embeddingModel != null ? embeddingModel.providerType() : "none",
                embeddingModel != null ? embeddingModel.modelName() : "none"
        );
    }

    /**
     * 索引状态
     */
    public record IndexStatus(
            long totalChunks,
            long chunksWithEmbedding,
            boolean vectorSearchEnabled,
            String embeddingProvider,
            String embeddingModel
    ) {}
}
