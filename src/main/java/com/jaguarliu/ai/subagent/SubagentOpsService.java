package com.jaguarliu.ai.subagent;

import com.jaguarliu.ai.gateway.events.AgentEvent;
import com.jaguarliu.ai.gateway.events.EventBus;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.runtime.*;
import com.jaguarliu.ai.session.MessageService;
import com.jaguarliu.ai.session.RunService;
import com.jaguarliu.ai.session.RunStatus;
import com.jaguarliu.ai.session.SessionService;
import com.jaguarliu.ai.storage.entity.RunEntity;
import com.jaguarliu.ai.storage.entity.SessionEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SubAgent 运维服务
 * 提供 list/stop/send 操作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubagentOpsService {

    private final RunService runService;
    private final SessionService sessionService;
    private final MessageService messageService;
    private final CancellationManager cancellationManager;
    private final LaneAwareQueueManager queueManager;
    private final AgentRuntime agentRuntime;
    private final ContextBuilder contextBuilder;
    private final EventBus eventBus;
    private final LoopConfig loopConfig;
    private final SubagentAnnounceService announceService;

    /**
     * 列出指定父运行的所有子代理运行
     *
     * @param parentRunId 父运行 ID
     * @return 子代理运行列表
     */
    public List<RunEntity> listByParentRun(String parentRunId) {
        return runService.listSubagentRuns(parentRunId);
    }

    /**
     * 列出指定会话请求的所有子代理运行
     *
     * @param sessionId 请求方会话 ID
     * @return 子代理运行列表
     */
    public List<RunEntity> listByRequesterSession(String sessionId) {
        return runService.listByRequesterSession(sessionId);
    }

    /**
     * 停止指定的子代理运行
     *
     * @param subRunId 子代理运行 ID
     * @return 停止结果
     */
    public StopResult stop(String subRunId) {
        Optional<RunEntity> runOpt = runService.get(subRunId);
        if (runOpt.isEmpty()) {
            return StopResult.notFound("Run not found: " + subRunId);
        }

        RunEntity run = runOpt.get();

        // 验证是 subagent run
        if (!"subagent".equals(run.getRunKind())) {
            return StopResult.invalidState("Not a subagent run: " + subRunId);
        }

        // 检查状态是否可取消
        RunStatus currentStatus = RunStatus.fromValue(run.getStatus());
        if (!currentStatus.canTransitionTo(RunStatus.CANCELED)) {
            return StopResult.invalidState("Cannot stop run in status: " + currentStatus.getValue());
        }

        // 请求取消
        cancellationManager.requestCancel(subRunId);
        log.info("Subagent stop requested: subRunId={}", subRunId);

        return StopResult.success(subRunId);
    }

    /**
     * 向子代理会话发送新消息（创建新的子 run）
     *
     * @param connectionId   连接 ID
     * @param subSessionId   子会话 ID
     * @param message        消息内容
     * @return 发送结果
     */
    public SendResult send(String connectionId, String subSessionId, String message) {
        if (message == null || message.isBlank()) {
            return SendResult.invalidParams("Message is required");
        }

        Optional<SessionEntity> sessionOpt = sessionService.get(subSessionId);
        if (sessionOpt.isEmpty()) {
            return SendResult.notFound("Session not found: " + subSessionId);
        }

        SessionEntity subSession = sessionOpt.get();

        // 验证是 subagent session
        if (!"subagent".equals(subSession.getSessionKind())) {
            return SendResult.invalidState("Not a subagent session: " + subSessionId);
        }

        try {
            // 创建新的子 run
            RunEntity newRun = runService.createSubagentRun(
                    subSessionId,
                    subSession.getCreatedByRunId(),  // 保持原始父 run
                    subSession.getParentSessionId(), // 保持原始请求会话
                    subSession.getAgentId(),
                    message,
                    false  // send 操作默认不转发流
            );

            // 提交到 subagent lane 执行
            long sequence = queueManager.nextSequence(subSessionId);
            queueManager.submit(
                    subSessionId,
                    newRun.getId(),
                    sequence,
                    LaneAwareQueueManager.LANE_SUBAGENT,
                    () -> {
                        executeSubagentRun(connectionId, newRun, subSession, message);
                        return null;
                    }
            ).subscribe();

            log.info("Subagent send queued: subSessionId={}, newRunId={}, message={}",
                    subSessionId, newRun.getId(), truncate(message, 50));

            return SendResult.success(newRun.getId(), subSessionId);

        } catch (Exception e) {
            log.error("Failed to send to subagent: subSessionId={}", subSessionId, e);
            return SendResult.error("Send failed: " + e.getMessage());
        }
    }

    /**
     * 执行子代理运行（send 触发的）
     */
    private void executeSubagentRun(String connectionId,
                                     RunEntity subRun,
                                     SessionEntity subSession,
                                     String message) {
        String runId = subRun.getId();
        String sessionId = subSession.getId();
        LocalDateTime startTime = LocalDateTime.now();

        try {
            // 1. lifecycle.start
            runService.updateStatus(runId, RunStatus.RUNNING);
            eventBus.publish(AgentEvent.subagentStarted(connectionId, subRun.getParentRunId(), runId));

            // 2. 保存用户消息
            messageService.saveUserMessage(sessionId, runId, message);

            // 3. 获取历史消息构建上下文（包含之前的对话）
            var history = messageService.getSessionHistory(sessionId, 20);
            var historyMessages = history.stream()
                    .filter(m -> !m.getRunId().equals(runId))
                    .map(m -> LlmRequest.Message.builder().role(m.getRole()).content(m.getContent()).build())
                    .toList();
            List<LlmRequest.Message> messages = contextBuilder.buildMessages(historyMessages, message);

            // 4. 创建 subagent RunContext
            RunContext context = RunContext.createSubagent(
                    runId,
                    connectionId,
                    sessionId,
                    subRun.getAgentId(),
                    subRun.getParentRunId(),
                    subRun.getRequesterSessionId(),
                    subRun.getDeliver(),
                    loopConfig,
                    cancellationManager
            );

            // 5. 执行 ReAct 循环
            String response = agentRuntime.executeLoopWithContext(context, messages, message);

            // 6. 保存助手消息
            messageService.saveAssistantMessage(sessionId, runId, response);

            // 7. lifecycle.end
            runService.updateStatus(runId, RunStatus.DONE);

            // 8. Announce（send 触发的也回传）
            announceService.announce(connectionId, subRun, subSession, response, null, startTime);

            log.info("Subagent send run completed: runId={}, response length={}", runId, response.length());

        } catch (java.util.concurrent.CancellationException e) {
            log.info("Subagent send run cancelled: runId={}", runId);
            handleFailure(connectionId, subRun, subSession, "Cancelled by user", startTime);
        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("Subagent send run timed out: runId={}", runId);
            handleFailure(connectionId, subRun, subSession, "Timeout: " + e.getMessage(), startTime);
        } catch (Exception e) {
            log.error("Subagent send run failed: runId={}", runId, e);
            handleFailure(connectionId, subRun, subSession, "Error: " + e.getMessage(), startTime);
        }
    }

    private void handleFailure(String connectionId,
                                RunEntity subRun,
                                SessionEntity subSession,
                                String errorMessage,
                                LocalDateTime startTime) {
        try {
            runService.updateStatus(subRun.getId(), RunStatus.ERROR);
        } catch (Exception ignored) {}

        eventBus.publish(AgentEvent.subagentFailed(
                connectionId,
                subRun.getParentRunId(),
                subRun.getId(),
                errorMessage
        ));

        announceService.announce(connectionId, subRun, subSession, null, errorMessage, startTime);
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return "";
        return str.length() > maxLen ? str.substring(0, maxLen - 3) + "..." : str;
    }

    /**
     * 停止操作结果
     */
    public record StopResult(boolean success, String subRunId, String error, String errorCode) {
        public static StopResult success(String subRunId) {
            return new StopResult(true, subRunId, null, null);
        }

        public static StopResult notFound(String error) {
            return new StopResult(false, null, error, "NOT_FOUND");
        }

        public static StopResult invalidState(String error) {
            return new StopResult(false, null, error, "INVALID_STATE");
        }
    }

    /**
     * 发送操作结果
     */
    public record SendResult(boolean success, String newRunId, String subSessionId, String error, String errorCode) {
        public static SendResult success(String newRunId, String subSessionId) {
            return new SendResult(true, newRunId, subSessionId, null, null);
        }

        public static SendResult notFound(String error) {
            return new SendResult(false, null, null, error, "NOT_FOUND");
        }

        public static SendResult invalidState(String error) {
            return new SendResult(false, null, null, error, "INVALID_STATE");
        }

        public static SendResult invalidParams(String error) {
            return new SendResult(false, null, null, error, "INVALID_PARAMS");
        }

        public static SendResult error(String error) {
            return new SendResult(false, null, null, error, "INTERNAL_ERROR");
        }
    }
}
