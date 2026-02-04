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
        SKILL_ACTIVATED("skill.activated");

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
}
