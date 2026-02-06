package com.jaguarliu.ai.subagent;

import com.jaguarliu.ai.agents.AgentRegistry;
import com.jaguarliu.ai.agents.model.AgentProfile;
import com.jaguarliu.ai.gateway.events.EventBus;
import com.jaguarliu.ai.runtime.AgentRuntime;
import com.jaguarliu.ai.runtime.CancellationManager;
import com.jaguarliu.ai.runtime.ContextBuilder;
import com.jaguarliu.ai.runtime.LaneAwareQueueManager;
import com.jaguarliu.ai.runtime.LoopConfig;
import com.jaguarliu.ai.session.MessageService;
import com.jaguarliu.ai.session.RunService;
import com.jaguarliu.ai.session.SessionService;
import com.jaguarliu.ai.storage.entity.RunEntity;
import com.jaguarliu.ai.storage.entity.SessionEntity;
import com.jaguarliu.ai.subagent.model.SubagentSpawnRequest;
import com.jaguarliu.ai.subagent.model.SubagentSpawnResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SubagentService 单元测试
 *
 * 测试覆盖：
 * 1. spawn 成功创建子 session/run
 * 2. 返回正确的 subSessionId/subRunId/sessionKey
 * 3. 非法 agentId 拒绝
 * 4. task 为空拒绝
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SubagentService Tests")
class SubagentServiceTest {

    @Mock private SessionService sessionService;
    @Mock private RunService runService;
    @Mock private MessageService messageService;
    @Mock private AgentRegistry agentRegistry;
    @Mock private LaneAwareQueueManager queueManager;
    @Mock private AgentRuntime agentRuntime;
    @Mock private ContextBuilder contextBuilder;
    @Mock private EventBus eventBus;
    @Mock private LoopConfig loopConfig;
    @Mock private CancellationManager cancellationManager;
    @Mock private SubagentAnnounceService announceService;

    private SubagentService subagentService;

    @BeforeEach
    void setUp() {
        subagentService = new SubagentService(
                sessionService,
                runService,
                messageService,
                agentRegistry,
                queueManager,
                agentRuntime,
                contextBuilder,
                eventBus,
                loopConfig,
                cancellationManager,
                announceService
        );
    }

    // ==================== Spawn 成功测试 ====================

    @Nested
    @DisplayName("Spawn Success")
    class SpawnSuccessTests {

        @BeforeEach
        void setUpMocks() {
            // Mock agentRegistry
            when(agentRegistry.isValidAgentId(anyString())).thenReturn(true);
            when(agentRegistry.getOrDefault(anyString())).thenReturn(new AgentProfile());

            // Mock session creation
            SessionEntity subSession = SessionEntity.builder()
                    .id("sub-session-123")
                    .sessionKey("agent:main:subagent:sub-session-123")
                    .build();
            when(sessionService.createSubagentSession(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(subSession);

            // Mock run creation
            RunEntity subRun = RunEntity.builder()
                    .id("sub-run-456")
                    .sessionId("sub-session-123")
                    .parentRunId("parent-run-789")
                    .requesterSessionId("parent-session-000")
                    .agentId("main")
                    .build();
            when(runService.createSubagentRun(anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean()))
                    .thenReturn(subRun);

            // Mock queue
            when(queueManager.nextSequence(anyString())).thenReturn(0L);
            when(queueManager.submit(anyString(), anyString(), anyLong(), anyString(), any()))
                    .thenReturn(Mono.empty());
        }

        @Test
        @DisplayName("spawn 成功返回 accepted=true")
        void spawnSuccessReturnsAccepted() {
            SubagentSpawnRequest request = SubagentSpawnRequest.builder()
                    .task("分析AI芯片市场")
                    .build();

            SubagentSpawnResult result = subagentService.spawn(
                    "parent-run-789",
                    "parent-session-000",
                    "main",
                    "conn-123",
                    request
            );

            assertTrue(result.isAccepted());
            assertNull(result.getError());
        }

        @Test
        @DisplayName("spawn 成功返回正确的 subSessionId")
        void spawnSuccessReturnsSubSessionId() {
            SubagentSpawnRequest request = SubagentSpawnRequest.builder()
                    .task("分析AI芯片市场")
                    .build();

            SubagentSpawnResult result = subagentService.spawn(
                    "parent-run-789",
                    "parent-session-000",
                    "main",
                    "conn-123",
                    request
            );

            assertEquals("sub-session-123", result.getSubSessionId());
        }

        @Test
        @DisplayName("spawn 成功返回正确的 subRunId")
        void spawnSuccessReturnsSubRunId() {
            SubagentSpawnRequest request = SubagentSpawnRequest.builder()
                    .task("分析AI芯片市场")
                    .build();

            SubagentSpawnResult result = subagentService.spawn(
                    "parent-run-789",
                    "parent-session-000",
                    "main",
                    "conn-123",
                    request
            );

            assertEquals("sub-run-456", result.getSubRunId());
        }

        @Test
        @DisplayName("spawn 成功返回正确的 sessionKey")
        void spawnSuccessReturnsSessionKey() {
            SubagentSpawnRequest request = SubagentSpawnRequest.builder()
                    .task("分析AI芯片市场")
                    .build();

            SubagentSpawnResult result = subagentService.spawn(
                    "parent-run-789",
                    "parent-session-000",
                    "main",
                    "conn-123",
                    request
            );

            assertEquals("agent:main:subagent:sub-session-123", result.getSessionKey());
        }

        @Test
        @DisplayName("spawn 成功返回 lane=subagent")
        void spawnSuccessReturnsSubagentLane() {
            SubagentSpawnRequest request = SubagentSpawnRequest.builder()
                    .task("分析AI芯片市场")
                    .build();

            SubagentSpawnResult result = subagentService.spawn(
                    "parent-run-789",
                    "parent-session-000",
                    "main",
                    "conn-123",
                    request
            );

            assertEquals("subagent", result.getLane());
        }

        @Test
        @DisplayName("spawn 创建子会话时传递正确参数")
        void spawnCreatesSubSessionWithCorrectParams() {
            SubagentSpawnRequest request = SubagentSpawnRequest.builder()
                    .task("分析AI芯片市场")
                    .agentId("researcher")
                    .build();

            subagentService.spawn(
                    "parent-run-789",
                    "parent-session-000",
                    "main",
                    "conn-123",
                    request
            );

            verify(sessionService).createSubagentSession(
                    eq("parent-session-000"),
                    eq("parent-run-789"),
                    eq("researcher"),
                    eq("分析AI芯片市场")
            );
        }

        @Test
        @DisplayName("spawn 创建子运行时传递正确参数")
        void spawnCreatesSubRunWithCorrectParams() {
            SubagentSpawnRequest request = SubagentSpawnRequest.builder()
                    .task("分析AI芯片市场")
                    .agentId("researcher")
                    .deliver(true)
                    .build();

            subagentService.spawn(
                    "parent-run-789",
                    "parent-session-000",
                    "main",
                    "conn-123",
                    request
            );

            verify(runService).createSubagentRun(
                    eq("sub-session-123"),
                    eq("parent-run-789"),
                    eq("parent-session-000"),
                    eq("researcher"),
                    eq("分析AI芯片市场"),
                    eq(true)
            );
        }

        @Test
        @DisplayName("spawn 提交到 subagent lane")
        void spawnSubmitsToSubagentLane() {
            SubagentSpawnRequest request = SubagentSpawnRequest.builder()
                    .task("分析AI芯片市场")
                    .build();

            subagentService.spawn(
                    "parent-run-789",
                    "parent-session-000",
                    "main",
                    "conn-123",
                    request
            );

            verify(queueManager).submit(
                    eq("sub-session-123"),
                    eq("sub-run-456"),
                    anyLong(),
                    eq(LaneAwareQueueManager.LANE_SUBAGENT),
                    any()
            );
        }

        @Test
        @DisplayName("agentId 为空时继承父 agentId")
        void emptyAgentIdInheritsFromParent() {
            SubagentSpawnRequest request = SubagentSpawnRequest.builder()
                    .task("分析AI芯片市场")
                    .agentId(null) // 不指定
                    .build();

            subagentService.spawn(
                    "parent-run-789",
                    "parent-session-000",
                    "researcher", // 父 agentId
                    "conn-123",
                    request
            );

            // 应该使用父的 agentId
            verify(sessionService).createSubagentSession(
                    anyString(),
                    anyString(),
                    eq("researcher"),
                    anyString()
            );
        }
    }

    // ==================== Spawn 失败测试 ====================

    @Nested
    @DisplayName("Spawn Failure")
    class SpawnFailureTests {

        @Test
        @DisplayName("task 为空时返回失败")
        void emptyTaskReturnsFailed() {
            SubagentSpawnRequest request = SubagentSpawnRequest.builder()
                    .task("")
                    .build();

            SubagentSpawnResult result = subagentService.spawn(
                    "parent-run-789",
                    "parent-session-000",
                    "main",
                    "conn-123",
                    request
            );

            assertFalse(result.isAccepted());
            assertEquals("Task is required", result.getError());
        }

        @Test
        @DisplayName("task 为 null 时返回失败")
        void nullTaskReturnsFailed() {
            SubagentSpawnRequest request = SubagentSpawnRequest.builder()
                    .task(null)
                    .build();

            SubagentSpawnResult result = subagentService.spawn(
                    "parent-run-789",
                    "parent-session-000",
                    "main",
                    "conn-123",
                    request
            );

            assertFalse(result.isAccepted());
            assertEquals("Task is required", result.getError());
        }

        @Test
        @DisplayName("无效 agentId 返回失败")
        void invalidAgentIdReturnsFailed() {
            when(agentRegistry.isValidAgentId("invalid-agent")).thenReturn(false);

            SubagentSpawnRequest request = SubagentSpawnRequest.builder()
                    .task("分析AI芯片市场")
                    .agentId("invalid-agent")
                    .build();

            SubagentSpawnResult result = subagentService.spawn(
                    "parent-run-789",
                    "parent-session-000",
                    "main",
                    "conn-123",
                    request
            );

            assertFalse(result.isAccepted());
            assertTrue(result.getError().contains("Invalid agentId"));
        }

        @Test
        @DisplayName("创建子会话异常时返回失败")
        void sessionCreationExceptionReturnsFailed() {
            when(agentRegistry.isValidAgentId(anyString())).thenReturn(true);
            when(agentRegistry.getOrDefault(anyString())).thenReturn(new AgentProfile());
            when(sessionService.createSubagentSession(anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("DB error"));

            SubagentSpawnRequest request = SubagentSpawnRequest.builder()
                    .task("分析AI芯片市场")
                    .build();

            SubagentSpawnResult result = subagentService.spawn(
                    "parent-run-789",
                    "parent-session-000",
                    "main",
                    "conn-123",
                    request
            );

            assertFalse(result.isAccepted());
            assertTrue(result.getError().contains("Spawn failed"));
        }
    }

    // ==================== 事件发布测试 ====================

    @Nested
    @DisplayName("Event Publishing")
    class EventPublishingTests {

        @BeforeEach
        void setUpMocks() {
            when(agentRegistry.isValidAgentId(anyString())).thenReturn(true);
            when(agentRegistry.getOrDefault(anyString())).thenReturn(new AgentProfile());

            SessionEntity subSession = SessionEntity.builder()
                    .id("sub-session-123")
                    .sessionKey("agent:main:subagent:sub-session-123")
                    .build();
            when(sessionService.createSubagentSession(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(subSession);

            RunEntity subRun = RunEntity.builder()
                    .id("sub-run-456")
                    .sessionId("sub-session-123")
                    .parentRunId("parent-run-789")
                    .build();
            when(runService.createSubagentRun(anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean()))
                    .thenReturn(subRun);

            when(queueManager.nextSequence(anyString())).thenReturn(0L);
            when(queueManager.submit(anyString(), anyString(), anyLong(), anyString(), any()))
                    .thenReturn(Mono.empty());
        }

        @Test
        @DisplayName("spawn 成功后发布 subagent.spawned 事件")
        void spawnPublishesSpawnedEvent() {
            SubagentSpawnRequest request = SubagentSpawnRequest.builder()
                    .task("分析AI芯片市场")
                    .build();

            subagentService.spawn(
                    "parent-run-789",
                    "parent-session-000",
                    "main",
                    "conn-123",
                    request
            );

            verify(eventBus).publish(argThat(event ->
                    event.getType().getValue().equals("subagent.spawned") &&
                    event.getConnectionId().equals("conn-123") &&
                    event.getRunId().equals("parent-run-789")
            ));
        }
    }
}
