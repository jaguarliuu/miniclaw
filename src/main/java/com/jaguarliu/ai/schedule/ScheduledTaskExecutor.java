package com.jaguarliu.ai.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.channel.ChannelService;
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
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 定时任务执行器
 * 创建无头 Agent 会话，执行 prompt，并将结果推送到指定渠道
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledTaskExecutor {

    private final SessionService sessionService;
    private final RunService runService;
    private final MessageService messageService;
    private final AgentRuntime agentRuntime;
    private final ContextBuilder contextBuilder;
    private final ChannelService channelService;
    private final CancellationManager cancellationManager;
    private final LoopConfig loopConfig;
    private final ScheduledTaskRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * 执行定时任务
     */
    public void execute(ScheduledTaskEntity task) {
        log.info("Scheduled task executing: name={}, id={}", task.getName(), task.getId());
        try {
            // 1. 创建专用会话
            SessionEntity session = sessionService.create("[Scheduled] " + task.getName());

            // 2. 创建 run
            RunEntity run = runService.create(session.getId(), task.getPrompt());
            runService.updateStatus(run.getId(), RunStatus.RUNNING);

            // 3. 保存用户消息
            messageService.saveUserMessage(session.getId(), run.getId(), task.getPrompt());

            // 4. 构建消息上下文（无历史）
            List<LlmRequest.Message> messages = contextBuilder.buildMessages(List.of(), task.getPrompt());

            // 5. 构建 scheduled RunContext（runKind = "scheduled"，跳过 HITL）
            RunContext context = RunContext.createScheduled(
                    run.getId(), session.getId(), loopConfig, cancellationManager);

            // 6. 执行 Agent 循环
            String response = agentRuntime.executeLoopWithContext(context, messages, task.getPrompt());

            // 7. 保存助手消息
            messageService.saveAssistantMessage(session.getId(), run.getId(), response);
            runService.updateStatus(run.getId(), RunStatus.DONE);

            // 8. 推送结果到渠道
            pushToChannel(task, response, true, null);

            // 9. 更新任务状态
            task.setLastRunAt(LocalDateTime.now());
            task.setLastRunSuccess(true);
            task.setLastRunError(null);
            repository.save(task);

            log.info("Scheduled task completed: name={}, id={}", task.getName(), task.getId());

        } catch (Exception e) {
            log.error("Scheduled task failed: name={}, error={}", task.getName(), e.getMessage(), e);

            // 推送错误到渠道
            pushToChannel(task, null, false, e.getMessage());

            // 更新任务状态
            task.setLastRunAt(LocalDateTime.now());
            task.setLastRunSuccess(false);
            task.setLastRunError(e.getMessage());
            repository.save(task);
        }
    }

    private void pushToChannel(ScheduledTaskEntity task, String result, boolean success, String error) {
        try {
            if ("email".equals(task.getChannelType())) {
                String subject = success
                        ? "[MiniClaw] " + task.getName() + " - 执行完成"
                        : "[MiniClaw] " + task.getName() + " - 执行失败";
                String body = success ? result : "任务执行失败：\n" + error;
                channelService.sendEmailById(task.getChannelId(), task.getEmailTo(), subject, body, task.getEmailCc());
            } else if ("webhook".equals(task.getChannelType())) {
                Map<String, Object> payload = Map.of(
                        "task", task.getName(),
                        "success", success,
                        "content", success ? result : error,
                        "timestamp", LocalDateTime.now().toString()
                );
                channelService.sendWebhookById(task.getChannelId(), objectMapper.writeValueAsString(payload));
            }
        } catch (Exception e) {
            log.error("Failed to push scheduled task result to channel: {}", e.getMessage());
        }
    }
}
