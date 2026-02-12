package com.jaguarliu.ai.gateway.rpc.handler.agent;

import com.jaguarliu.ai.gateway.events.AgentEvent;
import com.jaguarliu.ai.gateway.events.EventBus;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.llm.LlmClient;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.llm.model.LlmResponse;
import com.jaguarliu.ai.runtime.AgentRuntime;
import com.jaguarliu.ai.runtime.CancellationManager;
import com.jaguarliu.ai.runtime.ContextBuilder;
import com.jaguarliu.ai.runtime.LoopConfig;
import com.jaguarliu.ai.runtime.RunContext;
import com.jaguarliu.ai.runtime.SessionLaneManager;
import com.jaguarliu.ai.runtime.strategy.AgentContext;
import com.jaguarliu.ai.runtime.strategy.AgentExecutionPlan;
import com.jaguarliu.ai.runtime.strategy.AgentStrategy;
import com.jaguarliu.ai.runtime.strategy.AgentStrategyResolver;
import com.jaguarliu.ai.session.MessageService;
import com.jaguarliu.ai.session.RunService;
import com.jaguarliu.ai.session.RunStatus;
import com.jaguarliu.ai.session.SessionService;
import com.jaguarliu.ai.storage.entity.MessageEntity;
import com.jaguarliu.ai.storage.entity.RunEntity;
import com.jaguarliu.ai.storage.entity.SessionEntity;
import com.jaguarliu.ai.tools.ToolConfigProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
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
    private final LlmClient llmClient;
    private final ToolConfigProperties toolConfigProperties;
    private final AgentStrategyResolver strategyResolver;
    private final LoopConfig loopConfig;
    private final CancellationManager cancellationManager;

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
        Set<String> excludedMcpServers = extractExcludedMcpServers(request.getPayload());
        String dataSourceId = extractDataSourceId(request.getPayload());

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
                    executeRun(connectionId, result.run, excludedMcpServers, dataSourceId);
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
     * 执行 run（使用 Agent Strategy 路由到不同策略）
     */
    private void executeRun(String connectionId, RunEntity run, Set<String> excludedMcpServers, String dataSourceId) {
        String runId = run.getId();
        String sessionId = run.getSessionId();
        String prompt = run.getPrompt();

        try {
            // 1. lifecycle.start
            runService.updateStatus(runId, RunStatus.RUNNING);
            eventBus.publish(AgentEvent.lifecycleStart(connectionId, runId));

            // 2. 保存用户消息
            messageService.saveUserMessage(sessionId, runId, prompt);

            // 3. 获取历史消息
            List<MessageEntity> history = messageService.getSessionHistory(sessionId, maxHistoryMessages);
            // 排除刚保存的这条用户消息
            List<LlmRequest.Message> historyMessages = history.stream()
                    .filter(m -> !m.getRunId().equals(runId))
                    .map(m -> LlmRequest.Message.builder().role(m.getRole()).content(m.getContent()).build())
                    .toList();

            // 4. 构建策略上下文
            AgentContext agentCtx = AgentContext.builder()
                    .sessionId(sessionId)
                    .runId(runId)
                    .connectionId(connectionId)
                    .prompt(prompt)
                    .dataSourceId(dataSourceId)
                    .excludedMcpServers(excludedMcpServers)
                    .build();

            // 5. 解析策略并生成执行方案
            AgentStrategy strategy = strategyResolver.resolve(agentCtx);
            AgentExecutionPlan plan = strategy.prepare(agentCtx);

            // 6. 组装消息（使用策略的 system prompt + 会话历史 + 用户输入）
            List<LlmRequest.Message> messages = new ArrayList<>();
            messages.add(LlmRequest.Message.system(plan.getSystemPrompt()));
            messages.addAll(historyMessages);
            messages.add(LlmRequest.Message.user(prompt));

            log.debug("Context built: strategy={}, history={} messages, dataSourceId={}",
                    plan.getStrategyName(), historyMessages.size(), dataSourceId);

            // 7. 构建 RunContext 并设置工具白名单
            LoopConfig effectiveConfig = plan.getMaxStepsOverride() != null
                    ? LoopConfig.withMaxSteps(plan.getMaxStepsOverride(), loopConfig)
                    : loopConfig;
            RunContext context = RunContext.create(runId, connectionId, sessionId,
                    effectiveConfig, cancellationManager);
            context.setExcludedMcpServers(plan.getExcludedMcpServers());
            if (plan.getAllowedTools() != null) {
                // 复用 SkillAwareRequest 机制传递工具白名单
                // activeSkillName 传 null：策略不是 skill，不应阻止 use_skill 调用
                context.setActiveSkill(new ContextBuilder.SkillAwareRequest(
                        null, null, plan.getAllowedTools(), null, null));
            }
            context.setOriginalInput(prompt);

            // 8. 执行
            String response = agentRuntime.executeLoopWithContext(context, messages, prompt);

            // 9. 保存助手消息
            messageService.saveAssistantMessage(sessionId, runId, response);

            // 10. lifecycle.end
            runService.updateStatus(runId, RunStatus.DONE);
            eventBus.publish(AgentEvent.lifecycleEnd(connectionId, runId));

            log.info("Run completed: id={}, strategy={}, response length={}",
                    runId, plan.getStrategyName(), response.length());

            // 11. 自动生成会话标题（首轮对话）
            tryGenerateSessionTitle(connectionId, runId, sessionId, prompt, response);

        } catch (java.util.concurrent.CancellationException e) {
            log.info("Run cancelled: id={}", runId);
            try {
                runService.updateStatus(runId, RunStatus.CANCELED);
            } catch (Exception ignored) {}
            eventBus.publish(AgentEvent.lifecycleError(connectionId, runId, "Cancelled by user"));
        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("Run timed out: id={}", runId);
            try {
                runService.updateStatus(runId, RunStatus.ERROR);
            } catch (Exception ignored) {}
            eventBus.publish(AgentEvent.lifecycleError(connectionId, runId, e.getMessage()));
        } catch (Exception e) {
            log.error("Run failed: id={}", runId, e);
            try {
                runService.updateStatus(runId, RunStatus.ERROR);
            } catch (Exception ignored) {}
            eventBus.publish(AgentEvent.lifecycleError(connectionId, runId, e.getMessage()));
        } finally {
            // 清除搜索结果临时白名单
            toolConfigProperties.clearSearchDiscoveredDomains();
        }
    }

    /**
     * 尝试自动生成会话标题（首轮对话时）
     */
    private void tryGenerateSessionTitle(String connectionId, String runId,
                                          String sessionId, String prompt, String response) {
        try {
            var sessionOpt = sessionService.get(sessionId);
            if (sessionOpt.isEmpty()) return;
            String currentName = sessionOpt.get().getName();
            // 只对默认名称的会话生成标题
            if (!"New Conversation".equals(currentName) && !"New Session".equals(currentName)) return;

            // 异步生成，不阻塞主流程
            CompletableFuture.runAsync(() -> {
                try {
                    String title = generateTitle(prompt, response);
                    if (title != null && !title.isBlank()) {
                        sessionService.rename(sessionId, title);
                        eventBus.publish(AgentEvent.sessionRenamed(connectionId, runId, sessionId, title));
                        log.info("Auto-named session: id={}, title={}", sessionId, title);
                    }
                } catch (Exception e) {
                    log.warn("Failed to auto-name session: id={}", sessionId, e);
                }
            });
        } catch (Exception e) {
            log.warn("Failed to check session for auto-naming: id={}", sessionId, e);
        }
    }

    /**
     * 调用 LLM 生成会话标题
     */
    private String generateTitle(String prompt, String response) {
        String truncatedPrompt = prompt.length() > 500 ? prompt.substring(0, 500) : prompt;
        String truncatedResponse = response.length() > 500 ? response.substring(0, 500) : response;

        LlmRequest request = LlmRequest.builder()
                .messages(List.of(
                        LlmRequest.Message.system("Generate a short title (max 8 words) for this conversation. " +
                                "Return ONLY the title, no quotes, no punctuation at the end. " +
                                "Use the same language as the user."),
                        LlmRequest.Message.user("User: " + truncatedPrompt + "\n\nAssistant: " + truncatedResponse)
                ))
                .maxTokens(30)
                .temperature(0.5)
                .build();

        LlmResponse llmResponse = llmClient.chat(request);
        String title = llmResponse.getContent();
        if (title == null) return null;
        title = title.trim();
        if (title.length() > 80) title = title.substring(0, 77) + "...";
        return title;
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

    @SuppressWarnings("unchecked")
    private Set<String> extractExcludedMcpServers(Object payload) {
        if (payload instanceof Map) {
            Object excluded = ((Map<?, ?>) payload).get("excludedMcpServers");
            if (excluded instanceof List<?> list) {
                Set<String> result = new java.util.HashSet<>();
                for (Object item : list) {
                    if (item != null) {
                        result.add(item.toString());
                    }
                }
                return result.isEmpty() ? null : result;
            }
        }
        return null;
    }

    private String extractDataSourceId(Object payload) {
        if (payload instanceof Map) {
            Object id = ((Map<?, ?>) payload).get("dataSourceId");
            return id != null ? id.toString() : null;
        }
        return null;
    }

    private record PrepareResult(String sessionId, RunEntity run, long sequence, RpcResponse error) {}
}
