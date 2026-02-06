package com.jaguarliu.ai.subagent;

import com.jaguarliu.ai.gateway.events.EventBus;
import com.jaguarliu.ai.runtime.*;
import com.jaguarliu.ai.session.MessageService;
import com.jaguarliu.ai.session.RunService;
import com.jaguarliu.ai.session.SessionService;
import com.jaguarliu.ai.storage.entity.RunEntity;
import com.jaguarliu.ai.storage.entity.SessionEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SubagentOpsService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SubagentOpsService Tests")
class SubagentOpsServiceTest {

    @Mock private RunService runService;
    @Mock private SessionService sessionService;
    @Mock private MessageService messageService;
    @Mock private CancellationManager cancellationManager;
    @Mock private LaneAwareQueueManager queueManager;
    @Mock private AgentRuntime agentRuntime;
    @Mock private ContextBuilder contextBuilder;
    @Mock private EventBus eventBus;
    @Mock private LoopConfig loopConfig;
    @Mock private SubagentAnnounceService announceService;

    private SubagentOpsService opsService;

    @BeforeEach
    void setUp() {
        opsService = new SubagentOpsService(
                runService,
                sessionService,
                messageService,
                cancellationManager,
                queueManager,
                agentRuntime,
                contextBuilder,
                eventBus,
                loopConfig,
                announceService
        );
    }

    // ==================== List Tests ====================

    @Nested
    @DisplayName("List Operations")
    class ListOperationsTests {

        @Test
        @DisplayName("listByParentRun 调用 runService")
        void listByParentRunCallsRunService() {
            RunEntity subRun = createSubRun();
            when(runService.listSubagentRuns("parent-run-789")).thenReturn(List.of(subRun));

            List<RunEntity> result = opsService.listByParentRun("parent-run-789");

            assertEquals(1, result.size());
            assertEquals("sub-run-123", result.get(0).getId());
            verify(runService).listSubagentRuns("parent-run-789");
        }

        @Test
        @DisplayName("listByRequesterSession 调用 runService")
        void listByRequesterSessionCallsRunService() {
            RunEntity subRun = createSubRun();
            when(runService.listByRequesterSession("parent-session-000")).thenReturn(List.of(subRun));

            List<RunEntity> result = opsService.listByRequesterSession("parent-session-000");

            assertEquals(1, result.size());
            verify(runService).listByRequesterSession("parent-session-000");
        }
    }

    // ==================== Stop Tests ====================

    @Nested
    @DisplayName("Stop Operations")
    class StopOperationsTests {

        @Test
        @DisplayName("停止运行中的 subagent 成功")
        void stopRunningSubagentSucceeds() {
            RunEntity subRun = createSubRun();
            subRun.setStatus("running");
            when(runService.get("sub-run-123")).thenReturn(Optional.of(subRun));

            SubagentOpsService.StopResult result = opsService.stop("sub-run-123");

            assertTrue(result.success());
            assertEquals("sub-run-123", result.subRunId());
            verify(cancellationManager).requestCancel("sub-run-123");
        }

        @Test
        @DisplayName("停止不存在的 run 返回 NOT_FOUND")
        void stopNotFoundReturnsNotFound() {
            when(runService.get("not-found")).thenReturn(Optional.empty());

            SubagentOpsService.StopResult result = opsService.stop("not-found");

            assertFalse(result.success());
            assertEquals("NOT_FOUND", result.errorCode());
            verifyNoInteractions(cancellationManager);
        }

        @Test
        @DisplayName("停止非 subagent run 返回 INVALID_STATE")
        void stopNonSubagentReturnsInvalidState() {
            RunEntity mainRun = RunEntity.builder()
                    .id("main-run-123")
                    .runKind("main")
                    .status("running")
                    .build();
            when(runService.get("main-run-123")).thenReturn(Optional.of(mainRun));

            SubagentOpsService.StopResult result = opsService.stop("main-run-123");

            assertFalse(result.success());
            assertEquals("INVALID_STATE", result.errorCode());
            assertTrue(result.error().contains("Not a subagent run"));
            verifyNoInteractions(cancellationManager);
        }

        @Test
        @DisplayName("停止已完成的 run 返回 INVALID_STATE")
        void stopCompletedRunReturnsInvalidState() {
            RunEntity subRun = createSubRun();
            subRun.setStatus("done");
            when(runService.get("sub-run-123")).thenReturn(Optional.of(subRun));

            SubagentOpsService.StopResult result = opsService.stop("sub-run-123");

            assertFalse(result.success());
            assertEquals("INVALID_STATE", result.errorCode());
            verifyNoInteractions(cancellationManager);
        }
    }

    // ==================== Send Tests ====================

    @Nested
    @DisplayName("Send Operations")
    class SendOperationsTests {

        @Test
        @DisplayName("发送消息到 subagent session 成功")
        void sendToSubagentSessionSucceeds() {
            SessionEntity subSession = createSubSession();
            RunEntity newRun = createSubRun();
            newRun.setId("new-run-789");

            when(sessionService.get("sub-session-456")).thenReturn(Optional.of(subSession));
            when(runService.createSubagentRun(anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean()))
                    .thenReturn(newRun);
            when(queueManager.nextSequence(anyString())).thenReturn(0L);
            when(queueManager.submit(anyString(), anyString(), anyLong(), anyString(), any()))
                    .thenReturn(Mono.empty());

            SubagentOpsService.SendResult result = opsService.send("conn-123", "sub-session-456", "继续分析");

            assertTrue(result.success());
            assertEquals("new-run-789", result.newRunId());
            assertEquals("sub-session-456", result.subSessionId());
        }

        @Test
        @DisplayName("发送到不存在的 session 返回 NOT_FOUND")
        void sendToNotFoundSessionReturnsNotFound() {
            when(sessionService.get("not-found")).thenReturn(Optional.empty());

            SubagentOpsService.SendResult result = opsService.send("conn-123", "not-found", "test");

            assertFalse(result.success());
            assertEquals("NOT_FOUND", result.errorCode());
        }

        @Test
        @DisplayName("发送到非 subagent session 返回 INVALID_STATE")
        void sendToNonSubagentSessionReturnsInvalidState() {
            SessionEntity mainSession = SessionEntity.builder()
                    .id("main-session-123")
                    .sessionKind("main")
                    .build();
            when(sessionService.get("main-session-123")).thenReturn(Optional.of(mainSession));

            SubagentOpsService.SendResult result = opsService.send("conn-123", "main-session-123", "test");

            assertFalse(result.success());
            assertEquals("INVALID_STATE", result.errorCode());
        }

        @Test
        @DisplayName("发送空消息返回 INVALID_PARAMS")
        void sendEmptyMessageReturnsInvalidParams() {
            SubagentOpsService.SendResult result = opsService.send("conn-123", "sub-session-456", "");

            assertFalse(result.success());
            assertEquals("INVALID_PARAMS", result.errorCode());
        }

        @Test
        @DisplayName("发送 null 消息返回 INVALID_PARAMS")
        void sendNullMessageReturnsInvalidParams() {
            SubagentOpsService.SendResult result = opsService.send("conn-123", "sub-session-456", null);

            assertFalse(result.success());
            assertEquals("INVALID_PARAMS", result.errorCode());
        }

        @Test
        @DisplayName("发送提交到 subagent lane")
        void sendSubmitsToSubagentLane() {
            SessionEntity subSession = createSubSession();
            RunEntity newRun = createSubRun();

            when(sessionService.get("sub-session-456")).thenReturn(Optional.of(subSession));
            when(runService.createSubagentRun(anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean()))
                    .thenReturn(newRun);
            when(queueManager.nextSequence(anyString())).thenReturn(0L);
            when(queueManager.submit(anyString(), anyString(), anyLong(), anyString(), any()))
                    .thenReturn(Mono.empty());

            opsService.send("conn-123", "sub-session-456", "继续分析");

            verify(queueManager).submit(
                    eq("sub-session-456"),
                    eq("sub-run-123"),
                    anyLong(),
                    eq(LaneAwareQueueManager.LANE_SUBAGENT),
                    any()
            );
        }
    }

    // ==================== Helper Methods ====================

    private RunEntity createSubRun() {
        return RunEntity.builder()
                .id("sub-run-123")
                .sessionId("sub-session-456")
                .parentRunId("parent-run-789")
                .requesterSessionId("parent-session-000")
                .agentId("main")
                .status("queued")
                .prompt("分析AI芯片市场")
                .runKind("subagent")
                .lane("subagent")
                .deliver(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private SessionEntity createSubSession() {
        return SessionEntity.builder()
                .id("sub-session-456")
                .name("分析AI芯片市场")
                .agentId("main")
                .sessionKind("subagent")
                .sessionKey("agent:main:subagent:sub-session-456")
                .parentSessionId("parent-session-000")
                .createdByRunId("parent-run-789")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
