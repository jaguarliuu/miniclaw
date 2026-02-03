package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.gateway.events.AgentEvent;
import com.jaguarliu.ai.gateway.events.EventBus;
import com.jaguarliu.ai.llm.LlmClient;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.llm.model.ToolCall;
import com.jaguarliu.ai.tools.ToolDispatcher;
import com.jaguarliu.ai.tools.ToolRegistry;
import com.jaguarliu.ai.tools.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

/**
 * Agent 运行时
 * 负责 ReAct 循环执行：
 * 1. 调用 LLM（带 tools）
 * 2. 如果返回 tool_calls → 执行工具（需要 HITL 的工具先等待确认）
 * 3. 将工具结果追加到上下文
 * 4. 循环直到模型收口或达到限制
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRuntime {

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final ToolDispatcher toolDispatcher;
    private final EventBus eventBus;
    private final LoopConfig loopConfig;
    private final CancellationManager cancellationManager;
    private final HitlManager hitlManager;

    /**
     * 执行 ReAct 多步循环
     *
     * @param connectionId 连接 ID
     * @param runId        运行 ID
     * @param sessionId    会话 ID
     * @param messages     初始消息列表（会被修改）
     * @return 最终回复内容
     * @throws CancellationException 如果被取消
     * @throws TimeoutException      如果超时
     */
    public String executeLoop(String connectionId, String runId, String sessionId,
                              List<LlmRequest.Message> messages) throws TimeoutException {
        // 1. 创建运行上下文
        RunContext context = RunContext.create(runId, connectionId, sessionId,
                loopConfig, cancellationManager);

        // 2. 注册到取消管理器
        cancellationManager.register(runId);

        try {
            return doExecuteLoop(context, messages);
        } finally {
            // 3. 清理取消标记
            cancellationManager.clearCancellation(runId);
        }
    }

    /**
     * 执行循环核心逻辑
     */
    private String doExecuteLoop(RunContext context, List<LlmRequest.Message> messages) throws TimeoutException {
        log.info("Starting ReAct loop: runId={}, maxSteps={}, timeout={}s",
                context.getRunId(), context.getConfig().getMaxSteps(),
                context.getConfig().getRunTimeoutSeconds());

        while (!context.isMaxStepsReached()) {
            // 检查取消
            if (context.isAborted()) {
                log.info("Loop aborted by cancellation: runId={}, step={}",
                        context.getRunId(), context.getCurrentStep());
                throw new CancellationException("Run cancelled by user");
            }

            // 检查超时
            if (context.isTimedOut()) {
                log.warn("Loop timed out: runId={}, elapsed={}s, limit={}s",
                        context.getRunId(), context.getElapsedSeconds(),
                        context.getConfig().getRunTimeoutSeconds());
                throw new TimeoutException("ReAct loop timeout after " +
                        context.getElapsedSeconds() + " seconds");
            }

            // 执行单步 LLM 调用
            StepResult result = executeSingleStep(context, messages);

            // 如果没有工具调用，正常结束
            if (!result.hasToolCalls()) {
                messages.add(LlmRequest.Message.assistant(result.content()));
                log.info("Loop completed normally: runId={}, steps={}",
                        context.getRunId(), context.getCurrentStep());
                return result.content();
            }

            // 有工具调用，执行工具
            log.info("Step {} has {} tool calls: runId={}",
                    context.getCurrentStep(), result.toolCalls().size(), context.getRunId());

            messages.add(LlmRequest.Message.assistantWithToolCalls(result.toolCalls()));

            for (ToolCall toolCall : result.toolCalls()) {
                ToolResult toolResult = executeToolCall(context, toolCall);
                messages.add(LlmRequest.Message.toolResult(toolCall.getId(), toolResult.getContent()));
            }

            // 增加步数并发布事件
            context.incrementStep();
            eventBus.publish(AgentEvent.stepCompleted(
                    context.getConnectionId(),
                    context.getRunId(),
                    context.getCurrentStep(),
                    context.getConfig().getMaxSteps(),
                    context.getElapsedSeconds()));

            log.debug("Step completed: runId={}, step={}/{}",
                    context.getRunId(), context.getCurrentStep(),
                    context.getConfig().getMaxSteps());
        }

        // 达到最大步数
        log.warn("Loop reached max steps: runId={}, maxSteps={}",
                context.getRunId(), context.getConfig().getMaxSteps());

        // 最后一次调用 LLM 获取总结
        StepResult finalResult = executeSingleStep(context, messages);
        messages.add(LlmRequest.Message.assistant(finalResult.content()));

        return finalResult.content();
    }

    /**
     * 执行单步（一次 LLM 调用）
     */
    private StepResult executeSingleStep(RunContext context, List<LlmRequest.Message> messages) {
        LlmRequest request = LlmRequest.builder()
                .messages(messages)
                .tools(toolRegistry.toOpenAiTools())
                .toolChoice("auto")
                .build();

        return streamLlmCall(context.getConnectionId(), context.getRunId(), request);
    }

    /**
     * 流式调用 LLM，收集内容和 tool_calls
     */
    private StepResult streamLlmCall(String connectionId, String runId, LlmRequest request) {
        StringBuilder content = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();

        llmClient.stream(request)
                .doOnNext(chunk -> {
                    // 收集内容增量
                    if (chunk.getDelta() != null) {
                        content.append(chunk.getDelta());
                        eventBus.publish(AgentEvent.assistantDelta(connectionId, runId, chunk.getDelta()));
                    }
                    // 收集 tool_calls（最后一个 chunk 包含完整的 tool_calls）
                    if (chunk.hasToolCalls()) {
                        toolCalls.clear();
                        toolCalls.addAll(chunk.getToolCalls());
                    }
                })
                .blockLast();

        return new StepResult(content.toString(), toolCalls);
    }

    /**
     * 执行单个工具调用（使用 RunContext，支持 HITL）
     */
    private ToolResult executeToolCall(RunContext context, ToolCall toolCall) {
        String toolName = toolCall.getName();
        String callId = toolCall.getId();
        String argumentsJson = toolCall.getArguments();

        log.info("Executing tool: name={}, callId={}, runId={}, step={}",
                toolName, callId, context.getRunId(), context.getCurrentStep());

        Map<String, Object> arguments = parseArguments(argumentsJson);

        // 检查是否需要 HITL 确认
        boolean requiresHitl = toolDispatcher.requiresHitl(toolName);

        if (requiresHitl) {
            log.info("Tool requires HITL confirmation: name={}, callId={}", toolName, callId);

            // 发布 tool.confirm_request 事件
            eventBus.publish(AgentEvent.toolConfirmRequest(
                    context.getConnectionId(),
                    context.getRunId(),
                    callId,
                    toolName,
                    arguments));

            // 等待用户决策
            HitlDecision decision = hitlManager.requestConfirmation(callId, toolName).block();

            if (decision == null || !decision.isApproved()) {
                log.info("Tool rejected by HITL: name={}, callId={}", toolName, callId);

                ToolResult rejectResult = ToolResult.error("Tool execution rejected by user");

                // 发布 tool.result 事件（被拒绝）
                eventBus.publish(AgentEvent.toolResult(
                        context.getConnectionId(),
                        context.getRunId(),
                        callId,
                        false,
                        rejectResult.getContent()));

                return rejectResult;
            }

            // 如果用户修改了参数，使用修改后的参数
            if (decision.getModifiedArguments() != null && !decision.getModifiedArguments().isEmpty()) {
                arguments = decision.getModifiedArguments();
                log.info("Using modified arguments for tool: name={}, callId={}", toolName, callId);
            }
        }

        // 发布 tool.call 事件
        eventBus.publish(AgentEvent.toolCall(
                context.getConnectionId(),
                context.getRunId(),
                callId,
                toolName,
                arguments));

        // 执行工具
        ToolResult result = toolDispatcher.dispatch(toolName, arguments).block();

        if (result == null) {
            result = ToolResult.error("Tool execution returned null");
        }

        // 发布 tool.result 事件
        eventBus.publish(AgentEvent.toolResult(
                context.getConnectionId(),
                context.getRunId(),
                callId,
                result.isSuccess(),
                result.getContent()));

        log.info("Tool executed: name={}, success={}, runId={}",
                toolName, result.isSuccess(), context.getRunId());
        return result;
    }

    /**
     * 解析工具参数 JSON
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Map.of();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(argumentsJson, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse tool arguments: {}", argumentsJson, e);
            return Map.of();
        }
    }

    /**
     * 单步执行结果
     */
    private record StepResult(String content, List<ToolCall> toolCalls) {
        boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }
}
