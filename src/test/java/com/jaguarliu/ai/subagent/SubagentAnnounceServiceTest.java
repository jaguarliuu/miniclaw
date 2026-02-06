package com.jaguarliu.ai.subagent;

import com.jaguarliu.ai.gateway.events.AgentEvent;
import com.jaguarliu.ai.gateway.events.EventBus;
import com.jaguarliu.ai.session.MessageService;
import com.jaguarliu.ai.storage.entity.MessageEntity;
import com.jaguarliu.ai.storage.entity.RunEntity;
import com.jaguarliu.ai.storage.entity.SessionEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SubagentAnnounceService 单元测试
 *
 * 测试覆盖：
 * 1. 成功完成后父 session 获得 announce 消息
 * 2. 失败后父 session 获得包含错误的 announce 消息
 * 3. 事件 subagent.announced 正确发布
 * 4. 消息包含 subRunId/subSessionId/duration/status
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SubagentAnnounceService Tests")
class SubagentAnnounceServiceTest {

    @Mock
    private MessageService messageService;

    @Mock
    private EventBus eventBus;

    private SubagentAnnounceService announceService;

    @BeforeEach
    void setUp() {
        announceService = new SubagentAnnounceService(messageService, eventBus);
    }

    private RunEntity createSubRun() {
        return RunEntity.builder()
                .id("sub-run-123")
                .sessionId("sub-session-456")
                .parentRunId("parent-run-789")
                .requesterSessionId("parent-session-000")
                .agentId("main")
                .prompt("分析AI芯片市场")
                .build();
    }

    private SessionEntity createSubSession() {
        return SessionEntity.builder()
                .id("sub-session-456")
                .sessionKey("agent:main:subagent:sub-session-456")
                .name("分析AI芯片市场")
                .build();
    }

    // ==================== 成功完成测试 ====================

    @Nested
    @DisplayName("Success Announce")
    class SuccessAnnounceTests {

        @BeforeEach
        void setUpMocks() {
            when(messageService.saveSubagentAnnounce(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(MessageEntity.builder().id("msg-123").build());
        }

        @Test
        @DisplayName("成功完成后写入 announce 消息到父会话")
        void successAnnounceSavesMessage() {
            RunEntity subRun = createSubRun();
            SessionEntity subSession = createSubSession();
            LocalDateTime startTime = LocalDateTime.now().minusSeconds(10);

            announceService.announce("conn-123", subRun, subSession, "分析结果...", null, startTime);

            verify(messageService).saveSubagentAnnounce(
                    eq("parent-session-000"),
                    eq("parent-run-789"),
                    eq("sub-run-123"),
                    eq("sub-session-456"),
                    anyString()
            );
        }

        @Test
        @DisplayName("announce 消息内容包含 status=completed")
        void successAnnounceContainsCompletedStatus() {
            RunEntity subRun = createSubRun();
            SessionEntity subSession = createSubSession();

            ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);

            announceService.announce("conn-123", subRun, subSession, "分析结果...", null, LocalDateTime.now());

            verify(messageService).saveSubagentAnnounce(
                    anyString(), anyString(), anyString(), anyString(),
                    contentCaptor.capture()
            );

            String content = contentCaptor.getValue();
            assertTrue(content.contains("\"status\":\"completed\""));
        }

        @Test
        @DisplayName("announce 消息内容包含 result")
        void successAnnounceContainsResult() {
            RunEntity subRun = createSubRun();
            SessionEntity subSession = createSubSession();

            ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);

            announceService.announce("conn-123", subRun, subSession, "这是分析结果", null, LocalDateTime.now());

            verify(messageService).saveSubagentAnnounce(
                    anyString(), anyString(), anyString(), anyString(),
                    contentCaptor.capture()
            );

            String content = contentCaptor.getValue();
            assertTrue(content.contains("这是分析结果"));
        }

        @Test
        @DisplayName("成功完成后发布 subagent.announced 事件")
        void successAnnouncePublishesEvent() {
            RunEntity subRun = createSubRun();
            SessionEntity subSession = createSubSession();

            announceService.announce("conn-123", subRun, subSession, "结果", null, LocalDateTime.now());

            verify(eventBus).publish(argThat(event ->
                    event.getType() == AgentEvent.EventType.SUBAGENT_ANNOUNCED &&
                    event.getConnectionId().equals("conn-123") &&
                    event.getRunId().equals("parent-run-789")
            ));
        }
    }

    // ==================== 失败完成测试 ====================

    @Nested
    @DisplayName("Failure Announce")
    class FailureAnnounceTests {

        @BeforeEach
        void setUpMocks() {
            when(messageService.saveSubagentAnnounce(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(MessageEntity.builder().id("msg-123").build());
        }

        @Test
        @DisplayName("失败后写入 announce 消息包含 error")
        void failureAnnounceContainsError() {
            RunEntity subRun = createSubRun();
            SessionEntity subSession = createSubSession();

            ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);

            announceService.announce("conn-123", subRun, subSession, null, "Timeout exceeded", LocalDateTime.now());

            verify(messageService).saveSubagentAnnounce(
                    anyString(), anyString(), anyString(), anyString(),
                    contentCaptor.capture()
            );

            String content = contentCaptor.getValue();
            assertTrue(content.contains("\"status\":\"failed\""));
            assertTrue(content.contains("Timeout exceeded"));
        }

        @Test
        @DisplayName("失败后发布 subagent.announced 事件包含 error")
        void failureAnnouncePublishesEventWithError() {
            RunEntity subRun = createSubRun();
            SessionEntity subSession = createSubSession();

            announceService.announce("conn-123", subRun, subSession, null, "Some error", LocalDateTime.now());

            verify(eventBus).publish(argThat(event -> {
                if (event.getType() != AgentEvent.EventType.SUBAGENT_ANNOUNCED) return false;
                AgentEvent.SubagentAnnouncedData data = (AgentEvent.SubagentAnnouncedData) event.getData();
                return data.getError() != null && data.getError().equals("Some error");
            }));
        }
    }

    // ==================== 消息内容测试 ====================

    @Nested
    @DisplayName("Message Content")
    class MessageContentTests {

        @BeforeEach
        void setUpMocks() {
            when(messageService.saveSubagentAnnounce(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(MessageEntity.builder().id("msg-123").build());
        }

        @Test
        @DisplayName("消息包含 subRunId")
        void messageContainsSubRunId() {
            RunEntity subRun = createSubRun();
            SessionEntity subSession = createSubSession();

            ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);

            announceService.announce("conn-123", subRun, subSession, "结果", null, LocalDateTime.now());

            verify(messageService).saveSubagentAnnounce(
                    anyString(), anyString(), anyString(), anyString(),
                    contentCaptor.capture()
            );

            assertTrue(contentCaptor.getValue().contains("sub-run-123"));
        }

        @Test
        @DisplayName("消息包含 subSessionId")
        void messageContainsSubSessionId() {
            RunEntity subRun = createSubRun();
            SessionEntity subSession = createSubSession();

            ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);

            announceService.announce("conn-123", subRun, subSession, "结果", null, LocalDateTime.now());

            verify(messageService).saveSubagentAnnounce(
                    anyString(), anyString(), anyString(), anyString(),
                    contentCaptor.capture()
            );

            assertTrue(contentCaptor.getValue().contains("sub-session-456"));
        }

        @Test
        @DisplayName("消息包含 durationMs")
        void messageContainsDuration() {
            RunEntity subRun = createSubRun();
            SessionEntity subSession = createSubSession();
            LocalDateTime startTime = LocalDateTime.now().minusSeconds(5);

            ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);

            announceService.announce("conn-123", subRun, subSession, "结果", null, startTime);

            verify(messageService).saveSubagentAnnounce(
                    anyString(), anyString(), anyString(), anyString(),
                    contentCaptor.capture()
            );

            assertTrue(contentCaptor.getValue().contains("durationMs"));
        }

        @Test
        @DisplayName("消息包含 sessionKey")
        void messageContainsSessionKey() {
            RunEntity subRun = createSubRun();
            SessionEntity subSession = createSubSession();

            ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);

            announceService.announce("conn-123", subRun, subSession, "结果", null, LocalDateTime.now());

            verify(messageService).saveSubagentAnnounce(
                    anyString(), anyString(), anyString(), anyString(),
                    contentCaptor.capture()
            );

            assertTrue(contentCaptor.getValue().contains("agent:main:subagent:sub-session-456"));
        }

        @Test
        @DisplayName("超长结果被截断")
        void longResultIsTruncated() {
            RunEntity subRun = createSubRun();
            SessionEntity subSession = createSubSession();
            String longResult = "x".repeat(3000);

            ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);

            announceService.announce("conn-123", subRun, subSession, longResult, null, LocalDateTime.now());

            verify(messageService).saveSubagentAnnounce(
                    anyString(), anyString(), anyString(), anyString(),
                    contentCaptor.capture()
            );

            String content = contentCaptor.getValue();
            // 结果应该被截断到 2000 字符
            assertTrue(content.contains("..."));
            assertTrue(content.length() < longResult.length());
        }
    }

    // ==================== 边界情况测试 ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("parentSessionId 为 null 时不执行 announce")
        void nullParentSessionIdSkipsAnnounce() {
            RunEntity subRun = RunEntity.builder()
                    .id("sub-run-123")
                    .requesterSessionId(null)  // null
                    .parentRunId("parent-run-789")
                    .build();
            SessionEntity subSession = createSubSession();

            announceService.announce("conn-123", subRun, subSession, "结果", null, LocalDateTime.now());

            verifyNoInteractions(messageService);
        }

        @Test
        @DisplayName("startTime 为 null 时 duration 为 0")
        void nullStartTimeResultsInZeroDuration() {
            when(messageService.saveSubagentAnnounce(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(MessageEntity.builder().id("msg-123").build());

            RunEntity subRun = createSubRun();
            SessionEntity subSession = createSubSession();

            ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);

            announceService.announce("conn-123", subRun, subSession, "结果", null, null);

            verify(messageService).saveSubagentAnnounce(
                    anyString(), anyString(), anyString(), anyString(),
                    contentCaptor.capture()
            );

            assertTrue(contentCaptor.getValue().contains("\"durationMs\":0"));
        }
    }
}
