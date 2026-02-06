package com.jaguarliu.ai.runtime;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.nio.file.Path;
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
     * Agent ID（关联的 Agent Profile）
     */
    @Builder.Default
    private final String agentId = "main";

    /**
     * 运行类型：main / subagent
     */
    @Builder.Default
    private final String runKind = "main";

    /**
     * 执行通道：main / subagent
     */
    @Builder.Default
    private final String lane = "main";

    /**
     * 父运行 ID（仅 subagent 有值）
     */
    private final String parentRunId;

    /**
     * 请求方会话 ID（subagent 的父会话）
     */
    private final String requesterSessionId;

    /**
     * 派生深度（main=0, 直接子代理=1）
     */
    @Builder.Default
    private final int depth = 0;

    /**
     * 是否转发中间流到父会话
     */
    @Builder.Default
    private final boolean deliver = false;

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
     * 原始用户输入（用于 skill 激活）
     */
    @Setter
    private String originalInput;

    /**
     * 当前激活的 skill（如果有）
     */
    @Setter
    private ContextBuilder.SkillAwareRequest activeSkill;

    /**
     * 当前激活的 skill 的资源目录
     */
    @Setter
    private Path skillBasePath;

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
     * 创建上下文（兼容旧接口，默认为 main run）
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
                .agentId("main")
                .runKind("main")
                .lane("main")
                .depth(0)
                .deliver(false)
                .startTime(Instant.now())
                .config(config)
                .cancellationManager(cancellationManager)
                .currentStep(0)
                .build();
    }

    /**
     * 创建子代理运行上下文
     *
     * @param runId              子运行 ID
     * @param connectionId       连接 ID
     * @param sessionId          子会话 ID
     * @param agentId            Agent Profile ID
     * @param parentRunId        父运行 ID
     * @param requesterSessionId 请求方会话 ID
     * @param deliver            是否转发中间流
     * @param config             循环配置
     * @param cancellationManager 取消管理器
     * @return 子代理运行上下文
     */
    public static RunContext createSubagent(
            String runId,
            String connectionId,
            String sessionId,
            String agentId,
            String parentRunId,
            String requesterSessionId,
            boolean deliver,
            LoopConfig config,
            CancellationManager cancellationManager) {
        return RunContext.builder()
                .runId(runId)
                .connectionId(connectionId)
                .sessionId(sessionId)
                .agentId(agentId)
                .runKind("subagent")
                .lane("subagent")
                .parentRunId(parentRunId)
                .requesterSessionId(requesterSessionId)
                .depth(1)
                .deliver(deliver)
                .startTime(Instant.now())
                .config(config)
                .cancellationManager(cancellationManager)
                .currentStep(0)
                .build();
    }

    /**
     * 判断是否为子代理运行
     */
    public boolean isSubagent() {
        return "subagent".equals(runKind);
    }

    /**
     * 判断是否为主运行
     */
    public boolean isMain() {
        return "main".equals(runKind);
    }
}
