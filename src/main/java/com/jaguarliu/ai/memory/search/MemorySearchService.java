package com.jaguarliu.ai.memory.search;

import com.jaguarliu.ai.memory.MemoryProperties;
import com.jaguarliu.ai.memory.embedding.EmbeddingModel;
import com.jaguarliu.ai.memory.index.MemoryChunkRepository;
import com.jaguarliu.ai.memory.index.MemoryIndexer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 全局记忆检索服务
 *
 * 检索范围：所有历史记忆（全局，跨会话）
 *
 * 三级降级策略：
 * 1. 向量检索（需要 embedding provider）→ 语义相似度
 * 2. 全文检索（PostgreSQL tsvector，始终可用）→ 关键词匹配
 * 3. 合并去重 → 返回 top-k
 *
 * 如果 embedding provider 不可用，只走 FTS。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemorySearchService {

    private final MemoryChunkRepository chunkRepository;
    private final MemoryIndexer indexer;
    private final MemoryProperties properties;

    /**
     * 语义检索全局记忆
     *
     * @param query 检索查询
     * @return 排序后的检索结果（全局，不区分会话）
     */
    public List<SearchResult> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        MemoryProperties.SearchConfig config = properties.getSearch();
        Map<String, SearchResult> resultMap = new LinkedHashMap<>();

        // 1. 向量检索（如果可用）
        if (indexer.isVectorSearchEnabled()) {
            try {
                List<SearchResult> vectorResults = searchByVector(query, config.getVectorTopK());
                for (SearchResult r : vectorResults) {
                    if (r.getScore() >= config.getMinSimilarity()) {
                        resultMap.putIfAbsent(r.getDedupeKey(), r);
                    }
                }
                log.debug("Vector search returned {} results (after similarity filter)",
                        vectorResults.stream().filter(r -> r.getScore() >= config.getMinSimilarity()).count());
            } catch (Exception e) {
                log.warn("Vector search failed, falling back to FTS only: {}", e.getMessage());
            }
        }

        // 2. FTS 检索（始终执行，补充向量检索的遗漏）
        try {
            List<SearchResult> ftsResults = searchByFts(query, config.getFtsTopK());
            for (SearchResult r : ftsResults) {
                // 不覆盖已有的向量结果（向量分数更准确）
                resultMap.putIfAbsent(r.getDedupeKey(), r);
            }
            log.debug("FTS search returned {} results", ftsResults.size());
        } catch (Exception e) {
            log.warn("FTS search failed: {}", e.getMessage());
        }

        // 3. 合并排序 → 返回 top-k
        List<SearchResult> merged = resultMap.values().stream()
                .sorted(Comparator.comparingDouble(SearchResult::getScore).reversed())
                .limit(config.getFinalTopK())
                .toList();

        log.info("Global memory search for '{}': {} results (vector={}, merged={})",
                truncate(query, 50), merged.size(), indexer.isVectorSearchEnabled(), resultMap.size());

        return merged;
    }

    /**
     * 仅向量检索
     *
     * @param query 检索查询
     * @param topK  返回数量
     * @return 检索结果列表
     */
    public List<SearchResult> searchByVectorOnly(String query, int topK) {
        if (!indexer.isVectorSearchEnabled()) {
            log.debug("Vector search not available");
            return List.of();
        }
        return searchByVector(query, topK);
    }

    /**
     * 仅 FTS 检索
     *
     * @param query 检索查询
     * @param topK  返回数量
     * @return 检索结果列表
     */
    public List<SearchResult> searchByFtsOnly(String query, int topK) {
        return searchByFts(query, topK);
    }

    /**
     * 向量检索实现
     */
    private List<SearchResult> searchByVector(String query, int topK) {
        EmbeddingModel embeddingModel = indexer.getEmbeddingModel();
        if (embeddingModel == null) {
            return List.of();
        }

        // 生成查询向量
        float[] queryVector = embeddingModel.embed(query);
        if (queryVector == null || queryVector.length == 0) {
            log.warn("Failed to generate query embedding");
            return List.of();
        }

        // 格式化为 PostgreSQL vector 字符串
        String vectorStr = formatVector(queryVector);

        // 执行检索
        List<Object[]> rows = chunkRepository.searchByVector(vectorStr, topK);
        int snippetMax = properties.getSearch().getSnippetMaxChars();

        return rows.stream()
                .map(row -> SearchResult.builder()
                        .filePath((String) row[1])
                        .lineStart(((Number) row[2]).intValue())
                        .lineEnd(((Number) row[3]).intValue())
                        .snippet(truncate((String) row[4], snippetMax))
                        .score(((Number) row[5]).doubleValue())
                        .source("vector")
                        .build())
                .toList();
    }

    /**
     * 全文检索实现
     */
    private List<SearchResult> searchByFts(String query, int topK) {
        List<Object[]> rows = chunkRepository.searchByFts(query, topK);
        int snippetMax = properties.getSearch().getSnippetMaxChars();

        return rows.stream()
                .map(row -> SearchResult.builder()
                        .filePath((String) row[1])
                        .lineStart(((Number) row[2]).intValue())
                        .lineEnd(((Number) row[3]).intValue())
                        .snippet(truncate((String) row[4], snippetMax))
                        .score(normalizeRank(((Number) row[5]).doubleValue()))
                        .source("fts")
                        .build())
                .toList();
    }

    /**
     * 将 FTS rank 归一化到 0~1 区间
     * ts_rank 通常在 0~1 之间，但不保证
     */
    private double normalizeRank(double rank) {
        return Math.min(1.0, Math.max(0.0, rank));
    }

    /**
     * 格式化向量为 PostgreSQL vector 字符串
     */
    private String formatVector(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 截断字符串
     */
    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
}
