package com.jaguarliu.ai.gateway.events;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Agent 事件模型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentEvent {

    /**
     * 事件类型
     */
    private EventType type;

    /**
     * 关联的 runId
     */
    private String runId;

    /**
     * 关联的 connectionId（用于路由到正确的 WebSocket 连接）
     */
    private String connectionId;

    /**
     * 事件数据
     */
    private Object data;

    /**
     * 事件类型枚举
     */
    public enum EventType {
        LIFECYCLE_START("lifecycle.start"),
        ASSISTANT_DELTA("assistant.delta"),
        LIFECYCLE_END("lifecycle.end"),
        LIFECYCLE_ERROR("lifecycle.error"),
        STEP_COMPLETED("step.completed"),
        TOOL_CALL("tool.call"),
        TOOL_RESULT("tool.result"),
        TOOL_CONFIRM_REQUEST("tool.confirm_request"),
        SKILL_ACTIVATED("skill.activated"),
        // SubAgent 事件
        SUBAGENT_SPAWNED("subagent.spawned"),
        SUBAGENT_STARTED("subagent.started"),
        SUBAGENT_ANNOUNCED("subagent.announced"),
        SUBAGENT_FAILED("subagent.failed"),
        SESSION_RENAMED("session.renamed"),
        // Artifact 流式预览事件
        ARTIFACT_OPEN("artifact.open"),
        ARTIFACT_DELTA("artifact.delta");

        private final String value;

        EventType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * 创建 lifecycle.start 事件
     */
    public static AgentEvent lifecycleStart(String connectionId, String runId) {
        return AgentEvent.builder()
                .type(EventType.LIFECYCLE_START)
                .connectionId(connectionId)
                .runId(runId)
                .build();
    }

    /**
     * 创建 assistant.delta 事件
     */
    public static AgentEvent assistantDelta(String connectionId, String runId, String content) {
        return AgentEvent.builder()
                .type(EventType.ASSISTANT_DELTA)
                .connectionId(connectionId)
                .runId(runId)
                .data(new DeltaData(content))
                .build();
    }

    /**
     * 创建 lifecycle.end 事件
     */
    public static AgentEvent lifecycleEnd(String connectionId, String runId) {
        return AgentEvent.builder()
                .type(EventType.LIFECYCLE_END)
                .connectionId(connectionId)
                .runId(runId)
                .build();
    }

    /**
     * 创建 lifecycle.error 事件
     */
    public static AgentEvent lifecycleError(String connectionId, String runId, String error) {
        return AgentEvent.builder()
                .type(EventType.LIFECYCLE_ERROR)
                .connectionId(connectionId)
                .runId(runId)
                .data(new ErrorData(error))
                .build();
    }

    /**
     * 创建 step.completed 事件
     */
    public static AgentEvent stepCompleted(String connectionId, String runId, int step, int maxSteps, long elapsedSeconds) {
        return AgentEvent.builder()
                .type(EventType.STEP_COMPLETED)
                .connectionId(connectionId)
                .runId(runId)
                .data(new StepData(step, maxSteps, elapsedSeconds))
                .build();
    }

    /**
     * 创建 tool.call 事件（工具调用开始）
     */
    public static AgentEvent toolCall(String connectionId, String runId, String callId, String toolName, Object arguments) {
        return AgentEvent.builder()
                .type(EventType.TOOL_CALL)
                .connectionId(connectionId)
                .runId(runId)
                .data(new ToolCallData(callId, toolName, arguments))
                .build();
    }

    /**
     * 创建 tool.result 事件（工具调用结果）
     */
    public static AgentEvent toolResult(String connectionId, String runId, String callId, boolean success, String content) {
        return AgentEvent.builder()
                .type(EventType.TOOL_RESULT)
                .connectionId(connectionId)
                .runId(runId)
                .data(new ToolResultData(callId, success, content))
                .build();
    }

    /**
     * 创建 tool.confirm_request 事件（请求 HITL 确认）
     */
    public static AgentEvent toolConfirmRequest(String connectionId, String runId, String callId, String toolName, Object arguments) {
        return AgentEvent.builder()
                .type(EventType.TOOL_CONFIRM_REQUEST)
                .connectionId(connectionId)
                .runId(runId)
                .data(new ToolConfirmRequestData(callId, toolName, arguments))
                .build();
    }

    /**
     * 创建 skill.activated 事件
     */
    public static AgentEvent skillActivated(String connectionId, String runId, String skillName, String source) {
        return AgentEvent.builder()
                .type(EventType.SKILL_ACTIVATED)
                .connectionId(connectionId)
                .runId(runId)
                .data(new SkillActivatedData(skillName, source))
                .build();
    }

    /**
     * 创建 session.renamed 事件
     */
    public static AgentEvent sessionRenamed(String connectionId, String runId, String sessionId, String name) {
        return AgentEvent.builder()
                .type(EventType.SESSION_RENAMED)
                .connectionId(connectionId)
                .runId(runId)
                .data(new SessionRenamedData(sessionId, name))
                .build();
    }

    /**
     * 创建 artifact.open 事件（AI 开始写文件，打开预览面板）
     */
    public static AgentEvent artifactOpen(String connectionId, String runId, String path) {
        return AgentEvent.builder()
                .type(EventType.ARTIFACT_OPEN)
                .connectionId(connectionId)
                .runId(runId)
                .data(new ArtifactOpenData(path))
                .build();
    }

    /**
     * 创建 artifact.delta 事件（文件内容增量到达）
     */
    public static AgentEvent artifactDelta(String connectionId, String runId, String content) {
        return AgentEvent.builder()
                .type(EventType.ARTIFACT_DELTA)
                .connectionId(connectionId)
                .runId(runId)
                .data(new ArtifactDeltaData(content))
                .build();
    }

    @Data
    @AllArgsConstructor
    public static class DeltaData {
        private String content;
    }

    @Data
    @AllArgsConstructor
    public static class ErrorData {
        private String message;
    }

    @Data
    @AllArgsConstructor
    public static class StepData {
        private int step;
        private int maxSteps;
        private long elapsedSeconds;
    }

    @Data
    @AllArgsConstructor
    public static class ToolCallData {
        private String callId;
        private String toolName;
        private Object arguments;
    }

    @Data
    @AllArgsConstructor
    public static class ToolResultData {
        private String callId;
        private boolean success;
        private String content;
    }

    @Data
    @AllArgsConstructor
    public static class ToolConfirmRequestData {
        private String callId;
        private String toolName;
        private Object arguments;
    }

    @Data
    @AllArgsConstructor
    public static class SkillActivatedData {
        private String skillName;
        private String source;  // "manual" or "auto"
    }

    @Data
    @AllArgsConstructor
    public static class SessionRenamedData {
        private String sessionId;
        private String name;
    }

    @Data
    @AllArgsConstructor
    public static class ArtifactOpenData {
        private String path;
    }

    @Data
    @AllArgsConstructor
    public static class ArtifactDeltaData {
        private String content;
    }

    // ==================== SubAgent 事件 ====================

    /**
     * 创建 subagent.spawned 事件（子代理已派生）
     */
    public static AgentEvent subagentSpawned(String connectionId, String parentRunId,
                                              String subRunId, String subSessionId,
                                              String sessionKey, String agentId,
                                              String task, String lane) {
        return AgentEvent.builder()
                .type(EventType.SUBAGENT_SPAWNED)
                .connectionId(connectionId)
                .runId(parentRunId)
                .data(new SubagentSpawnedData(subRunId, subSessionId, sessionKey, agentId, task, lane))
                .build();
    }

    /**
     * 创建 subagent.started 事件（子代理开始执行）
     */
    public static AgentEvent subagentStarted(String connectionId, String parentRunId, String subRunId) {
        return AgentEvent.builder()
                .type(EventType.SUBAGENT_STARTED)
                .connectionId(connectionId)
                .runId(parentRunId)
                .data(new SubagentStartedData(subRunId))
                .build();
    }

    /**
     * 创建 subagent.announced 事件（子代理完成并回传结果）
     */
    public static AgentEvent subagentAnnounced(String connectionId, String parentRunId,
                                                String subRunId, String subSessionId,
                                                String sessionKey, String agentId,
                                                String task, String status,
                                                String result, String error,
                                                long durationMs) {
        return AgentEvent.builder()
                .type(EventType.SUBAGENT_ANNOUNCED)
                .connectionId(connectionId)
                .runId(parentRunId)
                .data(new SubagentAnnouncedData(subRunId, subSessionId, sessionKey,
                        agentId, task, status, result, error, durationMs))
                .build();
    }

    /**
     * 创建 subagent.failed 事件（子代理执行失败）
     */
    public static AgentEvent subagentFailed(String connectionId, String parentRunId,
                                             String subRunId, String agentId,
                                             String task, String error) {
        return AgentEvent.builder()
                .type(EventType.SUBAGENT_FAILED)
                .connectionId(connectionId)
                .runId(parentRunId)
                .data(new SubagentFailedData(subRunId, agentId, task, error))
                .build();
    }

    @Data
    @AllArgsConstructor
    public static class SubagentSpawnedData {
        private String subRunId;
        private String subSessionId;
        private String sessionKey;
        private String agentId;
        private String task;
        private String lane;
    }

    @Data
    @AllArgsConstructor
    public static class SubagentStartedData {
        private String subRunId;
    }

    @Data
    @AllArgsConstructor
    public static class SubagentAnnouncedData {
        private String subRunId;
        private String subSessionId;
        private String sessionKey;
        private String agentId;
        private String task;
        private String status;
        private String result;
        private String error;
        private long durationMs;
    }

    @Data
    @AllArgsConstructor
    public static class SubagentFailedData {
        private String subRunId;
        private String agentId;
        private String task;
        private String error;
    }

}
