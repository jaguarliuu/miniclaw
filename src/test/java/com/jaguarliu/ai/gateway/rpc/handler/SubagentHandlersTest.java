package com.jaguarliu.ai.gateway.rpc.handler;

import com.jaguarliu.ai.gateway.rpc.handler.subagent.SubagentListHandler;
import com.jaguarliu.ai.gateway.rpc.handler.subagent.SubagentSendHandler;
import com.jaguarliu.ai.gateway.rpc.handler.subagent.SubagentStopHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.storage.entity.RunEntity;
import com.jaguarliu.ai.subagent.SubagentOpsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SubAgent RPC Handlers 单元测试
 *
 * 测试覆盖：
 * 1. subagent.list 返回父子关系与状态
 * 2. subagent.stop 触发取消
 * 3. subagent.send 追加任务并产生新子 run
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Subagent RPC Handlers Tests")
class SubagentHandlersTest {

    @Mock
    private SubagentOpsService subagentOpsService;

    // ==================== SubagentListHandler Tests ====================

    @Nested
    @DisplayName("SubagentListHandler Tests")
    class SubagentListHandlerTests {

        private SubagentListHandler handler;

        @BeforeEach
        void setUp() {
            handler = new SubagentListHandler(subagentOpsService);
        }

        @Test
        @DisplayName("getMethod 返回 subagent.list")
        void getMethodReturnsCorrectMethod() {
            assertEquals("subagent.list", handler.getMethod());
        }

        @Test
        @DisplayName("按 parentRunId 查询返回子代理列表")
        void listByParentRunIdReturnsSubagents() {
            RunEntity subRun = createSubRun("sub-run-123", "sub-session-456", "parent-run-789");

            when(subagentOpsService.listByParentRun("parent-run-789"))
                    .thenReturn(List.of(subRun));

            RpcRequest request = RpcRequest.builder()
                    .id("req-1")
                    .method("subagent.list")
                    .payload(Map.of("parentRunId", "parent-run-789"))
                    .build();

            RpcResponse response = handler.handle("conn-123", request).block();

            assertNotNull(response);
            assertNull(response.getError());

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) response.getPayload();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> subagents = (List<Map<String, Object>>) result.get("subagents");

            assertEquals(1, subagents.size());
            assertEquals("sub-run-123", subagents.get(0).get("subRunId"));
            assertEquals("sub-session-456", subagents.get(0).get("subSessionId"));
            assertEquals("parent-run-789", subagents.get(0).get("parentRunId"));
        }

        @Test
        @DisplayName("按 sessionId 查询返回子代理列表")
        void listBySessionIdReturnsSubagents() {
            RunEntity subRun = createSubRun("sub-run-123", "sub-session-456", "parent-run-789");

            when(subagentOpsService.listByRequesterSession("parent-session-000"))
                    .thenReturn(List.of(subRun));

            RpcRequest request = RpcRequest.builder()
                    .id("req-1")
                    .method("subagent.list")
                    .payload(Map.of("sessionId", "parent-session-000"))
                    .build();

            RpcResponse response = handler.handle("conn-123", request).block();

            assertNotNull(response);
            assertNull(response.getError());

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) response.getPayload();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> subagents = (List<Map<String, Object>>) result.get("subagents");

            assertEquals(1, subagents.size());
        }

        @Test
        @DisplayName("缺少参数返回错误")
        void missingParamsReturnsError() {
            RpcRequest request = RpcRequest.builder()
                    .id("req-1")
                    .method("subagent.list")
                    .payload(Map.of())
                    .build();

            RpcResponse response = handler.handle("conn-123", request).block();

            assertNotNull(response);
            assertNotNull(response.getError());
            assertEquals("INVALID_PARAMS", response.getError().getCode());
        }

        @Test
        @DisplayName("返回结果包含状态和任务摘要")
        void resultContainsStatusAndTask() {
            RunEntity subRun = RunEntity.builder()
                    .id("sub-run-123")
                    .sessionId("sub-session-456")
                    .parentRunId("parent-run-789")
                    .requesterSessionId("parent-session-000")
                    .agentId("main")
                    .status("running")
                    .prompt("分析AI芯片市场趋势")
                    .runKind("subagent")
                    .lane("subagent")
                    .deliver(true)
                    .createdAt(LocalDateTime.now())
                    .build();

            when(subagentOpsService.listByParentRun("parent-run-789"))
                    .thenReturn(List.of(subRun));

            RpcRequest request = RpcRequest.builder()
                    .id("req-1")
                    .method("subagent.list")
                    .payload(Map.of("parentRunId", "parent-run-789"))
                    .build();

            RpcResponse response = handler.handle("conn-123", request).block();

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) response.getPayload();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> subagents = (List<Map<String, Object>>) result.get("subagents");

            assertEquals("running", subagents.get(0).get("status"));
            assertEquals("分析AI芯片市场趋势", subagents.get(0).get("task"));
            assertEquals(true, subagents.get(0).get("deliver"));
            assertEquals("main", subagents.get(0).get("agentId"));
        }
    }

    // ==================== SubagentStopHandler Tests ====================

    @Nested
    @DisplayName("SubagentStopHandler Tests")
    class SubagentStopHandlerTests {

        private SubagentStopHandler handler;

        @BeforeEach
        void setUp() {
            handler = new SubagentStopHandler(subagentOpsService);
        }

        @Test
        @DisplayName("getMethod 返回 subagent.stop")
        void getMethodReturnsCorrectMethod() {
            assertEquals("subagent.stop", handler.getMethod());
        }

        @Test
        @DisplayName("停止成功返回 stopped=true")
        void stopSuccessReturnsStopped() {
            when(subagentOpsService.stop("sub-run-123"))
                    .thenReturn(SubagentOpsService.StopResult.success("sub-run-123"));

            RpcRequest request = RpcRequest.builder()
                    .id("req-1")
                    .method("subagent.stop")
                    .payload(Map.of("subRunId", "sub-run-123"))
                    .build();

            RpcResponse response = handler.handle("conn-123", request).block();

            assertNotNull(response);
            assertNull(response.getError());

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) response.getPayload();
            assertEquals("sub-run-123", result.get("subRunId"));
            assertEquals(true, result.get("stopped"));
        }

        @Test
        @DisplayName("停止不存在的 run 返回 NOT_FOUND")
        void stopNotFoundReturnsError() {
            when(subagentOpsService.stop("not-found"))
                    .thenReturn(SubagentOpsService.StopResult.notFound("Run not found: not-found"));

            RpcRequest request = RpcRequest.builder()
                    .id("req-1")
                    .method("subagent.stop")
                    .payload(Map.of("subRunId", "not-found"))
                    .build();

            RpcResponse response = handler.handle("conn-123", request).block();

            assertNotNull(response);
            assertNotNull(response.getError());
            assertEquals("NOT_FOUND", response.getError().getCode());
        }

        @Test
        @DisplayName("停止非 subagent run 返回 INVALID_STATE")
        void stopNonSubagentReturnsError() {
            when(subagentOpsService.stop("main-run-123"))
                    .thenReturn(SubagentOpsService.StopResult.invalidState("Not a subagent run"));

            RpcRequest request = RpcRequest.builder()
                    .id("req-1")
                    .method("subagent.stop")
                    .payload(Map.of("subRunId", "main-run-123"))
                    .build();

            RpcResponse response = handler.handle("conn-123", request).block();

            assertNotNull(response);
            assertNotNull(response.getError());
            assertEquals("INVALID_STATE", response.getError().getCode());
        }

        @Test
        @DisplayName("缺少 subRunId 返回错误")
        void missingSubRunIdReturnsError() {
            RpcRequest request = RpcRequest.builder()
                    .id("req-1")
                    .method("subagent.stop")
                    .payload(Map.of())
                    .build();

            RpcResponse response = handler.handle("conn-123", request).block();

            assertNotNull(response);
            assertNotNull(response.getError());
            assertEquals("INVALID_PARAMS", response.getError().getCode());
        }
    }

    // ==================== SubagentSendHandler Tests ====================

    @Nested
    @DisplayName("SubagentSendHandler Tests")
    class SubagentSendHandlerTests {

        private SubagentSendHandler handler;

        @BeforeEach
        void setUp() {
            handler = new SubagentSendHandler(subagentOpsService);
        }

        @Test
        @DisplayName("getMethod 返回 subagent.send")
        void getMethodReturnsCorrectMethod() {
            assertEquals("subagent.send", handler.getMethod());
        }

        @Test
        @DisplayName("发送成功返回新 runId")
        void sendSuccessReturnsNewRunId() {
            when(subagentOpsService.send(eq("conn-123"), eq("sub-session-456"), eq("继续分析")))
                    .thenReturn(SubagentOpsService.SendResult.success("new-run-789", "sub-session-456"));

            RpcRequest request = RpcRequest.builder()
                    .id("req-1")
                    .method("subagent.send")
                    .payload(Map.of(
                            "subSessionId", "sub-session-456",
                            "message", "继续分析"
                    ))
                    .build();

            RpcResponse response = handler.handle("conn-123", request).block();

            assertNotNull(response);
            assertNull(response.getError());

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) response.getPayload();
            assertEquals("new-run-789", result.get("newRunId"));
            assertEquals("sub-session-456", result.get("subSessionId"));
            assertEquals(true, result.get("queued"));
        }

        @Test
        @DisplayName("发送到不存在的 session 返回 NOT_FOUND")
        void sendToNotFoundSessionReturnsError() {
            when(subagentOpsService.send(anyString(), eq("not-found"), anyString()))
                    .thenReturn(SubagentOpsService.SendResult.notFound("Session not found"));

            RpcRequest request = RpcRequest.builder()
                    .id("req-1")
                    .method("subagent.send")
                    .payload(Map.of(
                            "subSessionId", "not-found",
                            "message", "test"
                    ))
                    .build();

            RpcResponse response = handler.handle("conn-123", request).block();

            assertNotNull(response);
            assertNotNull(response.getError());
            assertEquals("NOT_FOUND", response.getError().getCode());
        }

        @Test
        @DisplayName("发送到非 subagent session 返回 INVALID_STATE")
        void sendToNonSubagentSessionReturnsError() {
            when(subagentOpsService.send(anyString(), eq("main-session"), anyString()))
                    .thenReturn(SubagentOpsService.SendResult.invalidState("Not a subagent session"));

            RpcRequest request = RpcRequest.builder()
                    .id("req-1")
                    .method("subagent.send")
                    .payload(Map.of(
                            "subSessionId", "main-session",
                            "message", "test"
                    ))
                    .build();

            RpcResponse response = handler.handle("conn-123", request).block();

            assertNotNull(response);
            assertNotNull(response.getError());
            assertEquals("INVALID_STATE", response.getError().getCode());
        }

        @Test
        @DisplayName("缺少 subSessionId 返回错误")
        void missingSubSessionIdReturnsError() {
            RpcRequest request = RpcRequest.builder()
                    .id("req-1")
                    .method("subagent.send")
                    .payload(Map.of("message", "test"))
                    .build();

            RpcResponse response = handler.handle("conn-123", request).block();

            assertNotNull(response);
            assertNotNull(response.getError());
            assertEquals("INVALID_PARAMS", response.getError().getCode());
        }

        @Test
        @DisplayName("缺少 message 返回错误")
        void missingMessageReturnsError() {
            RpcRequest request = RpcRequest.builder()
                    .id("req-1")
                    .method("subagent.send")
                    .payload(Map.of("subSessionId", "sub-session-456"))
                    .build();

            RpcResponse response = handler.handle("conn-123", request).block();

            assertNotNull(response);
            assertNotNull(response.getError());
            assertEquals("INVALID_PARAMS", response.getError().getCode());
        }
    }

    // ==================== Helper Methods ====================

    private RunEntity createSubRun(String runId, String sessionId, String parentRunId) {
        return RunEntity.builder()
                .id(runId)
                .sessionId(sessionId)
                .parentRunId(parentRunId)
                .requesterSessionId("parent-session-000")
                .agentId("main")
                .status("queued")
                .prompt("分析任务")
                .runKind("subagent")
                .lane("subagent")
                .deliver(false)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
