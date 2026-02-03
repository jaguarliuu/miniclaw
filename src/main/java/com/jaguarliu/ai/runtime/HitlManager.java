package com.jaguarliu.ai.runtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HITL (Human-in-the-Loop) 确认管理器
 * 使用 Reactor Sinks.One 实现异步等待用户决策
 */
@Slf4j
@Component
public class HitlManager {

    /**
     * 待确认的请求：callId -> Sinks.One<HitlDecision>
     */
    private final Map<String, Sinks.One<HitlDecision>> pendingConfirmations = new ConcurrentHashMap<>();

    /**
     * 确认超时时间（秒）
     */
    @Value("${agent.hitl.timeout-seconds:300}")
    private long timeoutSeconds;

    /**
     * 请求确认
     * 创建一个 Sinks.One 并返回 Mono，调用方阻塞等待用户决策
     *
     * @param callId    工具调用 ID
     * @param toolName  工具名称（用于日志）
     * @return 用户的决策
     */
    public Mono<HitlDecision> requestConfirmation(String callId, String toolName) {
        Sinks.One<HitlDecision> sink = Sinks.one();
        pendingConfirmations.put(callId, sink);

        log.info("HITL confirmation requested: callId={}, tool={}", callId, toolName);

        return sink.asMono()
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .doOnError(e -> {
                    log.warn("HITL confirmation timeout: callId={}, tool={}", callId, toolName);
                    pendingConfirmations.remove(callId);
                })
                .onErrorReturn(HitlDecision.reject())
                .doFinally(signal -> pendingConfirmations.remove(callId));
    }

    /**
     * 提交确认决策（由 RPC Handler 调用）
     *
     * @param callId   工具调用 ID
     * @param decision 用户决策
     * @return 是否成功（callId 是否存在）
     */
    public boolean submitDecision(String callId, HitlDecision decision) {
        Sinks.One<HitlDecision> sink = pendingConfirmations.get(callId);

        if (sink == null) {
            log.warn("No pending confirmation for callId={}", callId);
            return false;
        }

        Sinks.EmitResult result = sink.tryEmitValue(decision);

        if (result.isSuccess()) {
            log.info("HITL decision submitted: callId={}, action={}", callId, decision.getAction());
            return true;
        } else {
            log.warn("Failed to submit HITL decision: callId={}, result={}", callId, result);
            return false;
        }
    }

    /**
     * 检查是否有待确认的请求
     */
    public boolean hasPending(String callId) {
        return pendingConfirmations.containsKey(callId);
    }

    /**
     * 获取待确认数量
     */
    public int getPendingCount() {
        return pendingConfirmations.size();
    }
}
