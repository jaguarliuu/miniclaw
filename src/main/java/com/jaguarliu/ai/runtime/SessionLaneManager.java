package com.jaguarliu.ai.runtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Session Lane 管理器
 * 确保同一 session 下的 run 严格按提交顺序串行执行，不同 session 可并行
 */
@Slf4j
@Component
public class SessionLaneManager {

    /**
     * 每个 session 的执行队列
     */
    private final Map<String, SessionLane> lanes = new ConcurrentHashMap<>();

    /**
     * 共享线程池（用于执行任务）
     */
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * 获取指定 session 的下一个序号（用于排序）
     * 必须在提交任务之前调用，确保序号分配顺序和调用顺序一致
     */
    public long nextSequence(String sessionId) {
        SessionLane lane = getOrCreateLane(sessionId);
        return lane.nextSequence();
    }

    /**
     * 提交任务到指定 session 的队列
     * @param sessionId session ID
     * @param runId run ID（用于日志）
     * @param sequence 序号（由 nextSequence 获取）
     * @param task 要执行的任务
     * @return 任务执行结果
     */
    public <T> Mono<T> submit(String sessionId, String runId, long sequence, Supplier<T> task) {
        SessionLane lane = getOrCreateLane(sessionId);
        return lane.submit(runId, sequence, task);
    }

    private SessionLane getOrCreateLane(String sessionId) {
        return lanes.computeIfAbsent(sessionId, id -> {
            log.info("Creating new lane for session: {}", id);
            return new SessionLane(id, executor);
        });
    }

    /**
     * 单个 Session 的执行队列
     */
    private static class SessionLane {
        private final String sessionId;
        private final PriorityBlockingQueue<Task<?>> queue = new PriorityBlockingQueue<>();
        private final AtomicLong sequencer = new AtomicLong(0);

        SessionLane(String sessionId, ExecutorService executor) {
            this.sessionId = sessionId;

            // 启动消费者线程
            executor.submit(() -> {
                while (true) {
                    try {
                        Task<?> task = queue.take();
                        executeTask(task);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        log.error("Lane consumer error: sessionId={}", sessionId, e);
                    }
                }
            });
        }

        long nextSequence() {
            return sequencer.getAndIncrement();
        }

        <T> Mono<T> submit(String runId, long sequence, Supplier<T> task) {
            return Mono.create(emitter -> {
                Task<T> wrappedTask = new Task<>(sequence, runId, task, emitter);
                queue.offer(wrappedTask);
                log.info("Queued run: sessionId={}, runId={}, seq={}, queueSize={}",
                        sessionId, runId, sequence, queue.size());
            });
        }

        private <T> void executeTask(Task<T> task) {
            log.info("Executing run: sessionId={}, runId={}, seq={}", sessionId, task.runId, task.seq);
            try {
                T result = task.supplier.get();
                task.emitter.success(result);
            } catch (Exception e) {
                log.error("Run failed: sessionId={}, runId={}", sessionId, task.runId, e);
                task.emitter.error(e);
            }
            log.info("Completed run: sessionId={}, runId={}, seq={}", sessionId, task.runId, task.seq);
        }

        /**
         * 包装任务，按序号排序
         */
        private record Task<T>(long seq, String runId, Supplier<T> supplier, MonoSink<T> emitter)
                implements Comparable<Task<?>> {
            @Override
            public int compareTo(Task<?> other) {
                return Long.compare(this.seq, other.seq);
            }
        }
    }
}
