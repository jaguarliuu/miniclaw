package com.jaguarliu.ai.memory.index;

import com.jaguarliu.ai.memory.MemoryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 记忆文本分块器
 *
 * 策略：
 * - 按行累积直到达到目标 token 数
 * - 目标 ~400 tokens/chunk，overlap ~80 tokens
 * - token 估算：中文字符 ×2，其他字符 ×0.3
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryChunker {

    private final MemoryProperties properties;

    /**
     * 将文件内容切分为 chunks
     *
     * @param filePath 文件相对路径
     * @param content  文件内容
     * @return chunk 列表
     */
    public List<MemoryChunk> chunk(String filePath, String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        int targetTokens = properties.getChunk().getTargetTokens();
        int overlapTokens = properties.getChunk().getOverlapTokens();

        String[] lines = content.split("\n", -1);
        List<MemoryChunk> chunks = new ArrayList<>();

        int currentStart = 0; // 0-based line index

        while (currentStart < lines.length) {
            // 向前累积直到达到目标 token 数
            int currentEnd = currentStart;
            int tokenCount = 0;

            while (currentEnd < lines.length && tokenCount < targetTokens) {
                tokenCount += estimateTokens(lines[currentEnd]);
                currentEnd++;
            }

            // 提取 chunk 内容
            StringBuilder chunkContent = new StringBuilder();
            for (int i = currentStart; i < currentEnd; i++) {
                if (i > currentStart) chunkContent.append("\n");
                chunkContent.append(lines[i]);
            }

            String text = chunkContent.toString().trim();
            if (!text.isEmpty()) {
                chunks.add(MemoryChunk.builder()
                        .filePath(filePath)
                        .lineStart(currentStart + 1)   // 转 1-based
                        .lineEnd(currentEnd)            // 已经是下一行，刚好是含末行的 1-based
                        .content(text)
                        .build());
            }

            // 如果已到文件末尾，退出
            if (currentEnd >= lines.length) {
                break;
            }

            // 计算下一个 chunk 的起始位置（回退 overlap）
            int overlapLines = 0;
            int overlapCount = 0;
            for (int i = currentEnd - 1; i > currentStart && overlapCount < overlapTokens; i--) {
                overlapCount += estimateTokens(lines[i]);
                overlapLines++;
            }

            int nextStart = currentEnd - overlapLines;
            // 防止无进展：确保至少前进一行
            if (nextStart <= currentStart) {
                nextStart = currentStart + 1;
            }
            currentStart = nextStart;
        }

        log.debug("Chunked {}: {} chunks from {} lines", filePath, chunks.size(), lines.length);
        return chunks;
    }

    /**
     * 估算单行 token 数
     * 简单规则：中文字符 ×2，其他字符 ×0.3
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 1; // 空行算 1 token

        int chineseCount = 0;
        int otherCount = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                chineseCount++;
            } else {
                otherCount++;
            }
        }
        return Math.max(1, (int) (chineseCount * 2 + otherCount * 0.3));
    }
}
