package com.jaguarliu.ai.runtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * RunContext 单元测试
 *
 * 测试覆盖：
 * 1. 默认值（main run）
 * 2. SubAgent 上下文创建
 * 3. 辅助方法（isSubagent/isMain）
 * 4. 状态检查（超时、取消、步数）
 */
@DisplayName("RunContext Tests")
class RunContextTest {

    private LoopConfig loopConfig;
    private CancellationManager cancellationManager;

    @BeforeEach
    void setUp() {
        loopConfig = mock(LoopConfig.class);
        when(loopConfig.getMaxSteps()).thenReturn(50);
        when(loopConfig.getRunTimeoutSeconds()).thenReturn(300L);

        cancellationManager = mock(CancellationManager.class);
    }

    // ==================== 默认值测试（Main Run）====================

    @Nested
    @DisplayName("Main Run Context")
    class MainRunContextTests {

        @Test
        @DisplayName("create() 创建默认 main run 上下文")
        void createDefaultMainContext() {
            RunContext context = RunContext.create(
                    "run-123",
                    "conn-456",
                    "session-789",
                    loopConfig,
                    cancellationManager
            );

            assertEquals("run-123", context.getRunId());
            assertEquals("conn-456", context.getConnectionId());
            assertEquals("session-789", context.getSessionId());
            assertEquals("main", context.getAgentId());
            assertEquals("main", context.getRunKind());
            assertEquals("main", context.getLane());
            assertEquals(0, context.getDepth());
            assertFalse(context.isDeliver());
            assertNull(context.getParentRunId());
            assertNull(context.getRequesterSessionId());
        }

        @Test
        @DisplayName("main run isMain() 返回 true")
        void mainRunIsMainTrue() {
            RunContext context = RunContext.create(
                    "run-123", "conn-456", "session-789",
                    loopConfig, cancellationManager
            );

            assertTrue(context.isMain());
            assertFalse(context.isSubagent());
        }

        @Test
        @DisplayName("currentStep 初始值为 0")
        void initialStepIsZero() {
            RunContext context = RunContext.create(
                    "run-123", "conn-456", "session-789",
                    loopConfig, cancellationManager
            );

            assertEquals(0, context.getCurrentStep());
        }

        @Test
        @DisplayName("startTime 应为创建时间附近")
        void startTimeIsNow() {
            Instant before = Instant.now();
            RunContext context = RunContext.create(
                    "run-123", "conn-456", "session-789",
                    loopConfig, cancellationManager
            );
            Instant after = Instant.now();

            assertTrue(context.getStartTime().isAfter(before.minusMillis(100)));
            assertTrue(context.getStartTime().isBefore(after.plusMillis(100)));
        }
    }

    // ==================== SubAgent 上下文测试 ====================

    @Nested
    @DisplayName("SubAgent Run Context")
    class SubagentRunContextTests {

        @Test
        @DisplayName("createSubagent() 创建子代理上下文")
        void createSubagentContext() {
            RunContext context = RunContext.createSubagent(
                    "sub-run-123",
                    "conn-456",
                    "sub-session-789",
                    "researcher",
                    "parent-run-000",
                    "parent-session-000",
                    true,
                    loopConfig,
                    cancellationManager
            );

            assertEquals("sub-run-123", context.getRunId());
            assertEquals("conn-456", context.getConnectionId());
            assertEquals("sub-session-789", context.getSessionId());
            assertEquals("researcher", context.getAgentId());
            assertEquals("subagent", context.getRunKind());
            assertEquals("subagent", context.getLane());
            assertEquals("parent-run-000", context.getParentRunId());
            assertEquals("parent-session-000", context.getRequesterSessionId());
            assertEquals(1, context.getDepth());
            assertTrue(context.isDeliver());
        }

        @Test
        @DisplayName("subagent run isSubagent() 返回 true")
        void subagentRunIsSubagentTrue() {
            RunContext context = RunContext.createSubagent(
                    "sub-run-123", "conn-456", "sub-session-789",
                    "main", "parent-run-000", "parent-session-000",
                    false, loopConfig, cancellationManager
            );

            assertTrue(context.isSubagent());
            assertFalse(context.isMain());
        }

        @Test
        @DisplayName("deliver=false 时 isDeliver() 返回 false")
        void deliverFalse() {
            RunContext context = RunContext.createSubagent(
                    "sub-run-123", "conn-456", "sub-session-789",
                    "main", "parent-run-000", "parent-session-000",
                    false, loopConfig, cancellationManager
            );

            assertFalse(context.isDeliver());
        }
    }

    // ==================== Builder 测试 ====================

    @Nested
    @DisplayName("Builder Pattern")
    class BuilderTests {

        @Test
        @DisplayName("使用 builder 创建自定义上下文")
        void customBuilderContext() {
            RunContext context = RunContext.builder()
                    .runId("custom-run")
                    .connectionId("custom-conn")
                    .sessionId("custom-session")
                    .agentId("custom-agent")
                    .runKind("subagent")
                    .lane("subagent")
                    .parentRunId("parent-run")
                    .requesterSessionId("requester-session")
                    .depth(2)
                    .deliver(true)
                    .startTime(Instant.now())
                    .config(loopConfig)
                    .cancellationManager(cancellationManager)
                    .currentStep(5)
                    .build();

            assertEquals("custom-run", context.getRunId());
            assertEquals("custom-agent", context.getAgentId());
            assertEquals("subagent", context.getRunKind());
            assertEquals(2, context.getDepth());
            assertEquals(5, context.getCurrentStep());
            assertTrue(context.isDeliver());
        }

        @Test
        @DisplayName("builder 默认值正确")
        void builderDefaultValues() {
            RunContext context = RunContext.builder()
                    .runId("run-123")
                    .connectionId("conn-456")
                    .sessionId("session-789")
                    .startTime(Instant.now())
                    .config(loopConfig)
                    .cancellationManager(cancellationManager)
                    .build();

            assertEquals("main", context.getAgentId());
            assertEquals("main", context.getRunKind());
            assertEquals("main", context.getLane());
            assertEquals(0, context.getDepth());
            assertFalse(context.isDeliver());
        }
    }

    // ==================== 状态检查测试 ====================

    @Nested
    @DisplayName("State Checks")
    class StateCheckTests {

        @Test
        @DisplayName("incrementStep 增加步数")
        void incrementStepIncreasesCount() {
            RunContext context = RunContext.create(
                    "run-123", "conn-456", "session-789",
                    loopConfig, cancellationManager
            );

            assertEquals(0, context.getCurrentStep());
            context.incrementStep();
            assertEquals(1, context.getCurrentStep());
            context.incrementStep();
            assertEquals(2, context.getCurrentStep());
        }

        @Test
        @DisplayName("isMaxStepsReached 在达到最大步数时返回 true")
        void maxStepsReached() {
            when(loopConfig.getMaxSteps()).thenReturn(3);

            RunContext context = RunContext.create(
                    "run-123", "conn-456", "session-789",
                    loopConfig, cancellationManager
            );

            assertFalse(context.isMaxStepsReached());
            context.incrementStep(); // 1
            assertFalse(context.isMaxStepsReached());
            context.incrementStep(); // 2
            assertFalse(context.isMaxStepsReached());
            context.incrementStep(); // 3
            assertTrue(context.isMaxStepsReached());
        }

        @Test
        @DisplayName("isAborted 委托给 cancellationManager")
        void isAbortedDelegatesToCancellationManager() {
            when(cancellationManager.isCancelled("run-123")).thenReturn(false);

            RunContext context = RunContext.create(
                    "run-123", "conn-456", "session-789",
                    loopConfig, cancellationManager
            );

            assertFalse(context.isAborted());

            when(cancellationManager.isCancelled("run-123")).thenReturn(true);
            assertTrue(context.isAborted());
        }

        @Test
        @DisplayName("getElapsedSeconds 返回正确的已用时间")
        void elapsedSecondsCorrect() throws InterruptedException {
            RunContext context = RunContext.create(
                    "run-123", "conn-456", "session-789",
                    loopConfig, cancellationManager
            );

            // 等待一小段时间
            Thread.sleep(100);

            long elapsed = context.getElapsedSeconds();
            assertTrue(elapsed >= 0);
            assertTrue(elapsed < 5); // 应该很短
        }
    }

    // ==================== 可变属性测试 ====================

    @Nested
    @DisplayName("Mutable Properties")
    class MutablePropertiesTests {

        @Test
        @DisplayName("可以设置 originalInput")
        void setOriginalInput() {
            RunContext context = RunContext.create(
                    "run-123", "conn-456", "session-789",
                    loopConfig, cancellationManager
            );

            assertNull(context.getOriginalInput());
            context.setOriginalInput("Hello, world!");
            assertEquals("Hello, world!", context.getOriginalInput());
        }

        @Test
        @DisplayName("可以设置 activeSkill")
        void setActiveSkill() {
            RunContext context = RunContext.create(
                    "run-123", "conn-456", "session-789",
                    loopConfig, cancellationManager
            );

            assertNull(context.getActiveSkill());
            // activeSkill 是复杂类型，这里只测试 null 检查
        }

        @Test
        @DisplayName("可以设置 skillBasePath")
        void setSkillBasePath() {
            RunContext context = RunContext.create(
                    "run-123", "conn-456", "session-789",
                    loopConfig, cancellationManager
            );

            assertNull(context.getSkillBasePath());
            context.setSkillBasePath(java.nio.file.Path.of("/tmp/skills/test"));
            assertEquals(java.nio.file.Path.of("/tmp/skills/test"), context.getSkillBasePath());
        }
    }
}
