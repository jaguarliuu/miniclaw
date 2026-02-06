package com.jaguarliu.ai.session;

import com.jaguarliu.ai.storage.entity.RunEntity;
import com.jaguarliu.ai.storage.entity.SessionEntity;
import com.jaguarliu.ai.storage.repository.MessageRepository;
import com.jaguarliu.ai.storage.repository.RunRepository;
import com.jaguarliu.ai.storage.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SubAgent Session/Run 创建能力测试
 *
 * 测试覆盖：
 * 1. SessionService.createSubagentSession
 * 2. RunService.createSubagentRun
 * 3. 字段正确落库
 * 4. 查询方法
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SubAgent Session/Run Service Tests")
class SubagentSessionRunServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private RunRepository runRepository;

    @Mock
    private MessageRepository messageRepository;

    private SessionService sessionService;
    private RunService runService;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(sessionRepository, runRepository, messageRepository);
        runService = new RunService(runRepository);
    }

    // ==================== SessionService 测试 ====================

    @Nested
    @DisplayName("SessionService.createSubagentSession")
    class CreateSubagentSessionTests {

        @Test
        @DisplayName("创建子代理会话 - 所有字段正确设置")
        void createSubagentSessionFieldsCorrect() {
            // Arrange
            when(sessionRepository.save(any(SessionEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            SessionEntity session = sessionService.createSubagentSession(
                    "parent-session-123",
                    "parent-run-456",
                    "researcher",
                    "分析AI芯片市场动态"
            );

            // Assert
            ArgumentCaptor<SessionEntity> captor = ArgumentCaptor.forClass(SessionEntity.class);
            verify(sessionRepository).save(captor.capture());

            SessionEntity saved = captor.getValue();
            assertNotNull(saved.getId());
            assertEquals("分析AI芯片市场动态", saved.getName());
            assertEquals("researcher", saved.getAgentId());
            assertEquals("subagent", saved.getSessionKind());
            assertEquals("parent-session-123", saved.getParentSessionId());
            assertEquals("parent-run-456", saved.getCreatedByRunId());
            assertTrue(saved.getSessionKey().startsWith("agent:researcher:subagent:"));
        }

        @Test
        @DisplayName("任务摘要超长时截断")
        void truncateLongTaskSummary() {
            when(sessionRepository.save(any(SessionEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // 确保超过 50 个字符
            String longTask = "这是一个非常非常非常非常非常非常非常非常非常非常非常非常非常非常非常非常非常非常非常长的任务描述需要被截断";

            SessionEntity session = sessionService.createSubagentSession(
                    "parent-session-123",
                    "parent-run-456",
                    "main",
                    longTask
            );

            assertEquals(50, session.getName().length());
            assertTrue(session.getName().endsWith("..."));
        }

        @Test
        @DisplayName("任务摘要为空时使用默认名称")
        void defaultNameWhenTaskSummaryEmpty() {
            when(sessionRepository.save(any(SessionEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            SessionEntity session = sessionService.createSubagentSession(
                    "parent-session-123",
                    "parent-run-456",
                    "main",
                    ""
            );

            assertEquals("SubAgent Task", session.getName());
        }

        @Test
        @DisplayName("sessionKey 格式正确")
        void sessionKeyFormatCorrect() {
            when(sessionRepository.save(any(SessionEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            SessionEntity session = sessionService.createSubagentSession(
                    "parent-session-123",
                    "parent-run-456",
                    "researcher",
                    "任务"
            );

            // 格式: agent:{agentId}:subagent:{sessionId}
            String expectedPrefix = "agent:researcher:subagent:" + session.getId();
            assertEquals(expectedPrefix, session.getSessionKey());
        }
    }

    @Nested
    @DisplayName("SessionService 主会话创建")
    class CreateMainSessionTests {

        @Test
        @DisplayName("create(name) 创建主会话 - sessionKind=main")
        void createMainSession() {
            when(sessionRepository.save(any(SessionEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            SessionEntity session = sessionService.create("Test Session");

            ArgumentCaptor<SessionEntity> captor = ArgumentCaptor.forClass(SessionEntity.class);
            verify(sessionRepository).save(captor.capture());

            SessionEntity saved = captor.getValue();
            assertEquals("Test Session", saved.getName());
            assertEquals("main", saved.getAgentId());
            assertEquals("main", saved.getSessionKind());
            assertNull(saved.getParentSessionId());
            assertNull(saved.getCreatedByRunId());
        }

        @Test
        @DisplayName("create(name, agentId) 创建指定 agent 的主会话")
        void createMainSessionWithAgentId() {
            when(sessionRepository.save(any(SessionEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            SessionEntity session = sessionService.create("Test Session", "researcher");

            ArgumentCaptor<SessionEntity> captor = ArgumentCaptor.forClass(SessionEntity.class);
            verify(sessionRepository).save(captor.capture());

            assertEquals("researcher", captor.getValue().getAgentId());
        }
    }

    @Nested
    @DisplayName("SessionService 查询方法")
    class SessionQueryTests {

        @Test
        @DisplayName("listMainSessions 查询主会话")
        void listMainSessions() {
            SessionEntity main1 = SessionEntity.builder().id("1").sessionKind("main").build();
            SessionEntity main2 = SessionEntity.builder().id("2").sessionKind("main").build();
            when(sessionRepository.findBySessionKindOrderByCreatedAtDesc("main"))
                    .thenReturn(Arrays.asList(main1, main2));

            List<SessionEntity> sessions = sessionService.listMainSessions();

            assertEquals(2, sessions.size());
            verify(sessionRepository).findBySessionKindOrderByCreatedAtDesc("main");
        }

        @Test
        @DisplayName("listSubagentSessions 查询子代理会话")
        void listSubagentSessions() {
            SessionEntity sub1 = SessionEntity.builder().id("sub1").parentSessionId("parent").build();
            when(sessionRepository.findByParentSessionIdOrderByCreatedAtDesc("parent"))
                    .thenReturn(Arrays.asList(sub1));

            List<SessionEntity> sessions = sessionService.listSubagentSessions("parent");

            assertEquals(1, sessions.size());
            assertEquals("sub1", sessions.get(0).getId());
        }

        @Test
        @DisplayName("getBySessionKey 根据 sessionKey 查询")
        void getBySessionKey() {
            SessionEntity session = SessionEntity.builder()
                    .id("123")
                    .sessionKey("agent:main:subagent:123")
                    .build();
            when(sessionRepository.findBySessionKey("agent:main:subagent:123"))
                    .thenReturn(Optional.of(session));

            Optional<SessionEntity> result = sessionService.getBySessionKey("agent:main:subagent:123");

            assertTrue(result.isPresent());
            assertEquals("123", result.get().getId());
        }
    }

    // ==================== RunService 测试 ====================

    @Nested
    @DisplayName("RunService.createSubagentRun")
    class CreateSubagentRunTests {

        @Test
        @DisplayName("创建子代理运行 - 所有字段正确设置")
        void createSubagentRunFieldsCorrect() {
            when(runRepository.save(any(RunEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            RunEntity run = runService.createSubagentRun(
                    "sub-session-123",
                    "parent-run-456",
                    "parent-session-789",
                    "researcher",
                    "分析AI芯片市场",
                    true
            );

            ArgumentCaptor<RunEntity> captor = ArgumentCaptor.forClass(RunEntity.class);
            verify(runRepository).save(captor.capture());

            RunEntity saved = captor.getValue();
            assertNotNull(saved.getId());
            assertEquals("sub-session-123", saved.getSessionId());
            assertEquals("parent-run-456", saved.getParentRunId());
            assertEquals("parent-session-789", saved.getRequesterSessionId());
            assertEquals("researcher", saved.getAgentId());
            assertEquals("subagent", saved.getRunKind());
            assertEquals("subagent", saved.getLane());
            assertEquals("分析AI芯片市场", saved.getPrompt());
            assertTrue(saved.getDeliver());
            assertEquals(RunStatus.QUEUED.getValue(), saved.getStatus());
        }

        @Test
        @DisplayName("deliver=false 时正确设置")
        void createSubagentRunDeliverFalse() {
            when(runRepository.save(any(RunEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            RunEntity run = runService.createSubagentRun(
                    "sub-session-123",
                    "parent-run-456",
                    "parent-session-789",
                    "main",
                    "任务",
                    false
            );

            assertFalse(run.getDeliver());
        }
    }

    @Nested
    @DisplayName("RunService 主运行创建")
    class CreateMainRunTests {

        @Test
        @DisplayName("create(sessionId, prompt) 创建主运行")
        void createMainRun() {
            when(runRepository.save(any(RunEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            RunEntity run = runService.create("session-123", "Hello");

            ArgumentCaptor<RunEntity> captor = ArgumentCaptor.forClass(RunEntity.class);
            verify(runRepository).save(captor.capture());

            RunEntity saved = captor.getValue();
            assertEquals("session-123", saved.getSessionId());
            assertEquals("Hello", saved.getPrompt());
            assertEquals("main", saved.getAgentId());
            assertEquals("main", saved.getRunKind());
            assertEquals("main", saved.getLane());
            assertNull(saved.getParentRunId());
            assertNull(saved.getRequesterSessionId());
            assertFalse(saved.getDeliver());
        }

        @Test
        @DisplayName("create(sessionId, prompt, agentId) 创建指定 agent 的主运行")
        void createMainRunWithAgentId() {
            when(runRepository.save(any(RunEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            RunEntity run = runService.create("session-123", "Hello", "researcher");

            ArgumentCaptor<RunEntity> captor = ArgumentCaptor.forClass(RunEntity.class);
            verify(runRepository).save(captor.capture());

            assertEquals("researcher", captor.getValue().getAgentId());
        }
    }

    @Nested
    @DisplayName("RunService 查询方法")
    class RunQueryTests {

        @Test
        @DisplayName("listSubagentRuns 查询子代理运行")
        void listSubagentRuns() {
            RunEntity sub1 = RunEntity.builder().id("sub1").parentRunId("parent").build();
            when(runRepository.findByParentRunIdOrderByCreatedAtDesc("parent"))
                    .thenReturn(Arrays.asList(sub1));

            List<RunEntity> runs = runService.listSubagentRuns("parent");

            assertEquals(1, runs.size());
            assertEquals("sub1", runs.get(0).getId());
        }

        @Test
        @DisplayName("countRunningByLane 统计指定 lane 运行中的数量")
        void countRunningByLane() {
            when(runRepository.countByLaneAndStatus("subagent", RunStatus.RUNNING.getValue()))
                    .thenReturn(3L);

            long count = runService.countRunningByLane("subagent");

            assertEquals(3, count);
        }

        @Test
        @DisplayName("listQueuedByLane 查询指定 lane 排队中的运行")
        void listQueuedByLane() {
            RunEntity queued = RunEntity.builder().id("q1").lane("subagent").status("queued").build();
            when(runRepository.findByLaneAndStatusOrderByCreatedAtAsc("subagent", RunStatus.QUEUED.getValue()))
                    .thenReturn(Arrays.asList(queued));

            List<RunEntity> runs = runService.listQueuedByLane("subagent");

            assertEquals(1, runs.size());
            assertEquals("q1", runs.get(0).getId());
        }
    }
}
