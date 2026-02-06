package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.agents.AgentRegistry;
import com.jaguarliu.ai.agents.AgentsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * LaneAwareQueueManager 单元测试
 *
 * 测试覆盖：
 * 1. 同 session 串行执行
 * 2. main/subagent lane 独立并发上限
 * 3. subagent 不影响 main 可用并发
 * 4. 序号顺序保证
 */
@DisplayName("LaneAwareQueueManager Tests")
class LaneAwareQueueManagerTest {

    private AgentRegistry agentRegistry;
    private LaneAwareQueueManager queueManager;

    @BeforeEach
    void setUp() {
        AgentsProperties properties = new AgentsProperties();
        AgentsProperties.LaneConfig laneConfig = new AgentsProperties.LaneConfig();
        laneConfig.setMainMaxConcurrency(2);
        laneConfig.setSubagentMaxConcurrency(3);
        properties.setLane(laneConfig);

        agentRegistry = mock(AgentRegistry.class);
        when(agentRegistry.getLaneConfig()).thenReturn(laneConfig);

        queueManager = new LaneAwareQueueManager(agentRegistry);
    }

    // ==================== 初始化测试 ====================

    @Nested
    @DisplayName("Initialization")
    class InitializationTests {

        @Test
        @DisplayName("正确加载 lane 配置")
        void loadLaneConfig() {
            assertEquals(2, queueManager.getMainMaxConcurrency());
            assertEquals(3, queueManager.getSubagentMaxConcurrency());
        }

        @Test
        @DisplayName("初始运行数为 0")
        void initialRunningCountIsZero() {
            assertEquals(0, queueManager.getMainRunning());
            assertEquals(0, queueManager.getSubagentRunning());
        }

        @Test
        @DisplayName("初始可用配额等于最大并发数")
        void initialAvailableEqualsMax() {
            assertEquals(2, queueManager.getMainAvailable());
            assertEquals(3, queueManager.getSubagentAvailable());
        }
    }

    // ==================== 同 Session 串行测试 ====================

    @Nested
    @DisplayName("Same Session Serialization")
    class SameSessionSerializationTests {

        @Test
        @DisplayName("同 session 任务按提交顺序串行执行")
        void tasksExecuteInSubmitOrder() throws InterruptedException {
            String sessionId = "session-1";
            List<Integer> executionOrder = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch latch = new CountDownLatch(3);

            // 按顺序分配序号并提交（模拟实际使用场景）
            for (int i = 1; i <= 3; i++) {
                long seq = queueManager.nextSequence(sessionId);
                int taskId = i;
                queueManager.submit(sessionId, "run-" + i, seq, LaneAwareQueueManager.LANE_MAIN, () -> {
                    executionOrder.add(taskId);
                    latch.countDown();
                    return "done-" + taskId;
                }).subscribe();
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS), "Tasks should complete within timeout");

            // 应该按提交顺序执行：1, 2, 3
            assertEquals(List.of(1, 2, 3), executionOrder);
        }

        @Test
        @DisplayName("同 session 任务串行执行（无并发）")
        void tasksExecuteSerially() throws InterruptedException {
            String sessionId = "session-1";
            AtomicInteger maxConcurrent = new AtomicInteger(0);
            AtomicInteger currentConcurrent = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(3);

            for (int i = 0; i < 3; i++) {
                long seq = queueManager.nextSequence(sessionId);
                int taskId = i;
                queueManager.submit(sessionId, "run-" + i, seq, LaneAwareQueueManager.LANE_MAIN, () -> {
                    int concurrent = currentConcurrent.incrementAndGet();
                    maxConcurrent.updateAndGet(max -> Math.max(max, concurrent));
                    try {
                        Thread.sleep(50); // 模拟执行时间
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    currentConcurrent.decrementAndGet();
                    latch.countDown();
                    return "done-" + taskId;
                }).subscribe();
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS), "Tasks should complete within timeout");

            // 同 session 应该串行，最大并发为 1
            assertEquals(1, maxConcurrent.get());
        }
    }

    // ==================== 不同 Session 并行测试 ====================

    @Nested
    @DisplayName("Different Sessions Parallelism")
    class DifferentSessionsParallelismTests {

        @Test
        @DisplayName("不同 session 可以并行执行")
        void differentSessionsCanRunInParallel() throws InterruptedException {
            AtomicInteger maxConcurrent = new AtomicInteger(0);
            AtomicInteger currentConcurrent = new AtomicInteger(0);
            CountDownLatch startLatch = new CountDownLatch(2);
            CountDownLatch endLatch = new CountDownLatch(2);

            // 两个不同 session 的任务
            for (int i = 0; i < 2; i++) {
                String sessionId = "session-" + i;
                long seq = queueManager.nextSequence(sessionId);
                queueManager.submit(sessionId, "run-" + i, seq, LaneAwareQueueManager.LANE_MAIN, () -> {
                    int concurrent = currentConcurrent.incrementAndGet();
                    maxConcurrent.updateAndGet(max -> Math.max(max, concurrent));
                    startLatch.countDown();
                    try {
                        Thread.sleep(100); // 模拟执行时间
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    currentConcurrent.decrementAndGet();
                    endLatch.countDown();
                    return "done";
                }).subscribe();
            }

            assertTrue(endLatch.await(5, TimeUnit.SECONDS), "Tasks should complete within timeout");

            // 不同 session 应该并行，最大并发为 2
            assertEquals(2, maxConcurrent.get());
        }
    }

    // ==================== Lane 并发限制测试 ====================

    @Nested
    @DisplayName("Lane Concurrency Limits")
    class LaneConcurrencyLimitsTests {

        @Test
        @DisplayName("main lane 并发不超过配置上限")
        void mainLaneRespectsLimit() throws InterruptedException {
            AtomicInteger maxConcurrent = new AtomicInteger(0);
            AtomicInteger currentConcurrent = new AtomicInteger(0);
            int taskCount = 5; // 超过 main 上限 (2)
            CountDownLatch latch = new CountDownLatch(taskCount);

            for (int i = 0; i < taskCount; i++) {
                String sessionId = "session-" + i; // 不同 session
                long seq = queueManager.nextSequence(sessionId);
                queueManager.submit(sessionId, "run-" + i, seq, LaneAwareQueueManager.LANE_MAIN, () -> {
                    int concurrent = currentConcurrent.incrementAndGet();
                    maxConcurrent.updateAndGet(max -> Math.max(max, concurrent));
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    currentConcurrent.decrementAndGet();
                    latch.countDown();
                    return "done";
                }).subscribe();
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS), "Tasks should complete within timeout");

            // main lane 最大并发应该是 2
            assertEquals(2, maxConcurrent.get());
        }

        @Test
        @DisplayName("subagent lane 并发不超过配置上限")
        void subagentLaneRespectsLimit() throws InterruptedException {
            AtomicInteger maxConcurrent = new AtomicInteger(0);
            AtomicInteger currentConcurrent = new AtomicInteger(0);
            int taskCount = 6; // 超过 subagent 上限 (3)
            CountDownLatch latch = new CountDownLatch(taskCount);

            for (int i = 0; i < taskCount; i++) {
                String sessionId = "sub-session-" + i;
                long seq = queueManager.nextSequence(sessionId);
                queueManager.submit(sessionId, "run-" + i, seq, LaneAwareQueueManager.LANE_SUBAGENT, () -> {
                    int concurrent = currentConcurrent.incrementAndGet();
                    maxConcurrent.updateAndGet(max -> Math.max(max, concurrent));
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    currentConcurrent.decrementAndGet();
                    latch.countDown();
                    return "done";
                }).subscribe();
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS), "Tasks should complete within timeout");

            // subagent lane 最大并发应该是 3
            assertEquals(3, maxConcurrent.get());
        }
    }

    // ==================== Lane 隔离测试 ====================

    @Nested
    @DisplayName("Lane Isolation")
    class LaneIsolationTests {

        @Test
        @DisplayName("subagent 不影响 main 可用配额")
        void subagentDoesNotAffectMainQuota() throws InterruptedException {
            AtomicInteger mainMaxConcurrent = new AtomicInteger(0);
            AtomicInteger mainCurrentConcurrent = new AtomicInteger(0);
            CountDownLatch subagentStarted = new CountDownLatch(3);
            CountDownLatch mainCompleted = new CountDownLatch(2);
            CountDownLatch allCompleted = new CountDownLatch(5);

            // 先启动 3 个 subagent 任务占满 subagent lane
            for (int i = 0; i < 3; i++) {
                String sessionId = "sub-session-" + i;
                long seq = queueManager.nextSequence(sessionId);
                queueManager.submit(sessionId, "sub-run-" + i, seq, LaneAwareQueueManager.LANE_SUBAGENT, () -> {
                    subagentStarted.countDown();
                    try {
                        Thread.sleep(300); // 较长执行时间
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    allCompleted.countDown();
                    return "done";
                }).subscribe();
            }

            // 等待 subagent 任务开始
            assertTrue(subagentStarted.await(2, TimeUnit.SECONDS), "Subagent tasks should start");

            // 此时 subagent lane 已满，但 main lane 应该仍可用
            assertEquals(0, queueManager.getSubagentAvailable());
            assertEquals(2, queueManager.getMainAvailable());

            // 提交 2 个 main 任务
            for (int i = 0; i < 2; i++) {
                String sessionId = "main-session-" + i;
                long seq = queueManager.nextSequence(sessionId);
                queueManager.submit(sessionId, "main-run-" + i, seq, LaneAwareQueueManager.LANE_MAIN, () -> {
                    int concurrent = mainCurrentConcurrent.incrementAndGet();
                    mainMaxConcurrent.updateAndGet(max -> Math.max(max, concurrent));
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    mainCurrentConcurrent.decrementAndGet();
                    mainCompleted.countDown();
                    allCompleted.countDown();
                    return "done";
                }).subscribe();
            }

            // main 任务应该能正常完成，不被 subagent 阻塞
            assertTrue(mainCompleted.await(2, TimeUnit.SECONDS), "Main tasks should complete without being blocked by subagent");

            // main lane 最大并发应该是 2
            assertEquals(2, mainMaxConcurrent.get());

            // 等待所有任务完成
            assertTrue(allCompleted.await(5, TimeUnit.SECONDS), "All tasks should complete");
        }
    }

    // ==================== 序号生成测试 ====================

    @Nested
    @DisplayName("Sequence Generation")
    class SequenceGenerationTests {

        @Test
        @DisplayName("同 session 序号递增")
        void sequenceIncrementsForSameSession() {
            String sessionId = "session-1";

            long seq1 = queueManager.nextSequence(sessionId);
            long seq2 = queueManager.nextSequence(sessionId);
            long seq3 = queueManager.nextSequence(sessionId);

            assertEquals(0, seq1);
            assertEquals(1, seq2);
            assertEquals(2, seq3);
        }

        @Test
        @DisplayName("不同 session 序号独立")
        void sequenceIndependentForDifferentSessions() {
            long seq1 = queueManager.nextSequence("session-1");
            long seq2 = queueManager.nextSequence("session-2");
            long seq3 = queueManager.nextSequence("session-1");

            assertEquals(0, seq1);
            assertEquals(0, seq2); // 不同 session 独立从 0 开始
            assertEquals(1, seq3);
        }
    }

    // ==================== 兼容接口测试 ====================

    @Nested
    @DisplayName("Backward Compatibility")
    class BackwardCompatibilityTests {

        @Test
        @DisplayName("4 参数 submit 默认使用 main lane")
        void fourArgSubmitUsesMainLane() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicInteger mainLaneUsed = new AtomicInteger(0);

            String sessionId = "session-1";
            long seq = queueManager.nextSequence(sessionId);

            // 使用 4 参数版本（不指定 lane）
            queueManager.submit(sessionId, "run-1", seq, () -> {
                // 检查 main lane 的运行数增加了
                if (queueManager.getMainAvailable() < queueManager.getMainMaxConcurrency()) {
                    mainLaneUsed.incrementAndGet();
                }
                latch.countDown();
                return "done";
            }).subscribe();

            assertTrue(latch.await(2, TimeUnit.SECONDS));
        }
    }
}
