package com.jaguarliu.ai.runtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.function.Supplier;

/**
 * Session Lane 管理器（兼容层）
 *
 * 委托给 LaneAwareQueueManager，保持旧接口兼容性。
 * 新代码应直接使用 LaneAwareQueueManager。
 *
 * @deprecated 使用 {@link LaneAwareQueueManager} 代替
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Deprecated
public class SessionLaneManager {

    private final LaneAwareQueueManager laneAwareQueueManager;

    /**
     * 获取指定 session 的下一个序号
     */
    public long nextSequence(String sessionId) {
        return laneAwareQueueManager.nextSequence(sessionId);
    }

    /**
     * 提交任务到指定 session 的队列（默认 main lane）
     */
    public <T> Mono<T> submit(String sessionId, String runId, long sequence, Supplier<T> task) {
        return laneAwareQueueManager.submit(sessionId, runId, sequence, LaneAwareQueueManager.LANE_MAIN, task);
    }

    /**
     * 提交任务到指定 session 和 lane 的队列
     */
    public <T> Mono<T> submit(String sessionId, String runId, long sequence, String lane, Supplier<T> task) {
        return laneAwareQueueManager.submit(sessionId, runId, sequence, lane, task);
    }
}
