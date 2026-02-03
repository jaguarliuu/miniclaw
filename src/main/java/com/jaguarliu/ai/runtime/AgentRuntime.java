package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.gateway.events.AgentEvent;
import com.jaguarliu.ai.gateway.events.EventBus;
import com.jaguarliu.ai.llm.LlmClient;
import com.jaguarliu.ai.llm.model.LlmChunk;
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

/**
 * Agent 运行时
 * 负责 ReAct 循环的单步执行：
 * 1. 调用 LLM（带 tools）
 * 2. 如果返回 tool_calls → 执行工具
 * 3. 将工具结果追加到上下文
 * 4. 再次调用 LLM 获取最终回复
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRuntime {

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final ToolDispatcher toolDispatcher;
    private final EventBus eventBus;

    /**
     * 执行单步 ReAct
     * 如果 LLM 返回 tool_calls，执行工具后再次调用 LLM
     *
     * @param connectionId 连接 ID（用于事件推送）
     * @param runId        运行 ID
     * @param messages     当前上下文消息列表（会被修改，追加 assistant/tool 消息）
     * @return 最终的 assistant 回复内容
     */
    public String executeStep(String connectionId, String runId, List<LlmRequest.Message> messages) {
        // 1. 构建带 tools 的请求
        LlmRequest request = LlmRequest.builder()
                .messages(messages)
                .tools(toolRegistry.toOpenAiTools())
                .toolChoice("auto")
                .build();

        log.debug("Executing step: runId={}, messageCount={}, toolCount={}",
                runId, messages.size(), toolRegistry.size());

        // 2. 调用 LLM（流式）
        StepResult result = streamLlmCall(connectionId, runId, request);

        // 3. 如果没有 tool_calls，直接返回内容
        if (!result.hasToolCalls()) {
            log.info("Step completed without tool calls: runId={}", runId);
            // 追加 assistant 消息到上下文
            messages.add(LlmRequest.Message.assistant(result.content));
            return result.content;
        }

        // 4. 有 tool_calls，执行工具
        log.info("Step has {} tool calls: runId={}", result.toolCalls.size(), runId);

        // 追加带 tool_calls 的 assistant 消息
        messages.add(LlmRequest.Message.assistantWithToolCalls(result.toolCalls));

        // 5. 执行每个工具调用
        for (ToolCall toolCall : result.toolCalls) {
            ToolResult toolResult = executeToolCall(connectionId, runId, toolCall);

            // 追加 tool 结果消息
            messages.add(LlmRequest.Message.toolResult(toolCall.getId(), toolResult.getContent()));
        }

        // 6. 再次调用 LLM 获取最终回复
        log.debug("Calling LLM again after tool execution: runId={}", runId);

        LlmRequest followUpRequest = LlmRequest.builder()
                .messages(messages)
                .tools(toolRegistry.toOpenAiTools())
                .toolChoice("auto")
                .build();

        StepResult followUpResult = streamLlmCall(connectionId, runId, followUpRequest);

        // 追加最终 assistant 消息
        messages.add(LlmRequest.Message.assistant(followUpResult.content));

        log.info("Step completed after tool execution: runId={}", runId);
        return followUpResult.content;
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
     * 执行单个工具调用
     */
    private ToolResult executeToolCall(String connectionId, String runId, ToolCall toolCall) {
        String toolName = toolCall.getName();
        String argumentsJson = toolCall.getArguments();

        log.info("Executing tool: name={}, callId={}, runId={}", toolName, toolCall.getId(), runId);

        // 解析参数 JSON
        Map<String, Object> arguments = parseArguments(argumentsJson);

        // 执行工具
        ToolResult result = toolDispatcher.dispatch(toolName, arguments).block();

        if (result == null) {
            result = ToolResult.error("Tool execution returned null");
        }

        log.info("Tool executed: name={}, success={}, runId={}", toolName, result.isSuccess(), runId);
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
            // 使用简单的 JSON 解析（Jackson ObjectMapper 由 Spring 自动配置）
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
