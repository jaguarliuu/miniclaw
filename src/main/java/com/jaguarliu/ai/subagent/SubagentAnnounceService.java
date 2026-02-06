package com.jaguarliu.ai.subagent;

import com.jaguarliu.ai.gateway.events.AgentEvent;
import com.jaguarliu.ai.gateway.events.EventBus;
import com.jaguarliu.ai.session.MessageService;
import com.jaguarliu.ai.storage.entity.MessageEntity;
import com.jaguarliu.ai.storage.entity.RunEntity;
import com.jaguarliu.ai.storage.entity.SessionEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SubAgent Announce 服务
 * 负责将子代理完成结果回传到父会话
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubagentAnnounceService {

    private final MessageService messageService;
    private final EventBus eventBus;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Announce 子代理完成结果到父会话
     *
     * @param connectionId     连接 ID
     * @param subRun           子运行实体
     * @param subSession       子会话实体
     * @param result           执行结果（成功时）
     * @param error            错误信息（失败时）
     * @param startTime        开始时间
     */
    public void announce(String connectionId,
                         RunEntity subRun,
                         SessionEntity subSession,
                         String result,
                         String error,
                         LocalDateTime startTime) {
        String parentSessionId = subRun.getRequesterSessionId();
        String parentRunId = subRun.getParentRunId();

        if (parentSessionId == null) {
            log.warn("Cannot announce: parentSessionId is null for subRunId={}", subRun.getId());
            return;
        }

        try {
            // 1. 计算执行时长
            long durationMs = 0;
            if (startTime != null) {
                durationMs = Duration.between(startTime, LocalDateTime.now()).toMillis();
            }

            // 2. 构建 announce 消息内容
            String announceContent = buildAnnounceContent(subRun, subSession, result, error, durationMs);

            // 3. 将 announce 消息写入父会话
            MessageEntity message = messageService.saveSubagentAnnounce(
                    parentSessionId,
                    parentRunId,
                    subRun.getId(),
                    subSession.getId(),
                    announceContent
            );

            // 4. 发布 subagent.announced 事件
            eventBus.publish(AgentEvent.subagentAnnounced(
                    connectionId,
                    parentRunId,
                    subRun.getId(),
                    subSession.getId(),
                    subSession.getSessionKey(),
                    result,
                    error
            ));

            log.info("Announced subagent completion: subRunId={}, parentSessionId={}, success={}, durationMs={}",
                    subRun.getId(), parentSessionId, error == null, durationMs);

        } catch (Exception e) {
            log.error("Failed to announce subagent completion: subRunId={}, parentSessionId={}",
                    subRun.getId(), parentSessionId, e);
            // TODO: 落入 outbox 等待重试（SA-10）
        }
    }

    /**
     * 构建 announce 消息内容
     */
    private String buildAnnounceContent(RunEntity subRun,
                                         SessionEntity subSession,
                                         String result,
                                         String error,
                                         long durationMs) {
        Map<String, Object> announceData = new LinkedHashMap<>();
        announceData.put("type", "subagent_announce");
        announceData.put("subRunId", subRun.getId());
        announceData.put("subSessionId", subSession.getId());
        announceData.put("sessionKey", subSession.getSessionKey());
        announceData.put("task", subRun.getPrompt());
        announceData.put("status", error == null ? "completed" : "failed");
        announceData.put("durationMs", durationMs);

        if (error != null) {
            announceData.put("error", error);
        }

        if (result != null) {
            // 如果结果太长，截取摘要
            String summary = result.length() > 2000
                    ? result.substring(0, 1997) + "..."
                    : result;
            announceData.put("result", summary);
        }

        try {
            return objectMapper.writeValueAsString(announceData);
        } catch (Exception e) {
            log.warn("Failed to serialize announce data", e);
            // 回退到简单格式
            return String.format(
                    "[SubAgent %s] Task: %s | Status: %s | Duration: %dms",
                    error == null ? "Completed" : "Failed",
                    truncate(subRun.getPrompt(), 50),
                    error == null ? "success" : error,
                    durationMs
            );
        }
    }

    /**
     * 截断字符串
     */
    private String truncate(String str, int maxLen) {
        if (str == null) return "";
        return str.length() > maxLen ? str.substring(0, maxLen - 3) + "..." : str;
    }
}
