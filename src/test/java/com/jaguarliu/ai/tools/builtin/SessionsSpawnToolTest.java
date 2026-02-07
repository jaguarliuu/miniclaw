package com.jaguarliu.ai.tools.builtin;

import com.jaguarliu.ai.subagent.SubagentService;
import com.jaguarliu.ai.subagent.model.SubagentSpawnResult;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolExecutionContext;
import com.jaguarliu.ai.tools.ToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SessionsSpawnTool 单元测试
 *
 * 测试覆盖：
 * 1. main run 调用成功
 * 2. subagent run 调用被拒绝（禁止嵌套）
 * 3. 工具 schema 正确
 * 4. 参数验证
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SessionsSpawnTool Tests")
class SessionsSpawnToolTest {

    @Mock
    private SubagentService subagentService;

    private SessionsSpawnTool tool;

    @BeforeEach
    void setUp() {
        tool = new SessionsSpawnTool(subagentService);
    }

    @AfterEach
    void tearDown() {
        ToolExecutionContext.clear();
    }

    // ==================== 工具定义测试 ====================

    @Nested
    @DisplayName("Tool Definition")
    class ToolDefinitionTests {

        @Test
        @DisplayName("工具名称为 sessions_spawn")
        void toolNameIsSessionsSpawn() {
            ToolDefinition def = tool.getDefinition();
            assertEquals("sessions_spawn", def.getName());
        }

        @Test
        @DisplayName("工具描述不为空")
        void toolDescriptionNotEmpty() {
            ToolDefinition def = tool.getDefinition();
            assertNotNull(def.getDescription());
            assertFalse(def.getDescription().isBlank());
        }

        @Test
        @DisplayName("task 参数是必须的")
        void taskParameterIsRequired() {
            ToolDefinition def = tool.getDefinition();
            @SuppressWarnings("unchecked")
            var required = (java.util.List<String>) def.getParameters().get("required");
            assertTrue(required.contains("task"));
        }

        @Test
        @DisplayName("不需要 HITL 确认")
        void noHitlRequired() {
            ToolDefinition def = tool.getDefinition();
            assertFalse(def.isHitl());
        }
    }

    // ==================== Main Run 调用测试 ====================

    @Nested
    @DisplayName("Main Run Execution")
    class MainRunExecutionTests {

        @BeforeEach
        void setUpMainContext() {
            // 设置 main run 上下文
            ToolExecutionContext context = ToolExecutionContext.builder()
                    .runId("main-run-123")
                    .sessionId("main-session-456")
                    .connectionId("conn-789")
                    .agentId("main")
                    .runKind("main")
                    .depth(0)
                    .build();
            ToolExecutionContext.set(context);
        }

        @Test
        @DisplayName("main run 调用 sessions_spawn 成功")
        void mainRunCanSpawn() {
            when(subagentService.spawn(anyString(), anyString(), anyString(), any(), any()))
                    .thenReturn(SubagentSpawnResult.success(
                            "sub-session-789",
                            "sub-run-000",
                            "agent:main:subagent:sub-session-789"
                    ));

            ToolResult result = tool.execute(Map.of("task", "分析AI芯片市场")).block();

            assertTrue(result.isSuccess());
            assertTrue(result.getContent().contains("sub-run-000"));
            assertTrue(result.getContent().contains("accepted"));
        }

        @Test
        @DisplayName("spawn 成功返回 subSessionId")
        void spawnReturnsSubSessionId() {
            when(subagentService.spawn(anyString(), anyString(), anyString(), any(), any()))
                    .thenReturn(SubagentSpawnResult.success(
                            "sub-session-789",
                            "sub-run-000",
                            "agent:main:subagent:sub-session-789"
                    ));

            ToolResult result = tool.execute(Map.of("task", "分析AI芯片市场")).block();

            assertTrue(result.getContent().contains("sub-session-789"));
        }

        @Test
        @DisplayName("spawn 成功返回 sessionKey")
        void spawnReturnsSessionKey() {
            when(subagentService.spawn(anyString(), anyString(), anyString(), any(), any()))
                    .thenReturn(SubagentSpawnResult.success(
                            "sub-session-789",
                            "sub-run-000",
                            "agent:main:subagent:sub-session-789"
                    ));

            ToolResult result = tool.execute(Map.of("task", "分析AI芯片市场")).block();

            assertTrue(result.getContent().contains("agent:main:subagent:sub-session-789"));
        }

        @Test
        @DisplayName("传递所有参数到 SubagentService（含 connectionId）")
        void passesAllParametersToService() {
            when(subagentService.spawn(anyString(), anyString(), anyString(), any(), any()))
                    .thenReturn(SubagentSpawnResult.success("s", "r", "k"));

            tool.execute(Map.of(
                    "task", "分析AI芯片市场",
                    "agentId", "researcher",
                    "deliver", true,
                    "announce", false,
                    "timeoutSeconds", 300
            )).block();

            verify(subagentService).spawn(
                    eq("main-run-123"),
                    eq("main-session-456"),
                    eq("main"),
                    eq("conn-789"),
                    argThat(req ->
                            req.getTask().equals("分析AI芯片市场") &&
                            req.getAgentId().equals("researcher") &&
                            req.isDeliver() &&
                            !req.isAnnounce() &&
                            req.getTimeoutSeconds() == 300
                    )
            );
        }
    }

    // ==================== SubAgent Run 调用测试（禁止嵌套）====================

    @Nested
    @DisplayName("SubAgent Run Execution (No Nested)")
    class SubagentRunExecutionTests {

        @BeforeEach
        void setUpSubagentContext() {
            // 设置 subagent run 上下文
            ToolExecutionContext context = ToolExecutionContext.builder()
                    .runId("sub-run-123")
                    .sessionId("sub-session-456")
                    .agentId("main")
                    .runKind("subagent")  // 这是一个 subagent
                    .parentRunId("parent-run-000")
                    .depth(1)
                    .build();
            ToolExecutionContext.set(context);
        }

        @Test
        @DisplayName("subagent run 调用 sessions_spawn 被拒绝")
        void subagentCannotSpawn() {
            ToolResult result = tool.execute(Map.of("task", "分析AI芯片市场")).block();

            assertFalse(result.isSuccess());
            assertTrue(result.getContent().contains("Nested spawn is not allowed"));
        }

        @Test
        @DisplayName("subagent 拒绝时不调用 SubagentService")
        void subagentRejectionDoesNotCallService() {
            tool.execute(Map.of("task", "分析AI芯片市场")).block();

            verifyNoInteractions(subagentService);
        }
    }

    // ==================== 参数验证测试 ====================

    @Nested
    @DisplayName("Parameter Validation")
    class ParameterValidationTests {

        @BeforeEach
        void setUpMainContext() {
            ToolExecutionContext context = ToolExecutionContext.builder()
                    .runId("main-run-123")
                    .sessionId("main-session-456")
                    .agentId("main")
                    .runKind("main")
                    .build();
            ToolExecutionContext.set(context);
        }

        @Test
        @DisplayName("task 为空时返回错误")
        void emptyTaskReturnsError() {
            ToolResult result = tool.execute(Map.of("task", "")).block();

            assertFalse(result.isSuccess());
            assertTrue(result.getContent().contains("task"));
        }

        @Test
        @DisplayName("task 缺失时返回错误")
        void missingTaskReturnsError() {
            ToolResult result = tool.execute(Map.of()).block();

            assertFalse(result.isSuccess());
            assertTrue(result.getContent().contains("task"));
        }

        @Test
        @DisplayName("SubagentService 返回失败时返回错误")
        void serviceFailureReturnsError() {
            when(subagentService.spawn(anyString(), anyString(), anyString(), any(), any()))
                    .thenReturn(SubagentSpawnResult.failure("Invalid agentId"));

            ToolResult result = tool.execute(Map.of("task", "分析AI芯片市场")).block();

            assertFalse(result.isSuccess());
            assertTrue(result.getContent().contains("Invalid agentId"));
        }
    }

    // ==================== 上下文缺失测试 ====================

    @Nested
    @DisplayName("Context Missing")
    class ContextMissingTests {

        @Test
        @DisplayName("无执行上下文时返回错误")
        void noContextReturnsError() {
            // 不设置上下文
            ToolExecutionContext.clear();

            ToolResult result = tool.execute(Map.of("task", "分析AI芯片市场")).block();

            assertFalse(result.isSuccess());
            assertTrue(result.getContent().contains("context"));
        }
    }
}
