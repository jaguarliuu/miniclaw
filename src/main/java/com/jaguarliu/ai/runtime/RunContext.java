package com.jaguarliu.ai.runtime;

import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;

/**
 * ReAct 循环运行上下文
 * 封装运行时状态、配置和控制信号
 */
@Getter
@Builder
public class RunContext {

    /**
     * 运行 ID
     */
    private final String runId;

    /**
     * 连接 ID（用于事件推送）
     */
    private final String connectionId;

    /**
     * 会话 ID
     */
    private final String sessionId;

    /**
     * 循环开始时间
     */
    private final Instant startTime;

    /**
     * 循环配置
     */
    private final LoopConfig config;

    /**
     * 取消管理器
     */
    private final CancellationManager cancellationManager;

    /**
     * 当前步数
     */
    @Builder.Default
    private int currentStep = 0;

    /**
     * 检查是否已被取消
     */
    public boolean isAborted() {
        return cancellationManager.isCancelled(runId);
    }

    /**
     * 检查是否已超时（整个循环）
     */
    public boolean isTimedOut() {
        long elapsedSeconds = Duration.between(startTime, Instant.now()).getSeconds();
        return elapsedSeconds > config.getRunTimeoutSeconds();
    }

    /**
     * 检查是否达到最大步数
     */
    public boolean isMaxStepsReached() {
        return currentStep >= config.getMaxSteps();
    }

    /**
     * 增加步数
     */
    public void incrementStep() {
        this.currentStep++;
    }

    /**
     * 获取已用时间（秒）
     */
    public long getElapsedSeconds() {
        return Duration.between(startTime, Instant.now()).getSeconds();
    }

    /**
     * 创建上下文
     */
    public static RunContext create(
            String runId,
            String connectionId,
            String sessionId,
            LoopConfig config,
            CancellationManager cancellationManager) {
        return RunContext.builder()
                .runId(runId)
                .connectionId(connectionId)
                .sessionId(sessionId)
                .startTime(Instant.now())
                .config(config)
                .cancellationManager(cancellationManager)
                .currentStep(0)
                .build();
    }
}
