package com.jaguarliu.ai.tools.search;

import lombok.Builder;
import lombok.Data;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 搜索引擎提供商接口
 */
public interface SearchProvider {

    /**
     * 提供商类型标识
     */
    String getType();

    /**
     * 提供商显示名称
     */
    String getDisplayName();

    /**
     * 执行搜索
     *
     * @param query      搜索关键词
     * @param maxResults 最大结果数
     * @return 搜索结果列表
     */
    Mono<List<SearchResult>> search(String query, int maxResults);

    /**
     * 搜索结果
     */
    @Data
    @Builder
    class SearchResult {
        private String title;
        private String url;
        private String snippet;
    }
}
