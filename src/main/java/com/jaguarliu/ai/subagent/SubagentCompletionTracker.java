package com.jaguarliu.ai.subagent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * SubAgent 完成跟踪器
 *
 * 用于主循环等待子代理完成：
 * 1. spawn 时 register → 创建 CompletableFuture
 * 2. announce/failure 时 complete → 完成 Future
 * 3. AgentRuntime 在循环退出前 wait → 阻塞等待所有 Future
 */
@Slf4j
@Component
public class SubagentCompletionTracker {

    private final ConcurrentHashMap<String, CompletableFuture<SubagentResult>> pending = new ConcurrentHashMap<>();

    /**
     * 注册一个待完成的子代理
     *
     * @param subRunId 子运行 ID
     * @return CompletableFuture，在子代理完成时 resolve
     */
    public CompletableFuture<SubagentResult> register(String subRunId) {
        CompletableFuture<SubagentResult> future = new CompletableFuture<>();
        pending.put(subRunId, future);
        log.debug("Registered pending subagent: subRunId={}", subRunId);
        return future;
    }

    /**
     * 标记子代理完成
     *
     * @param subRunId 子运行 ID
     * @param result   完成结果
     */
    public void complete(String subRunId, SubagentResult result) {
        CompletableFuture<SubagentResult> future = pending.remove(subRunId);
        if (future != null) {
            future.complete(result);
            log.debug("Completed pending subagent: subRunId={}, status={}", subRunId, result.status());
        } else {
            log.debug("No pending future for subRunId={} (may have timed out or already completed)", subRunId);
        }
    }

    /**
     * 获取子代理的 CompletableFuture
     *
     * @param subRunId 子运行 ID
     * @return future（可能为 null）
     */
    public CompletableFuture<SubagentResult> getFuture(String subRunId) {
        return pending.get(subRunId);
    }

    /**
     * 子代理完成结果
     */
    public record SubagentResult(
            String subRunId,
            String task,
            String status,       // "completed" or "failed"
            String result,       // 成功时的结果文本
            String error,        // 失败时的错误信息
            long durationMs
    ) {
        public boolean isSuccess() {
            return "completed".equals(status);
        }
    }
}
