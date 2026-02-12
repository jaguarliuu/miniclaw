package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.gateway.events.AgentEvent;
import com.jaguarliu.ai.gateway.events.EventBus;
import com.jaguarliu.ai.llm.LlmClient;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.llm.model.ToolCall;
import com.jaguarliu.ai.memory.flush.PreCompactionFlushHook;
import com.jaguarliu.ai.session.SessionFileService;
import com.jaguarliu.ai.skills.selector.SkillSelector;
import com.jaguarliu.ai.skills.selector.SkillSelection;
import com.jaguarliu.ai.subagent.SubagentCompletionTracker;
import com.jaguarliu.ai.tools.ToolDispatcher;
import com.jaguarliu.ai.tools.ToolExecutionContext;
import com.jaguarliu.ai.tools.ToolRegistry;
import com.jaguarliu.ai.tools.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Agent 运行时
 * 负责 ReAct 循环执行：
 * 1. 调用 LLM（带 tools）
 * 2. 如果返回 tool_calls → 执行工具（需要 HITL 的工具先等待确认）
 * 3. 如果返回 [USE_SKILL:xxx] → 激活技能并重新调用
 * 4. 将工具结果追加到上下文
 * 5. 循环直到模型收口或达到限制
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
    private final ContextBuilder contextBuilder;
    private final SkillSelector skillSelector;
    private final PreCompactionFlushHook flushHook;
    private final SubagentCompletionTracker subagentCompletionTracker;
    private final SessionFileService sessionFileService;

    /**
     * 执行 ReAct 多步循环
     *
     * @param connectionId  连接 ID
     * @param runId         运行 ID
     * @param sessionId     会话 ID
     * @param messages      初始消息列表（会被修改）
     * @param originalInput 原始用户输入（用于 skill 激活）
     * @return 最终回复内容
     * @throws CancellationException 如果被取消
     * @throws TimeoutException      如果超时
     */
    public String executeLoop(String connectionId, String runId, String sessionId,
                              List<LlmRequest.Message> messages, String originalInput) throws TimeoutException {
        // 1. 创建运行上下文
        RunContext context = RunContext.create(runId, connectionId, sessionId,
                loopConfig, cancellationManager);
        context.setOriginalInput(originalInput);

        // 2. 注册到取消管理器
        cancellationManager.register(runId);

        try {
            return doExecuteLoop(context, messages);
        } finally {
            // 3. 清理取消标记
            cancellationManager.clearCancellation(runId);
            // 4. 清理 flush 标记
            flushHook.clearRun(runId);
        }
    }

    /**
     * 执行 ReAct 多步循环（兼容旧接口）
     */
    public String executeLoop(String connectionId, String runId, String sessionId,
                              List<LlmRequest.Message> messages) throws TimeoutException {
        // 从消息中提取原始用户输入
        String originalInput = extractOriginalInput(messages);
        return executeLoop(connectionId, runId, sessionId, messages, originalInput);
    }

    /**
     * 执行 ReAct 多步循环（支持排除 MCP 服务器）
     *
     * @param connectionId       连接 ID
     * @param runId              运行 ID
     * @param sessionId          会话 ID
     * @param messages           初始消息列表（会被修改）
     * @param excludedMcpServers 要排除的 MCP 服务器名称集合
     * @return 最终回复内容
     * @throws TimeoutException 如果超时
     */
    public String executeLoop(String connectionId, String runId, String sessionId,
                              List<LlmRequest.Message> messages,
                              Set<String> excludedMcpServers) throws TimeoutException {
        String originalInput = extractOriginalInput(messages);
        return executeLoop(connectionId, runId, sessionId, messages, originalInput, excludedMcpServers);
    }

    /**
     * 执行 ReAct 多步循环（完整版，支持排除 MCP 服务器）
     */
    public String executeLoop(String connectionId, String runId, String sessionId,
                              List<LlmRequest.Message> messages, String originalInput,
                              Set<String> excludedMcpServers) throws TimeoutException {
        // 1. 创建运行上下文
        RunContext context = RunContext.create(runId, connectionId, sessionId,
                loopConfig, cancellationManager);
        context.setOriginalInput(originalInput);
        context.setExcludedMcpServers(excludedMcpServers);

        // 2. 注册到取消管理器
        cancellationManager.register(runId);

        try {
            return doExecuteLoop(context, messages);
        } finally {
            // 3. 清理取消标记
            cancellationManager.clearCancellation(runId);
            // 4. 清理 flush 标记
            flushHook.clearRun(runId);
        }
    }

    /**
     * 执行 ReAct 多步循环（使用预构建的 RunContext）
     * 用于 SubAgent 场景，允许外部构建带有 subagent 元数据的 context
     *
     * @param context       预构建的运行上下文
     * @param messages      初始消息列表（会被修改）
     * @param originalInput 原始用户输入（用于 skill 激活）
     * @return 最终回复内容
     * @throws TimeoutException 如果超时
     */
    public String executeLoopWithContext(RunContext context,
                                          List<LlmRequest.Message> messages,
                                          String originalInput) throws TimeoutException {
        context.setOriginalInput(originalInput);

        // 注册到取消管理器
        cancellationManager.register(context.getRunId());

        try {
            log.info("Starting ReAct loop: runId={}, runKind={}, agentId={}, parentRunId={}",
                    context.getRunId(), context.getRunKind(), context.getAgentId(), context.getParentRunId());
            return doExecuteLoop(context, messages);
        } finally {
            // 清理取消标记
            cancellationManager.clearCancellation(context.getRunId());
            // 清理 flush 标记
            flushHook.clearRun(context.getRunId());
        }
    }

    /**
     * 从消息列表中提取原始用户输入
     */
    private String extractOriginalInput(List<LlmRequest.Message> messages) {
        // 找最后一条 user 消息
        for (int i = messages.size() - 1; i >= 0; i--) {
            LlmRequest.Message msg = messages.get(i);
            if ("user".equals(msg.getRole())) {
                return msg.getContent();
            }
        }
        return "";
    }

    /**
     * 执行循环核心逻辑
     */
    private String doExecuteLoop(RunContext context, List<LlmRequest.Message> messages) throws TimeoutException {
        log.info("Starting ReAct loop: runId={}, maxSteps={}, timeout={}s",
                context.getRunId(), context.getConfig().getMaxSteps(),
                context.getConfig().getRunTimeoutSeconds());

        // 跟踪本次运行中 spawn 的子代理 subRunId（用于屏障等待）
        List<String> pendingSubRunIds = new ArrayList<>();

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

            // Pre-compaction flush 检查（写入全局记忆）
            flushHook.checkAndFlush(context.getRunId(), messages);

            // 执行单步 LLM 调用
            StepResult result = executeSingleStep(context, messages);

            // 如果没有工具调用，检查是否有 skill 自动选择
            if (!result.hasToolCalls()) {
                // 检查 [USE_SKILL:xxx] 标记
                SkillSelection selection = skillSelector.parseFromLlmResponse(
                        result.content(), context.getOriginalInput());

                if (selection.isSelected()) {
                    log.info("Detected skill auto-selection: skill={}, runId={}",
                            selection.getSkillName(), context.getRunId());

                    // 尝试激活 skill 并重新调用
                    Optional<ContextBuilder.SkillAwareRequest> skillRequest =
                            contextBuilder.handleAutoSkillSelection(
                                    result.content(),
                                    context.getOriginalInput(),
                                    extractHistory(messages),
                                    true);

                    if (skillRequest.isPresent()) {
                        // 发布 skill.activated 事件
                        eventBus.publish(AgentEvent.skillActivated(
                                context.getConnectionId(),
                                context.getRunId(),
                                selection.getSkillName(),
                                "auto"));

                        // 用新的请求重新开始循环
                        messages.clear();
                        messages.addAll(skillRequest.get().request().getMessages());
                        context.setActiveSkill(skillRequest.get());
                        context.setSkillBasePath(skillRequest.get().skillBasePath());

                        log.info("Re-invoking with skill: {}, runId={}",
                                selection.getSkillName(), context.getRunId());
                        continue;
                    }
                }

                // ===== SubAgent 屏障：等待所有已 spawn 的子代理完成 =====
                if (!pendingSubRunIds.isEmpty() && context.isMain()) {
                    log.info("Waiting for {} pending subagents before loop exit: runId={}",
                            pendingSubRunIds.size(), context.getRunId());

                    // 先将 LLM 的中间回复加入消息历史
                    messages.add(LlmRequest.Message.assistant(result.content()));

                    // 等待所有子代理完成，收集结果
                    String subagentResultsSummary = waitForPendingSubagents(pendingSubRunIds, context);
                    pendingSubRunIds.clear();

                    // 将子代理结果作为用户消息注入，让 LLM 在下一轮总结
                    messages.add(LlmRequest.Message.user(subagentResultsSummary));

                    log.info("Subagent results injected, continuing loop for summary: runId={}",
                            context.getRunId());
                    continue;
                }

                // 正常结束
                messages.add(LlmRequest.Message.assistant(result.content()));
                log.info("Loop completed normally: runId={}, steps={}",
                        context.getRunId(), context.getCurrentStep());
                return result.content();
            }

            // 有工具调用，执行工具
            log.info("Step {} has {} tool calls: runId={}",
                    context.getCurrentStep(), result.toolCalls().size(), context.getRunId());

            // 记录当前步骤前的消息数（用于 skill 激活时提取干净历史）
            int preStepMessageCount = messages.size();

            messages.add(LlmRequest.Message.assistantWithToolCalls(result.toolCalls()));

            for (ToolCall toolCall : result.toolCalls()) {
                ToolResult toolResult = executeToolCall(context, toolCall);
                messages.add(LlmRequest.Message.toolResult(toolCall.getId(), toolResult.getContent()));

                // 跟踪 sessions_spawn 结果中的 subRunId
                if ("sessions_spawn".equals(toolCall.getName()) && toolResult.isSuccess()) {
                    String subRunId = parseSubRunIdFromToolResult(toolResult.getContent());
                    if (subRunId != null) {
                        pendingSubRunIds.add(subRunId);
                        log.info("Tracked spawned subagent: subRunId={}, runId={}",
                                subRunId, context.getRunId());
                    }
                }
            }

            // ===== use_skill 工具拦截：检测 skill 激活并重启循环 =====
            // 允许激活条件：没有 activeSkill，或 activeSkill 不是真正的 skill（如策略仅用于工具白名单）
            String activatedSkillName = detectUseSkillActivation(result.toolCalls());
            if (activatedSkillName != null
                    && (context.getActiveSkill() == null || !context.getActiveSkill().hasActiveSkill())) {
                log.info("Detected skill activation via use_skill tool: skill={}, runId={}",
                        activatedSkillName, context.getRunId());

                // 提取当前步骤之前的干净历史（不含 use_skill 工具调用和结果）
                List<LlmRequest.Message> cleanHistory = extractHistory(
                        messages.subList(0, preStepMessageCount));

                Optional<ContextBuilder.SkillAwareRequest> skillRequest =
                        contextBuilder.handleSkillActivationByName(
                                activatedSkillName,
                                context.getOriginalInput(),
                                cleanHistory,
                                true);

                if (skillRequest.isPresent()) {
                    // 发布 skill.activated 事件
                    eventBus.publish(AgentEvent.skillActivated(
                            context.getConnectionId(),
                            context.getRunId(),
                            activatedSkillName,
                            "tool"));

                    // 用 skill 上下文重新开始循环
                    messages.clear();
                    messages.addAll(skillRequest.get().request().getMessages());
                    context.setActiveSkill(skillRequest.get());
                    context.setSkillBasePath(skillRequest.get().skillBasePath());

                    log.info("Re-invoking with skill (via tool): {}, runId={}",
                            activatedSkillName, context.getRunId());
                    continue;
                }
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
     * 从消息列表中提取历史消息（排除 system 和最后一条 user）
     */
    private List<LlmRequest.Message> extractHistory(List<LlmRequest.Message> messages) {
        List<LlmRequest.Message> history = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            LlmRequest.Message msg = messages.get(i);
            // 跳过 system 消息
            if ("system".equals(msg.getRole())) {
                continue;
            }
            // 跳过最后一条 user 消息
            if (i == messages.size() - 1 && "user".equals(msg.getRole())) {
                continue;
            }
            history.add(msg);
        }
        return history;
    }

    /**
     * 执行单步（一次 LLM 调用）
     */
    private StepResult executeSingleStep(RunContext context, List<LlmRequest.Message> messages) {
        LlmRequest.LlmRequestBuilder requestBuilder = LlmRequest.builder()
                .messages(messages)
                .toolChoice("auto");

        // 获取排除的 MCP 服务器
        Set<String> excluded = context.getExcludedMcpServers();

        // 如果有激活的 skill，使用其工具限制
        ContextBuilder.SkillAwareRequest activeSkill = context.getActiveSkill();
        Set<String> allowed = (activeSkill != null) ? activeSkill.allowedTools() : null;

        // 获取完整工具列表
        List<Map<String, Object>> tools;
        if (allowed != null && !allowed.isEmpty()) {
            tools = toolRegistry.toOpenAiTools(allowed, excluded);
        } else if (excluded != null && !excluded.isEmpty()) {
            tools = toolRegistry.toOpenAiToolsExcludingServers(excluded);
        } else {
            tools = toolRegistry.toOpenAiTools();
        }

        // skill 已激活时，移除 use_skill 工具（防止 LLM 重复调用）
        if (activeSkill != null && activeSkill.hasActiveSkill()) {
            tools = tools.stream()
                    .filter(t -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> fn = (Map<String, Object>) t.get("function");
                        return fn == null || !"use_skill".equals(fn.get("name"));
                    })
                    .toList();
        }

        requestBuilder.tools(tools);

        return streamLlmCall(context.getConnectionId(), context.getRunId(), requestBuilder.build());
    }

    /**
     * 流式调用 LLM，收集内容和 tool_calls
     */
    private StepResult streamLlmCall(String connectionId, String runId, LlmRequest request) {
        StringBuilder content = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();
        ArtifactStreamExtractor artifactExtractor = new ArtifactStreamExtractor();

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

                    // Artifact 流式提取
                    if ("write_file".equals(chunk.getToolCallFunctionName())) {
                        artifactExtractor.activate();
                    }
                    if (artifactExtractor.isActive() && chunk.getToolCallArgumentsDelta() != null) {
                        var result = artifactExtractor.append(chunk.getToolCallArgumentsDelta());
                        if (result.pathDetected() != null) {
                            eventBus.publish(AgentEvent.artifactOpen(
                                    connectionId, runId, result.pathDetected()));
                        }
                        if (result.contentDelta() != null) {
                            eventBus.publish(AgentEvent.artifactDelta(
                                    connectionId, runId, result.contentDelta()));
                        }
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

        // 定时任务运行时跳过所有 HITL 确认
        boolean isScheduledRun = "scheduled".equals(context.getRunKind());
        // 检查是否需要 HITL 确认（传入参数以便检测危险命令）
        boolean requiresHitl = !isScheduledRun && toolDispatcher.requiresHitl(toolName, null, arguments);

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

        // 设置工具执行上下文（传递 skill 资源目录等信息）
        setupToolExecutionContext(context);

        // 执行工具
        ToolResult result;
        try {
            result = toolDispatcher.dispatch(toolName, arguments).block();
        } finally {
            ToolExecutionContext.clear();
        }

        if (result == null) {
            result = ToolResult.error("Tool execution returned null");
        }

        // 记录 write_file 成功创建的文件
        if ("write_file".equals(toolName) && result.isSuccess()) {
            recordWriteFile(context, arguments);
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
     * 设置工具执行上下文
     * 传递运行时信息（如 skill 资源目录、subagent 元数据）到工具实现
     */
    private void setupToolExecutionContext(RunContext context) {
        ToolExecutionContext.Builder builder = ToolExecutionContext.builder()
                .runId(context.getRunId())
                .sessionId(context.getSessionId())
                .connectionId(context.getConnectionId())
                .agentId(context.getAgentId())
                .runKind(context.getRunKind())
                .parentRunId(context.getParentRunId())
                .depth(context.getDepth());

        // 如果有激活的 skill，添加其资源目录到允许路径
        if (context.getSkillBasePath() != null) {
            builder.addAllowedPath(context.getSkillBasePath());
            log.debug("Added skill resource path to tool context: {}", context.getSkillBasePath());
        }

        ToolExecutionContext.set(builder.build());
    }

    // ==================== 文件追踪辅助方法 ====================

    /**
     * 记录 write_file 成功创建的文件
     */
    private void recordWriteFile(RunContext context, Map<String, Object> arguments) {
        String path = (String) arguments.get("path");
        String content = (String) arguments.get("content");
        if (path == null) return;

        String fileName = Path.of(path).getFileName().toString();
        long size = content != null ? content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length : 0;

        try {
            var entity = sessionFileService.record(
                    context.getSessionId(),
                    context.getRunId(),
                    path,
                    fileName,
                    size
            );

            // 发布 file.created 事件（前端实时更新）
            eventBus.publish(AgentEvent.fileCreated(
                    context.getConnectionId(),
                    context.getRunId(),
                    entity.getId(),
                    path,
                    fileName,
                    size
            ));
        } catch (Exception e) {
            log.warn("Failed to record write_file: path={}, error={}", path, e.getMessage());
        }
    }

    // ==================== Skill 工具激活辅助方法 ====================

    /**
     * 检测 tool_calls 中是否包含 use_skill 调用，提取 skill 名称
     *
     * @param toolCalls LLM 返回的工具调用列表
     * @return 要激活的 skill 名称，如果没有返回 null
     */
    private String detectUseSkillActivation(List<ToolCall> toolCalls) {
        for (ToolCall toolCall : toolCalls) {
            if ("use_skill".equals(toolCall.getName())) {
                Map<String, Object> args = parseArguments(toolCall.getArguments());
                String skillName = (String) args.get("skill_name");
                if (skillName != null && !skillName.isBlank()) {
                    return skillName.trim();
                }
            }
        }
        return null;
    }

    // ==================== SubAgent 屏障辅助方法 ====================

    /**
     * 等待所有已 spawn 的子代理完成，并构建结果摘要
     *
     * @param subRunIds 待等待的子运行 ID 列表
     * @param context   当前运行上下文
     * @return 格式化的子代理结果摘要（注入到 LLM 上下文中）
     */
    private String waitForPendingSubagents(List<String> subRunIds, RunContext context) {
        long remainingSeconds = context.getConfig().getRunTimeoutSeconds() - context.getElapsedSeconds();
        long waitTimeoutSeconds = Math.max(30, Math.min(remainingSeconds, 600));

        List<SubagentCompletionTracker.SubagentResult> results = new ArrayList<>();

        for (String subRunId : subRunIds) {
            if (context.isAborted()) {
                log.info("Subagent barrier aborted by cancellation: runId={}", context.getRunId());
                break;
            }

            CompletableFuture<SubagentCompletionTracker.SubagentResult> future =
                    subagentCompletionTracker.getFuture(subRunId);

            if (future == null) {
                log.warn("No completion future for subRunId={}, skipping", subRunId);
                results.add(new SubagentCompletionTracker.SubagentResult(
                        subRunId, "unknown", "unknown", null, "Completion tracking lost", 0));
                continue;
            }

            try {
                SubagentCompletionTracker.SubagentResult result =
                        future.get(waitTimeoutSeconds, TimeUnit.SECONDS);
                results.add(result);
                log.info("Subagent completed: subRunId={}, status={}", subRunId, result.status());
            } catch (TimeoutException e) {
                log.warn("Timed out waiting for subagent: subRunId={}", subRunId);
                results.add(new SubagentCompletionTracker.SubagentResult(
                        subRunId, "unknown", "timeout", null, "Timed out waiting for result", 0));
            } catch (Exception e) {
                log.error("Error waiting for subagent: subRunId={}", subRunId, e);
                results.add(new SubagentCompletionTracker.SubagentResult(
                        subRunId, "unknown", "error", null, "Error: " + e.getMessage(), 0));
            }
        }

        return formatSubagentResults(results);
    }

    /**
     * 将子代理结果格式化为 LLM 可读的消息
     */
    private String formatSubagentResults(List<SubagentCompletionTracker.SubagentResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("[All spawned SubAgents have completed. Here are their results:]\n\n");

        for (int i = 0; i < results.size(); i++) {
            SubagentCompletionTracker.SubagentResult r = results.get(i);
            sb.append("--- SubAgent ").append(i + 1).append(" ---\n");
            sb.append("Task: ").append(r.task() != null ? r.task() : "unknown").append("\n");
            sb.append("Status: ").append(r.status()).append("\n");

            if (r.isSuccess() && r.result() != null) {
                // 截取结果避免上下文过长
                String resultText = r.result().length() > 2000
                        ? r.result().substring(0, 1997) + "..."
                        : r.result();
                sb.append("Result:\n").append(resultText).append("\n");
            }

            if (!r.isSuccess() && r.error() != null) {
                sb.append("Error: ").append(r.error()).append("\n");
            }

            sb.append("\n");
        }

        sb.append("Please summarize the above subagent results for the user. ");
        sb.append("If any subagent failed, explain what happened. ");
        sb.append("Provide a clear, consolidated response.");

        return sb.toString();
    }

    /**
     * 从 sessions_spawn 工具结果 JSON 中解析 subRunId
     */
    private String parseSubRunIdFromToolResult(String resultContent) {
        if (resultContent == null || resultContent.isBlank()) return null;
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> map = mapper.readValue(resultContent, Map.class);
            Object accepted = map.get("accepted");
            if (Boolean.TRUE.equals(accepted)) {
                return (String) map.get("subRunId");
            }
        } catch (Exception e) {
            log.debug("Failed to parse subRunId from tool result: {}", resultContent, e);
        }
        return null;
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
