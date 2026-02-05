package com.jaguarliu.ai.memory.index;

import lombok.Builder;
import lombok.Data;

/**
 * 记忆文本块
 * Chunking 产出的最小检索单元
 */
@Data
@Builder
public class MemoryChunk {
    /** 来源文件（相对于 memory 目录） */
    private String filePath;
    /** 起始行号（1-based） */
    private int lineStart;
    /** 结束行号（1-based，含） */
    private int lineEnd;
    /** chunk 原文 */
    private String content;
}
