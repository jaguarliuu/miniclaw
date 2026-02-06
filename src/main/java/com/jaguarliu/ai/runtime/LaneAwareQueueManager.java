package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.agents.AgentRegistry;
import com.jaguarliu.ai.agents.AgentsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Lane-aware 队列管理器
 *
 * 双层队列模型：
 * 1. Session Lane（内层）：同一 session 内的任务严格串行
 * 2. Global Lane（外层）：main 和 subagent 各有独立并发上限
 *
 * 调度策略：
 * - 同一 session 内 FIFO 串行
 * - main lane 和 subagent lane 独立配额，互不影响
 * - subagent 不可抢占 main 的配额
 */
@Slf4j
@Component
public class LaneAwareQueueManager {

    /**
     * Lane 类型
     */
    public static final String LANE_MAIN = "main";
    public static final String LANE_SUBAGENT = "subagent";

    /**
     * 每个 session 的执行队列
     */
    private final Map<String, SessionLane> sessionLanes = new ConcurrentHashMap<>();

    /**
     * main lane 并发信号量
     */
    private final Semaphore mainSemaphore;

    /**
     * subagent lane 并发信号量
     */
    private final Semaphore subagentSemaphore;

    /**
     * main lane 当前运行数
     */
    private final AtomicInteger mainRunning = new AtomicInteger(0);

    /**
     * subagent lane 当前运行数
     */
    private final AtomicInteger subagentRunning = new AtomicInteger(0);

    /**
     * main lane 最大并发数
     */
    private final int mainMaxConcurrency;

    /**
     * subagent lane 最大并发数
     */
    private final int subagentMaxConcurrency;

    /**
     * 共享线程池（用于执行任务）
     */
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "lane-worker");
        t.setDaemon(true);
        return t;
    });

    public LaneAwareQueueManager(AgentRegistry agentRegistry) {
        AgentsProperties.LaneConfig laneConfig = agentRegistry.getLaneConfig();
        this.mainMaxConcurrency = laneConfig.getMainMaxConcurrency();
        this.subagentMaxConcurrency = laneConfig.getSubagentMaxConcurrency();
        this.mainSemaphore = new Semaphore(mainMaxConcurrency);
        this.subagentSemaphore = new Semaphore(subagentMaxConcurrency);

        log.info("LaneAwareQueueManager initialized: mainMax={}, subagentMax={}",
                mainMaxConcurrency, subagentMaxConcurrency);
    }

    /**
     * 获取指定 session 的下一个序号
     */
    public long nextSequence(String sessionId) {
        SessionLane lane = getOrCreateSessionLane(sessionId);
        return lane.nextSequence();
    }

    /**
     * 提交任务到队列
     *
     * @param sessionId session ID
     * @param runId     run ID
     * @param sequence  序号
     * @param lane      执行通道（main/subagent）
     * @param task      要执行的任务
     * @return 任务执行结果
     */
    public <T> Mono<T> submit(String sessionId, String runId, long sequence, String lane, Supplier<T> task) {
        SessionLane sessionLane = getOrCreateSessionLane(sessionId);
        return sessionLane.submit(runId, sequence, lane, task);
    }

    /**
     * 提交任务到 main lane（兼容旧接口）
     */
    public <T> Mono<T> submit(String sessionId, String runId, long sequence, Supplier<T> task) {
        return submit(sessionId, runId, sequence, LANE_MAIN, task);
    }

    /**
     * 获取 main lane 当前运行数
     */
    public int getMainRunning() {
        return mainRunning.get();
    }

    /**
     * 获取 subagent lane 当前运行数
     */
    public int getSubagentRunning() {
        return subagentRunning.get();
    }

    /**
     * 获取 main lane 最大并发数
     */
    public int getMainMaxConcurrency() {
        return mainMaxConcurrency;
    }

    /**
     * 获取 subagent lane 最大并发数
     */
    public int getSubagentMaxConcurrency() {
        return subagentMaxConcurrency;
    }

    /**
     * 获取 main lane 可用配额
     */
    public int getMainAvailable() {
        return mainSemaphore.availablePermits();
    }

    /**
     * 获取 subagent lane 可用配额
     */
    public int getSubagentAvailable() {
        return subagentSemaphore.availablePermits();
    }

    private SessionLane getOrCreateSessionLane(String sessionId) {
        return sessionLanes.computeIfAbsent(sessionId, id -> {
            log.debug("Creating session lane: sessionId={}", id);
            return new SessionLane(id);
        });
    }

    /**
     * 获取指定 lane 的信号量
     */
    private Semaphore getSemaphore(String lane) {
        return LANE_SUBAGENT.equals(lane) ? subagentSemaphore : mainSemaphore;
    }

    /**
     * 获取指定 lane 的运行计数器
     */
    private AtomicInteger getRunningCounter(String lane) {
        return LANE_SUBAGENT.equals(lane) ? subagentRunning : mainRunning;
    }

    /**
     * 单个 Session 的执行队列
     * 保证同一 session 内任务串行执行
     */
    private class SessionLane {
        private final String sessionId;
        private final PriorityBlockingQueue<Task<?>> queue = new PriorityBlockingQueue<>();
        private final AtomicLong sequencer = new AtomicLong(0);

        SessionLane(String sessionId) {
            this.sessionId = sessionId;

            // 启动消费者线程
            executor.submit(this::consumeLoop);
        }

        long nextSequence() {
            return sequencer.getAndIncrement();
        }

        <T> Mono<T> submit(String runId, long sequence, String lane, Supplier<T> taskSupplier) {
            return Mono.create(emitter -> {
                Task<T> task = new Task<>(sequence, runId, lane, taskSupplier, emitter);
                queue.offer(task);
                log.info("Queued run: sessionId={}, runId={}, seq={}, lane={}, queueSize={}",
                        sessionId, runId, sequence, lane, queue.size());
            });
        }

        private void consumeLoop() {
            while (true) {
                try {
                    Task<?> task = queue.take();
                    executeWithLaneLimit(task);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.info("Session lane consumer interrupted: sessionId={}", sessionId);
                    break;
                } catch (Exception e) {
                    log.error("Session lane consumer error: sessionId={}", sessionId, e);
                }
            }
        }

        private <T> void executeWithLaneLimit(Task<T> task) {
            String lane = task.lane;
            Semaphore semaphore = getSemaphore(lane);
            AtomicInteger runningCounter = getRunningCounter(lane);

            try {
                // 获取 lane 配额（阻塞等待）
                log.debug("Acquiring lane permit: sessionId={}, runId={}, lane={}, available={}",
                        sessionId, task.runId, lane, semaphore.availablePermits());
                semaphore.acquire();
                runningCounter.incrementAndGet();

                log.info("Executing run: sessionId={}, runId={}, seq={}, lane={}, running={}/{}",
                        sessionId, task.runId, task.seq, lane,
                        runningCounter.get(),
                        LANE_SUBAGENT.equals(lane) ? subagentMaxConcurrency : mainMaxConcurrency);

                try {
                    T result = task.supplier.get();
                    task.emitter.success(result);
                } catch (Exception e) {
                    log.error("Run failed: sessionId={}, runId={}, lane={}", sessionId, task.runId, lane, e);
                    task.emitter.error(e);
                }

                log.info("Completed run: sessionId={}, runId={}, seq={}, lane={}",
                        sessionId, task.runId, task.seq, lane);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                task.emitter.error(e);
            } finally {
                // 释放 lane 配额
                runningCounter.decrementAndGet();
                semaphore.release();
                log.debug("Released lane permit: sessionId={}, runId={}, lane={}, available={}",
                        sessionId, task.runId, lane, semaphore.availablePermits());
            }
        }

        /**
         * 包装任务，按序号排序
         */
        private record Task<T>(long seq, String runId, String lane, Supplier<T> supplier, MonoSink<T> emitter)
                implements Comparable<Task<?>> {
            @Override
            public int compareTo(Task<?> other) {
                return Long.compare(this.seq, other.seq);
            }
        }
    }
}
