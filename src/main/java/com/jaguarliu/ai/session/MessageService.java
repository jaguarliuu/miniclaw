package com.jaguarliu.ai.session;

import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.storage.entity.MessageEntity;
import com.jaguarliu.ai.storage.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Message 服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;

    /**
     * 保存用户消息
     */
    @Transactional
    public MessageEntity saveUserMessage(String sessionId, String runId, String content) {
        return saveMessage(sessionId, runId, "user", content);
    }

    /**
     * 保存助手消息
     */
    @Transactional
    public MessageEntity saveAssistantMessage(String sessionId, String runId, String content) {
        return saveMessage(sessionId, runId, "assistant", content);
    }

    /**
     * 保存子代理 announce 消息到父会话
     *
     * @param parentSessionId 父会话 ID
     * @param parentRunId     父运行 ID（可选，announce 可能在父 run 结束后到达）
     * @param subRunId        子运行 ID
     * @param subSessionId    子会话 ID
     * @param content         announce 内容（JSON 格式）
     * @return 保存的消息实体
     */
    @Transactional
    public MessageEntity saveSubagentAnnounce(String parentSessionId,
                                               String parentRunId,
                                               String subRunId,
                                               String subSessionId,
                                               String content) {
        // 使用特殊的 role 标记这是 subagent 的 announce 消息
        // 前端可以根据 role 或 content 中的 type 来识别并特殊显示
        MessageEntity message = MessageEntity.builder()
                .id(UUID.randomUUID().toString())
                .sessionId(parentSessionId)
                .runId(parentRunId)  // 可能为 null
                .role("assistant")   // 作为 assistant 消息，前端可正常显示
                .content(content)
                .build();

        message = messageRepository.save(message);
        log.info("Saved subagent announce: parentSessionId={}, subRunId={}, subSessionId={}",
                parentSessionId, subRunId, subSessionId);
        return message;
    }

    /**
     * 保存消息
     */
    private MessageEntity saveMessage(String sessionId, String runId, String role, String content) {
        MessageEntity message = MessageEntity.builder()
                .id(UUID.randomUUID().toString())
                .sessionId(sessionId)
                .runId(runId)
                .role(role)
                .content(content)
                .build();

        message = messageRepository.save(message);
        log.debug("Saved message: sessionId={}, role={}, length={}", sessionId, role, content.length());
        return message;
    }

    /**
     * 获取 session 的历史消息
     */
    public List<MessageEntity> getSessionHistory(String sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    /**
     * 获取 session 的历史消息（限制数量，取最近的 N 条）
     */
    public List<MessageEntity> getSessionHistory(String sessionId, int limit) {
        List<MessageEntity> all = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        if (all.size() <= limit) {
            return all;
        }
        // 取最近的 limit 条
        return all.subList(all.size() - limit, all.size());
    }

    /**
     * 将历史消息转换为 LlmRequest.Message 列表
     */
    public List<LlmRequest.Message> toRequestMessages(List<MessageEntity> messages) {
        return messages.stream()
                .map(m -> LlmRequest.Message.builder().role(m.getRole()).content(m.getContent()).build())
                .toList();
    }
}
