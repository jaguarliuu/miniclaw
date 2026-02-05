package com.jaguarliu.ai.memory.flush;

import com.jaguarliu.ai.llm.LlmClient;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.llm.model.LlmResponse;
import com.jaguarliu.ai.memory.MemoryProperties;
import com.jaguarliu.ai.memory.index.MemoryChunker;
import com.jaguarliu.ai.memory.index.MemoryIndexer;
import com.jaguarliu.ai.memory.store.MemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pre-Compaction Memory Flush
 *
 * 当 token 估算接近窗口阈值时：
 * 1. 强制让 LLM 总结当前对话关键状态
 * 2. 写入全局记忆 memory/YYYY-MM-DD.md
 * 3. 标记本次 run 已 flush（避免重复）
 *
 * 写入的总结是全局的，后续任何会话都能检索到。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PreCompactionFlushHook {

    private final MemoryProperties properties;
    private final LlmClient llmClient;
    private final MemoryStore memoryStore;
    private final MemoryIndexer indexer;

    /** 已 flush 的 runId 集合（防止同一 run 重复 flush） */
    private final Set<String> flushedRuns = ConcurrentHashMap.newKeySet();

    private static final String FLUSH_PROMPT = """
        请总结当前对话的关键信息，包括：
        1. 用户的核心需求/问题
        2. 已完成的操作和结果
        3. 重要的决策和原因
        4. 需要记住的用户偏好或约束

        用简洁的 Markdown 格式输出，不超过 500 字。
        这份总结将被保存到全局记忆中，供未来的所有对话参考。
        """;

    /**
     * 检查是否需要 flush，如果需要则执行
     *
     * @param runId    当前运行 ID
     * @param messages 当前对话消息列表
     * @return true 如果执行了 flush
     */
    public boolean checkAndFlush(String runId, List<LlmRequest.Message> messages) {
        if (!properties.getFlush().isEnabled()) {
            return false;
        }

        // 已经 flush 过，跳过
        if (flushedRuns.contains(runId)) {
            return false;
        }

        // 估算当前 token 数
        int estimatedTokens = estimateTokens(messages);
        int threshold = properties.getFlush().getTokenThreshold();

        if (estimatedTokens < threshold) {
            return false;
        }

        log.info("Token threshold reached ({} >= {}), triggering memory flush for run {}",
                estimatedTokens, threshold, runId);

        try {
            executeFlush(runId, messages);
            flushedRuns.add(runId);
            return true;
        } catch (Exception e) {
            log.error("Memory flush failed for run {}: {}", runId, e.getMessage());
            return false;
        }
    }

    /**
     * 执行 flush：让 LLM 总结 → 写入全局日记
     */
    private void executeFlush(String runId, List<LlmRequest.Message> messages) throws Exception {
        // 构建总结请求
        List<LlmRequest.Message> summaryMessages = new ArrayList<>(messages);
        summaryMessages.add(LlmRequest.Message.user(FLUSH_PROMPT));

        LlmRequest request = LlmRequest.builder()
                .messages(summaryMessages)
                .build();

        // 同步调用 LLM 获取总结
        LlmResponse response = llmClient.chat(request);
        String summary = response.getContent();

        if (summary == null || summary.isBlank()) {
            log.warn("Flush summary is empty for run {}", runId);
            return;
        }

        // 写入全局日记文件
        String header = String.format("\n## Session Summary (%s)\n\n",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        memoryStore.appendToDaily(header + summary);

        // 更新索引
        String dailyFile = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + ".md";
        indexer.indexFile(dailyFile);

        log.info("Memory flush completed for run {}: {} chars written to global memory", runId, summary.length());
    }

    /**
     * 估算消息列表的总 token 数
     */
    private int estimateTokens(List<LlmRequest.Message> messages) {
        return messages.stream()
                .mapToInt(m -> MemoryChunker.estimateTokens(
                        m.getContent() != null ? m.getContent() : ""))
                .sum();
    }

    /**
     * 清理已完成 run 的 flush 标记
     */
    public void clearRun(String runId) {
        flushedRuns.remove(runId);
    }

    /**
     * 获取当前已 flush 的 run 数量（用于监控/测试）
     */
    public int getFlushedRunCount() {
        return flushedRuns.size();
    }
}
