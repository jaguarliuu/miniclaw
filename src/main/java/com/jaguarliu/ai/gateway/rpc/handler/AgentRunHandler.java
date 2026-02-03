package com.jaguarliu.ai.gateway.rpc.handler;

import com.jaguarliu.ai.gateway.events.AgentEvent;
import com.jaguarliu.ai.gateway.events.EventBus;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.runtime.AgentRuntime;
import com.jaguarliu.ai.runtime.ContextBuilder;
import com.jaguarliu.ai.runtime.SessionLaneManager;
import com.jaguarliu.ai.session.MessageService;
import com.jaguarliu.ai.session.RunService;
import com.jaguarliu.ai.session.RunStatus;
import com.jaguarliu.ai.session.SessionService;
import com.jaguarliu.ai.storage.entity.MessageEntity;
import com.jaguarliu.ai.storage.entity.RunEntity;
import com.jaguarliu.ai.storage.entity.SessionEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * agent.run 处理器
 * 创建 run 并通过 SessionLane 串行执行 LLM 调用
 * 执行过程中通过 EventBus 推送流式事件
 * 支持多轮对话历史和 ReAct 工具调用
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRunHandler implements RpcHandler {

    private final SessionService sessionService;
    private final RunService runService;
    private final MessageService messageService;
    private final SessionLaneManager sessionLaneManager;
    private final EventBus eventBus;
    private final ContextBuilder contextBuilder;
    private final AgentRuntime agentRuntime;

    /**
     * 历史消息数量限制（避免上下文过长）
     */
    @Value("${agent.max-history-messages:20}")
    private int maxHistoryMessages;

    /**
     * 每个 session 的锁对象
     */
    private final Map<String, Object> sessionLocks = new ConcurrentHashMap<>();

    @Override
    public String getMethod() {
        return "agent.run";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        String sessionId = extractSessionId(request.getPayload());
        String prompt = extractPrompt(request.getPayload());

        if (prompt == null || prompt.isBlank()) {
            return Mono.just(RpcResponse.error(request.getId(), "INVALID_PARAMS", "Missing prompt"));
        }

        // 同步创建 run 并获取序号
        PrepareResult result = prepareRun(sessionId, prompt, request.getId());
        if (result.error != null) {
            return Mono.just(result.error);
        }

        // 立即返回 runId
        RpcResponse response = RpcResponse.success(request.getId(), Map.of(
                "runId", result.run.getId(),
                "sessionId", result.sessionId,
                "status", RunStatus.QUEUED.getValue()
        ));

        // 提交到 SessionLane 异步执行
        sessionLaneManager.submit(
                result.sessionId,
                result.run.getId(),
                result.sequence,
                () -> {
                    executeRun(connectionId, result.run);
                    return null;
                }
        ).subscribe();

        return Mono.just(response);
    }

    private PrepareResult prepareRun(String sessionId, String prompt, String requestId) {
        String resolvedSessionId;
        if (sessionId == null || sessionId.isBlank()) {
            SessionEntity session = sessionService.create("New Session");
            resolvedSessionId = session.getId();
        } else {
            if (sessionService.get(sessionId).isEmpty()) {
                return new PrepareResult(null, null, 0,
                        RpcResponse.error(requestId, "NOT_FOUND", "Session not found: " + sessionId));
            }
            resolvedSessionId = sessionId;
        }

        Object lock = sessionLocks.computeIfAbsent(resolvedSessionId, k -> new Object());
        synchronized (lock) {
            long sequence = sessionLaneManager.nextSequence(resolvedSessionId);
            RunEntity run = runService.create(resolvedSessionId, prompt);
            log.info("Prepared run: sessionId={}, runId={}, seq={}", resolvedSessionId, run.getId(), sequence);
            return new PrepareResult(resolvedSessionId, run, sequence, null);
        }
    }

    /**
     * 执行 run（使用 AgentRuntime 支持 ReAct 工具调用）
     */
    private void executeRun(String connectionId, RunEntity run) {
        String runId = run.getId();
        String sessionId = run.getSessionId();
        String prompt = run.getPrompt();

        try {
            // 1. lifecycle.start
            runService.updateStatus(runId, RunStatus.RUNNING);
            eventBus.publish(AgentEvent.lifecycleStart(connectionId, runId));

            // 2. 保存用户消息
            messageService.saveUserMessage(sessionId, runId, prompt);

            // 3. 获取历史消息并构建上下文
            List<MessageEntity> history = messageService.getSessionHistory(sessionId, maxHistoryMessages);
            // 排除刚保存的这条用户消息（会在 ContextBuilder 中添加）
            List<LlmRequest.Message> historyMessages = history.stream()
                    .filter(m -> !m.getRunId().equals(runId))
                    .map(m -> LlmRequest.Message.builder().role(m.getRole()).content(m.getContent()).build())
                    .toList();

            // 4. 使用 AgentRuntime 执行（支持工具调用）
            List<LlmRequest.Message> messages = contextBuilder.buildMessages(historyMessages, prompt);
            log.debug("Context built: history={} messages", historyMessages.size());

            String response = agentRuntime.executeStep(connectionId, runId, messages);

            // 5. 保存助手消息
            messageService.saveAssistantMessage(sessionId, runId, response);

            // 6. lifecycle.end
            runService.updateStatus(runId, RunStatus.DONE);
            eventBus.publish(AgentEvent.lifecycleEnd(connectionId, runId));

            log.info("Run completed: id={}, response length={}", runId, response.length());

        } catch (Exception e) {
            log.error("Run failed: id={}", runId, e);
            try {
                runService.updateStatus(runId, RunStatus.ERROR);
            } catch (Exception ignored) {}
            eventBus.publish(AgentEvent.lifecycleError(connectionId, runId, e.getMessage()));
        }
    }

    private String extractSessionId(Object payload) {
        if (payload instanceof Map) {
            Object id = ((Map<?, ?>) payload).get("sessionId");
            return id != null ? id.toString() : null;
        }
        return null;
    }

    private String extractPrompt(Object payload) {
        if (payload instanceof Map) {
            Object prompt = ((Map<?, ?>) payload).get("prompt");
            return prompt != null ? prompt.toString() : null;
        }
        return null;
    }

    private record PrepareResult(String sessionId, RunEntity run, long sequence, RpcResponse error) {}
}
