package com.jaguarliu.ai.runtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 取消管理器
 * 管理 run 的取消请求，支持外部取消正在执行的 ReAct 循环
 */
@Slf4j
@Component
public class CancellationManager {

    /**
     * 取消标记注册表：runId -> 是否已取消
     */
    private final Map<String, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();

    /**
     * 请求取消指定的 run
     *
     * @param runId 运行 ID
     */
    public void requestCancel(String runId) {
        cancelFlags.computeIfAbsent(runId, k -> new AtomicBoolean(false)).set(true);
        log.info("Cancellation requested: runId={}", runId);
    }

    /**
     * 检查 run 是否已被取消
     *
     * @param runId 运行 ID
     * @return 是否已取消
     */
    public boolean isCancelled(String runId) {
        AtomicBoolean flag = cancelFlags.get(runId);
        return flag != null && flag.get();
    }

    /**
     * 清理取消标记（run 结束后调用）
     *
     * @param runId 运行 ID
     */
    public void clearCancellation(String runId) {
        cancelFlags.remove(runId);
        log.debug("Cancellation cleared: runId={}", runId);
    }

    /**
     * 注册 run（开始执行时调用）
     *
     * @param runId 运行 ID
     */
    public void register(String runId) {
        cancelFlags.put(runId, new AtomicBoolean(false));
        log.debug("Run registered for cancellation tracking: runId={}", runId);
    }
}
