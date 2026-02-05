package com.jaguarliu.ai.memory.search;

import lombok.Builder;
import lombok.Data;

/**
 * 记忆检索结果
 */
@Data
@Builder
public class SearchResult {
    /** 来源文件路径（相对于 memory 目录） */
    private String filePath;
    /** 起始行号 */
    private int lineStart;
    /** 结束行号 */
    private int lineEnd;
    /** 内容片段（截断到 snippetMaxChars） */
    private String snippet;
    /** 相关性评分 (0~1) */
    private double score;
    /** 检索来源（vector / fts） */
    private String source;

    /**
     * 生成去重用的 key
     */
    public String getDedupeKey() {
        return filePath + ":" + lineStart;
    }
}
